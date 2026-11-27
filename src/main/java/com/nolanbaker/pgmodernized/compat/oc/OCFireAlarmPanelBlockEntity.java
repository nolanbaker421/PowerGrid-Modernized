package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import com.nolanbaker.pgmodernized.conduit.fire.FireDevice;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * The fire alarm panel as an OpenComputers node, component "powergrid_fire_alarm", reached over
 * its Cat6 jack: state, devices, zones, and Silence, Reset and Drill. Signals "fire_alarm"
 * (state, alarm, silenced, drill, powered) and "fire_device" (address, status, kind, label, zone).
 */
public class OCFireAlarmPanelBlockEntity extends FireAlarmPanelBlockEntity implements Environment, FireAlarmPanelBlockEntity.Listener {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_fire_alarm");

    public OCFireAlarmPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addListener(this);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
    }

    @Override
    public void panelChanged(State state, boolean alarm, boolean silenced, boolean drill, boolean powered) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "fire_alarm", state.name().toLowerCase(Locale.ROOT), alarm, silenced, drill, powered);
    }

    @Override
    public void deviceChanged(FireDevice device) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "fire_device", device.address(), device.status().name().toLowerCase(Locale.ROOT), device.kind().id(), device.label(), zoneOf(device.address()));
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    private static Object[] result(Object... values) {
        return values;
    }

    private static int zone(Arguments args) {
        int zone = args.checkInteger(0);
        if(zone < 1 || zone > MAX_ZONES)
            throw new IllegalArgumentException("Zone must be between 1 and " + MAX_ZONES);
        return zone;
    }

    @Callback(direct = true, doc = "function():string -- normal, alarm or trouble.")
    public Object[] getState(Context context, Arguments args) {
        return result(state().name().toLowerCase(Locale.ROOT));
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the panel is in alarm.")
    public Object[] isInAlarm(Context context, Arguments args) {
        return result(inAlarm());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the panel has power.")
    public Object[] isPowered(Context context, Arguments args) {
        return result(powered());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the horns are silenced.")
    public Object[] isSilenced(Context context, Arguments args) {
        return result(silenced());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether a drill is running.")
    public Object[] isDrill(Context context, Arguments args) {
        return result(drill());
    }

    @Callback(direct = true, doc = "function():table -- state, alarm, silenced, drill, powered, feedVoltage, loopCurrent, notificationCurrent, loopOpen, notificationOpen, devices.")
    public Object[] getStatus(Context context, Arguments args) {
        return result(describe());
    }

    @Callback(direct = true, doc = "function():table -- Every device by loop address: address, kind, label, status, tripped, zone, zoneName, x, y, z.")
    public Object[] getDevices(Context context, Arguments args) {
        return result(describeDevices());
    }

    @Callback(direct = true, doc = "function(address:number):table -- One device by address, or nil.")
    public Object[] getDevice(Context context, Arguments args) {
        return result(describeDevice(args.checkInteger(0)));
    }

    @Callback(direct = true, doc = "function():table -- Every zone with a name or a device: zone, name, devices, alarm.")
    public Object[] getZones(Context context, Arguments args) {
        return result(describeZones());
    }

    @Callback(direct = true, doc = "function(zone:number):table -- One zone: zone, name, devices, alarm.")
    public Object[] getZone(Context context, Arguments args) {
        return result(describeZone(zone(args)));
    }

    @Callback(direct = true, doc = "function(zone:number):boolean -- Whether a device of that zone is in alarm.")
    public Object[] isZoneInAlarm(Context context, Arguments args) {
        return result(zoneAlarm(zone(args)));
    }

    @Callback(doc = "function() -- Quiets the horns; the alarm stands.")
    public Object[] silence(Context context, Arguments args) {
        plcSilence();
        return result();
    }

    @Callback(doc = "function():boolean -- Clears the alarm; false while a device still holds the loop.")
    public Object[] reset(Context context, Arguments args) {
        return result(plcReset());
    }

    @Callback(doc = "function(on:boolean) -- Starts or ends a drill, an alarm with no device; ending it is a reset.")
    public Object[] drill(Context context, Arguments args) {
        plcDrill(args.checkBoolean(0));
        return result();
    }
}
