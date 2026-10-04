package com.nolanbaker.pgmodernized.ac.compat.oc;

import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlockEntity;
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

import com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport;
import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Synchroscope that plugs straight into OpenComputers cables as component "powergrid_synchroscope". */
public class OCSynchroscopeBlockEntity extends SynchroscopeBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_synchroscope");
    private boolean reportedSync;
    private boolean reportedAny;

    public OCSynchroscopeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || ocNode.network() == null)
            return;
        boolean ready = inSync();
        if(!reportedAny || ready != reportedSync) {
            reportedAny = true;
            reportedSync = ready;
            ocNode.sendToReachable("computer.signal", "sync_change", ready, (double) angle(), (double) slip());
        }
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

    @Callback(direct = true, doc = "function():number -- Bus frequency (Hz).")
    public Object[] getBusFrequency(Context context, Arguments args) {
        return result((double) busFrequency());
    }

    @Callback(direct = true, doc = "function():number -- Incoming frequency (Hz).")
    public Object[] getIncomingFrequency(Context context, Arguments args) {
        return result((double) incomingFrequency());
    }

    @Callback(direct = true, doc = "function():number -- Bus voltage (V RMS).")
    public Object[] getBusVoltage(Context context, Arguments args) {
        return result((double) busVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Incoming voltage (V RMS).")
    public Object[] getIncomingVoltage(Context context, Arguments args) {
        return result((double) incomingVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Hertz the incoming runs faster than the bus.")
    public Object[] getSlip(Context context, Arguments args) {
        return result((double) slip());
    }

    @Callback(direct = true, doc = "function():number -- Degrees the incoming leads the bus, -180 to 180.")
    public Object[] getPhaseAngle(Context context, Arguments args) {
        return result((double) angle());
    }

    @Callback(direct = true, doc = "function():boolean -- Within a tenth of a hertz, ten degrees and five percent: ready to close.")
    public Object[] isInSync(Context context, Arguments args) {
        return result(inSync());
    }
}
