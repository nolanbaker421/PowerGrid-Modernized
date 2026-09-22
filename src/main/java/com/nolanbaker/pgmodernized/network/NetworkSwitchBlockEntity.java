package com.nolanbaker.pgmodernized.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Eight ports, one network node. Lights each port's block state flag while a cable is plugged into it. */
public class NetworkSwitchBlockEntity extends NetworkJackBlockEntity {
    private static final int REFRESH_INTERVAL = 20;

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
}
