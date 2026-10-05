package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.*;

/**
 * The cabinet's insides. Six rail slots hold modules; six door cells hold devices; each device is
 * wired to one channel of one module (an input device to an input or VFD module, a light to an
 * output module, a dial to a VFD module's speed). The power supply module draws from line and
 * neutral and the bus is live while it sees at least fifty volts; without power the inputs read
 * nothing, the lights are dark, the relays open and the drives stop, though a pressed E-stop stays
 * pressed. Every relay contact exists on the terminals whether or not a relay module is there;
 * only a module can close one. A VFD module runs one drive found over the cabinet's Cat6 from
 * its Start, Stop and Reverse inputs and its speed dial, between the minimum and maximum set in
 * the cabinet's screen; a pressed E-stop stops every drive. The computer bindings listen through
 * {@link Listener}.
 */
public class ControlsCabinetBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, INetworkJack, IHaveGoggleInformation {
    /** Replaced by the computer bridges with their network-node subclasses. */
    public static BlockEntityFactory<ControlsCabinetBlockEntity> FACTORY = ControlsCabinetBlockEntity::new;

    public static final int CHANNELS = 8;
    public static final float SENSE = 1_000_000f;
    public static final float OPEN = 1_000_000f;
    public static final float MIN_VOLTS = 50;
    public static final int PULSE_TICKS = 10;
    public static final int DIAL_STEP = 10;
    public static final int[] LED_COLORS = {0x30E040, 0xE03030, 0xF0B020, 0x3070F0, 0xF0F0F0};

    /** What the computer bindings subscribe to. */
    public interface Listener {
        void input(int slot, int channel, boolean state);

        void estop(boolean active);

        void device(int cell, PanelDevice type, int state);
    }

    private DeviceSpliceHost deviceHubs;
    private final JackSupport jack = new JackSupport(this, false);
    private final List<Listener> listeners = new ArrayList<>();

    // No initialisers below are read by buildCircuit.
    private ElectricWire sense;
    private ElectricWire psuLoad;
    private SwitchedWire[] contacts;
    private AcReadings.Filter reading;

    private final ControlModule[] rail = new ControlModule[RAIL];
    private final PanelDevice[] panel = new PanelDevice[CELLS];
    private final int[] wire = new int[CELLS];
    private final int[] color = new int[CELLS];
    private final boolean[] latched = new boolean[CELLS];
    private final int[] selector = new int[CELLS];
    private final int[] pulse = new int[CELLS];
    private final int[] display = new int[CELLS];
    private final int[] dial = new int[CELLS];
    private final boolean[] outputs = new boolean[RAIL * CHANNELS];
    private final boolean[] relays = new boolean[RAIL * RELAY_CHANNELS];
    private final boolean[] inputs = new boolean[RAIL * CHANNELS];
    // VFD modules: the drive each commands, its range, and its run latch.
    private final BlockPos[] vfdTarget = new BlockPos[RAIL];
    private final float[] vfdMin = new float[RAIL];
    private final float[] vfdMax = new float[RAIL];
    private final boolean[] vfdRun = new boolean[RAIL];
    private final boolean[] startWas = new boolean[RAIL];
    private final boolean[] stopWas = new boolean[RAIL];
    private final int[] lastCommand = new int[RAIL];
    /** Drives found over the Cat6, for the screen to choose from; refreshed on the server, synced to the client. */
    private List<BlockPos> drives = new ArrayList<>();
    private boolean[] drivesHertz = new boolean[0];
    private boolean powered;
    private boolean estopWas;
    private float volts;
    private float appliedLoad = -1;
    private boolean dirty;
    private int syncTimer;
    private int commandTimer;

    public ControlsCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(wire, -1);
        Arrays.fill(vfdMin, 5);
        Arrays.fill(vfdMax, 30);
        Arrays.fill(lastCommand, Integer.MIN_VALUE);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var line = builder.terminalNode(TERMINAL_LINE);
        var neutral = builder.terminalNode(TERMINAL_NEUTRAL);
        sense = builder.connect(SENSE, line, neutral);
        psuLoad = builder.connect(OPEN, line, neutral);
        contacts = new SwitchedWire[RAIL * RELAY_CHANNELS];
        for(int slot = 0; slot < RAIL; ++slot) {
            for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                contacts[slot * RELAY_CHANNELS + ch] = builder.connectSwitch(resistance("contact"),
                        builder.terminalNode(relayTerminal(slot, ch, 0)), builder.terminalNode(relayTerminal(slot, ch, 1)), false);
            }
        }
        reading = new AcReadings.Filter();
        appliedLoad = OPEN;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private boolean hasModule(ControlModule module) {
        for(var m : rail)
            if(m == module)
                return true;
        return false;
    }

    private int moduleCount() {
        int n = 0;
        for(var m : rail)
            if(m != null)
                ++n;
        return n;
    }

    @Override
    public void electricalTick() {
        if(sense == null)
            return;
        reading.sample(sense);
        volts = (float) reading.rmsVoltage();
        boolean psu = hasModule(ControlModule.POWER_SUPPLY);
        boolean now = psu && volts >= MIN_VOLTS;
        if(now != powered) {
            powered = now;
            dirty = true;
        }
        // The supply's own draw: fifteen watts and two per module, at whatever voltage it sees.
        float load = psu && volts > 1 ? Math.max(50, volts * volts / (15 + 2 * moduleCount())) : OPEN;
        if(Math.abs(load - appliedLoad) > appliedLoad * 0.01f) {
            psuLoad.setResistance(load);
            appliedLoad = load;
        }
        for(int slot = 0; slot < RAIL; ++slot) {
            boolean relay = rail[slot] == ControlModule.RELAY;
            for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                int i = slot * RELAY_CHANNELS + ch;
                contacts[i].setState(powered && relay && relays[i]);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide)
            return;
        for(int cell = 0; cell < CELLS; ++cell) {
            if(pulse[cell] > 0 && --pulse[cell] == 0)
                dirty = true;
        }
        refreshInputs();
        commandDrives();
        boolean estop = isEStopped();
        if(estop != estopWas) {
            estopWas = estop;
            for(var listener : listeners)
                listener.estop(estop);
        }
        if(dirty && ++syncTimer >= 2) {
            syncTimer = 0;
            dirty = false;
            setChanged();
            sendData();
        }
    }

    /** What a device is doing right now: 0 or 1 for buttons and lights, the position for a selector, the value for a display, percent for a dial. */
    public int deviceState(int cell) {
        var device = panel[cell];
        if(device == null)
            return 0;
        return switch(device) {
            case E_STOP, TOGGLE -> latched[cell] ? 1 : 0;
            case MOMENTARY -> pulse[cell] > 0 ? 1 : 0;
            case SELECTOR -> selector[cell];
            case LED -> powered && wire[cell] >= 0 && outputs[wire[cell]] ? 1 : 0;
            case DISPLAY -> display[cell];
            case DIAL -> dial[cell];
        };
    }

    /** Whether the device in a cell closes the given input channel index (slot * CHANNELS + channel). */
    private boolean drives(int cell, int channel) {
        var device = panel[cell];
        if(device == null || !device.isInput() || wire[cell] < 0)
            return false;
        return switch(device) {
            case E_STOP, TOGGLE -> wire[cell] == channel && latched[cell];
            case MOMENTARY -> wire[cell] == channel && pulse[cell] > 0;
            case SELECTOR -> (selector[cell] == 0 && wire[cell] == channel) || (selector[cell] == 2 && wire[cell] + 1 == channel && (wire[cell] % CHANNELS) < CHANNELS - 1);
            default -> false;
        };
    }

    private void refreshInputs() {
        for(int slot = 0; slot < RAIL; ++slot) {
            boolean digital = rail[slot] == ControlModule.DIGITAL_IN;
            boolean vfd = rail[slot] == ControlModule.VFD;
            for(int ch = 0; ch < CHANNELS; ++ch) {
                int i = slot * CHANNELS + ch;
                boolean state = false;
                if(powered && (digital || (vfd && ch < ControlModule.VFD_SPEED))) {
                    for(int cell = 0; cell < CELLS && !state; ++cell)
                        state = drives(cell, i);
                }
                if(state != inputs[i]) {
                    inputs[i] = state;
                    dirty = true;
                    for(var listener : listeners)
                        listener.input(slot, ch, state);
                }
            }
        }
    }

    // ---- drives ----

    /** The speed dial wired to a VFD module's speed channel, 0 to 100; full speed with none. */
    private int dialPercent(int slot) {
        int target = slot * CHANNELS + ControlModule.VFD_SPEED;
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] == PanelDevice.DIAL && wire[cell] == target)
                return dial[cell];
        return 100;
    }

    /** Start and Stop latch a run on their rising edges; Reverse is a level; an E-stop or a dead bus stops everything. */
    private void commandDrives() {
        if(++commandTimer < 5)
            return;
        commandTimer = 0;
        boolean estop = isEStopped();
        for(int slot = 0; slot < RAIL; ++slot) {
            if(rail[slot] != ControlModule.VFD) {
                vfdRun[slot] = false;
                continue;
            }
            boolean start = inputs[slot * CHANNELS + ControlModule.VFD_START];
            boolean stop = inputs[slot * CHANNELS + ControlModule.VFD_STOP];
            boolean reverse = inputs[slot * CHANNELS + ControlModule.VFD_REVERSE];
            if(start && !startWas[slot])
                vfdRun[slot] = true;
            if(stop && !stopWas[slot])
                vfdRun[slot] = false;
            startWas[slot] = start;
            stopWas[slot] = stop;
            if(!powered || estop)
                vfdRun[slot] = false;
            var target = vfdTarget[slot];
            if(target == null || !level.isLoaded(target))
                continue;
            var be = level.getBlockEntity(target);
            if(!DriveLink.isDrive(be))
                continue;
            float setting = vfdMin[slot] + (vfdMax[slot] - vfdMin[slot]) * dialPercent(slot) / 100f;
            int command = (vfdRun[slot] ? 1 : 0) | (reverse ? 2 : 0) | Math.round(setting * 100) << 2;
            if(command != lastCommand[slot]) {
                lastCommand[slot] = command;
                DriveLink.command(be, vfdRun[slot], reverse, setting);
                dirty = true;
            }
        }
    }

    public @Nullable BlockPos vfdTarget(int slot) {
        return slot >= 0 && slot < RAIL ? vfdTarget[slot] : null;
    }

    public float vfdMin(int slot) {
        return vfdMin[slot];
    }

    public float vfdMax(int slot) {
        return vfdMax[slot];
    }

    public boolean vfdRunning(int slot) {
        return slot >= 0 && slot < RAIL && vfdRun[slot];
    }

    /** Drives found over the Cat6 at the last look, by position. */
    public List<BlockPos> drives() {
        return drives;
    }

    /** Whether the n-th found drive is set in hertz (a three-phase drive) rather than volts. */
    public boolean driveUsesHertz(int index) {
        return index >= 0 && index < drivesHertz.length && drivesHertz[index];
    }

    /** Server side, from the screen. The range is clamped to what the drive can do. */
    public void setVfd(int slot, @Nullable BlockPos target, float min, float max) {
        if(slot < 0 || slot >= RAIL || rail[slot] != ControlModule.VFD)
            return;
        if(target != null && !(level != null && level.isLoaded(target) && DriveLink.isDrive(level.getBlockEntity(target))))
            target = null;
        float ceiling = target == null ? 10_000 : DriveLink.ceiling(level.getBlockEntity(target));
        min = Float.isFinite(min) ? Math.max(0, Math.min(min, ceiling)) : 0;
        max = Float.isFinite(max) ? Math.max(min, Math.min(max, ceiling)) : min;
        vfdTarget[slot] = target;
        vfdMin[slot] = min;
        vfdMax[slot] = max;
        lastCommand[slot] = Integer.MIN_VALUE;
        dirty = true;
        syncTimer = 2;
    }

    private void refreshDrives() {
        var found = DriveLink.discover(level, jack);
        var hertz = new boolean[found.size()];
        for(int i = 0; i < found.size(); ++i)
            hertz[i] = DriveLink.usesHertz(level.getBlockEntity(found.get(i)));
        if(!found.equals(drives) || !Arrays.equals(hertz, drivesHertz)) {
            drives = found;
            drivesHertz = hertz;
            dirty = true;
        }
    }

    // ---- the rail and the door ----

    public @Nullable ControlModule module(int slot) {
        return slot >= 0 && slot < RAIL ? rail[slot] : null;
    }

    public @Nullable PanelDevice device(int cell) {
        return cell >= 0 && cell < CELLS ? panel[cell] : null;
    }

    public int wireOf(int cell) {
        return wire[cell];
    }

    public int colorOf(int cell) {
        return color[cell];
    }

    public int displayValue(int cell) {
        return display[cell];
    }

    public boolean isPowered() {
        return powered;
    }

    public float volts() {
        return volts;
    }

    public boolean isEStopped() {
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] == PanelDevice.E_STOP && latched[cell])
                return true;
        return false;
    }

    public boolean input(int slot, int channel) {
        return slot >= 0 && slot < RAIL && channel >= 0 && channel < CHANNELS && inputs[slot * CHANNELS + channel];
    }

    public boolean output(int slot, int channel) {
        return slot >= 0 && slot < RAIL && channel >= 0 && channel < CHANNELS && outputs[slot * CHANNELS + channel];
    }

    public boolean relay(int slot, int channel) {
        return slot >= 0 && slot < RAIL && channel >= 0 && channel < RELAY_CHANNELS && relays[slot * RELAY_CHANNELS + channel];
    }

    /** Server side, from the computer. Only an output module's channels take a value. */
    public boolean setOutput(int slot, int channel, boolean state) {
        if(slot < 0 || slot >= RAIL || channel < 0 || channel >= CHANNELS || rail[slot] != ControlModule.DIGITAL_OUT)
            return false;
        int i = slot * CHANNELS + channel;
        if(outputs[i] != state) {
            outputs[i] = state;
            dirty = true;
        }
        return true;
    }

    public boolean setRelay(int slot, int channel, boolean state) {
        if(slot < 0 || slot >= RAIL || channel < 0 || channel >= RELAY_CHANNELS || rail[slot] != ControlModule.RELAY)
            return false;
        int i = slot * RELAY_CHANNELS + channel;
        if(relays[i] != state) {
            relays[i] = state;
            dirty = true;
        }
        return true;
    }

    public boolean setDisplay(int cell, int value) {
        if(cell < 0 || cell >= CELLS || panel[cell] != PanelDevice.DISPLAY)
            return false;
        value = Math.max(-999, Math.min(9999, value));
        if(display[cell] != value) {
            display[cell] = value;
            dirty = true;
        }
        return true;
    }

    /** Server side: a player works the device in a cell. */
    public void operate(int cell, Player player) {
        var device = panel[cell];
        if(device == null || level == null)
            return;
        switch(device) {
            case E_STOP -> {
                latched[cell] = !latched[cell];
                level.playSound(null, worldPosition, latched[cell] ? SoundEvents.ANVIL_LAND : SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, latched[cell] ? 1.6f : 0.8f);
            }
            case TOGGLE -> {
                latched[cell] = !latched[cell];
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, latched[cell] ? 0.7f : 0.6f);
            }
            case MOMENTARY -> {
                pulse[cell] = PULSE_TICKS;
                level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.2f);
            }
            case SELECTOR -> {
                selector[cell] = (selector[cell] + 1) % 3;
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.9f);
            }
            case DIAL -> {
                dial[cell] = dial[cell] >= 100 ? 0 : Math.min(100, dial[cell] + DIAL_STEP);
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25f, 1.2f + dial[cell] / 200f);
            }
            default -> {
                return;
            }
        }
        dirty = true;
        syncTimer = 2;
        for(var listener : listeners)
            listener.device(cell, device, deviceState(cell));
    }

    /** Server side. */
    public boolean installDevice(int cell, PanelDevice device, Player player) {
        if(cell < 0 || cell >= CELLS || panel[cell] != null) {
            player.displayClientMessage(Lang.builder().translate("message.controls.cell_used").style(ChatFormatting.RED).component(), true);
            return false;
        }
        panel[cell] = device;
        wire[cell] = -1;
        color[cell] = 0;
        latched[cell] = false;
        selector[cell] = 1;
        pulse[cell] = 0;
        display[cell] = 0;
        dial[cell] = 0;
        autoWire(cell);
        dirty = true;
        syncTimer = 2;
        return true;
    }

    /** Server side: onto the first free rail slot. */
    public boolean installModule(ControlModule module, Player player) {
        for(int slot = 0; slot < RAIL; ++slot) {
            if(rail[slot] == null) {
                rail[slot] = module;
                vfdTarget[slot] = null;
                vfdMin[slot] = 5;
                vfdMax[slot] = 30;
                vfdRun[slot] = false;
                for(int cell = 0; cell < CELLS; ++cell)
                    if(panel[cell] != null && wire[cell] < 0)
                        autoWire(cell);
                if(module == ControlModule.VFD && level != null && drives.size() == 1)
                    vfdTarget[slot] = drives.get(0);
                dirty = true;
                syncTimer = 2;
                return true;
            }
        }
        player.displayClientMessage(Lang.builder().translate("message.controls.rail_full").style(ChatFormatting.RED).component(), true);
        return false;
    }

    private boolean freeTarget(int target, int cell) {
        for(int other = 0; other < CELLS; ++other)
            if(other != cell && wire[other] == target)
                return false;
        return true;
    }

    private boolean wireTo(int cell, ControlModule module, int firstChannel, int lastChannel) {
        for(int slot = 0; slot < RAIL; ++slot) {
            if(rail[slot] != module)
                continue;
            for(int ch = firstChannel; ch <= lastChannel; ++ch) {
                int target = slot * CHANNELS + ch;
                if(freeTarget(target, cell)) {
                    wire[cell] = target;
                    return true;
                }
            }
        }
        return false;
    }

    /** A new device takes the first free channel of the first fitting module, so simple cabinets wire themselves. */
    private void autoWire(int cell) {
        var device = panel[cell];
        if(device == null)
            return;
        if(device == PanelDevice.DIAL) {
            wireTo(cell, ControlModule.VFD, ControlModule.VFD_SPEED, ControlModule.VFD_SPEED);
        } else if(device.isInput()) {
            if(!wireTo(cell, ControlModule.DIGITAL_IN, 0, CHANNELS - 1))
                wireTo(cell, ControlModule.VFD, ControlModule.VFD_START, ControlModule.VFD_REVERSE);
        } else if(device.isOutput()) {
            wireTo(cell, ControlModule.DIGITAL_OUT, 0, CHANNELS - 1);
        }
    }

    /** Whether a device may be wired to slot * CHANNELS + channel. */
    public boolean canWire(int cell, int target) {
        var device = cell >= 0 && cell < CELLS ? panel[cell] : null;
        if(device == null)
            return false;
        if(target < 0)
            return true;
        int slot = target / CHANNELS, ch = target % CHANNELS;
        var module = slot < RAIL ? rail[slot] : null;
        if(device == PanelDevice.DIAL)
            return module == ControlModule.VFD && ch == ControlModule.VFD_SPEED;
        if(device.isInput())
            return module == ControlModule.DIGITAL_IN || (module == ControlModule.VFD && ch < ControlModule.VFD_SPEED);
        return device.isOutput() && module == ControlModule.DIGITAL_OUT;
    }

    /** Server side, from the screen: wire a cell to slot * CHANNELS + channel, or -1 for nothing. */
    public void setWire(int cell, int target) {
        if(!canWire(cell, target))
            return;
        wire[cell] = target;
        dirty = true;
        syncTimer = 2;
    }

    public void setColor(int cell, int index) {
        if(cell < 0 || cell >= CELLS)
            return;
        color[cell] = Math.floorMod(index, LED_COLORS.length);
        dirty = true;
        syncTimer = 2;
    }

    /** Server side. */
    public void removeDevice(int cell, @Nullable Player player) {
        if(cell < 0 || cell >= CELLS || panel[cell] == null || level == null)
            return;
        drop(ModItems.PANEL_DEVICES.get(panel[cell]).asStack());
        panel[cell] = null;
        wire[cell] = -1;
        latched[cell] = false;
        pulse[cell] = 0;
        dirty = true;
        syncTimer = 2;
    }

    /** Server side: the last module on the rail comes off. */
    public void removeLastModule(@Nullable Player player) {
        for(int slot = RAIL - 1; slot >= 0; --slot) {
            if(rail[slot] != null) {
                removeModule(slot, player);
                return;
            }
        }
    }

    public void removeModule(int slot, @Nullable Player player) {
        if(slot < 0 || slot >= RAIL || rail[slot] == null || level == null)
            return;
        if(rail[slot] == ControlModule.VFD && vfdTarget[slot] != null && level.isLoaded(vfdTarget[slot])) {
            var be = level.getBlockEntity(vfdTarget[slot]);
            if(DriveLink.isDrive(be))
                DriveLink.command(be, false, false, 0);
        }
        drop(ModItems.CONTROL_MODULES.get(rail[slot]).asStack());
        rail[slot] = null;
        vfdTarget[slot] = null;
        vfdRun[slot] = false;
        for(int cell = 0; cell < CELLS; ++cell)
            if(wire[cell] >= 0 && wire[cell] / CHANNELS == slot)
                wire[cell] = -1;
        for(int ch = 0; ch < CHANNELS; ++ch)
            outputs[slot * CHANNELS + ch] = false;
        for(int ch = 0; ch < RELAY_CHANNELS; ++ch)
            relays[slot * RELAY_CHANNELS + ch] = false;
        dirty = true;
        syncTimer = 2;
    }

    private void drop(net.minecraft.world.item.ItemStack stack) {
        var at = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing(getBlockState()).getNormal()).scale(0.6));
        Containers.dropItemStack(level, at.x, at.y, at.z, stack);
    }

    @Override
    public void destroy() {
        super.destroy();
        if(level == null || level.isClientSide)
            return;
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] != null)
                drop(ModItems.PANEL_DEVICES.get(panel[cell]).asStack());
        for(int slot = 0; slot < RAIL; ++slot)
            if(rail[slot] != null)
                drop(ModItems.CONTROL_MODULES.get(rail[slot]).asStack());
    }

    // ---- jack ----

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(hasModule(ControlModule.VFD) || !drives.isEmpty())
            refreshDrives();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return IElectric.getTerminalPos(level, worldPosition, JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return JACK;
    }

    // ---- persistence ----

    private static byte[] bytes(boolean[] values) {
        var out = new byte[values.length];
        for(int i = 0; i < values.length; ++i)
            out[i] = (byte) (values[i] ? 1 : 0);
        return out;
    }

    private static void fill(boolean[] values, byte[] in) {
        for(int i = 0; i < values.length; ++i)
            values[i] = i < in.length && in[i] != 0;
    }

    private static void fill(int[] values, int[] in, int fallback) {
        for(int i = 0; i < values.length; ++i)
            values[i] = i < in.length ? in[i] : fallback;
    }

    private static int[] tenths(float[] values) {
        var out = new int[values.length];
        for(int i = 0; i < values.length; ++i)
            out[i] = Math.round(values[i] * 10);
        return out;
    }

    private static void fillTenths(float[] values, int[] in, float fallback) {
        for(int i = 0; i < values.length; ++i)
            values[i] = i < in.length ? in[i] / 10f : fallback;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        var railTag = new int[RAIL];
        for(int i = 0; i < RAIL; ++i)
            railTag[i] = rail[i] == null ? -1 : rail[i].ordinal();
        var panelTag = new int[CELLS];
        for(int i = 0; i < CELLS; ++i)
            panelTag[i] = panel[i] == null ? -1 : panel[i].ordinal();
        var targets = new long[RAIL];
        for(int i = 0; i < RAIL; ++i)
            targets[i] = vfdTarget[i] == null ? Long.MIN_VALUE : vfdTarget[i].asLong();
        tag.putIntArray("Rail", railTag);
        tag.putIntArray("Panel", panelTag);
        tag.putIntArray("Wire", wire);
        tag.putIntArray("Color", color);
        tag.putByteArray("Latched", bytes(latched));
        tag.putIntArray("Selector", selector);
        tag.putIntArray("Display", display);
        tag.putIntArray("Dial", dial);
        tag.putByteArray("Outputs", bytes(outputs));
        tag.putByteArray("Relays", bytes(relays));
        tag.putLongArray("VfdTarget", targets);
        tag.putIntArray("VfdMin", tenths(vfdMin));
        tag.putIntArray("VfdMax", tenths(vfdMax));
        tag.putByteArray("VfdRun", bytes(vfdRun));
        if(clientPacket) {
            tag.putByteArray("Inputs", bytes(inputs));
            tag.putIntArray("Pulse", pulse);
            tag.putBoolean("Powered", powered);
            tag.putFloat("Volts", volts);
            var found = new long[drives.size()];
            for(int i = 0; i < found.length; ++i)
                found[i] = drives.get(i).asLong();
            tag.putLongArray("Drives", found);
            tag.putByteArray("DrivesHertz", bytes(drivesHertz));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        var railTag = tag.getIntArray("Rail");
        for(int i = 0; i < RAIL; ++i)
            rail[i] = i < railTag.length ? ControlModule.fromOrdinal(railTag[i]) : null;
        var panelTag = tag.getIntArray("Panel");
        for(int i = 0; i < CELLS; ++i)
            panel[i] = i < panelTag.length ? PanelDevice.fromOrdinal(panelTag[i]) : null;
        fill(wire, tag.getIntArray("Wire"), -1);
        fill(color, tag.getIntArray("Color"), 0);
        fill(latched, tag.getByteArray("Latched"));
        fill(selector, tag.getIntArray("Selector"), 1);
        fill(display, tag.getIntArray("Display"), 0);
        fill(dial, tag.getIntArray("Dial"), 0);
        fill(outputs, tag.getByteArray("Outputs"));
        fill(relays, tag.getByteArray("Relays"));
        var targets = tag.getLongArray("VfdTarget");
        for(int i = 0; i < RAIL; ++i)
            vfdTarget[i] = i < targets.length && targets[i] != Long.MIN_VALUE ? BlockPos.of(targets[i]) : null;
        fillTenths(vfdMin, tag.getIntArray("VfdMin"), 5);
        fillTenths(vfdMax, tag.getIntArray("VfdMax"), 30);
        fill(vfdRun, tag.getByteArray("VfdRun"));
        if(clientPacket) {
            fill(inputs, tag.getByteArray("Inputs"));
            fill(pulse, tag.getIntArray("Pulse"), 0);
            powered = tag.getBoolean("Powered");
            volts = tag.getFloat("Volts");
            var found = tag.getLongArray("Drives");
            var list = new ArrayList<BlockPos>(found.length);
            for(long l : found)
                list.add(BlockPos.of(l));
            drives = list;
            drivesHertz = new boolean[found.length];
            fill(drivesHertz, tag.getByteArray("DrivesHertz"));
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.controls.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(powered ? "gui.controls.powered" : "gui.controls.unpowered", String.format("%.0f", volts))
                .style(powered ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        if(isEStopped())
            Lang.builder().translate("gui.controls.estopped").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.controls.modules", moduleCount(), RAIL).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        for(int slot = 0; slot < RAIL; ++slot) {
            if(rail[slot] == ControlModule.VFD)
                Lang.builder().translate(vfdRun[slot] ? "gui.controls.vfd_running" : "gui.controls.vfd_stopped", slot + 1)
                        .style(vfdRun[slot] ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
