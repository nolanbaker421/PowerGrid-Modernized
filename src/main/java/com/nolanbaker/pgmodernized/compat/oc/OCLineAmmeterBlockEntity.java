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
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Line ammeter that plugs straight into OpenComputers cables as component "powergrid_ammeter". */
public class OCLineAmmeterBlockEntity extends LineAmmeterBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_ammeter");
    private float reported;
    private float changeThreshold = 0;

    public OCLineAmmeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || changeThreshold <= 0 || ocNode.network() == null)
            return;
        float value = getCurrent();
        if(Math.abs(value - reported) >= changeThreshold) {
            reported = value;
            ocNode.sendToReachable("computer.signal", "current_change", (double) value);
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

    @Callback(direct = true, doc = "function():number -- Current (A) through the meter, positive from the IN to the OUT terminal.")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) getCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Power (W) dissipated in the meter's shunt.")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) getPower());
    }

    @Callback(doc = "function(amps:number) -- Minimum change that raises a 'current_change' signal; 0 disables signals.")
    public Object[] setChangeThreshold(Context context, Arguments args) {
        changeThreshold = (float) Math.max(0, args.checkDouble(0));
        setChanged();
        return result();
    }
}
