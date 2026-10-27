package com.nolanbaker.pgmodernized.conduit.gang;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * The gang box's devices and wiring. Each gang holds a device and its toggle; the circuit is the
 * splices plus each device's own contacts between its points: a switch or button between line
 * and load, a 3-way from common to one traveller or the other, a 4-way straight or crossed. The
 * first receptacle is tied to the cord poles so a power cord in the plate reaches it.
 */
public class GangBoxBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation, com.nolanbaker.pgmodernized.conduit.fire.IFireDevice {
    private static final int HOLD_TICKS = 10;

    private SpliceSupport splices;
    // No initialisers: buildCircuit runs from the superclass constructor, before field initialisers would.
    private GangDevice[] devices;
    private boolean[] on;
    private int[] hold;
    /** Up to four contacts a gang, by device: [line-load], [common-A, common-B], [inA-outA, inB-outB, inA-outB, inB-outA]. */
    private SwitchedWire[][] contacts;
    /** A horn strobe's load in each gang that has one, and the volts across it. */
    private org.patryk3211.powergrid.electricity.sim.ElectricWire[] horns;
    private float[] hornVolts;
    private boolean[] strobing;
    private int hornTimer;
    /** Per gang: the loop address a fire alarm panel gave a pull station or horn strobe there, and its name. */
    private int[] fireAddresses;
    private String[] fireLabels;
    private List<SplicePoint> points;

    public GangBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    private void ensureArrays() {
        if(devices == null) {
            devices = new GangDevice[GangLayout.MAX_GANGS];
            on = new boolean[GangLayout.MAX_GANGS];
            hold = new int[GangLayout.MAX_GANGS];
            contacts = new SwitchedWire[GangLayout.MAX_GANGS][];
            horns = new org.patryk3211.powergrid.electricity.sim.ElectricWire[GangLayout.MAX_GANGS];
            hornVolts = new float[GangLayout.MAX_GANGS];
            strobing = new boolean[GangLayout.MAX_GANGS];
            fireAddresses = new int[GangLayout.MAX_GANGS];
            fireLabels = new String[GangLayout.MAX_GANGS];
            java.util.Arrays.fill(fireLabels, "");
        }
    }

    // ---- as fire alarm devices ----

    @Override
    public int fireSlots() {
        return gangs();
    }

    @Override
    public @Nullable com.nolanbaker.pgmodernized.conduit.fire.FireDeviceKind fireKind(int slot) {
        var device = device(slot);
        if(device == GangDevice.PULL_STATION)
            return com.nolanbaker.pgmodernized.conduit.fire.FireDeviceKind.PULL;
        if(device == GangDevice.HORN_STROBE)
            return com.nolanbaker.pgmodernized.conduit.fire.FireDeviceKind.HORN;
        return null;
    }

    @Override
    public int fireAddress(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? fireAddresses[slot] : 0;
    }

    @Override
    public void setFireAddress(int slot, int address) {
        ensureArrays();
        if(slot < 0 || slot >= GangLayout.MAX_GANGS || fireAddresses[slot] == address)
            return;
        fireAddresses[slot] = address;
        setChanged();
        sendData();
    }

    @Override
    public String fireLabel(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? fireLabels[slot] : "";
    }

    @Override
    public boolean fireTripped(int slot) {
        return device(slot) == GangDevice.PULL_STATION && isOn(slot);
    }

    @Override
    public float fireHornVolts(int slot) {
        return device(slot) == GangDevice.HORN_STROBE ? hornVolts(slot) : 0;
    }

    @Override
    public boolean fireSpliced(int slot) {
        int a = GangLayout.point(slot, 0), b = GangLayout.point(slot, 1);
        for(var splice : splices().list())
            if(splice[0] == a || splice[1] == a || splice[0] == b || splice[1] == b)
                return true;
        return false;
    }

    /** A renamed name tag names the fire alarm device in that gang; a plain one clears the name. The tag is used up. Server side. */
    public boolean label(int slot, Player player, ItemStack tag) {
        ensureArrays();
        if(level == null || level.isClientSide || fireKind(slot) == null)
            return false;
        String text = tag.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        fireLabels[slot] = text.length() > 24 ? text.substring(0, 24) : text;
        if(!player.isCreative())
            tag.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.displayClientMessage(Lang.builder().translate(fireLabels[slot].isEmpty() ? "message.fire_alarm.label_cleared" : "message.fire_alarm.labelled", fireLabels[slot])
                .style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
        return true;
    }

    /** The volts across a horn strobe's load in that gang, zero for any other device. Works on both sides. */
    public float hornVolts(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? hornVolts[slot] : 0;
    }

    public int gangs() {
        return getBlockState().getBlock() instanceof GangBoxBlock box ? box.gangs() : 1;
    }

    @Nullable
    public GangDevice device(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? devices[slot] : null;
    }

    public boolean isOn(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS && on[slot];
    }

    // ---- circuit ----

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureArrays();
        builder.setTerminalCount(GangLayout.TERMINAL_COUNT);
        splices().buildCircuit(builder);
        float r = resistance("contact");
        boolean cordWired = false;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            var device = devices[slot];
            contacts[slot] = null;
            horns[slot] = null;
            if(device == null)
                continue;
            var p = new int[GangDevice.MAX_POINTS];
            for(int k = 0; k < p.length; ++k)
                p[k] = GangLayout.point(slot, k);
            switch(device) {
                case SWITCH, BUTTON -> contacts[slot] = new SwitchedWire[] {
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[1]), on[slot])};
                case THREE_WAY -> contacts[slot] = new SwitchedWire[] {
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[1]), !on[slot]),
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[2]), on[slot])};
                case FOUR_WAY -> contacts[slot] = new SwitchedWire[] {
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[2]), !on[slot]),
                        builder.connectSwitch(r, builder.terminalNode(p[1]), builder.terminalNode(p[3]), !on[slot]),
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[3]), on[slot]),
                        builder.connectSwitch(r, builder.terminalNode(p[1]), builder.terminalNode(p[2]), on[slot])};
                case PULL_STATION -> {
                    builder.connect(resistance("supervisory"), builder.terminalNode(p[0]), builder.terminalNode(p[1]));
                    contacts[slot] = new SwitchedWire[] {
                            builder.connectSwitch(resistance("detector"), builder.terminalNode(p[0]), builder.terminalNode(p[1]), on[slot])};
                }
                case HORN_STROBE -> horns[slot] = builder.connect(resistance("horn"), builder.terminalNode(p[0]), builder.terminalNode(p[1]));
                case RECEPTACLE -> {
                    if(!cordWired) {
                        builder.connect(SpliceSupport.SPLICE_RESISTANCE, builder.terminalNode(GangLayout.CORD_A), builder.terminalNode(p[0]));
                        builder.connect(SpliceSupport.SPLICE_RESISTANCE, builder.terminalNode(GangLayout.CORD_B), builder.terminalNode(p[1]));
                        cordWired = true;
                    }
                }
                case BLANK -> {}
            }
        }
    }

    private void applyContacts(int slot) {
        ensureArrays();
        var wires = contacts[slot];
        var device = devices[slot];
        if(wires == null || device == null)
            return;
        boolean state = on[slot];
        switch(device) {
            case SWITCH, BUTTON, PULL_STATION -> wires[0].setState(state);
            case THREE_WAY -> {
                wires[0].setState(!state);
                wires[1].setState(state);
            }
            case FOUR_WAY -> {
                wires[0].setState(!state);
                wires[1].setState(!state);
                wires[2].setState(state);
                wires[3].setState(state);
            }
            default -> {}
        }
    }

    /** The block state names the gang a cord plugs into: the first receptacle, or none. */
    private void syncSocket() {
        if(level == null || level.isClientSide)
            return;
        var state = getBlockState();
        if(!state.hasProperty(GangBoxBlock.SOCKET))
            return;
        int gang = 0;
        for(int slot = 0; slot < gangs(); ++slot) {
            if(devices[slot] == GangDevice.RECEPTACLE) {
                gang = slot + 1;
                break;
            }
        }
        if(state.getValue(GangBoxBlock.SOCKET) != gang)
            level.setBlock(worldPosition, state.setValue(GangBoxBlock.SOCKET, gang), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    private void rebuild() {
        points = null;
        syncSocket();
        if(level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        setChanged();
        sendData();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    // ---- devices ----

    /** Server side: puts the device in the gang clicked, or the first empty one; a full box says so. */
    public void install(int slot, GangDevice device, Player player, ItemStack stack) {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        int gangs = gangs();
        if(slot < 0 || slot >= gangs || devices[slot] != null) {
            slot = -1;
            for(int i = 0; i < gangs; ++i) {
                if(devices[i] == null) {
                    slot = i;
                    break;
                }
            }
        }
        if(slot < 0) {
            player.displayClientMessage(Lang.builder().translate("message.gang.full").style(ChatFormatting.RED).component(), true);
            return;
        }
        devices[slot] = device;
        on[slot] = false;
        hold[slot] = 0;
        fireAddresses[slot] = 0;
        fireLabels[slot] = "";
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        rebuild();
    }

    /** Server side: takes the device out of that gang; its splices go with it. */
    public void remove(int slot, Player player) {
        ensureArrays();
        if(level == null || level.isClientSide || device(slot) == null)
            return;
        var stack = ModItems.gangDevice(devices[slot]);
        for(int k = 0; k < GangDevice.MAX_POINTS; ++k) {
            int point = GangLayout.point(slot, k);
            splices().list().removeIf(s -> s[0] == point || s[1] == point);
        }
        devices[slot] = null;
        on[slot] = false;
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        rebuild();
    }

    /** The box is going: every device falls out. */
    public void dropDevices() {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(devices[slot] != null)
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, ModItems.gangDevice(devices[slot]));
            devices[slot] = null;
        }
    }

    /** Server side: a pull station's handle comes down and stays; a wrench resets it. */
    public void pull(int slot, Player player) {
        ensureArrays();
        if(device(slot) != GangDevice.PULL_STATION || level == null || level.isClientSide)
            return;
        if(on[slot]) {
            player.displayClientMessage(Lang.translate("message.conduit_box.pull_reset_wrench").style(ChatFormatting.GRAY).component(), true);
            return;
        }
        on[slot] = true;
        applyContacts(slot);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.5f);
        player.displayClientMessage(Lang.translate("message.conduit_box.pulled").style(ChatFormatting.RED).component(), true);
        setChanged();
        sendData();
    }

    /** Server side: a wrench on a pulled station resets it. True when there was one to reset. */
    public boolean resetPull(int slot, Player player) {
        ensureArrays();
        if(device(slot) != GangDevice.PULL_STATION || !on[slot] || level == null || level.isClientSide)
            return false;
        on[slot] = false;
        applyContacts(slot);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.7f);
        player.displayClientMessage(Lang.translate("message.conduit_box.pull_reset").style(ChatFormatting.GREEN).component(), true);
        setChanged();
        sendData();
        return true;
    }

    /** Every tick: the volts at each horn strobe set its strobe and horn; one three-pulse pattern plays for the box. */
    private void hornTick() {
        boolean anySound = false, changed = false;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            var horn = horns[slot];
            if(horn == null) {
                if(hornVolts[slot] != 0 || strobing[slot])
                    changed = true;
                hornVolts[slot] = 0;
                strobing[slot] = false;
                continue;
            }
            if(horn.isConverged()) {
                double v = Math.abs(horn.current()) * horn.getResistance();
                hornVolts[slot] = Float.isFinite((float) v) ? (float) v : 0;
            }
            boolean strobe = hornVolts[slot] >= ConduitBoxBlockEntity.STROBE_VOLTS;
            if(strobe != strobing[slot]) {
                strobing[slot] = strobe;
                changed = true;
            }
            anySound |= hornVolts[slot] >= ConduitBoxBlockEntity.HORN_VOLTS;
        }
        if(changed)
            sendData();
        if(!anySound) {
            hornTimer = 0;
            return;
        }
        int t = hornTimer++ % 80;
        if(t < 60 && t % 20 < 10 && t % 4 == 0)
            level.playSound(null, worldPosition, com.nolanbaker.pgmodernized.registry.ModSounds.FIRE_ALARM_HORN.get(), SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    /** Server side: a switch flips; a button closes for its hold time. */
    public void toggle(int slot, Player player) {
        ensureArrays();
        var device = device(slot);
        if(device == null || !device.toggles() || level == null || level.isClientSide)
            return;
        if(device.momentary()) {
            hold[slot] = HOLD_TICKS;
            if(on[slot])
                return;
        }
        setOn(slot, !on[slot]);
    }

    private void setOn(int slot, boolean value) {
        ensureArrays();
        on[slot] = value;
        applyContacts(slot);
        if(level != null)
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, value ? 0.6f : 0.5f);
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        ensureArrays();
        super.tick();
        if(level == null || level.isClientSide)
            return;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(hold[slot] > 0 && --hold[slot] == 0 && on[slot])
                setOn(slot, false);
        }
        hornTick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        ensureArrays();
        if(points == null) {
            var list = new ArrayList<SplicePoint>();
            for(int slot = 0; slot < gangs(); ++slot) {
                var device = devices[slot];
                if(device == null)
                    continue;
                for(int k = 0; k < device.points(); ++k) {
                    var name = Lang.builder().translate("gang.slot", slot + 1).text(" ").add(device.pointName(k).copy()).component();
                    list.add(new SplicePoint(GangLayout.point(slot, k), name, device.pointColor(k)));
                }
            }
            points = List.copyOf(list);
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        ensureArrays();
        if(!GangLayout.isPoint(terminal))
            return false;
        var device = device(GangLayout.slotOfPoint(terminal));
        return device != null && GangLayout.indexOfPoint(terminal) < device.points();
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public int hubCount() {
        return GangLayout.hubCount(gangs());
    }

    @Override
    public Component hubName(int hub) {
        return GangLayout.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return GangLayout.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        int hub = GangLayout.hubAt(terminal);
        return hub < hubCount() ? hub : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return GangLayout.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        int hub = GangLayout.hubOf(terminal);
        return hub < hubCount() ? hub : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return GangLayout.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub >= 0 && hub < hubCount() ? SpliceSupport.runAt(this, GangLayout.hubTerminal(hub)) : null;
    }

    // ---- persistence ----

    private void writeDevices(CompoundTag tag) {
        ensureArrays();
        var list = new ListTag();
        int bits = 0;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            list.add(StringTag.valueOf(devices[slot] == null ? "" : devices[slot].id()));
            if(on[slot])
                bits |= 1 << slot;
        }
        tag.put("Devices", list);
        tag.putInt("On", bits);
        var volts = new int[GangLayout.MAX_GANGS];
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            volts[slot] = Math.round(hornVolts[slot] * 10);
        tag.putIntArray("HornVolts", volts);
        tag.putIntArray("FireAddresses", fireAddresses.clone());
        var labels = new ListTag();
        for(var label : fireLabels)
            labels.add(StringTag.valueOf(label));
        tag.put("FireLabels", labels);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        splices().write(tag);
        writeDevices(tag);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
        writeDevices(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ensureArrays();
        super.read(tag, registries, clientPacket);
        splices().read(tag);
        var list = tag.getList("Devices", Tag.TAG_STRING);
        int bits = tag.getInt("On");
        boolean changed = false;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            var id = slot < list.size() ? list.getString(slot) : "";
            var device = id.isEmpty() ? null : GangDevice.byId(id);
            if(device != devices[slot])
                changed = true;
            devices[slot] = device;
            on[slot] = (bits & 1 << slot) != 0;
        }
        var volts = tag.getIntArray("HornVolts");
        for(int slot = 0; slot < GangLayout.MAX_GANGS && slot < volts.length; ++slot)
            hornVolts[slot] = volts[slot] / 10f;
        var addresses = tag.getIntArray("FireAddresses");
        for(int slot = 0; slot < GangLayout.MAX_GANGS && slot < addresses.length; ++slot)
            fireAddresses[slot] = addresses[slot];
        var labels = tag.getList("FireLabels", Tag.TAG_STRING);
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            fireLabels[slot] = slot < labels.size() ? labels.getString(slot) : "";
        points = null;
        if(changed && !clientPacket && level != null && !level.isClientSide && electricBehaviour != null) {
            electricBehaviour.rebuildCircuit(false);
            syncSocket();
        }
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            applyContacts(slot);
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ensureArrays();
        Lang.builder().translate("gui.gang.title", gangs()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        // One line for the gangs, left to right: the device, and its state where it has one.
        var line = Lang.builder();
        for(int slot = 0; slot < gangs(); ++slot) {
            var device = devices[slot];
            if(slot > 0)
                line.text(", ");
            if(device == null) {
                line.add(Lang.builder().translate("gang.empty").style(ChatFormatting.DARK_GRAY));
            } else {
                line.add(Lang.builder().text(device.label()).style(ChatFormatting.WHITE));
                if(device.toggles() || device == GangDevice.PULL_STATION)
                    line.text(" ").add(Lang.builder().translate(stateKey(device, on[slot])).style(on[slot] == (device == GangDevice.PULL_STATION) ? ChatFormatting.RED : ChatFormatting.GREEN));
                if(fireKind(slot) != null) {
                    if(fireAddresses[slot] > 0)
                        line.text(" ").add(Lang.builder().text(String.format("%03d", fireAddresses[slot])).style(ChatFormatting.AQUA));
                    if(!fireLabels[slot].isEmpty())
                        line.text(" ").add(Lang.builder().text(fireLabels[slot]).style(ChatFormatting.YELLOW));
                }
                if(device == GangDevice.HORN_STROBE) {
                    float v = hornVolts[slot];
                    String key = v >= ConduitBoxBlockEntity.HORN_VOLTS ? "gang.horn_sounding" : v >= ConduitBoxBlockEntity.STROBE_VOLTS ? "gang.horn_strobe" : "gang.horn_quiet";
                    line.text(" ").add(Lang.builder().translate(key).style(v >= ConduitBoxBlockEntity.STROBE_VOLTS ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                }
            }
        }
        line.forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    private static String stateKey(GangDevice device, boolean on) {
        return switch(device) {
            case THREE_WAY -> on ? "gui.conduit_switch.to_b" : "gui.conduit_switch.to_a";
            case FOUR_WAY -> on ? "gang.crossed" : "gang.straight";
            case PULL_STATION -> on ? "gang.pulled" : "gang.set";
            default -> on ? "gui.conduit_switch.on" : "gui.conduit_switch.off";
        };
    }
}
