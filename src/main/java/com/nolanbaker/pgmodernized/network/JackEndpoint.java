package com.nolanbaker.pgmodernized.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

import java.util.Objects;

/**
 * Wire endpoint sitting on one port of an {@link INetworkJack}. Power Grid's endpoint type enum is
 * closed, so this serialises in the same shape as a block endpoint (position plus a "terminal", which
 * here is the port) and the Cat6 entities convert it back on load. It never takes part in the
 * electrical simulation.
 */
public class JackEndpoint implements IWireEndpoint {
    private BlockPos pos;
    private int port;

    public JackEndpoint(BlockPos pos) {
        this(pos, 0);
    }

    public JackEndpoint(BlockPos pos, int port) {
        this.pos = pos;
        this.port = port;
    }

    /** Accept whatever Power Grid deserialised and turn it into a jack endpoint. */
    @Nullable
    public static IWireEndpoint from(@Nullable IWireEndpoint endpoint) {
        if(endpoint == null || endpoint instanceof JackEndpoint)
            return endpoint;
        if(endpoint instanceof BlockWireEndpoint block)
            return new JackEndpoint(block.getPos(), block.getTerminal());
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
        nbt.putIntArray("Pos", new int[] { pos.getX(), pos.getY(), pos.getZ() });
        nbt.putInt("Terminal", port);
    }

    private boolean chunkLoaded(Level level) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Nullable
    public INetworkJack jackBlock(Level level) {
        if(!chunkLoaded(level))
            return null;
        return level.getBlockEntity(pos) instanceof INetworkJack jack ? jack : null;
    }

    @Nullable
    public JackSupport jack(Level level) {
        var block = jackBlock(level);
        return block == null ? null : block.networkJack();
    }

    /** The jack terminal's placement on a device block, for path finding; null for jack and switch blocks. */
    @Nullable
    public ITerminalPlacement terminalPlacement(Level level) {
        var jack = jackBlock(level);
        if(jack == null || jack.jackTerminalIndex() < 0)
            return null;
        var electric = IElectric.getAt(level, pos);
        if(electric == null)
            return null;
        return electric.terminal(level.getBlockState(pos), jack.jackTerminalIndex());
    }

    @Override
    @NotNull
    public Vec3 getExactPosition(Level level) {
        var jack = jackBlock(level);
        return jack == null ? pos.getCenter() : jack.jackPosition(port);
    }

    @Override
    public boolean isValid(Level level) {
        var block = jackBlock(level);
        return block != null && port >= 0 && port < block.portCount() && !block.networkJack().isRemoved();
    }

    @Override
    public <T extends BaseWireEntity> boolean canAcceptType(Class<T> clazz) {
        return ICat6Cable.class.isAssignableFrom(clazz);
    }

    @Override
    public OwnedFloatingNode getNode(Level level) {
        return null;
    }

    @Override
    public void joinNetwork(Level level, ElectricalNetwork network) {}

    @Override
    public void assignWireEntity(BaseWireEntity entity) {
        var jack = jack(entity.level());
        if(jack != null && entity instanceof ICat6Cable cable)
            jack.addCable(cable);
    }

    @Override
    public void removeWireEntity(BaseWireEntity entity) {
        var jack = jack(entity.level());
        if(jack != null && entity instanceof ICat6Cable cable)
            jack.removeCable(cable);
    }

    @Override
    public IWireEndpoint makeOffset(BlockPos offset) {
        return new JackEndpoint(pos.offset(offset), port);
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
        return obj == this || (obj instanceof JackEndpoint other && pos.equals(other.pos) && port == other.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash("jack", pos, port);
    }

    @Override
    public String toString() {
        return "Jack(pos=" + pos + ", port=" + port + ")";
    }
}
