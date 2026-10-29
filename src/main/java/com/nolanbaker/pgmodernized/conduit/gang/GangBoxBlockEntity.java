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
    /** The weatherproof cover over each gang's device, or null. */
    private GangCover[] covers;
    /** The padlock on each gang's cover, or null. */
    private com.nolanbaker.pgmodernized.device.lock.Lock[] locks;
    /** Per gang: when a plugged cord was last looked for, and whether one was there. Either side. */
    private long[] cordChecked;
    private boolean[] cordIn;
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
    /** A monitor module's sense source across its contact points, and whether its contact is normally closed. */
    private org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling[] sense;
    private boolean[] monitorNc;
    /** A thermostat's mode, setpoint in Fahrenheit, calls (bit 0 cool, bit 1 heat, bit 2 fan) and the room it last read, tenths of a degree. */
    private int[] thermoMode, thermoSet, thermoCall, thermoTemp;
    private int thermoTimer;
    /** Loaded from disk before the level was set: the circuit built in the constructor knows no devices until the first tick rebuilds it. */
    private boolean needsRebuild;
    public static final int MODE_OFF = 0, MODE_COOL = 1, MODE_HEAT = 2, MODE_AUTO = 3;
    public static final int SET_DEFAULT_F = 72, SET_MIN_F = 50, SET_MAX_F = 90;
    public static final float HYSTERESIS_F = 1.5f;
    /** An annunciator's panel: where it is, and when its survey last reached this box. */
    private BlockPos[] panelPos;
    private long[] panelSeen;
    public static final float SENSE_VOLTS = 5, SENSE_AMPS = 0.001f;
    /** Ticks without a survey before an annunciator shows its link lost; surveys come every sixty. */
    public static final int LINK_TICKS = 200;
    private List<SplicePoint> points;

    public GangBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    private void ensureArrays() {
        if(devices == null) {
            devices = new GangDevice[GangLayout.MAX_GANGS];
            covers = new GangCover[GangLayout.MAX_GANGS];
            locks = new com.nolanbaker.pgmodernized.device.lock.Lock[GangLayout.MAX_GANGS];
            cordChecked = new long[GangLayout.MAX_GANGS];
            cordIn = new boolean[GangLayout.MAX_GANGS];
            on = new boolean[GangLayout.MAX_GANGS];
            hold = new int[GangLayout.MAX_GANGS];
            contacts = new SwitchedWire[GangLayout.MAX_GANGS][];
            horns = new org.patryk3211.powergrid.electricity.sim.ElectricWire[GangLayout.MAX_GANGS];
            hornVolts = new float[GangLayout.MAX_GANGS];
            strobing = new boolean[GangLayout.MAX_GANGS];
            fireAddresses = new int[GangLayout.MAX_GANGS];
            fireLabels = new String[GangLayout.MAX_GANGS];
            java.util.Arrays.fill(fireLabels, "");
            sense = new org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling[GangLayout.MAX_GANGS];
            monitorNc = new boolean[GangLayout.MAX_GANGS];
            thermoMode = new int[GangLayout.MAX_GANGS];
            thermoSet = new int[GangLayout.MAX_GANGS];
            thermoCall = new int[GangLayout.MAX_GANGS];
            thermoTemp = new int[GangLayout.MAX_GANGS];
            java.util.Arrays.fill(thermoMode, MODE_AUTO);
            java.util.Arrays.fill(thermoSet, SET_DEFAULT_F);
            panelPos = new BlockPos[GangLayout.MAX_GANGS];
            panelSeen = new long[GangLayout.MAX_GANGS];
        }
    }

    /** The gang that holds the device covering this one: the gang itself unless it is covered by a wide device. */
    public int owner(int slot) {
        ensureArrays();
        if(slot < 0 || slot >= GangLayout.MAX_GANGS)
            return -1;
        int s = slot;
        while(s > 0 && devices[s] == GangDevice.SPAN)
            --s;
        return s;
    }

    // ---- monitor modules ----

    /** Whether the module's contact is taken as normally closed, tripping when it opens. Works on both sides. */
    public boolean monitorNc(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS && monitorNc[slot];
    }

    /** Server side: a wrench flips the module between normally open and normally closed. */
    public void toggleMonitorMode(int slot, Player player) {
        ensureArrays();
        if(device(slot) != GangDevice.MONITOR_MODULE || level == null || level.isClientSide)
            return;
        monitorNc[slot] = !monitorNc[slot];
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 1.3f);
        player.displayClientMessage(Lang.translate(monitorNc[slot] ? "message.gang.monitor_nc" : "message.gang.monitor_no").style(ChatFormatting.GRAY).component(), true);
        monitorTick();
        setChanged();
        sendData();
    }

    /** Every tick: each monitor module reads the current through its contact and trips when the contact is not in its normal state. */
    private void monitorTick() {
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(devices[slot] != GangDevice.MONITOR_MODULE || sense[slot] == null)
                continue;
            double amps = sense[slot].getCurrent();
            boolean closed = Double.isFinite(amps) && Math.abs(amps) >= SENSE_AMPS;
            boolean trip = closed != monitorNc[slot];
            if(trip != on[slot]) {
                on[slot] = trip;
                applyContacts(slot);
                setChanged();
                sendData();
            }
        }
    }

    // ---- thermostats ----

    private void thermostatTick() {
        if(++thermoTimer < 20)
            return;
        thermoTimer = 0;
        boolean changed = false;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            if(devices[slot] == GangDevice.THERMOSTAT && thermostatUpdate(slot))
                changed = true;
        if(changed) {
            setChanged();
            sendData();
        }
    }

    /**
     * Reads the room, the nearest air handler's or the biome's, decides the calls with a degree
     * and a half of hysteresis either side of the setpoint, and sets the contacts. True when
     * anything the box shows changed. Server side.
     */
    private boolean thermostatUpdate(int slot) {
        var handler = com.nolanbaker.pgmodernized.device.hvac.AirHandlerBlockEntity.nearest(level, worldPosition);
        float temp = handler != null ? handler.roomF() : com.nolanbaker.pgmodernized.device.hvac.Climate.outdoorF(level, worldPosition);
        int mode = thermoMode[slot], set = thermoSet[slot], call = thermoCall[slot];
        boolean cool = (call & 1) != 0, heat = (call & 2) != 0;
        if(mode == MODE_COOL || mode == MODE_AUTO) {
            if(temp >= set + HYSTERESIS_F)
                cool = true;
            else if(temp <= set - HYSTERESIS_F)
                cool = false;
        } else
            cool = false;
        if(mode == MODE_HEAT || mode == MODE_AUTO) {
            if(temp <= set - HYSTERESIS_F)
                heat = true;
            else if(temp >= set + HYSTERESIS_F)
                heat = false;
        } else
            heat = false;
        if(cool)
            heat = false;
        int now = (cool ? 1 : 0) | (heat ? 2 : 0) | (cool || heat ? 4 : 0);
        int shown = Math.round(temp * 10);
        boolean changed = now != call || shown != thermoTemp[slot];
        thermoCall[slot] = now;
        thermoTemp[slot] = shown;
        if(now != call)
            applyContacts(slot);
        return changed;
    }

    public int thermoMode(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? thermoMode[slot] : MODE_OFF;
    }

    public int thermoSet(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? thermoSet[slot] : SET_DEFAULT_F;
    }

    public int thermoCall(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? thermoCall[slot] : 0;
    }

    /** The room as the thermostat last read it, tenths of a degree Fahrenheit. */
    public int thermoTemp(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? thermoTemp[slot] : 0;
    }

    public static String modeKey(int mode) {
        return switch(mode) {
            case MODE_COOL -> "gang.tstat.cool";
            case MODE_HEAT -> "gang.tstat.heat";
            case MODE_AUTO -> "gang.tstat.auto";
            default -> "gang.tstat.off";
        };
    }

    /** Server side: the wrench moves the setpoint, two degrees a click. */
    public void adjustSetpoint(int slot, int delta, Player player) {
        ensureArrays();
        if(level == null || level.isClientSide || device(slot) != GangDevice.THERMOSTAT)
            return;
        thermoSet[slot] = Math.max(SET_MIN_F, Math.min(SET_MAX_F, thermoSet[slot] + delta));
        thermostatUpdate(slot);
        player.displayClientMessage(Lang.builder().translate("message.gang.thermostat_set", thermoSet[slot]).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    // ---- annunciators ----

    @Override
    public void setFirePanel(int slot, BlockPos panel, long now) {
        ensureArrays();
        if(slot < 0 || slot >= GangLayout.MAX_GANGS || level == null || level.isClientSide)
            return;
        boolean changed = !panel.equals(panelPos[slot]);
        panelPos[slot] = panel;
        panelSeen[slot] = now;
        setChanged();
        if(changed || now % 200 < 60)
            sendData();
    }

    /** Where the annunciator's panel is, or null before any survey reached it. Works on both sides. */
    @Nullable
    public BlockPos annunciatorPanel(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? panelPos[slot] : null;
    }

    /** Whether the panel's survey has reached the annunciator lately. Works on both sides. */
    public boolean annunciatorLinked(int slot) {
        ensureArrays();
        return level != null && slot >= 0 && slot < GangLayout.MAX_GANGS && panelPos[slot] != null && level.getGameTime() - panelSeen[slot] <= LINK_TICKS;
    }

    /** Whether any annunciator in the box is the given panel's. */
    public boolean linksTo(BlockPos panel) {
        ensureArrays();
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            if(devices[slot] == GangDevice.ANNUNCIATOR && panel.equals(panelPos[slot]))
                return true;
        return false;
    }

    /** Server side: Silence or Reset on an annunciator presses the panel's button. */
    public void annunciatorPress(int slot, boolean silence, Player player) {
        ensureArrays();
        if(device(slot) != GangDevice.ANNUNCIATOR || level == null || level.isClientSide)
            return;
        var pos = panelPos[slot];
        if(!annunciatorLinked(slot) || pos == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity panel)) {
            player.displayClientMessage(Lang.translate("message.gang.no_panel").style(ChatFormatting.RED).component(), true);
            return;
        }
        if(silence)
            panel.silence(player);
        else
            panel.reset(player);
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
        if(device == GangDevice.MONITOR_MODULE)
            return com.nolanbaker.pgmodernized.conduit.fire.FireDeviceKind.MONITOR;
        if(device == GangDevice.ANNUNCIATOR)
            return com.nolanbaker.pgmodernized.conduit.fire.FireDeviceKind.ANNUNCIATOR;
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
        var device = device(slot);
        return (device == GangDevice.PULL_STATION || device == GangDevice.MONITOR_MODULE) && isOn(slot);
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

    @Override
    public int[] fireTerminals(int slot) {
        return new int[] {GangLayout.point(slot, 0), GangLayout.point(slot, 1)};
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

    @Nullable
    public GangCover cover(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? covers[slot] : null;
    }

    @Nullable
    public com.nolanbaker.pgmodernized.device.lock.Lock lock(int slot) {
        ensureArrays();
        return slot >= 0 && slot < GangLayout.MAX_GANGS ? locks[slot] : null;
    }

    /** Whether any gang is padlocked: the box then cannot be wrenched, picked up or broken. */
    public boolean locked() {
        ensureArrays();
        for(var lock : locks)
            if(lock != null)
                return true;
        return false;
    }

    /** A padlocked gang's points keep their splices. */
    @Override
    public boolean spliceLocked(int terminal) {
        return GangLayout.isPoint(terminal) && lock(owner(GangLayout.slotOfPoint(terminal))) != null;
    }

    /** Server side: a padlock hangs on the cover in that gang. */
    public void fitLock(int slot, Player player, ItemStack stack) {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        slot = owner(slot);
        if(cover(slot) == null) {
            player.displayClientMessage(Lang.translate("message.lock.padlock_fits").style(ChatFormatting.RED).component(), true);
            return;
        }
        if(locks[slot] != null) {
            player.displayClientMessage(Lang.translate("message.lock.already").style(ChatFormatting.RED).component(), true);
            return;
        }
        var lock = com.nolanbaker.pgmodernized.device.lock.Lock.fit(stack, player, level);
        locks[slot] = lock;
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.6f);
        player.displayClientMessage(Lang.builder().translate("message.lock.fitted", lock.serial()).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    /** Server side: the padlock comes off into the hand, the same numbered lock. The block asks for a key. */
    public void removeLock(int slot, Player player) {
        ensureArrays();
        slot = owner(slot);
        if(level == null || level.isClientSide || lock(slot) == null)
            return;
        var lock = locks[slot];
        locks[slot] = null;
        var stack = lock.item();
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.6f);
        player.displayClientMessage(Lang.builder().translate("message.lock.removed", lock.serial()).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    /**
     * Whether a power cord is plugged into that receptacle gang, for the cover's lid. Looked up
     * every half second at most, since it means a search of the cords nearby. Works on both sides.
     */
    public boolean cordPlugged(int slot) {
        ensureArrays();
        if(level == null || slot < 0 || slot >= GangLayout.MAX_GANGS || devices[slot] != GangDevice.RECEPTACLE || !(getBlockState().getBlock() instanceof GangBoxBlock box))
            return false;
        long now = level.getGameTime();
        if(cordChecked[slot] == 0 || now - cordChecked[slot] >= 10 || now < cordChecked[slot]) {
            cordChecked[slot] = now;
            cordIn[slot] = GangBoxBlock.cordOn(this, slot, box.cordEndpoint(worldPosition, getBlockState(), slot)) != null;
        }
        return cordIn[slot];
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
            sense[slot] = null;
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
                case MONITOR_MODULE -> {
                    // On the loop like a pull station; across its contact points a small sense source of its own, read every tick.
                    builder.connect(resistance("supervisory"), builder.terminalNode(p[0]), builder.terminalNode(p[1]));
                    contacts[slot] = new SwitchedWire[] {
                            builder.connectSwitch(resistance("detector"), builder.terminalNode(p[0]), builder.terminalNode(p[1]), on[slot])};
                    var source = new org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling(builder.terminalNode(p[2]), builder.terminalNode(p[3]), resistance("sense"));
                    source.setVoltageProvider(() -> SENSE_VOLTS);
                    builder.add(source);
                    sense[slot] = source;
                }
                case ANNUNCIATOR -> builder.connect(resistance("supervisory"), builder.terminalNode(p[0]), builder.terminalNode(p[1]));
                case THERMOSTAT -> contacts[slot] = new SwitchedWire[] {
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[1]), (thermoCall[slot] & 1) != 0),
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[2]), (thermoCall[slot] & 2) != 0),
                        builder.connectSwitch(r, builder.terminalNode(p[0]), builder.terminalNode(p[3]), (thermoCall[slot] & 4) != 0)};
                case SPAN -> {}
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
            case SWITCH, BUTTON, PULL_STATION, MONITOR_MODULE -> wires[0].setState(state);
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
            case THERMOSTAT -> {
                wires[0].setState((thermoCall[slot] & 1) != 0);
                wires[1].setState((thermoCall[slot] & 2) != 0);
                wires[2].setState((thermoCall[slot] & 4) != 0);
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
        int width = device.width();
        if(slot < 0 || !emptyRun(slot, width)) {
            slot = -1;
            for(int i = 0; i + width <= gangs; ++i) {
                if(emptyRun(i, width)) {
                    slot = i;
                    break;
                }
            }
        }
        if(slot < 0) {
            player.displayClientMessage(width > 1 ? Lang.builder().translate("message.gang.needs_wide", width).style(ChatFormatting.RED).component()
                    : Lang.builder().translate("message.gang.full").style(ChatFormatting.RED).component(), true);
            return;
        }
        for(int i = 0; i < width; ++i) {
            devices[slot + i] = i == 0 ? device : GangDevice.SPAN;
            covers[slot + i] = null;
            locks[slot + i] = null;
            on[slot + i] = false;
            hold[slot + i] = 0;
            fireAddresses[slot + i] = 0;
            fireLabels[slot + i] = "";
            monitorNc[slot + i] = false;
            thermoMode[slot + i] = MODE_AUTO;
            thermoSet[slot + i] = SET_DEFAULT_F;
            thermoCall[slot + i] = 0;
            thermoTemp[slot + i] = 0;
            panelPos[slot + i] = null;
        }
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        rebuild();
    }

    /** Whether {@code width} gangs from {@code slot} on are empty and in the box. */
    private boolean emptyRun(int slot, int width) {
        if(slot < 0 || slot + width > gangs())
            return false;
        for(int i = 0; i < width; ++i)
            if(devices[slot + i] != null)
                return false;
        return true;
    }

    /** Server side: takes the device out of that gang, or the wide device covering it; its splices go with it. */
    public void remove(int slot, Player player) {
        ensureArrays();
        slot = owner(slot);
        if(level == null || level.isClientSide || device(slot) == null || devices[slot] == GangDevice.SPAN)
            return;
        var stack = ModItems.gangDevice(devices[slot]);
        for(int i = 0; i < devices[slot].width() && slot + i < GangLayout.MAX_GANGS; ++i) {
            for(int k = 0; k < GangDevice.MAX_POINTS; ++k) {
                int point = GangLayout.point(slot + i, k);
                splices().list().removeIf(s -> s[0] == point || s[1] == point);
            }
            if(i > 0) {
                devices[slot + i] = null;
                on[slot + i] = false;
            }
        }
        devices[slot] = null;
        on[slot] = false;
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        if(covers[slot] != null) {
            var cover = ModItems.gangCover(covers[slot]);
            covers[slot] = null;
            if(!player.getInventory().add(cover))
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, cover);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        rebuild();
    }

    /** Server side: a weatherproof cover goes over the device in that gang, if it is one the cover fits and the gang is bare. */
    public void fitCover(int slot, GangCover cover, Player player, ItemStack stack) {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        slot = owner(slot);
        var device = device(slot);
        if(device == null || !cover.fits(device)) {
            player.displayClientMessage(Lang.translate("message.gang.cover_fits_" + cover.id()).style(ChatFormatting.RED).component(), true);
            return;
        }
        if(covers[slot] != null) {
            player.displayClientMessage(Lang.translate("message.gang.covered").style(ChatFormatting.RED).component(), true);
            return;
        }
        covers[slot] = cover;
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
        sendData();
    }

    /** Server side: the cover comes off that gang, into the hand, the device staying put. */
    public void removeCover(int slot, Player player) {
        ensureArrays();
        slot = owner(slot);
        if(level == null || level.isClientSide || cover(slot) == null)
            return;
        var stack = ModItems.gangCover(covers[slot]);
        covers[slot] = null;
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
        sendData();
    }

    /** The box is going: every device falls out. */
    public void dropDevices() {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(devices[slot] != null && devices[slot].isItem())
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, ModItems.gangDevice(devices[slot]));
            if(covers[slot] != null)
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, ModItems.gangCover(covers[slot]));
            if(locks[slot] != null)
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, locks[slot].item());
            devices[slot] = null;
            covers[slot] = null;
            locks[slot] = null;
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
        if(device == GangDevice.THERMOSTAT) {
            // A click steps the mode: off, cool, heat, auto.
            thermoMode[slot] = (thermoMode[slot] + 1) % 4;
            thermostatUpdate(slot);
            player.displayClientMessage(Lang.builder().translate("message.gang.thermostat_mode", Lang.builder().translate(modeKey(thermoMode[slot])).component()).style(ChatFormatting.GRAY).component(), true);
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.4f);
            setChanged();
            sendData();
            return;
        }
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
        if(needsRebuild) {
            needsRebuild = false;
            rebuild();
            for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
                applyContacts(slot);
        }
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(hold[slot] > 0 && --hold[slot] == 0 && on[slot])
                setOn(slot, false);
        }
        hornTick();
        monitorTick();
        thermostatTick();
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
        var coverList = new ListTag();
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            coverList.add(StringTag.valueOf(covers[slot] == null ? "" : covers[slot].id()));
        tag.put("Covers", coverList);
        var lockList = new ListTag();
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            lockList.add(locks[slot] == null ? new CompoundTag() : locks[slot].write());
        tag.put("Locks", lockList);
        var volts = new int[GangLayout.MAX_GANGS];
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            volts[slot] = Math.round(hornVolts[slot] * 10);
        tag.putIntArray("HornVolts", volts);
        tag.putIntArray("FireAddresses", fireAddresses.clone());
        var labels = new ListTag();
        for(var label : fireLabels)
            labels.add(StringTag.valueOf(label));
        tag.put("FireLabels", labels);
        int nc = 0;
        var panels = new long[GangLayout.MAX_GANGS];
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            if(monitorNc[slot])
                nc |= 1 << slot;
            panels[slot] = panelPos[slot] == null ? Long.MIN_VALUE : panelPos[slot].asLong();
        }
        tag.putInt("MonitorNc", nc);
        tag.putIntArray("ThermoMode", thermoMode.clone());
        tag.putIntArray("ThermoSet", thermoSet.clone());
        tag.putIntArray("ThermoCall", thermoCall.clone());
        tag.putIntArray("ThermoTemp", thermoTemp.clone());
        tag.putLongArray("Panels", panels);
        tag.putLongArray("PanelSeen", panelSeen.clone());
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
        var coverList = tag.getList("Covers", Tag.TAG_STRING);
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            var id = slot < coverList.size() ? coverList.getString(slot) : "";
            covers[slot] = id.isEmpty() ? null : GangCover.byId(id);
        }
        var lockList = tag.getList("Locks", Tag.TAG_COMPOUND);
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            locks[slot] = slot < lockList.size() ? com.nolanbaker.pgmodernized.device.lock.Lock.read(lockList.getCompound(slot)) : null;
        var volts = tag.getIntArray("HornVolts");
        for(int slot = 0; slot < GangLayout.MAX_GANGS && slot < volts.length; ++slot)
            hornVolts[slot] = volts[slot] / 10f;
        var addresses = tag.getIntArray("FireAddresses");
        for(int slot = 0; slot < GangLayout.MAX_GANGS && slot < addresses.length; ++slot)
            fireAddresses[slot] = addresses[slot];
        var labels = tag.getList("FireLabels", Tag.TAG_STRING);
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot)
            fireLabels[slot] = slot < labels.size() ? labels.getString(slot) : "";
        int nc = tag.getInt("MonitorNc");
        if(tag.contains("ThermoMode")) {
            var modes = tag.getIntArray("ThermoMode");
            var sets = tag.getIntArray("ThermoSet");
            var calls = tag.getIntArray("ThermoCall");
            var temps = tag.getIntArray("ThermoTemp");
            for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
                thermoMode[slot] = slot < modes.length ? modes[slot] : MODE_AUTO;
                thermoSet[slot] = slot < sets.length ? sets[slot] : SET_DEFAULT_F;
                thermoCall[slot] = slot < calls.length ? calls[slot] : 0;
                thermoTemp[slot] = slot < temps.length ? temps[slot] : 0;
            }
        }
        var panels = tag.getLongArray("Panels");
        var seen = tag.getLongArray("PanelSeen");
        for(int slot = 0; slot < GangLayout.MAX_GANGS; ++slot) {
            monitorNc[slot] = (nc & 1 << slot) != 0;
            panelPos[slot] = slot < panels.length && panels[slot] != Long.MIN_VALUE ? BlockPos.of(panels[slot]) : null;
            panelSeen[slot] = slot < seen.length ? seen[slot] : 0;
        }
        points = null;
        if(changed && !clientPacket && level != null && !level.isClientSide && electricBehaviour != null) {
            electricBehaviour.rebuildCircuit(false);
            syncSocket();
        } else if(changed && !clientPacket && level == null) {
            needsRebuild = true;
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
            } else if(device == GangDevice.SPAN) {
                line.add(Lang.builder().translate("gang.covered").style(ChatFormatting.DARK_GRAY));
            } else {
                line.add(Lang.builder().text(device.label()).style(ChatFormatting.WHITE));
                if(device == GangDevice.MONITOR_MODULE)
                    line.text(" ").add(Lang.builder().translate(monitorNc[slot] ? "gang.monitor_nc" : "gang.monitor_no").style(ChatFormatting.GRAY));
                if(device == GangDevice.THERMOSTAT) {
                    int call = thermoCall[slot];
                    String calling = (call & 1) != 0 ? "gang.tstat.calling_cool" : (call & 2) != 0 ? "gang.tstat.calling_heat" : "gang.tstat.satisfied";
                    line.text(" ").add(Lang.builder().translate(modeKey(thermoMode[slot])).style(ChatFormatting.GRAY))
                            .text(" ").add(Lang.builder().translate("gang.tstat.state", thermoSet[slot], String.format("%.1f", thermoTemp[slot] / 10f)).style(ChatFormatting.GRAY))
                            .text(" ").add(Lang.builder().translate(calling).style((call & 3) != 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
                }
                if(device == GangDevice.ANNUNCIATOR)
                    line.text(" ").add(Lang.builder().translate(annunciatorLinked(slot) ? "gang.linked" : "gang.unlinked").style(annunciatorLinked(slot) ? ChatFormatting.GREEN : ChatFormatting.RED));
                if(device.toggles() || device.initiating())
                    line.text(" ").add(Lang.builder().translate(stateKey(device, on[slot])).style(on[slot] == device.initiating() ? ChatFormatting.RED : ChatFormatting.GREEN));
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
        for(int slot = 0; slot < gangs(); ++slot)
            if(locks[slot] != null)
                Lang.builder().translate("gui.lock.gang", slot + 1, locks[slot].serial(), locks[slot].ownerName()).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    private static String stateKey(GangDevice device, boolean on) {
        return switch(device) {
            case THREE_WAY -> on ? "gui.conduit_switch.to_b" : "gui.conduit_switch.to_a";
            case FOUR_WAY -> on ? "gang.crossed" : "gang.straight";
            case PULL_STATION -> on ? "gang.pulled" : "gang.set";
            case MONITOR_MODULE -> on ? "gang.tripped" : "gang.normal";
            default -> on ? "gui.conduit_switch.on" : "gui.conduit_switch.off";
        };
    }
}
