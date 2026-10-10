package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.general.ceilingtile.CeilingBlock;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A conduit box sitting on top of a Power Grid ceiling tile, in the plenum above the ceiling, the way
 * the tile takes a wire connector or a cord junction. Right-click a tile with a conduit box to fit
 * it; the wrench takes the box off and leaves the tile. It is the ordinary box in every other way:
 * same hubs, cover plates and splice editor, with the box lifted the thickness of the tile.
 */
public class CeilingConduitBoxBlock extends ConduitBoxBlock implements CeilingBlock, SpecialBlockItemRequirement {
    private static final VoxelShape TILE = Block.box(0, 0, 0, 16, 2, 16);
    /** The tile is 2 px thick; the box's back sits on it. */
    public static final double LIFT = 2;

    /** North frame to the tile frame: the back wall (z = 16) becomes the tile's top (y = 2); +y becomes +z. */
    public static final UnaryOperator<AABB> ON_TILE = px -> new AABB(px.minX, 16 + LIFT - px.maxZ, px.minY, px.maxX, 16 + LIFT - px.minZ, px.maxY);

    public CeilingConduitBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN).setValue(COVER, ConduitCover.OPEN));
        var terminals = ConduitBoxGeometry.terminals(ON_TILE);
        var plain = Shapes.or(TILE, ConduitBoxGeometry.shape(false, ON_TILE));
        var node = Shapes.or(TILE, ConduitBoxGeometry.shape(true, ON_TILE));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> terminals)
                .withShapeMapper(state -> cover(state).hasTerminals() ? node : plain)
                .build());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    /** A ceiling tile in hand lays the next tile beside this one, as on any tile attachment. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(placementHelper(state, level, pos, player, hand, hit) == InteractionResult.SUCCESS)
            return ItemInteractionResult.SUCCESS;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** The wrench takes the box (and its plate) off the tile; runs and wires drop as on a broken box. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var cover = cover(state);
        var player = context.getPlayer();
        if(cover.isPlate() && player != null && !player.isCreative())
            player.addItem(cover == ConduitCover.NODE ? ModItems.CONDUIT_COVER_NODE.asStack() : ModItems.CONDUIT_COVER_BLANK.asStack());
        return removeCeilingAttachment(context, ModBlocks.CONDUIT_BOX.asStack());
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState blockState, @Nullable BlockEntity blockEntity) {
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, List.of(ModdedBlocks.CEILING_TILE.asStack(), ModBlocks.CONDUIT_BOX.asStack()));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ModBlocks.CONDUIT_BOX.asStack();
    }
}
