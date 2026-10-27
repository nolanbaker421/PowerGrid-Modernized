package com.nolanbaker.pgmodernized.conduit.fire;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.*;

/**
 * The panel's insides: a load across line and neutral that tells it whether it is fed, a 24 V
 * source on each loop, and the three states. The initiating loop reads its current: a detector's
 * supervisory resistor draws a milliampere or so, a detector in alarm draws twenty or more, and a
 * loop with nothing on it draws none. The notification loop idles at two volts, enough to see a
 * horn strobe's load without waking it, and goes to twenty-four in alarm, or twelve once silenced.
 */
public class FireAlarmPanelBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IHaveGoggleInformation, com.nolanbaker.pgmodernized.network.INetworkJack {
    public enum State { NORMAL, ALARM, TROUBLE }

    public static final float LOOP_VOLTS = 24, SILENCED_VOLTS = 12, SUPERVISORY_VOLTS = 2;
    public static final float POWERED_VOLTS = 90, ALARM_AMPS = 0.010f, SUPERVISED_AMPS = 0.0005f;

    private DeviceSpliceHost deviceHubs;
    private ElectricWire feed;
    private ProvidedVoltageSourceCoupling idc, nac;
    /** The three dry contacts: Alarm, Trouble, Silenced. */
    private org.patryk3211.powergrid.electricity.sim.SwitchedWire[] contacts;
    /** The network jack a controls cabinet reaches the panel through. */
    private final com.nolanbaker.pgmodernized.network.JackSupport jack = new com.nolanbaker.pgmodernized.network.JackSupport(this, false);
    /** A drill: alarm with no device, started by a PLC or a test, cleared by Reset. */
    private boolean drill;
    /** Zones: named groups of devices, by number; each device address belongs to one zone or none. */
    public static final int MAX_ZONES = 16;
    private final String[] zoneNames = new String[MAX_ZONES + 1];
    private final java.util.Map<Integer, Integer> zoneOf = new java.util.HashMap<>();
    private AcReadings.Filter feedReading;
    private float feedVolts, idcVolts, nacVolts, idcAmps, nacAmps;
    private boolean powered, alarm, silenced, idcOpen, nacOpen;
    private State state = State.NORMAL;
    private State syncedState = State.NORMAL;
    private boolean syncedPowered, syncedSilenced;
    private float syncedIdc, syncedNac;
    /** Every device the panel has found along its conduit, by address, as of the last survey. */
    private final java.util.TreeMap<Integer, FireDevice> devices = new java.util.TreeMap<>();
    private int nextAddress = 1;
    private int surveyTimer;
    private boolean deviceTrouble;
    /** Terminals a survey will look through at most; a mall is not a loop. */
    private static final int MAX_NODES = 8192, MAX_ADDRESS = 159;
    /** Ticks between surveys, and how long a device stays listed as missing before it is forgotten. */
    private static final int SURVEY_TICKS = 60, FORGET_TICKS = 6000;

    public FireAlarmPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        java.util.Arrays.fill(zoneNames, "");
    }

    // ---- zones ----

    public String zoneName(int zone) {
        return zone >= 1 && zone <= MAX_ZONES && zoneNames[zone] != null ? zoneNames[zone] : "";
    }

    /** The zone a device address belongs to, or 0 for none. */
    public int zoneOf(int address) {
        return zoneOf.getOrDefault(address, 0);
    }

    public int zoneCount(int zone) {
        int n = 0;
        for(var device : devices.values())
            if(zoneOf(device.address()) == zone)
                ++n;
        return n;
    }

    /** Whether any device in the zone is in alarm. Works on both sides. */
    public boolean zoneAlarm(int zone) {
        for(var device : devices.values())
            if(zoneOf(device.address()) == zone && device.status() == FireDevice.Status.ALARM)
                return true;
        return false;
    }

    /** Server side: puts a device in a zone, or in none with zero. */
    public void setZone(int address, int zone) {
        if(level == null || level.isClientSide || zone < 0 || zone > MAX_ZONES)
            return;
        if(zone == 0)
            zoneOf.remove(address);
        else
            zoneOf.put(address, zone);
        setChanged();
        sendData();
    }

    /** Server side. */
    public void setZoneName(int zone, String name) {
        if(level == null || level.isClientSide || zone < 1 || zone > MAX_ZONES)
            return;
        zoneNames[zone] = name == null ? "" : name;
        setChanged();
        sendData();
    }

    /** The display's name for a device's zone, or empty. */
    public String zoneDisplay(int address) {
        int zone = zoneOf(address);
        if(zone <= 0)
            return "";
        var name = zoneName(zone);
        return "ZONE " + zone + (name.isEmpty() ? "" : " " + name.toUpperCase());
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    // ---- the jack ----

    @Override
    public void tick() {
        super.tick();
        jack.tick();
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
    public com.nolanbaker.pgmodernized.network.JackSupport networkJack() {
        return jack;
    }

    @Override
    public net.minecraft.world.phys.Vec3 jackPosition(int port) {
        return org.patryk3211.powergrid.electricity.base.IElectric.getTerminalPos(level, worldPosition, JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return JACK;
    }

    // ---- what a PLC reads and writes over the jack ----

    public boolean drill() {
        return drill;
    }

    /** A device's state by address: 1 tripped or pulled, 0 quiet, NaN for no such device. */
    public double deviceTripped(int address) {
        var device = devices.get(address);
        if(device == null)
            return Double.NaN;
        return device.status() == FireDevice.Status.ALARM ? 1 : 0;
    }

    /** Server side: a PLC silences the horns. */
    public void plcSilence() {
        if(alarm && !silenced) {
            silenced = true;
            applyContacts();
            setChanged();
            sendData();
        }
    }

    /** Server side: a PLC resets the panel; refused while a device still holds the loop. True when it cleared. */
    public boolean plcReset() {
        if(!alarm)
            return true;
        if(idcAmps >= ALARM_AMPS)
            return false;
        alarm = false;
        silenced = false;
        drill = false;
        applyContacts();
        alarmChanged();
        return true;
    }

    /** Server side: a PLC starts or ends a drill, an alarm with no device; ending it is a reset. */
    public void plcDrill(boolean on) {
        if(on == drill)
            return;
        drill = on;
        if(on) {
            boolean was = alarm;
            alarm = true;
            applyContacts();
            if(!was)
                alarmChanged();
        } else {
            plcReset();
        }
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        feed = builder.connect(resistance("feed"), builder.terminalNode(TERMINAL_LINE), builder.terminalNode(TERMINAL_NEUTRAL));
        idc = new ProvidedVoltageSourceCoupling(builder.terminalNode(TERMINAL_IDC_PLUS), builder.terminalNode(TERMINAL_IDC_MINUS), resistance("loop"));
        idc.setVoltageProvider(() -> idcVolts);
        builder.add(idc);
        nac = new ProvidedVoltageSourceCoupling(builder.terminalNode(TERMINAL_NAC_PLUS), builder.terminalNode(TERMINAL_NAC_MINUS), resistance("loop"));
        nac.setVoltageProvider(() -> nacVolts);
        builder.add(nac);
        contacts = new org.patryk3211.powergrid.electricity.sim.SwitchedWire[CONTACT_COUNT];
        boolean[] closed = {alarm, state == State.TROUBLE, silenced};
        for(int c = 0; c < CONTACT_COUNT; ++c)
            contacts[c] = builder.connectSwitch(resistance("contact"), builder.terminalNode(contactTerminal(c, false)), builder.terminalNode(contactTerminal(c, true)), closed[c]);
    }

    private void applyContacts() {
        if(contacts == null)
            return;
        contacts[CONTACT_ALARM].setState(alarm);
        contacts[CONTACT_TROUBLE].setState(state == State.TROUBLE);
        contacts[CONTACT_SILENCED].setState(silenced);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    // ---- the panel's logic ----

    public boolean inAlarm() {
        return alarm;
    }

    public State state() {
        return state;
    }

    public boolean powered() {
        return powered;
    }

    public boolean silenced() {
        return silenced;
    }

    /** The panel's devices by address, as of the last survey. Works on both sides. */
    public java.util.Collection<FireDevice> devices() {
        return devices.values();
    }

    /**
     * Follows the wiring out of the panel's four loop terminals, conductor by conductor and splice
     * by splice, and gathers every fire alarm device it reaches: a new one gets the next free
     * address, a known one its state, and one no longer reached is marked missing until it has
     * been gone a while or is removed on the screen. A device in a box the loop passes through
     * but with nothing spliced to its own points shows as not spliced; a box the wiring never
     * reaches, however much conduit leads to it, is not on the loop.
     */
    private void survey() {
        long now = level.getGameTime();
        var seen = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint>();
        var hosts = new java.util.LinkedHashMap<BlockPos, java.util.Set<Integer>>();
        for(int terminal : new int[] {TERMINAL_IDC_PLUS, TERMINAL_IDC_MINUS, TERMINAL_NAC_PLUS, TERMINAL_NAC_MINUS}) {
            var start = new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(worldPosition, terminal);
            seen.add(nodeKey(start));
            queue.add(start);
        }
        while(!queue.isEmpty() && seen.size() < MAX_NODES) {
            var node = queue.poll();
            var pos = node.getPos();
            int terminal = node.getTerminal();
            if(!(level.getBlockEntity(pos) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity be))
                continue;
            hosts.computeIfAbsent(pos, p -> new java.util.HashSet<>()).add(terminal);
            var next = new java.util.ArrayList<org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint>();
            // Across a wire: a pulled conductor's far landing, or a bare wire's.
            next.addAll(com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.farEnds(be, terminal));
            // Across a splice inside the fitting.
            if(be instanceof com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost host) {
                for(var splice : host.splices().list()) {
                    if(splice[0] == terminal)
                        next.add(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos, splice[1]));
                    else if(splice[1] == terminal)
                        next.add(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos, splice[0]));
                }
            }
            for(var endpoint : next)
                if(seen.add(nodeKey(endpoint)))
                    queue.add(endpoint);
        }
        var found = new java.util.ArrayList<FireDevice>();
        for(var entry : hosts.entrySet()) {
            if(entry.getKey().equals(worldPosition))
                continue;
            if(level.getBlockEntity(entry.getKey()) instanceof IFireDevice device)
                gather(device, entry.getKey(), entry.getValue(), now, found);
        }
        // Addresses: a device keeps the one it has unless another found device holds it too.
        var taken = new java.util.HashSet<Integer>();
        var fresh = new java.util.TreeMap<Integer, FireDevice>();
        for(var device : found) {
            int address = device.address();
            if(address <= 0 || address > MAX_ADDRESS || taken.contains(address)) {
                address = nextFree(taken);
                if(address < 0)
                    continue;
                device = device.withAddress(address);
                if(level.getBlockEntity(device.pos()) instanceof IFireDevice holder)
                    holder.setFireAddress(device.slot(), address);
            }
            taken.add(address);
            fresh.put(address, device);
        }
        for(var old : devices.values()) {
            if(fresh.containsKey(old.address()))
                continue;
            if(now - old.lastSeen() < FORGET_TICKS)
                fresh.put(old.address(), old.with(FireDevice.Status.MISSING, old.lastSeen()));
        }
        boolean changed = !fresh.equals(devices);
        devices.clear();
        devices.putAll(fresh);
        deviceTrouble = devices.values().stream().anyMatch(d -> d.status().trouble());
        if(changed) {
            setChanged();
            sendData();
        }
    }

    private static long nodeKey(org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint endpoint) {
        return endpoint.getPos().asLong() * 1024 + (endpoint.getTerminal() & 1023);
    }

    /** The devices in one fitting the wiring reached; {@code reached} is the set of its terminals the loop got to. */
    private void gather(IFireDevice holder, BlockPos pos, java.util.Set<Integer> reached, long now, java.util.List<FireDevice> out) {
        for(int slot = 0; slot < holder.fireSlots(); ++slot) {
            var kind = holder.fireKind(slot);
            if(kind == null)
                continue;
            boolean wired = false;
            for(int terminal : holder.fireTerminals(slot))
                wired |= reached.contains(terminal);
            FireDevice.Status status;
            if(!wired)
                status = FireDevice.Status.NOT_SPLICED;
            else if(kind.initiating())
                status = holder.fireTripped(slot) ? FireDevice.Status.ALARM : FireDevice.Status.NORMAL;
            else {
                float v = holder.fireHornVolts(slot);
                status = v >= com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity.HORN_VOLTS ? FireDevice.Status.SOUNDING
                        : v >= com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity.STROBE_VOLTS ? FireDevice.Status.STROBING : FireDevice.Status.NORMAL;
            }
            out.add(new FireDevice(pos, slot, holder.fireAddress(slot), kind, holder.fireLabel(slot), status, now));
        }
    }

    private int nextFree(java.util.Set<Integer> taken) {
        for(int i = 0; i < MAX_ADDRESS; ++i) {
            int candidate = (nextAddress - 1 + i) % MAX_ADDRESS + 1;
            if(!taken.contains(candidate) && !devices.containsKey(candidate)) {
                nextAddress = candidate % MAX_ADDRESS + 1;
                return candidate;
            }
        }
        return -1;
    }

    /** Server side: drops a device the panel no longer reaches from the list, freeing its address. One it still reaches comes straight back. */
    public void forget(int address) {
        if(level == null || level.isClientSide || devices.remove(address) == null)
            return;
        zoneOf.remove(address);
        deviceTrouble = devices.values().stream().anyMatch(d -> d.status().trouble());
        setChanged();
        sendData();
    }

    /** The first device in the given status, by address, or null. Works on both sides. */
    @Nullable
    public FireDevice first(java.util.function.Predicate<FireDevice.Status> which) {
        for(var device : devices.values())
            if(which.test(device.status()))
                return device;
        return null;
    }

    public int count(java.util.function.Predicate<FireDevice.Status> which) {
        int n = 0;
        for(var device : devices.values())
            if(which.test(device.status()))
                ++n;
        return n;
    }

    /** For Ponder: a client-side panel shown in a state, with its lights. */
    public void demo(State shown, boolean silencedShown) {
        powered = true;
        state = shown;
        alarm = shown == State.ALARM;
        silenced = silencedShown;
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || feed == null)
            return;
        if(feedReading == null)
            feedReading = new AcReadings.Filter();
        if(feed.isConverged())
            feedReading.sample(feed);
        feedVolts = (float) AcReadings.finite(feedReading.rmsVoltage());
        powered = feedVolts >= POWERED_VOLTS;
        idcAmps = powered && idc != null ? (float) AcReadings.finite(Math.abs(idc.getCurrent())) : 0;
        nacAmps = powered && nac != null ? (float) AcReadings.finite(Math.abs(nac.getCurrent())) : 0;
        boolean wasAlarm = alarm;
        if(!powered) {
            idcVolts = 0;
            nacVolts = 0;
            idcOpen = false;
            nacOpen = false;
            state = State.TROUBLE;
        } else {
            if(idcAmps >= ALARM_AMPS || drill)
                alarm = true;
            idcOpen = idcAmps < SUPERVISED_AMPS;
            nacOpen = !alarm && nacAmps < SUPERVISED_AMPS;
            state = alarm ? State.ALARM : idcOpen || nacOpen || deviceTrouble ? State.TROUBLE : State.NORMAL;
            idcVolts = LOOP_VOLTS;
            nacVolts = alarm ? (silenced ? SILENCED_VOLTS : LOOP_VOLTS) : SUPERVISORY_VOLTS;
        }
        applyContacts();
        if(alarm != wasAlarm)
            alarmChanged();
    }

    private void alarmChanged() {
        setChanged();
        sendData();
        if(level != null)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    /** Silence: the horns stop, the strobes keep going, the alarm stands. Server side. */
    public void silence(Player player) {
        if(level == null || level.isClientSide)
            return;
        if(!alarm) {
            message(player, "message.fire_alarm.nothing_to_silence", ChatFormatting.GRAY);
            return;
        }
        silenced = true;
        applyContacts();
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.2f);
        message(player, "message.fire_alarm.silenced", ChatFormatting.YELLOW);
        setChanged();
        sendData();
    }

    /** Reset: the alarm clears if no detector is still holding the loop. Server side. */
    public void reset(Player player) {
        if(level == null || level.isClientSide)
            return;
        if(!alarm) {
            message(player, "message.fire_alarm.nothing_to_reset", ChatFormatting.GRAY);
            return;
        }
        if(idcAmps >= ALARM_AMPS) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5f, 0.8f);
            message(player, "message.fire_alarm.reset_refused", ChatFormatting.RED);
            return;
        }
        alarm = false;
        silenced = false;
        drill = false;
        applyContacts();
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.2f);
        message(player, "message.fire_alarm.reset", ChatFormatting.GREEN);
        alarmChanged();
    }

    private static void message(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        surveyTimer += 10;
        if(surveyTimer >= SURVEY_TICKS) {
            surveyTimer = 0;
            survey();
        }
        if(state != syncedState || powered != syncedPowered || silenced != syncedSilenced
                || Math.abs(idcAmps - syncedIdc) > 0.001f || Math.abs(nacAmps - syncedNac) > 0.005f) {
            syncedState = state;
            syncedPowered = powered;
            syncedSilenced = silenced;
            syncedIdc = idcAmps;
            syncedNac = nacAmps;
            sendData();
        }
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("Silenced", silenced);
        tag.putBoolean("Drill", drill);
        tag.putInt("State", state.ordinal());
        tag.putBoolean("Powered", powered);
        tag.putFloat("FeedVolts", feedVolts);
        tag.putFloat("IdcAmps", idcAmps);
        tag.putFloat("NacAmps", nacAmps);
        tag.putBoolean("IdcOpen", idcOpen);
        tag.putBoolean("NacOpen", nacOpen);
        writeDevices(tag);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        deviceHubs().write(tag, registries, false);
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("Silenced", silenced);
        tag.putBoolean("Drill", drill);
        writeDevices(tag);
    }

    private void writeDevices(CompoundTag tag) {
        var list = new net.minecraft.nbt.ListTag();
        for(var device : devices.values())
            list.add(device.write());
        tag.put("Devices", list);
        tag.putInt("NextAddress", nextAddress);
        var names = new net.minecraft.nbt.ListTag();
        for(int z = 1; z <= MAX_ZONES; ++z)
            names.add(net.minecraft.nbt.StringTag.valueOf(zoneName(z)));
        tag.put("ZoneNames", names);
        var zones = new int[zoneOf.size() * 2];
        int i = 0;
        for(var entry : zoneOf.entrySet()) {
            zones[i++] = entry.getKey();
            zones[i++] = entry.getValue();
        }
        tag.putIntArray("Zones", zones);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        alarm = tag.getBoolean("Alarm");
        silenced = tag.getBoolean("Silenced");
        drill = tag.getBoolean("Drill");
        devices.clear();
        var list = tag.getList("Devices", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i = 0; i < list.size(); ++i) {
            var device = FireDevice.read(list.getCompound(i));
            devices.put(device.address(), device);
        }
        nextAddress = Math.max(1, tag.getInt("NextAddress"));
        deviceTrouble = devices.values().stream().anyMatch(d -> d.status().trouble());
        var names = tag.getList("ZoneNames", net.minecraft.nbt.Tag.TAG_STRING);
        for(int z = 1; z <= MAX_ZONES; ++z)
            zoneNames[z] = z - 1 < names.size() ? names.getString(z - 1) : "";
        zoneOf.clear();
        var zones = tag.getIntArray("Zones");
        for(int i = 0; i + 1 < zones.length; i += 2)
            zoneOf.put(zones[i], zones[i + 1]);
        if(clientPacket) {
            int s = tag.getInt("State");
            state = s >= 0 && s < State.values().length ? State.values()[s] : State.NORMAL;
            powered = tag.getBoolean("Powered");
            feedVolts = tag.getFloat("FeedVolts");
            idcAmps = tag.getFloat("IdcAmps");
            nacAmps = tag.getFloat("NacAmps");
            idcOpen = tag.getBoolean("IdcOpen");
            nacOpen = tag.getBoolean("NacOpen");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.fire_alarm.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(powered)
            Lang.builder().translate("gui.fire_alarm.powered", String.format("%.0f", feedVolts)).style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.fire_alarm.unpowered").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        var stateStyle = switch(state) {
            case NORMAL -> ChatFormatting.GREEN;
            case ALARM -> ChatFormatting.RED;
            case TROUBLE -> ChatFormatting.YELLOW;
        };
        Lang.builder().translate("gui.fire_alarm.state_" + state.name().toLowerCase()).style(stateStyle).forGoggles(tooltip, 1);
        if(silenced)
            Lang.builder().translate("gui.fire_alarm.silenced").style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
        if(drill)
            Lang.builder().translate("gui.fire_alarm.drill").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        if(!jack.cables().isEmpty())
            Lang.builder().translate("gui.fire_alarm.networked").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        if(powered) {
            Lang.builder().translate(idcOpen ? "gui.fire_alarm.idc_open" : "gui.fire_alarm.idc", String.format("%.1f", idcAmps * 1000))
                    .style(idcOpen ? ChatFormatting.YELLOW : ChatFormatting.WHITE).forGoggles(tooltip, 1);
            Lang.builder().translate(nacOpen ? "gui.fire_alarm.nac_open" : "gui.fire_alarm.nac", String.format("%.1f", nacAmps * 1000))
                    .style(nacOpen ? ChatFormatting.YELLOW : ChatFormatting.WHITE).forGoggles(tooltip, 1);
        }
        Lang.builder().translate("gui.fire_alarm.devices", devices.size()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        int shown = 0;
        for(var device : devices.values()) {
            if(device.status() == FireDevice.Status.NORMAL || shown >= (isPlayerSneaking ? 12 : 4))
                continue;
            var style = switch(device.status()) {
                case ALARM, SOUNDING, STROBING -> ChatFormatting.RED;
                default -> ChatFormatting.YELLOW;
            };
            var line = Lang.builder().text(device.display()).text(" ").add(Lang.builder().translate("gui.fire_alarm.status_" + device.status().name().toLowerCase())).style(style);
            if(zoneOf(device.address()) > 0)
                line.text(" ").add(Lang.builder().text(zoneDisplay(device.address())).style(ChatFormatting.AQUA));
            line.forGoggles(tooltip, 1);
            ++shown;
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
