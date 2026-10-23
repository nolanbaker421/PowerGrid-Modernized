package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.radio.CollectorBlockEntity;
import com.nolanbaker.pgmodernized.radio.MeterReport;
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

import java.util.LinkedHashMap;
import java.util.Map;

/** Collector that is an OpenComputers node, component "powergrid_collector"; new reports arrive as the signal "meter_report". */
public class OCCollectorBlockEntity extends CollectorBlockEntity implements Environment, CollectorBlockEntity.Listener {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_collector");

    public OCCollectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addListener(this);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
    }

    @Override
    public void onReport(MeterReport report) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "meter_report", report.id(), report.totalWatts(), report.totalWattHours());
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

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    @Callback(direct = true, doc = "function():string -- The utility ID this collector listens for.")
    public Object[] getUtility(Context context, Arguments args) {
        return result(utility());
    }

    @Callback(doc = "function(id:string) -- Sets the utility ID.")
    public Object[] setUtility(Context context, Arguments args) {
        setUtility(args.checkString(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Reach in blocks after the coax run.")
    public Object[] getRange(Context context, Arguments args) {
        return result(range());
    }

    @Callback(direct = true, doc = "function():number -- Meters heard.")
    public Object[] getMeterCount(Context context, Arguments args) {
        return result(meters().size());
    }

    @Callback(direct = true, doc = "function():table -- Every meter, nearest first: id, utility, x, y, z, time, age, totalPower, totalEnergy, voltage{}, current{}, power{}, powerFactor{}, energy{}.")
    public Object[] getMeters(Context context, Arguments args) {
        var table = new LinkedHashMap<Integer, Map<String, Object>>();
        int i = 1;
        for(var report : meters())
            table.put(i++, report.toTable(now()));
        return result(table);
    }

    @Callback(direct = true, doc = "function(id:string):table -- One meter by ID, or nil.")
    public Object[] getMeter(Context context, Arguments args) {
        var report = meter(args.checkString(0));
        return result(report == null ? null : report.toTable(now()));
    }
}
