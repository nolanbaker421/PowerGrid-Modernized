package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.rangefinder.RangefinderBlockEntity;
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

/** Rangefinder as OpenComputers component "powergrid_rangefinder", with an optional 'distance_change' signal. */
public class OCRangefinderBlockEntity extends RangefinderBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_rangefinder");
    private float reported = -2;
    private float changeThreshold = 0;

    public OCRangefinderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || changeThreshold <= 0 || ocNode.network() == null)
            return;
        float value = distance();
        if(Math.abs(value - reported) >= changeThreshold) {
            reported = value;
            ocNode.sendToReachable("computer.signal", "distance_change", (double) value);
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

    @Callback(direct = true, doc = "function():number -- Distance to the target in blocks, or -1 with nothing in range.")
    public Object[] getDistance(Context context, Arguments args) {
        return result((double) distance());
    }

    @Callback(direct = true, doc = "function():string -- What the beam hit: none, block, body (a physics body) or entity.")
    public Object[] getTarget(Context context, Arguments args) {
        return result(switch(hitKind()) {
            case HIT_BLOCK -> "block";
            case HIT_BODY -> "body";
            case HIT_ENTITY -> "entity";
            default -> "none";
        });
    }

    @Callback(direct = true, doc = "function():number -- Range limit in blocks.")
    public Object[] getRange(Context context, Arguments args) {
        return result((double) range());
    }

    @Callback(doc = "function(blocks:number) -- Range limit in blocks, up to the configured maximum; also scales the comparator output.")
    public Object[] setRange(Context context, Arguments args) {
        setRange(args.checkDouble(0));
        return result((double) range());
    }

    @Callback(doc = "function(blocks:number) -- Minimum change that raises a 'distance_change' signal; 0 disables signals.")
    public Object[] setChangeThreshold(Context context, Arguments args) {
        changeThreshold = (float) Math.max(0, args.checkDouble(0));
        setChanged();
        return result();
    }
}
