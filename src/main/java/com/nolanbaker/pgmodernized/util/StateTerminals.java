package com.nolanbaker.pgmodernized.util;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Terminal collections that change with the block state. Power Grid's wiring overlay draws every
 * terminal in a state's collection, so a terminal a state does not have must be a speck in that
 * state's array, not merely refused by the block's lookup.
 */
public final class StateTerminals {
    private StateTerminals() {}

    /** A terminal nobody can see or hit: a hundredth of a pixel, inside the back wall, keeping its name and colour. */
    public static TerminalBoundingBox speck(TerminalBoundingBox like) {
        return new TerminalBoundingBox(like.getName(), 7.99, 7.99, 15.9, 8.01, 8.01, 15.92).withColor(like.getColor());
    }

    /** The array with every terminal that fails {@code present} turned into a speck. */
    public static TerminalBoundingBox[] present(TerminalBoundingBox[] all, IntPredicate present) {
        var out = new TerminalBoundingBox[all.length];
        for(int i = 0; i < all.length; ++i)
            out[i] = all[i] == null ? null : present.test(i) ? all[i] : speck(all[i]);
        return out;
    }

    /**
     * Power Grid's horizontal north-frame collection, with the terminal array chosen per state:
     * the same quarter turns per facing as {@code horizontalNorthTerminals}, and the same shape.
     */
    public static BlockStateTerminalCollection horizontalNorth(Block block, VoxelShape northShape, Function<BlockState, TerminalBoundingBox[]> perState) {
        var shaper = VoxelShaper.forHorizontal(northShape, Direction.NORTH);
        return BlockStateTerminalCollection.builder(block)
                .forAllStates(state -> BlockStateTerminalCollection.each(perState.apply(state), terminal -> switch(state.getValue(HorizontalElectricBlock.HORIZONTAL_FACING)) {
                    case SOUTH -> terminal.rotateAroundY(180);
                    case EAST -> terminal.rotateAroundY(90);
                    case WEST -> terminal.rotateAroundY(-90);
                    default -> terminal;
                }))
                .withShapeMapper(state -> shaper.get(state.getValue(HorizontalElectricBlock.HORIZONTAL_FACING)))
                .build();
    }
}
