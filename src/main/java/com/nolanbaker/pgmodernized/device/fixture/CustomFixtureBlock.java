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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.wire.powercord.AutoCordEndpoint;
import org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord;
import org.patryk3211.powergrid.utility.Lang;
import net.minecraft.world.phys.HitResult;

/**
 * A fixture made in the designer: one anchor block that draws the whole design round itself,
 * holds its bulbs, and carries L and N behind three knockouts on the canopy plate at its mount:
 * one on the mounting face itself, for a run that comes through the block by a chaseway, and one
 * on each of two canopy sides for runs along the surface. A power cord plugs in anywhere on it
 * instead, which is how a table lamp reaches a receptacle. An empty hand on the body is the pull
 * chain; on the canopy it opens the splice editor. Its shape is
 * whatever of the design falls inside the anchor block. It hangs from the block above, off the
 * block behind or stands on the block below as the design says, and drops as its item, design
 * and all, when that block goes.
 */
public class CustomFixtureBlock extends HorizontalElectricBlock implements IBE<CustomFixtureBlockEntity>, IAcceptCord {
    public static final int L = 0, N = 1, BASE_COUNT = 2, HUB_MOUNT = 0, HUB_A = 1, HUB_B = 2, HUB_COUNT = 3;
    public static final EnumProperty<FixtureDesign.Mount> MOUNT = EnumProperty.create("mount", FixtureDesign.Mount.class);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** Where the junction box sits on the mounting face: a three by three grid, {@link FixtureDesign#BOX_CENTRE} in the middle. */
    public static final IntegerProperty BOX = IntegerProperty.create("box", 0, FixtureDesign.BOX_COUNT - 1);
    /** Pixels between neighbouring box positions. */
    public static final int BOX_STEP = 4;
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUB_COUNT, new int[] {L, N});

    public CustomFixtureBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MOUNT, FixtureDesign.Mount.CEILING).setValue(LIGHT, 0).setValue(BOX, FixtureDesign.BOX_CENTRE));
        setTerminalCollection(StateTerminals.horizontalNorth(this,
                state -> DeviceHubs.withHubs(canopy(state), hubs(state)),
                state -> DeviceHubs.withHubs(terminals(), HIDDEN, hubs(state))));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT, LIGHT, BOX);
    }

    /**
     * Three knockouts on the canopy, in the north frame. The first is on the mounting face itself
     * (top of a ceiling fixture, back of a wall one, bottom of a floor one), for a run that comes
     * through the block it hangs from by a chaseway. The other two are on canopy sides, for runs
     * along the surface: left and right of a ceiling or floor fixture, above and below a wall one.
     * Mirrored by {@code tools/gen_light_assets.py}; change both.
     */
    public static AABB[] hubs(FixtureDesign.Mount mount, int box) {
        var o = boxOffset(mount, box);
        var hubs = switch(mount) {
            case CEILING -> new AABB[] {new AABB(7, 15, 7, 9, 16, 9), new AABB(11, 13.5, 7, 12, 15.5, 9), new AABB(4, 13.5, 7, 5, 15.5, 9)};
            case WALL -> new AABB[] {new AABB(7, 7, 15, 9, 9, 16), new AABB(7, 11, 13.5, 9, 12, 15.5), new AABB(7, 4, 13.5, 9, 5, 15.5)};
            case FLOOR -> new AABB[] {new AABB(7, 0, 7, 9, 1, 9), new AABB(11, 0, 7, 12, 2, 9), new AABB(4, 0, 7, 5, 2, 9)};
        };
        for(int i = 0; i < hubs.length; ++i)
            hubs[i] = hubs[i].move(o.x, o.y, o.z);
        return hubs;
    }

    public static AABB[] hubs(BlockState state) {
        return hubs(state.getValue(MOUNT), state.getValue(BOX));
    }

    /**
     * How far the junction box is moved from the middle of the mounting face, in pixels, north
     * frame: {@link #BOX_STEP} per grid step, across the face (x) and along it (z on a ceiling or
     * floor, y on a wall). Mirrored by {@code tools/gen_light_assets.py}; change both.
     */
    public static Vec3 boxOffset(FixtureDesign.Mount mount, int box) {
        int u = (box / 3 - 1) * BOX_STEP, v = (box % 3 - 1) * BOX_STEP;
        return mount == FixtureDesign.Mount.WALL ? new Vec3(u, v, 0) : new Vec3(u, 0, v);
    }

    /**
     * The least a fixture is: a canopy on its mounting face, so an empty design can still be clicked.
     * A floor fixture's is not drawn: its design supplies the base, and this is only the footprint.
     */
    public static VoxelShape canopy(FixtureDesign.Mount mount, int box) {
        var o = boxOffset(mount, box);
        return (switch(mount) {
            case CEILING -> box(4, 13, 4, 12, 16, 12);
            case WALL -> box(4, 4, 13, 12, 12, 16);
            case FLOOR -> box(4, 0, 4, 12, 2, 12);
        }).move(o.x / 16, o.y / 16, o.z / 16);
    }

    public static VoxelShape canopy(BlockState state) {
        return canopy(state.getValue(MOUNT), state.getValue(BOX));
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
        var canopy = rotate(canopy(state), facing(state));
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

    /** Pick block hands back the fixture with its design on it. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be ? be.toItem() : super.getCloneItemStack(state, target, level, pos, player);
    }

    /** A power cord plugs in where it is clicked, onto L and N. */
    @Override
    public AutoCordEndpoint getEndpoint(UseOnContext context) {
        return new AutoCordEndpoint(context.getClickedPos(), L, N, context.getClickLocation(), context.getClickedFace());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be) {
            be.dropBulbs();
            be.removeGlow();
        }
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
        // The canopy opens the splice editor; anywhere else on the fixture is its pull chain.
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if(rotate(canopy(state), facing(state)).bounds().inflate(0.02).contains(local)) {
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
            be.toggle();
        return InteractionResult.sidedSuccess(level.isClientSide);
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
