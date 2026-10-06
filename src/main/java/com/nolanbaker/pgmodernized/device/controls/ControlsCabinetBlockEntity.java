package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.device.controls.plc.DeviceBridge;
import com.nolanbaker.pgmodernized.device.controls.plc.NodeType;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcGraph;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcProgram;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcRunner;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import net.minecraft.world.phys.AABB;
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
import java.util.Locale;
import java.util.Map;
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
public class ControlsCabinetBlockEntity extends ElectricBlockEntity implements ISpliceHost, INetworkJack, IHaveGoggleInformation {
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

    private SpliceSupport splices;
    /** The rail read differs from what the circuit was built for; rebuilt on the next tick, once the behaviour exists. */
    private boolean railDirty;
    /** The head plus the extensions below it, from the block state. */
    private int sections = 1;
    private final JackSupport jack = new JackSupport(this, false);
    private final List<Listener> listeners = new ArrayList<>();

    // No initialisers below are read by buildCircuit.
    private ElectricWire sense;
    private ElectricWire psuLoad;
    private SwitchedWire[] contacts;
    private AcReadings.Filter reading;
    // Input and output module terminals: a sense to neutral per channel, and a contact from line per channel.
    private ElectricWire[] ioSense;
    private SwitchedWire[] ioOut;
    private AcReadings.Filter[] ioReading;
    private ElectricWire[] aiSense;
    private AcReadings.Filter[] aiReading;

    private final ControlModule[] rail = new ControlModule[MAX_RAIL];
    private final PanelDevice[] panel = new PanelDevice[MAX_CELLS];
    private final int[] wire = new int[MAX_CELLS];
    private final int[] color = new int[MAX_CELLS];
    private final boolean[] latched = new boolean[MAX_CELLS];
    private final int[] selector = new int[MAX_CELLS];
    private final int[] pulse = new int[MAX_CELLS];
    private final int[] display = new int[MAX_CELLS];
    private final int[] dial = new int[MAX_CELLS];
    private final boolean[] outputs = new boolean[MAX_RAIL * CHANNELS];
    private final boolean[] relays = new boolean[MAX_RAIL * RELAY_CHANNELS];
    private final boolean[] inputs = new boolean[MAX_RAIL * CHANNELS];
    /** Input channels driven from their terminal, by voltage against neutral. */
    private final boolean[] external = new boolean[MAX_RAIL * CHANNELS];
    /** Analog inputs: volts on the terminal, or a dial's percent; analog outputs as set. */
    private final float[] aiVolts = new float[MAX_RAIL * ANALOG_CHANNELS];
    private final float[] analogIn = new float[MAX_RAIL * ANALOG_CHANNELS];
    private final float[] analogOut = new float[MAX_RAIL * ANALOG_CHANNELS];
    // VFD modules: the drive each commands, its range, and its run latch.
    private final BlockPos[] vfdTarget = new BlockPos[MAX_RAIL];
    private final float[] vfdMin = new float[MAX_RAIL];
    private final float[] vfdMax = new float[MAX_RAIL];
    private final boolean[] vfdRun = new boolean[MAX_RAIL];
    private final boolean[] startWas = new boolean[MAX_RAIL];
    private final boolean[] stopWas = new boolean[MAX_RAIL];
    private final int[] lastCommand = new int[MAX_RAIL];
    /** Drives found over the Cat6, for the screen to choose from; refreshed on the server, synced to the client. */
    private List<BlockPos> drives = new ArrayList<>();
    private boolean[] drivesHertz = new boolean[0];
    // The PLC: its program, the compiled form, what it told the VFD modules, and the devices it can see.
    private PlcGraph graph = new PlcGraph();
    private CompoundTag graphTag = graph.save();
    private int plcRevision;
    @Nullable
    private PlcRunner plc;
    private float[] plcValues = new float[0];
    private List<String> plcMessages = List.of();
    private String plcError = "";
    private boolean plcCompiled;
    @Nullable
    private DeviceBridge bridge;
    private final boolean[] plcVfd = new boolean[MAX_RAIL * 3];
    private final float[] plcSpeed = new float[MAX_RAIL];
    private List<List<String>> plcDevices = new ArrayList<>();
    private final PlcIo plcIo = new PlcIo();
    /** The N and C tags: the cabinet's own, shared with the program, the external port and any HMI; saved. */
    private final Map<String, Double> plcBits = new java.util.HashMap<>();
    /** The S tags: text the program, a computer or an HMI can read; saved. */
    private final Map<String, String> plcText = new java.util.HashMap<>();
    private boolean powered;
    private boolean estopWas;
    private float volts;
    private float appliedLoad = -1;
    private boolean dirty;
    private int syncTimer;
    private int commandTimer;

    public ControlsCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        sections = ControlsCabinetBlock.sectionsOf(state);
        Arrays.fill(wire, -1);
        Arrays.fill(vfdMin, 5);
        Arrays.fill(vfdMax, 30);
        Arrays.fill(lastCommand, Integer.MIN_VALUE);
        Arrays.fill(plcSpeed, -1);
        setLazyTickRate(10);
    }

    // ---- sections ----

    public int sections() {
        return sections;
    }

    /** Rail slots the cabinet has with its present extensions. */
    public int slots() {
        return RAIL * sections;
    }

    /** Door cells the cabinet has with its present extensions. */
    public int cells() {
        return CELLS * sections;
    }

    /** Whether any of the head's bottom knockouts carries a run, which an extension would cover. */
    public boolean bottomHubsUsed() {
        for(int h = 4; h < 8; ++h)
            if(SpliceSupport.runAt(this, CabinetLayout.hubTerminal(h)) != null)
                return true;
        return false;
    }

    /** An extension came or went: what sat beyond the new end comes out, and the circuit gains or loses its terminals. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        int now = ControlsCabinetBlock.sectionsOf(state);
        if(now == sections)
            return;
        sections = now;
        if(level != null && !level.isClientSide) {
            for(int slot = MAX_RAIL - 1; slot >= slots(); --slot)
                if(rail[slot] != null)
                    removeModule(slot, null);
            for(int cell = MAX_CELLS - 1; cell >= cells(); --cell)
                if(panel[cell] != null)
                    removeDevice(cell, null);
            var behaviour = getBehaviour(ElectricBehaviour.TYPE);
            if(behaviour != null)
                behaviour.rebuildCircuit(false);
            plcCompiled = false;
            dirty = true;
            syncTimer = 2;
            notifyUpdate();
        }
    }

    /** The extensions' devices are drawn by this block entity, a block or two below it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY() - (sections - 1), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
    }

    // ---- splice host over the head and its extensions ----

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public boolean isPoint(int terminal) {
        return CabinetLayout.isPoint(terminal) && CabinetLayout.sectionOf(terminal) < sections;
    }

    @Override
    public int hubCount() {
        return CabinetLayout.hubCount(sections);
    }

    @Override
    public Component hubName(int hub) {
        return DeviceHubs.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return CabinetLayout.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return CabinetLayout.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return CabinetLayout.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return CabinetLayout.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return CabinetLayout.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= hubCount() ? null : SpliceSupport.runAt(this, CabinetLayout.hubTerminal(hub));
    }

    /**
     * Line, neutral, the contacts of the slots that hold a relay module, and the channel terminals
     * of input and output modules that no door device is wired to, over the head and its extensions.
     */
    @Override
    public List<SplicePoint> points() {
        var shown = new ArrayList<SplicePoint>();
        var state = getBlockState();
        if(!(state.getBlock() instanceof ControlsCabinetBlock block))
            return shown;
        for(int terminal : CabinetLayout.points(sections)) {
            int relaySlot = CabinetLayout.relaySlot(terminal);
            if(relaySlot >= 0 && rail[relaySlot] != ControlModule.RELAY)
                continue;
            int channel = CabinetLayout.ioChannel(terminal);
            if(channel >= 0) {
                var module = rail[channel / CHANNELS];
                if((module != ControlModule.DIGITAL_IN && module != ControlModule.DIGITAL_OUT) || channelWired(channel))
                    continue;
            }
            int analog = CabinetLayout.aiIndex(terminal);
            if(analog >= 0) {
                int slot = analog / ANALOG_CHANNELS, ch = analog % ANALOG_CHANNELS;
                if(rail[slot] != ControlModule.ANALOG_IN || channelWired(slot * CHANNELS + ch))
                    continue;
            }
            var placement = block.terminal(state, terminal);
            Component name = placement instanceof IDecoratedTerminal decorated && decorated.getName() != null
                    ? decorated.getName() : Component.literal("#" + terminal);
            int rgb = placement instanceof IDecoratedTerminal decorated ? decorated.getColor() : 0xAAAAAA;
            shown.add(new SplicePoint(terminal, name, rgb));
        }
        return shown;
    }

    /** Whether a door device is wired to that channel index (a selector takes the next channel too). */
    private boolean channelWired(int channel) {
        for(int cell = 0; cell < MAX_CELLS; ++cell) {
            if(panel[cell] == null || wire[cell] < 0)
                continue;
            if(wire[cell] == channel)
                return true;
            if(panel[cell] == PanelDevice.SELECTOR && wire[cell] + 1 == channel && wire[cell] % CHANNELS < CHANNELS - 1)
                return true;
        }
        return false;
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
        builder.setTerminalCount(CabinetLayout.TERMINAL_COUNT);
        splices().buildCircuit(builder);
        var line = builder.terminalNode(TERMINAL_LINE);
        var neutral = builder.terminalNode(TERMINAL_NEUTRAL);
        sense = builder.connect(SENSE, line, neutral);
        psuLoad = builder.connect(OPEN, line, neutral);
        // Only a fitted module puts wires in the circuit: a slot's contacts, channel senses and
        // output contacts exist while its module does, and the circuit is rebuilt when the rail
        // changes. (Wiring every possible slot made some four hundred wires, and the client's
        // network builder froze the game over them.) The rail is still null from the constructor.
        var modules = rail == null ? new ControlModule[MAX_RAIL] : rail;
        contacts = new SwitchedWire[MAX_RAIL * RELAY_CHANNELS];
        ioSense = new ElectricWire[MAX_RAIL * CHANNELS];
        ioOut = new SwitchedWire[MAX_RAIL * CHANNELS];
        ioReading = new AcReadings.Filter[MAX_RAIL * CHANNELS];
        aiSense = new ElectricWire[MAX_RAIL * ANALOG_CHANNELS];
        aiReading = new AcReadings.Filter[MAX_RAIL * ANALOG_CHANNELS];
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            var module = modules[slot];
            if(module == ControlModule.RELAY) {
                for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                    contacts[slot * RELAY_CHANNELS + ch] = builder.connectSwitch(resistance("contact"),
                            builder.terminalNode(CabinetLayout.relayTerminal(slot, ch, 0)), builder.terminalNode(CabinetLayout.relayTerminal(slot, ch, 1)), false);
                }
            } else if(module == ControlModule.DIGITAL_IN) {
                for(int ch = 0; ch < CHANNELS; ++ch) {
                    int i = slot * CHANNELS + ch;
                    ioSense[i] = builder.connect(SENSE, builder.terminalNode(CabinetLayout.ioTerminal(slot, ch)), neutral);
                    ioReading[i] = new AcReadings.Filter();
                }
            } else if(module == ControlModule.DIGITAL_OUT) {
                for(int ch = 0; ch < CHANNELS; ++ch)
                    ioOut[slot * CHANNELS + ch] = builder.connectSwitch(resistance("contact"), line, builder.terminalNode(CabinetLayout.ioTerminal(slot, ch)), false);
            } else if(module == ControlModule.ANALOG_IN) {
                for(int ch = 0; ch < ANALOG_CHANNELS; ++ch) {
                    int i = slot * ANALOG_CHANNELS + ch;
                    aiSense[i] = builder.connect(SENSE, builder.terminalNode(CabinetLayout.aiTerminal(slot, ch)), neutral);
                    aiReading[i] = new AcReadings.Filter();
                }
            }
        }
        reading = new AcReadings.Filter();
        appliedLoad = OPEN;
    }

    /** The rail changed: the circuit gets the fitted modules' wires. */
    private void rebuildCircuit() {
        var behaviour = getBehaviour(ElectricBehaviour.TYPE);
        if(behaviour != null)
            behaviour.rebuildCircuit(false);
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
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            boolean relay = rail[slot] == ControlModule.RELAY;
            for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                int i = slot * RELAY_CHANNELS + ch;
                if(contacts[i] != null)
                    contacts[i].setState(powered && relay && relays[i]);
            }
            // Output terminals source line while their channel is on; input terminals read against neutral.
            boolean in = rail[slot] == ControlModule.DIGITAL_IN, out = rail[slot] == ControlModule.DIGITAL_OUT;
            for(int ch = 0; ch < CHANNELS; ++ch) {
                int i = slot * CHANNELS + ch;
                if(ioOut[i] != null)
                    ioOut[i].setState(powered && out && outputs[i]);
                if(in && powered && ioSense[i] != null) {
                    ioReading[i].sample(ioSense[i]);
                    external[i] = ioReading[i].rmsVoltage() >= MIN_VOLTS;
                } else {
                    external[i] = false;
                }
            }
            boolean analog = rail[slot] == ControlModule.ANALOG_IN;
            for(int ch = 0; ch < ANALOG_CHANNELS; ++ch) {
                int i = slot * ANALOG_CHANNELS + ch;
                if(analog && powered && aiSense[i] != null) {
                    aiReading[i].sample(aiSense[i]);
                    aiVolts[i] = (float) aiReading[i].rmsVoltage();
                } else {
                    aiVolts[i] = 0;
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(railDirty) {
            railDirty = false;
            rebuildCircuit();
        }
        if(level == null || level.isClientSide)
            return;
        for(int cell = 0; cell < MAX_CELLS; ++cell) {
            if(pulse[cell] > 0 && --pulse[cell] == 0)
                dirty = true;
        }
        refreshInputs();
        refreshAnalog();
        scanPlc();
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

    /** Analog inputs follow a wired dial or their terminal; displays wired to an analog output follow it. */
    private void refreshAnalog() {
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            for(int ch = 0; ch < ANALOG_CHANNELS; ++ch) {
                int i = slot * ANALOG_CHANNELS + ch;
                float value = 0;
                if(powered && rail[slot] == ControlModule.ANALOG_IN) {
                    value = aiVolts[i];
                    for(int cell = 0; cell < MAX_CELLS; ++cell)
                        if(panel[cell] == PanelDevice.DIAL && wire[cell] == slot * CHANNELS + ch)
                            value = dial[cell];
                }
                analogIn[i] = value;
            }
        }
        for(int cell = 0; cell < MAX_CELLS; ++cell) {
            if(panel[cell] != PanelDevice.DISPLAY || wire[cell] < 0)
                continue;
            int slot = wire[cell] / CHANNELS, ch = wire[cell] % CHANNELS;
            if(rail[slot] != ControlModule.ANALOG_OUT || ch >= ANALOG_CHANNELS)
                continue;
            int shown = powered ? Math.round(analogOut[slot * ANALOG_CHANNELS + ch]) : 0;
            shown = Math.max(-999, Math.min(9999, shown));
            if(display[cell] != shown) {
                display[cell] = shown;
                dirty = true;
            }
        }
    }

    private void refreshInputs() {
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            boolean digital = rail[slot] == ControlModule.DIGITAL_IN;
            boolean vfd = rail[slot] == ControlModule.VFD;
            for(int ch = 0; ch < CHANNELS; ++ch) {
                int i = slot * CHANNELS + ch;
                boolean state = false;
                if(powered && (digital || (vfd && ch < ControlModule.VFD_SPEED))) {
                    for(int cell = 0; cell < MAX_CELLS && !state; ++cell)
                        state = drives(cell, i);
                    if(digital && external[i])
                        state = true;
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
        for(int cell = 0; cell < MAX_CELLS; ++cell)
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
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            if(rail[slot] != ControlModule.VFD) {
                vfdRun[slot] = false;
                continue;
            }
            boolean start = inputs[slot * CHANNELS + ControlModule.VFD_START] || plcVfd[slot * 3 + ControlModule.VFD_START];
            boolean stop = inputs[slot * CHANNELS + ControlModule.VFD_STOP] || plcVfd[slot * 3 + ControlModule.VFD_STOP];
            boolean reverse = inputs[slot * CHANNELS + ControlModule.VFD_REVERSE] || plcVfd[slot * 3 + ControlModule.VFD_REVERSE];
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
            float percent = plcSpeed[slot] >= 0 ? plcSpeed[slot] : dialPercent(slot);
            float setting = vfdMin[slot] + (vfdMax[slot] - vfdMin[slot]) * Math.max(0, Math.min(100, percent)) / 100f;
            int command = (vfdRun[slot] ? 1 : 0) | (reverse ? 2 : 0) | Math.round(setting * 100) << 2;
            if(command != lastCommand[slot]) {
                lastCommand[slot] = command;
                DriveLink.command(be, vfdRun[slot], reverse, setting);
                dirty = true;
            }
        }
    }

    public @Nullable BlockPos vfdTarget(int slot) {
        return slot >= 0 && slot < MAX_RAIL ? vfdTarget[slot] : null;
    }

    public float vfdMin(int slot) {
        return vfdMin[slot];
    }

    public float vfdMax(int slot) {
        return vfdMax[slot];
    }

    public boolean vfdRunning(int slot) {
        return slot >= 0 && slot < MAX_RAIL && vfdRun[slot];
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
        if(slot < 0 || slot >= MAX_RAIL || rail[slot] != ControlModule.VFD)
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

    // ---- the PLC ----

    /** The OpenComputers binding supplies the view of the internal network. */
    public void setDeviceBridge(@Nullable DeviceBridge bridge) {
        this.bridge = bridge;
    }

    /** The program as drawn; the editor copies it. */
    public PlcGraph graph() {
        return graph;
    }

    public boolean graphEmpty() {
        return graph.isEmpty();
    }

    /** Counts up whenever the program changes, so screens know to rebuild. */
    public int plcRevision() {
        return plcRevision;
    }

    /** Every block's output pins, in block order, as last synced. */
    public float[] plcValues() {
        return plcValues;
    }

    public List<String> plcMessages() {
        return plcMessages;
    }

    /** The text of the first rung block, for computers on the external port. */
    public String rungsText() {
        for(var node : graph.nodes)
            if(node.type == NodeType.RUNGS)
                return node.text;
        return "";
    }

    /** Server side: writes the first rung block, adding one when there is none. */
    public void setRungsText(String text) {
        PlcGraph.Node rungs = null;
        for(var node : graph.nodes)
            if(node.type == NodeType.RUNGS) {
                rungs = node;
                break;
            }
        if(rungs == null)
            rungs = graph.add(NodeType.RUNGS, 20, 20);
        if(rungs == null)
            return;
        rungs.text = text == null ? "" : text;
        setGraph(graph.save());
    }

    public String plcError() {
        return plcError;
    }

    public boolean hasPlc() {
        return hasModule(ControlModule.PLC);
    }

    /** Devices the PLC can see, each as alias, type, then its method names; synced for the screen. */
    public List<List<String>> plcDevices() {
        return plcDevices;
    }

    public Map<String, Double> plcBits() {
        return plcBits;
    }

    private static boolean isBitName(String name) {
        if(name.length() < 2)
            return false;
        char kind = name.charAt(0);
        return (kind == 'C' || kind == 'N' || kind == 'T') && name.substring(1).chars().allMatch(Character::isDigit);
    }

    /** Server side: a new program, compiled now; the error is kept for the screen and the port. */
    public void setGraph(CompoundTag tag) {
        graph = PlcGraph.load(tag);
        graph.sanitise();
        graphTag = graph.save();
        ++plcRevision;
        compilePlc();
        dirty = true;
        syncTimer = 2;
    }

    private void compilePlc() {
        plcCompiled = true;
        Arrays.fill(plcVfd, false);
        Arrays.fill(plcSpeed, -1);
        if(graph.isEmpty()) {
            plc = null;
            plcError = "";
            return;
        }
        try {
            plc = PlcRunner.compile(graph, plcIo, () -> plcDevices, plcBits);
            plcError = "";
        } catch(PlcRunner.CompileError e) {
            plc = null;
            var node = graph.node(e.node);
            plcError = "#" + e.node + " " + (node == null ? "" : node.type.title) + ": " + e.getMessage();
        }
    }

    private void scanPlc() {
        if(!plcCompiled)
            compilePlc();
        if(plc == null || !hasPlc() || !powered) {
            Arrays.fill(plcVfd, false);
            Arrays.fill(plcSpeed, -1);
            return;
        }
        plc.scan();
        var error = plc.lastError();
        if(!error.equals(plcError)) {
            plcError = error;
            dirty = true;
        }
        if(level != null && level.getGameTime() % 10 == 0) {
            var values = plc.values();
            var messages = plc.messages();
            if(!Arrays.equals(values, plcValues) || !messages.equals(plcMessages)) {
                plcValues = values;
                plcMessages = messages;
                dirty = true;
            }
        }
    }

    /** A name the program or the external port reads: NaN when unknown. */
    /** S1, S2, ...: the text tags. */
    public static boolean isTextName(String name) {
        return name.length() > 1 && name.charAt(0) == 'S' && name.substring(1).chars().allMatch(Character::isDigit);
    }

    /** A text tag's value, empty when unwritten; null for a name that is not a text tag. */
    @Nullable
    public String plcReadText(String name) {
        name = name.toUpperCase(Locale.ROOT);
        return isTextName(name) ? plcText.getOrDefault(name, "") : null;
    }

    public boolean plcWriteText(String name, String text) {
        name = name.toUpperCase(Locale.ROOT);
        if(!isTextName(name))
            return false;
        if(text == null)
            text = "";
        if(text.length() > 200)
            text = text.substring(0, 200);
        if(!text.equals(plcText.get(name))) {
            plcText.put(name, text);
            setChanged();
        }
        return true;
    }

    public double plcRead(String name) {
        name = name.toUpperCase(Locale.ROOT);
        if(isBitName(name))
            return plcBits.getOrDefault(name, 0.0);
        return plcIo.read(name);
    }

    /** From the external port: network bits and coils go to the program, anything else to the cabinet. */
    public boolean plcWrite(String name, double value) {
        name = name.toUpperCase(Locale.ROOT);
        if(isBitName(name) && name.charAt(0) != 'T') {
            plcBits.put(name, value);
            return true;
        }
        return plcIo.write(name, value);
    }

    /** Parses X1.1-style names: letter, slot, dot, channel. */
    private static int[] slotChannel(String name) {
        int dot = name.indexOf('.');
        if(dot < 2)
            return null;
        try {
            return new int[] {Integer.parseInt(name.substring(1, dot)) - 1, Integer.parseInt(name.substring(dot + 1)) - 1};
        } catch(NumberFormatException e) {
            return null;
        }
    }

    /** Index of an AI1.2-style analog name (slot * ANALOG_CHANNELS + channel), or -1. */
    private static int analogIndex(String name) {
        int dot = name.indexOf('.');
        if(dot < 3)
            return -1;
        try {
            int slot = Integer.parseInt(name.substring(2, dot)) - 1;
            int ch = Integer.parseInt(name.substring(dot + 1)) - 1;
            return slot < 0 || slot >= MAX_RAIL || ch < 0 || ch >= ANALOG_CHANNELS ? -1 : slot * ANALOG_CHANNELS + ch;
        } catch(NumberFormatException e) {
            return -1;
        }
    }

    private final class PlcIo implements PlcProgram.Io {
        @Override
        public double read(String name) {
            if(name.equals("E"))
                return isEStopped() ? 1 : 0;
            if(name.startsWith("AI") || name.startsWith("AO")) {
                int i = analogIndex(name);
                if(i < 0)
                    return Double.NaN;
                boolean in = name.charAt(1) == 'I';
                if(rail[i / ANALOG_CHANNELS] != (in ? ControlModule.ANALOG_IN : ControlModule.ANALOG_OUT))
                    return Double.NaN;
                return in ? analogIn[i] : analogOut[i];
            }
            if(name.equals("P"))
                return powered ? 1 : 0;
            if(name.startsWith("D") && name.length() > 1 && name.indexOf('.') < 0) {
                try {
                    int cell = Integer.parseInt(name.substring(1)) - 1;
                    return cell >= 0 && cell < MAX_CELLS && panel[cell] != null ? deviceState(cell) : Double.NaN;
                } catch(NumberFormatException e) {
                    return Double.NaN;
                }
            }
            if(name.startsWith("V")) {
                int dot = name.indexOf('.');
                if(dot < 2)
                    return Double.NaN;
                int slot;
                try {
                    slot = Integer.parseInt(name.substring(1, dot)) - 1;
                } catch(NumberFormatException e) {
                    return Double.NaN;
                }
                if(slot < 0 || slot >= MAX_RAIL || rail[slot] != ControlModule.VFD)
                    return Double.NaN;
                return switch(name.substring(dot + 1)) {
                    case "RUN" -> vfdRun[slot] ? 1 : 0;
                    case "START" -> plcVfd[slot * 3] ? 1 : 0;
                    case "STOP" -> plcVfd[slot * 3 + 1] ? 1 : 0;
                    case "REVERSE" -> plcVfd[slot * 3 + 2] ? 1 : 0;
                    case "SPEED" -> plcSpeed[slot] >= 0 ? plcSpeed[slot] : dialPercent(slot);
                    default -> Double.NaN;
                };
            }
            var sc = slotChannel(name);
            if(sc == null || sc[0] < 0 || sc[0] >= MAX_RAIL || sc[1] < 0)
                return Double.NaN;
            int slot = sc[0], ch = sc[1];
            return switch(name.charAt(0)) {
                case 'X' -> ch < CHANNELS && (rail[slot] == ControlModule.DIGITAL_IN || (rail[slot] == ControlModule.VFD && ch < ControlModule.VFD_SPEED)) ? (inputs[slot * CHANNELS + ch] ? 1 : 0) : Double.NaN;
                case 'Y' -> ch < CHANNELS && rail[slot] == ControlModule.DIGITAL_OUT ? (outputs[slot * CHANNELS + ch] ? 1 : 0) : Double.NaN;
                case 'R' -> ch < RELAY_CHANNELS && rail[slot] == ControlModule.RELAY ? (relays[slot * RELAY_CHANNELS + ch] ? 1 : 0) : Double.NaN;
                default -> Double.NaN;
            };
        }

        @Override
        public boolean exists(String name) {
            return !Double.isNaN(read(name));
        }

        /** A NaN value only asks whether the name may be written. */
        @Override
        public boolean write(String name, double value) {
            boolean probe = Double.isNaN(value);
            if(name.startsWith("AO")) {
                int i = analogIndex(name);
                if(i < 0 || rail[i / ANALOG_CHANNELS] != ControlModule.ANALOG_OUT)
                    return false;
                if(!probe)
                    analogOut[i] = (float) value;
                return true;
            }
            if(name.startsWith("V")) {
                int dot = name.indexOf('.');
                if(dot < 2)
                    return false;
                int slot;
                try {
                    slot = Integer.parseInt(name.substring(1, dot)) - 1;
                } catch(NumberFormatException e) {
                    return false;
                }
                if(slot < 0 || slot >= MAX_RAIL || rail[slot] != ControlModule.VFD)
                    return false;
                switch(name.substring(dot + 1)) {
                    case "START" -> { if(!probe) plcVfd[slot * 3] = value != 0; }
                    case "STOP" -> { if(!probe) plcVfd[slot * 3 + 1] = value != 0; }
                    case "REVERSE" -> { if(!probe) plcVfd[slot * 3 + 2] = value != 0; }
                    case "SPEED" -> { if(!probe) plcSpeed[slot] = (float) value; }
                    default -> { return false; }
                }
                return true;
            }
            var sc = slotChannel(name);
            if(sc == null || sc[0] < 0 || sc[0] >= MAX_RAIL || sc[1] < 0)
                return false;
            int slot = sc[0], ch = sc[1];
            switch(name.charAt(0)) {
                case 'Y' -> {
                    if(ch >= CHANNELS || rail[slot] != ControlModule.DIGITAL_OUT)
                        return false;
                    if(!probe)
                        setOutput(slot, ch, value != 0);
                    return true;
                }
                case 'R' -> {
                    if(ch >= RELAY_CHANNELS || rail[slot] != ControlModule.RELAY)
                        return false;
                    if(!probe)
                        setRelay(slot, ch, value != 0);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public @Nullable Object call(String alias, String method, List<Object> args) throws Exception {
            return bridge == null ? null : bridge.call(alias, method, args);
        }

        @Override
        public @Nullable String readText(String name) {
            return plcReadText(name);
        }

        @Override
        public boolean writeText(String name, String value) {
            return plcWriteText(name, value);
        }

        @Override
        public boolean hasDevice(String alias) {
            for(var device : plcDevices)
                if(device.get(0).equals(alias))
                    return true;
            return false;
        }
    }

    private void refreshPlcDevices() {
        var found = new ArrayList<List<String>>();
        if(bridge != null) {
            for(var device : bridge.devices()) {
                var entry = new ArrayList<String>();
                entry.add(device.alias());
                entry.add(device.type());
                entry.addAll(device.methods());
                found.add(entry);
            }
        }
        if(!found.equals(plcDevices)) {
            plcDevices = found;
            dirty = true;
            if(!plcError.isEmpty() && plc == null)
                compilePlc();   // a device that was missing may be here now
        }
    }

    // ---- the rail and the door ----

    public @Nullable ControlModule module(int slot) {
        return slot >= 0 && slot < MAX_RAIL ? rail[slot] : null;
    }

    public @Nullable PanelDevice device(int cell) {
        return cell >= 0 && cell < MAX_CELLS ? panel[cell] : null;
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
        for(int cell = 0; cell < MAX_CELLS; ++cell)
            if(panel[cell] == PanelDevice.E_STOP && latched[cell])
                return true;
        return false;
    }

    public boolean input(int slot, int channel) {
        return slot >= 0 && slot < MAX_RAIL && channel >= 0 && channel < CHANNELS && inputs[slot * CHANNELS + channel];
    }

    public boolean output(int slot, int channel) {
        return slot >= 0 && slot < MAX_RAIL && channel >= 0 && channel < CHANNELS && outputs[slot * CHANNELS + channel];
    }

    public float analogIn(int slot, int channel) {
        return slot >= 0 && slot < MAX_RAIL && channel >= 0 && channel < ANALOG_CHANNELS ? analogIn[slot * ANALOG_CHANNELS + channel] : 0;
    }

    public float analogOut(int slot, int channel) {
        return slot >= 0 && slot < MAX_RAIL && channel >= 0 && channel < ANALOG_CHANNELS ? analogOut[slot * ANALOG_CHANNELS + channel] : 0;
    }

    /** Server side, from the PLC or a computer. Only an analog output module's channels take a value. */
    public boolean setAnalogOut(int slot, int channel, float value) {
        if(slot < 0 || slot >= MAX_RAIL || channel < 0 || channel >= ANALOG_CHANNELS || rail[slot] != ControlModule.ANALOG_OUT)
            return false;
        if(Float.isNaN(value) || Float.isInfinite(value))
            return false;
        analogOut[slot * ANALOG_CHANNELS + channel] = value;
        return true;
    }

    public boolean relay(int slot, int channel) {
        return slot >= 0 && slot < MAX_RAIL && channel >= 0 && channel < RELAY_CHANNELS && relays[slot * RELAY_CHANNELS + channel];
    }

    /** Server side, from the computer. Only an output module's channels take a value. */
    public boolean setOutput(int slot, int channel, boolean state) {
        if(slot < 0 || slot >= MAX_RAIL || channel < 0 || channel >= CHANNELS || rail[slot] != ControlModule.DIGITAL_OUT)
            return false;
        int i = slot * CHANNELS + channel;
        if(outputs[i] != state) {
            outputs[i] = state;
            dirty = true;
        }
        return true;
    }

    public boolean setRelay(int slot, int channel, boolean state) {
        if(slot < 0 || slot >= MAX_RAIL || channel < 0 || channel >= RELAY_CHANNELS || rail[slot] != ControlModule.RELAY)
            return false;
        int i = slot * RELAY_CHANNELS + channel;
        if(relays[i] != state) {
            relays[i] = state;
            dirty = true;
        }
        return true;
    }

    public boolean setDisplay(int cell, int value) {
        if(cell < 0 || cell >= MAX_CELLS || panel[cell] != PanelDevice.DISPLAY)
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
        if(cell < 0 || cell >= cells() || panel[cell] != null) {
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
        for(int slot = 0; slot < slots(); ++slot) {
            if(rail[slot] == null) {
                rail[slot] = module;
                rebuildCircuit();
                vfdTarget[slot] = null;
                vfdMin[slot] = 5;
                vfdMax[slot] = 30;
                vfdRun[slot] = false;
                for(int cell = 0; cell < MAX_CELLS; ++cell)
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
        for(int other = 0; other < MAX_CELLS; ++other)
            if(other != cell && wire[other] == target)
                return false;
        return true;
    }

    private boolean wireTo(int cell, ControlModule module, int firstChannel, int lastChannel) {
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
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
            if(!wireTo(cell, ControlModule.VFD, ControlModule.VFD_SPEED, ControlModule.VFD_SPEED))
                wireTo(cell, ControlModule.ANALOG_IN, 0, ANALOG_CHANNELS - 1);
        } else if(device == PanelDevice.DISPLAY) {
            wireTo(cell, ControlModule.ANALOG_OUT, 0, ANALOG_CHANNELS - 1);
        } else if(device.isInput()) {
            if(!wireTo(cell, ControlModule.DIGITAL_IN, 0, CHANNELS - 1))
                wireTo(cell, ControlModule.VFD, ControlModule.VFD_START, ControlModule.VFD_REVERSE);
        } else if(device.isOutput()) {
            wireTo(cell, ControlModule.DIGITAL_OUT, 0, CHANNELS - 1);
        }
    }

    /** Whether a device may be wired to slot * CHANNELS + channel. */
    public boolean canWire(int cell, int target) {
        var device = cell >= 0 && cell < MAX_CELLS ? panel[cell] : null;
        if(device == null)
            return false;
        if(target < 0)
            return true;
        int slot = target / CHANNELS, ch = target % CHANNELS;
        var module = slot < MAX_RAIL ? rail[slot] : null;
        if(device == PanelDevice.DIAL)
            return (module == ControlModule.VFD && ch == ControlModule.VFD_SPEED) || (module == ControlModule.ANALOG_IN && ch < ANALOG_CHANNELS);
        if(device == PanelDevice.DISPLAY)
            return module == ControlModule.ANALOG_OUT && ch < ANALOG_CHANNELS;
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
        if(cell < 0 || cell >= MAX_CELLS)
            return;
        color[cell] = Math.floorMod(index, LED_COLORS.length);
        dirty = true;
        syncTimer = 2;
    }

    /** Server side. */
    public void removeDevice(int cell, @Nullable Player player) {
        if(cell < 0 || cell >= MAX_CELLS || panel[cell] == null || level == null)
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
        for(int slot = MAX_RAIL - 1; slot >= 0; --slot) {
            if(rail[slot] != null) {
                removeModule(slot, player);
                return;
            }
        }
    }

    public void removeModule(int slot, @Nullable Player player) {
        if(slot < 0 || slot >= MAX_RAIL || rail[slot] == null || level == null)
            return;
        if(rail[slot] == ControlModule.VFD && vfdTarget[slot] != null && level.isLoaded(vfdTarget[slot])) {
            var be = level.getBlockEntity(vfdTarget[slot]);
            if(DriveLink.isDrive(be))
                DriveLink.command(be, false, false, 0);
        }
        drop(ModItems.CONTROL_MODULES.get(rail[slot]).asStack());
        rail[slot] = null;
        rebuildCircuit();
        vfdTarget[slot] = null;
        vfdRun[slot] = false;
        for(int cell = 0; cell < MAX_CELLS; ++cell)
            if(wire[cell] >= 0 && wire[cell] / CHANNELS == slot)
                wire[cell] = -1;
        for(int ch = 0; ch < CHANNELS; ++ch)
            outputs[slot * CHANNELS + ch] = false;
        for(int ch = 0; ch < RELAY_CHANNELS; ++ch)
            relays[slot * RELAY_CHANNELS + ch] = false;
        for(int ch = 0; ch < ANALOG_CHANNELS; ++ch)
            analogOut[slot * ANALOG_CHANNELS + ch] = 0;
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
        for(int cell = 0; cell < MAX_CELLS; ++cell)
            if(panel[cell] != null)
                drop(ModItems.PANEL_DEVICES.get(panel[cell]).asStack());
        for(int slot = 0; slot < MAX_RAIL; ++slot)
            if(rail[slot] != null)
                drop(ModItems.CONTROL_MODULES.get(rail[slot]).asStack());
    }

    // ---- jack ----

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        if(hasModule(ControlModule.VFD) || !drives.isEmpty())
            refreshDrives();
        if(hasPlc() || !plcDevices.isEmpty())
            refreshPlcDevices();
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
    public int portCount() {
        return 2;
    }

    /** The nearer jack: port 0 on the right side, port 1 (the PLC's) on the left. */
    @Override
    public int portAt(Vec3 localHit) {
        var frame = toNorthFrame(getBlockState(), localHit);
        return frame.x < 8 ? 1 : 0;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return IElectric.getTerminalPos(level, worldPosition, port == 1 ? PLC_JACK : JACK);
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
        splices().write(tag);
        var railTag = new int[MAX_RAIL];
        for(int i = 0; i < MAX_RAIL; ++i)
            railTag[i] = rail[i] == null ? -1 : rail[i].ordinal();
        var panelTag = new int[MAX_CELLS];
        for(int i = 0; i < MAX_CELLS; ++i)
            panelTag[i] = panel[i] == null ? -1 : panel[i].ordinal();
        var targets = new long[MAX_RAIL];
        for(int i = 0; i < MAX_RAIL; ++i)
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
        var aoBits = new int[analogOut.length];
        for(int i = 0; i < aoBits.length; ++i)
            aoBits[i] = Float.floatToIntBits(analogOut[i]);
        tag.putIntArray("AnalogOut", aoBits);
        tag.put("Graph", graphTag.copy());
        if(!clientPacket) {
            var bitsTag = new CompoundTag();
            for(var entry : plcBits.entrySet())
                if(entry.getValue() != 0)
                    bitsTag.putDouble(entry.getKey(), entry.getValue());
            tag.put("PlcBits", bitsTag);
            var textTag = new CompoundTag();
            for(var entry : plcText.entrySet())
                if(!entry.getValue().isEmpty())
                    textTag.putString(entry.getKey(), entry.getValue());
            tag.put("PlcText", textTag);
        }
        if(clientPacket) {
            tag.putString("PlcError", plcError);
            var valueBits = new int[plcValues.length];
            for(int i = 0; i < valueBits.length; ++i)
                valueBits[i] = Float.floatToIntBits(plcValues[i]);
            tag.putIntArray("PlcValues", valueBits);
            var messageList = new net.minecraft.nbt.ListTag();
            for(var message : plcMessages)
                messageList.add(net.minecraft.nbt.StringTag.valueOf(message));
            tag.put("PlcMessages", messageList);
            var deviceList = new net.minecraft.nbt.ListTag();
            for(var device : plcDevices)
                deviceList.add(net.minecraft.nbt.StringTag.valueOf(String.join("|", device)));
            tag.put("PlcDevices", deviceList);
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
        splices().read(tag);
        var railTag = tag.getIntArray("Rail");
        boolean railChanged = false;
        for(int i = 0; i < MAX_RAIL; ++i) {
            var module = i < railTag.length ? ControlModule.fromOrdinal(railTag[i]) : null;
            if(module != rail[i])
                railChanged = true;
            rail[i] = module;
        }
        if(railChanged)
            railDirty = true;
        var panelTag = tag.getIntArray("Panel");
        for(int i = 0; i < MAX_CELLS; ++i)
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
        for(int i = 0; i < MAX_RAIL; ++i)
            vfdTarget[i] = i < targets.length && targets[i] != Long.MIN_VALUE ? BlockPos.of(targets[i]) : null;
        fillTenths(vfdMin, tag.getIntArray("VfdMin"), 5);
        fillTenths(vfdMax, tag.getIntArray("VfdMax"), 30);
        fill(vfdRun, tag.getByteArray("VfdRun"));
        var aoBits = tag.getIntArray("AnalogOut");
        for(int i = 0; i < Math.min(aoBits.length, analogOut.length); ++i)
            analogOut[i] = Float.intBitsToFloat(aoBits[i]);
        if(!clientPacket && tag.contains("PlcText")) {
            var textTag = tag.getCompound("PlcText");
            for(var key : textTag.getAllKeys())
                plcText.put(key, textTag.getString(key));
        }
        if(!clientPacket && tag.contains("PlcBits")) {
            var bitsTag = tag.getCompound("PlcBits");
            for(var key : bitsTag.getAllKeys())
                plcBits.put(key, bitsTag.getDouble(key));
        }
        if(tag.contains("Graph")) {
            var newGraph = tag.getCompound("Graph");
            if(!newGraph.equals(graphTag)) {
                graphTag = newGraph.copy();
                graph = PlcGraph.load(newGraph);
                ++plcRevision;
                plcCompiled = false;
            }
        } else if(!tag.getString("Program").isBlank()) {
            // A 0.20 text program becomes one rung block.
            graph = new PlcGraph();
            var rungs = graph.add(NodeType.RUNGS, 20, 20);
            if(rungs != null)
                rungs.text = tag.getString("Program");
            graphTag = graph.save();
            ++plcRevision;
            plcCompiled = false;
        }
        if(clientPacket) {
            plcError = tag.getString("PlcError");
            var valueBits = tag.getIntArray("PlcValues");
            plcValues = new float[valueBits.length];
            for(int i = 0; i < valueBits.length; ++i)
                plcValues[i] = Float.intBitsToFloat(valueBits[i]);
            var messageList = tag.getList("PlcMessages", net.minecraft.nbt.Tag.TAG_STRING);
            var messages = new ArrayList<String>(messageList.size());
            for(int i = 0; i < messageList.size(); ++i)
                messages.add(messageList.getString(i));
            plcMessages = messages;
            var deviceList = tag.getList("PlcDevices", net.minecraft.nbt.Tag.TAG_STRING);
            var foundDevices = new ArrayList<List<String>>();
            for(int i = 0; i < deviceList.size(); ++i)
                foundDevices.add(List.of(deviceList.getString(i).split("\\|")));
            plcDevices = foundDevices;
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
        Lang.builder().translate("gui.controls.modules", moduleCount(), MAX_RAIL).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(hasPlc())
            Lang.builder().translate(plcError.isEmpty() ? (graph.isEmpty() ? "gui.controls.plc_empty" : "gui.controls.plc_ok") : "gui.controls.plc_error_short")
                    .style(plcError.isEmpty() ? ChatFormatting.AQUA : ChatFormatting.RED).forGoggles(tooltip, 1);
        for(int slot = 0; slot < MAX_RAIL; ++slot) {
            if(rail[slot] == ControlModule.VFD)
                Lang.builder().translate(vfdRun[slot] ? "gui.controls.vfd_running" : "gui.controls.vfd_stopped", slot + 1)
                        .style(vfdRun[slot] ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        splices().addGoggleLines(tooltip);
        return true;
    }
}
