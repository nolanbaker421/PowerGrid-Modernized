package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A chaseway: a sleeve through a solid block, so conduit can land on one side of a wall, floor
 * or ceiling and carry on from the other without the block being gone. The block keeps the
 * look, sound and drop of the block it went through, which the block entity remembers, and
 * shows a sleeve end on the two faces its axis meets. Each end is a knockout that takes conduit
 * up to 2"; the wires in the run on one end come out of the other in the same order, slot for
 * slot, with nothing to splice. It cannot be placed as a block: the Chaseway item is used on a
 * block face, and the axis is that face's.
 */
public class ChasewayBlock extends ElectricBlock implements IBE<ChasewayBlockEntity> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final int HUB_POSITIVE = 0, HUB_NEGATIVE = 1;
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(0, 2, new int[0]);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);
    /** The sleeve end on a face: a six-pixel plate round the hole, matching the model. */
    public static final double PLATE_MIN = 5, PLATE_MAX = 11, PLATE_DEPTH = 0.5;

    public ChasewayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> DeviceHubs.withHubs(new TerminalBoundingBox[0], HIDDEN, hubs(state.getValue(AXIS))))
                .withShapeMapper(state -> Shapes.block())
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    /** The two sleeve ends: the positive face of the axis first, then the negative. */
    public static AABB[] hubs(Direction.Axis axis) {
        return new AABB[] {
                hub(Direction.get(Direction.AxisDirection.POSITIVE, axis)),
                hub(Direction.get(Direction.AxisDirection.NEGATIVE, axis))};
    }

    /** The plate on one face, in pixels. */
    public static AABB hub(Direction face) {
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double a = positive ? 16 - PLATE_DEPTH : 0, b = positive ? 16 : PLATE_DEPTH;
        return switch(face.getAxis()) {
            case X -> new AABB(a, PLATE_MIN, PLATE_MIN, b, PLATE_MAX, PLATE_MAX);
            case Y -> new AABB(PLATE_MIN, a, PLATE_MIN, PLATE_MAX, b, PLATE_MAX);
            case Z -> new AABB(PLATE_MIN, PLATE_MIN, a, PLATE_MAX, PLATE_MAX, b);
        };
    }

    /** The block this chaseway went through, or null before it is known. */
    @Nullable
    public static BlockState material(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChasewayBlockEntity be ? be.material() : null;
    }

    // ---- wearing the block it went through ----

    /** Neighbours with connected textures see the block that was here. */
    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        var material = material(level, pos);
        return material != null ? material : state;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        var material = material(level, pos);
        return material != null ? material.getSoundType(level, pos, entity) : super.getSoundType(state, level, pos, entity);
    }

    /** Broken, it gives the block back; the sleeve itself comes from the loot table. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var material = material(level, pos);
        if(!level.isClientSide && !player.isCreative() && material != null)
            popResource(level, pos, new ItemStack(material.getBlock()));
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModItems.CHASEWAY.get());
    }

    // ---- the two ends ----

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            IElectric.sendMessage(c, Lang.builder().translate("message.chaseway.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        });
    }

    /** Nothing to open: the wires pass straight through. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public Class<ChasewayBlockEntity> getBlockEntityClass() {
        return ChasewayBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChasewayBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CHASEWAY.get();
    }
}
