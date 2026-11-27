package com.nolanbaker.pgmodernized.device.light;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A glow that checks once a second that a lit lamp above still claims it, and goes out otherwise. */
public class StripLightGlowBlockEntity extends SmartBlockEntity {
    public StripLightGlowBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        // A fixture's lamp may hang a block or two below its anchor, so look a little past the projection.
        for(int i = 1; i <= StripLightBlockEntity.PROJECTION + 3; ++i) {
            var p = worldPosition.above(i);
            if(!level.isLoaded(p))
                return;
            if(level.getBlockEntity(p) instanceof IGlowOwner owner && owner.ownsGlow(worldPosition))
                return;
        }
        level.removeBlock(worldPosition, false);
    }
}
