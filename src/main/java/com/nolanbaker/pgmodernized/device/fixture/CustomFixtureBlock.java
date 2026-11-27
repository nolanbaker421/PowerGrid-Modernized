package com.nolanbaker.pgmodernized.device.fixture;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.StateTerminals;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A fixture made in the designer: one anchor block that draws the whole design round itself,
 * holds its bulbs, and carries L and N behind a knockout on the face it mounts by. Its shape is
 * whatever of the design falls inside the anchor block. It hangs from the block above, off the
 * block behind or stands on the block below as the design says, and drops as its item, design
 * and all, when that block goes.
 */
public class CustomFixtureBlock extends HorizontalElectricBlock implements IBE<CustomFixtureBlockEntity> {
    public static final int L = 0, N = 1, BASE_COUNT = 2, HUB = 0;
    public static final EnumProperty<FixtureDesign.Mount> MOUNT = EnumProperty.create("mount", FixtureDesign.Mount.class);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, 1, new int[] {L, N});

    public CustomFixtureBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MOUNT, FixtureDesign.Mount.CEILING).setValue(LIGHT, 0));
        setTerminalCollection(StateTerminals.horizontalNorth(this,
                state -> DeviceHubs.withHubs(canopy(state.getValue(MOUNT)), hub(state.getValue(MOUNT))),
                state -> DeviceHubs.withHubs(terminals(), HIDDEN, hub(state.getValue(MOUNT)))));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT, LIGHT);
    }

    /** The knockout sits on the mounting face: top for a ceiling fixture, back for a wall one, bottom for a floor one. */
    public static AABB hub(FixtureDesign.Mount mount) {
        return switch(mount) {
            case CEILING -> new AABB(7, 15, 7, 9, 16, 9);
            case WALL -> new AABB(7, 7, 15, 9, 9, 16);
            case FLOOR -> new AABB(7, 0, 7, 9, 1, 9);
        };
    }

    /** The least a fixture is: a canopy on its mounting face, so an empty design can still be clicked. */
    public static VoxelShape canopy(FixtureDesign.Mount mount) {
        return switch(mount) {
            case CEILING -> box(4, 13, 4, 12, 16, 12);
            case WALL -> box(4, 4, 13, 12, 12, 16);
            case FLOOR -> box(4, 0, 4, 12, 3, 12);
        };
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("strip_light.l", HIDDEN).withColor(0x202020),
                terminal("strip_light.n", HIDDEN).withColor(0xE8E8E8)};
    }

    private static TerminalBoundingBox terminal(String key, AABB px) {
        Component name = Lang.builder().translate(key).component();
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    /** The block that holds the fixture up, for its mount and facing. */
    public static BlockPos support(BlockState state, BlockPos pos) {
        return switch(state.getValue(MOUNT)) {
            case CEILING -> pos.above();
            case FLOOR -> pos.below();
            case WALL -> pos.relative(facing(state).getOpposite());
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var support = support(state, pos);
        var toward = switch(state.getValue(MOUNT)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> facing(state);
        };
        return level.getBlockState(support).isFaceSturdy(level, support, toward);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if(!level.isClientSide && !state.canSurvive(level, pos))
            level.destroyBlock(pos, true);
    }

    /** The design travels on the item; the block entity takes it up as the block is set. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var design = FixtureDesign.of(stack);
        if(design != null && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
            be.setDesign(design);
    }

    /** The parts inside the anchor block, turned to the facing; the canopy alone until the design arrives. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var canopy = rotate(canopy(state.getValue(MOUNT)), facing(state));
        if(level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
            return Shapes.or(canopy, be.shape(facing(state)));
        return canopy;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** A north-frame shape turned to a horizontal facing. */
    public static VoxelShape rotate(VoxelShape shape, Direction facing) {
        if(facing == Direction.NORTH)
            return shape;
        var out = Shapes.empty();
        for(var b : shape.toAabbs()) {
            var r = switch(facing) {
                case SOUTH -> new AABB(1 - b.maxX, b.minY, 1 - b.maxZ, 1 - b.minX, b.maxY, 1 - b.minZ);
                case EAST -> new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
                case WEST -> new AABB(b.minZ, b.minY, 1 - b.maxX, b.maxZ, b.maxY, 1 - b.minX);
                default -> b;
            };
            out = Shapes.or(out, Shapes.create(r));
        }
        return out;
    }

    /** Broken by a player, the fixture comes back as its item with the design on it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if(!level.isClientSide && !player.isCreative() && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
            popResource(level, pos, be.toItem());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
            be.dropBulbs();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            IElectric.sendMessage(c, Lang.builder().translate("message.fixture.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof ILightBulb) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
                be.fitBulbs(player, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.getItem() instanceof FixtureDesignerItem)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
                be.takeBulbs(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<CustomFixtureBlockEntity> getBlockEntityClass() {
        return CustomFixtureBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CustomFixtureBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CUSTOM_FIXTURE.get();
    }
}
