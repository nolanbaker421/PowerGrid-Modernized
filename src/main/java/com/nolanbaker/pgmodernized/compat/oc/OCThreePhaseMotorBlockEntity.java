package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.motor.ThreePhaseMotorBlockEntity;
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

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Three-phase motor that plugs straight into OpenComputers cables as component "powergrid_three_phase_motor". */
public class OCThreePhaseMotorBlockEntity extends ThreePhaseMotorBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_three_phase_motor");

    public OCThreePhaseMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
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

    @Callback(direct = true, doc = "function():number -- Supply frequency (Hz).")
    public Object[] getFrequency(Context context, Arguments args) {
        return result((double) frequency());
    }

    @Callback(direct = true, doc = "function():number -- Phase voltage (V RMS).")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) phaseVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Phase current (A RMS).")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) phaseCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Phase sequence: 1 for U-V-W, -1 reversed, 0 none.")
    public Object[] getSequence(Context context, Arguments args) {
        return result(sequence());
    }

    @Callback(direct = true, doc = "function():number -- Shaft speed (rpm).")
    public Object[] getSpeed(Context context, Arguments args) {
        return result((double) generatedRpm());
    }

    @Callback(direct = true, doc = "function():number -- Synchronous speed (rpm) at the present frequency.")
    public Object[] getSynchronousSpeed(Context context, Arguments args) {
        return result((double) synchronousSpeed());
    }

    @Callback(direct = true, doc = "function():number -- Pole pairs set on the motor.")
    public Object[] getPolePairs(Context context, Arguments args) {
        return result(polePairs());
    }
}
