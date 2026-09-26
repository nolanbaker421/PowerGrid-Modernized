package com.nolanbaker.pgmodernized.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Eight ports. In switch mode every port shares one network node, so computers on it see each
 * other's components as if on one cable. In relay mode each port is its own network and only
 * network messages cross between them, like OpenComputers' relay block. Lights each port's block
 * state flag while a cable is plugged into it.
 */
public class NetworkSwitchBlockEntity extends NetworkJackBlockEntity {
    private static final int REFRESH_INTERVAL = 20;

    private boolean isolated;

    public NetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int portCount() {
        return NetworkSwitchBlock.PORTS;
    }

    @Override
    public int portAt(Vec3 localHit) {
        return NetworkSwitchBlock.portAt(getBlockState(), localHit);
    }

    @Override
    public Vec3 jackPosition(int port) {
        return NetworkSwitchBlock.jackPosition(getBlockState(), getBlockPos(), port);
    }

    /** Relay mode: ports are separate networks that only pass network messages. */
    public boolean isIsolated() {
        return isolated;
    }

    /** Server side. Every cable re-links itself within a second. */
    public void toggleMode() {
        isolated = !isolated;
        onModeChanged();
        networkJack().unload();
        setChanged();
        sendData();
    }

    /** The computer-mod subclasses rebuild their nodes here. */
    protected void onModeChanged() {}

    @Override
    public void tick() {
        super.tick();
        // Cables that vanished while this chunk was unloaded never call back, so re-check now and then.
        if(level != null && !level.isClientSide && level.getGameTime() % REFRESH_INTERVAL == 0)
            onCablesChanged();
    }

    @Override
    public void onCablesChanged() {
        if(level == null || level.isClientSide)
            return;
        var state = getBlockState();
        if(!(state.getBlock() instanceof NetworkSwitchBlock))
            return;
        var jack = networkJack();
        var updated = state;
        for(int i = 0; i < NetworkSwitchBlock.PORTS; ++i)
            updated = updated.setValue(NetworkSwitchBlock.PORT[i], jack.isPortUsed(i));
        if(updated != state)
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Isolated", isolated);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boolean was = isolated;
        isolated = tag.getBoolean("Isolated");
        if(was != isolated && level != null && !level.isClientSide)
            onModeChanged();
    }
}
