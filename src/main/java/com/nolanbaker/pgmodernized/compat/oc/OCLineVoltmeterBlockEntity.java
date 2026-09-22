package com.nolanbaker.pgmodernized.compat.oc;

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
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Line voltmeter that plugs straight into OpenComputers cables as component "powergrid_voltmeter". */
public class OCLineVoltmeterBlockEntity extends LineVoltmeterBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_voltmeter");
    private float reported;
    private float changeThreshold = 0;

    public OCLineVoltmeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || changeThreshold <= 0 || ocNode.network() == null)
            return;
        float value = getVoltage();
        if(Math.abs(value - reported) >= changeThreshold) {
            reported = value;
            ocNode.sendToReachable("computer.signal", "voltage_change", (double) value);
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
        if(!clientPacket && tag.contains("ChangeThreshold"))
            changeThreshold = tag.getFloat("ChangeThreshold");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
        if(!clientPacket)
            tag.putFloat("ChangeThreshold", changeThreshold);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    @Callback(direct = true, doc = "function():number -- Voltage (V) of probe + relative to probe -. No range limit.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) getVoltage());
    }

    @Callback(doc = "function(volts:number) -- Minimum change that raises a 'voltage_change' signal; 0 disables signals.")
    public Object[] setChangeThreshold(Context context, Arguments args) {
        changeThreshold = (float) Math.max(0, args.checkDouble(0));
        setChanged();
        return result();
    }
}
