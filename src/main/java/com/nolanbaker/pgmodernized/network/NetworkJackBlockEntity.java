package com.nolanbaker.pgmodernized.network;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Standalone wall jack: one port, and adjacent computer-mod cables join its network. */
public class NetworkJackBlockEntity extends SmartBlockEntity implements INetworkJack {
    private final JackSupport jack = new JackSupport(this, true);

    public NetworkJackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return NetworkJackBlock.jackPosition(getBlockState(), getBlockPos());
    }
}
