package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A post insulator of a pole-top switch: one block of porcelain column. A contact head stands on
 * a stack of them over an operator; each insulator in the stack adds to the voltage the open
 * contact holds off, so a line wants as many under its switch as its voltage asks.
 */
public class SwitchInsulatorBlock extends Block {
    /** What one insulator adds to the open contact's withstand, and what a head alone has. */
    public static final int VOLTS_PER_INSULATOR = 18_000, BASE_WITHSTAND = 2_000;
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 16, 11);

    public SwitchInsulatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The withstand of a head standing on the stack of insulators under {@code head}. */
    public static int withstand(BlockGetter level, BlockPos head) {
        int count = 0;
        var at = head.below();
        while(count < 8 && level.getBlockState(at).getBlock() instanceof SwitchInsulatorBlock) {
            ++count;
            at = at.below();
        }
        return BASE_WITHSTAND + VOLTS_PER_INSULATOR * count;
    }
}
