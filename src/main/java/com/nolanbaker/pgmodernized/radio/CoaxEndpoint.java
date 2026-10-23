package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.network.JackEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

import java.util.Objects;

/**
 * Wire endpoint on one port of an {@link ICoaxPort}. As the Cat6 jack endpoint does, it serialises in
 * the shape of a block endpoint (position plus a "terminal", here the port) because Power Grid's
 * endpoint types are a closed set, and the coax entity converts it back on load. It never takes part
 * in the electrical simulation.
 */
public class CoaxEndpoint implements IWireEndpoint {
    private BlockPos pos;
    private int port;

    public CoaxEndpoint(BlockPos pos, int port) {
        this.pos = pos;
        this.port = port;
    }

    @Nullable
    public static IWireEndpoint from(@Nullable IWireEndpoint endpoint) {
        if(endpoint == null || endpoint instanceof CoaxEndpoint)
            return endpoint;
        if(endpoint instanceof BlockWireEndpoint block)
            return new CoaxEndpoint(block.getPos(), block.getTerminal());
        if(endpoint instanceof JackEndpoint jack)
            return new CoaxEndpoint(jack.getPos(), jack.getPort());
        return endpoint;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getPort() {
        return port;
    }

    @Override
    public WireEndpointType type() {
        return WireEndpointType.BLOCK;
    }

    @Override
    public void read(CompoundTag nbt) {
        var arr = nbt.getIntArray("Pos");
        pos = new BlockPos(arr[0], arr[1], arr[2]);
        port = nbt.getInt("Terminal");
    }

    @Override
    public void write(CompoundTag nbt) {
        nbt.putIntArray("Pos", new int[] {pos.getX(), pos.getY(), pos.getZ()});
        nbt.putInt("Terminal", port);
    }

    private boolean chunkLoaded(Level level) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Nullable
    public ICoaxPort portBlock(Level level) {
        if(!chunkLoaded(level))
            return null;
        return level.getBlockEntity(pos) instanceof ICoaxPort block ? block : null;
    }

    @Override
    @NotNull
    public Vec3 getExactPosition(Level level) {
        var block = portBlock(level);
        return block == null ? pos.getCenter() : block.coaxPosition(port);
    }

    @Override
    public boolean isValid(Level level) {
        var block = portBlock(level);
        return block != null && port >= 0 && port < block.coaxPortCount() && !block.coaxSupport().isRemoved();
    }

    @Override
    public <T extends BaseWireEntity> boolean canAcceptType(Class<T> clazz) {
        return CoaxWireEntity.class.isAssignableFrom(clazz);
    }

    @Override
    public OwnedFloatingNode getNode(Level level) {
        return null;
    }

    @Override
    public void joinNetwork(Level level, ElectricalNetwork network) {}

    @Override
    public void assignWireEntity(BaseWireEntity entity) {
        var block = portBlock(entity.level());
        if(block != null && entity instanceof CoaxWireEntity coax)
            block.coaxSupport().add(coax);
    }

    @Override
    public void removeWireEntity(BaseWireEntity entity) {
        var block = portBlock(entity.level());
        if(block != null && entity instanceof CoaxWireEntity coax)
            block.coaxSupport().remove(coax);
    }

    @Override
    public IWireEndpoint makeOffset(BlockPos offset) {
        return new CoaxEndpoint(pos.offset(offset), port);
    }

    @Override
    public MoveAction shouldMove(Level level, Iterable<BlockPos> allBlocks) {
        for(var block : allBlocks) {
            if(block.equals(pos))
                return MoveAction.MOVE;
        }
        return MoveAction.STAY;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CoaxEndpoint other && pos.equals(other.pos) && port == other.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash("coax", pos, port);
    }

    @Override
    public String toString() {
        return "Coax(pos=" + pos + ", port=" + port + ")";
    }
}
