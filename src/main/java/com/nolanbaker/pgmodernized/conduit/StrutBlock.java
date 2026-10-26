package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Strut channel: a 3 px square rail on any face, running along one axis, with clamp slots along
 * it in a pattern the player chooses, each marked by a strap across the rail. The patterns are
 * the wire trough's, so a rack of runs off a trough stays in line: six 2" at a breaker panel's six
 * positions, four 3", two 4", or eight for 1/2" and 3/4". It is not an electrical block and has no
 * terminals. A pending conduit run clicked onto it goes straight to the nearest slot, no tracer,
 * so runs on strut are rigid, parallel and evenly spaced; the run stays one piece over any number
 * of struts and closes on a knockout as usual. FACING is the face it is mounted on; AXIS is the
 * way it runs. Sneak-click with an empty hand to cycle the slots. A strut item clicked on a strut
 * stacks a second rail eight pixels out, a tier that holds a second row of runs in front of the
 * first; clicked again the lower rail comes away, leaving the upper rail on a post, and again
 * brings it back to a single. A tier is eight pixels because a rail is three and a 4" tube five.
 * A strut placed on the outer face of a raised or double strut stacks a tier further out: it takes
 * the direction and slots of the one behind and a post ties the two. A wrench turns a strut to
 * run the other way across its mount; runs already clipped to it stay where they are.
 */
public class StrutBlock extends Block implements com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Slots> SLOTS = EnumProperty.create("slots", Slots.class);
    public static final EnumProperty<Stack> STACK = EnumProperty.create("stack", Stack.class);
    /** A strut stacked on this one's outer face, mounted and running the same way: a post reaches out to it. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LINKED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("linked");
    public static final double THICK = 3, WIDTH = 3;
    /** The upper rail's distance from the mount: a rail and the biggest tube under it. */
    public static final double TIER = 8;

    /** Which rails a strut carries: the lower on the mount, the upper a tier out, or both. */
    public enum Stack implements net.minecraft.util.StringRepresentable {
        SINGLE("single", true, false, 1),
        DOUBLE("double", true, true, 2),
        RAISED("raised", false, true, 1);

        private final String id;
        private final boolean lower, upper;
        private final int struts;

        Stack(String id, boolean lower, boolean upper, int struts) {
            this.id = id;
            this.lower = lower;
            this.upper = upper;
            this.struts = struts;
        }

        public boolean lower() {
            return lower;
        }

        public boolean upper() {
            return upper;
        }

        /** Strut items the state is made of. */
        public int struts() {
            return struts;
        }

        public Stack next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** Clamp positions along the rail, in pixels, and the largest tube that sits in them without touching its neighbour. */
    public enum Slots implements net.minecraft.util.StringRepresentable {
        TWO("two", ConduitSize.FOUR, 4, 12),
        FOUR("four", ConduitSize.THREE, 2.75, 6.25, 9.75, 13.25),
        SIX("six", ConduitSize.TWO, 2.5, 4.7, 6.9, 9.1, 11.3, 13.5),
        EIGHT("eight", ConduitSize.THREE_QUARTER, 1.5, 3.5, 5.5, 7.5, 9.5, 11.5, 13.5, 15.5);

        private final String id;
        private final ConduitSize size;
        private final double[] along;

        Slots(String id, ConduitSize size, double... along) {
            this.id = id;
            this.size = size;
            this.along = along;
        }

        public double[] along() {
            return along;
        }

        public ConduitSize size() {
            return size;
        }

        public Slots next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public net.minecraft.network.chat.Component label() {
            return org.patryk3211.powergrid.utility.Lang.builder().translate("gui.strut.slots." + id).component();
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public StrutBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN).setValue(AXIS, Direction.Axis.X).setValue(SLOTS, Slots.SIX).setValue(STACK, Stack.SINGLE).setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS, SLOTS, STACK, LINKED);
    }

    public static Stack stackOf(BlockState state) {
        return state.hasProperty(STACK) ? state.getValue(STACK) : Stack.SINGLE;
    }

    /** A strut's axis is never the axis it is mounted on; the horizontal one for floors and ceilings, upright on a wall unless sneaking. */
    public static Direction.Axis axisFor(Direction facing, Direction.Axis wanted) {
        if(wanted != facing.getAxis())
            return wanted;
        return facing.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getClickedFace().getOpposite();
        var pos = ctx.getClickedPos();
        var behind = ctx.getLevel().getBlockState(pos.relative(facing));
        Direction.Axis axis;
        var slots = Slots.SIX;
        if(behind.getBlock() instanceof StrutBlock && behind.getValue(FACING) == facing) {
            // Stacked on a strut's outer face: the same way and the same slots, a tier on from its rails.
            axis = behind.getValue(AXIS);
            slots = behind.getValue(SLOTS);
        } else if(facing.getAxis() == Direction.Axis.Y) {
            axis = ctx.getHorizontalDirection().getAxis();
        } else {
            boolean along = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
            axis = along ? (facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
        }
        var state = defaultBlockState().setValue(FACING, facing).setValue(AXIS, axisFor(facing, axis)).setValue(SLOTS, slots);
        return state.setValue(LINKED, linkedOut(ctx.getLevel(), pos, state));
    }

    /** Whether the block a tier out is a strut stacked on this one: mounted the same way, running the same way. */
    public static boolean linkedOut(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        var facing = state.getValue(FACING);
        var out = level.getBlockState(pos.relative(facing.getOpposite()));
        return out.getBlock() instanceof StrutBlock && out.getValue(FACING) == facing && out.getValue(AXIS) == state.getValue(AXIS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if(direction == state.getValue(FACING).getOpposite())
            return state.setValue(LINKED, linkedOut(level, pos, state));
        return state;
    }

    /** The wrench turns the strut to run the other way across its mount. */
    @Override
    public net.minecraft.world.InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if(level.isClientSide)
            return net.minecraft.world.InteractionResult.SUCCESS;
        var facing = state.getValue(FACING);
        var current = state.getValue(AXIS);
        Direction.Axis other = current;
        for(var axis : Direction.Axis.values())
            if(axis != facing.getAxis() && axis != current)
                other = axis;
        var turned = state.setValue(AXIS, other);
        turned = turned.setValue(LINKED, linkedOut(level, pos, turned));
        level.setBlock(pos, turned, Block.UPDATE_ALL);
        com.simibubi.create.AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1, net.minecraft.util.Mth.nextFloat(level.random, 0.65f, 0.75f));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** The rail in pixels: the mount's thickness off its face, the width across, the full block along. */
    public static AABB bar(Direction facing, Direction.Axis axis) {
        return bar(facing, axis, 0);
    }

    /** The same rail a tier further out, or a post of that depth range when {@code from} and {@code to} are given. */
    public static AABB bar(Direction facing, Direction.Axis axis, double offset) {
        return slab(facing, axis, offset, offset + THICK, WIDTH);
    }

    private static AABB slab(Direction facing, Direction.Axis axis, double from, double to, double width) {
        double[] lo = {0, 0, 0}, hi = {16, 16, 16};
        int m = facing.getAxis().ordinal();
        if(facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            lo[m] = from;
            hi[m] = to;
        } else {
            lo[m] = 16 - to;
            hi[m] = 16 - from;
        }
        int across = 3 - m - axisFor(facing, axis).ordinal();
        lo[across] = 8 - width / 2;
        hi[across] = 8 + width / 2;
        return new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        var axis = state.getValue(AXIS);
        var stack = stackOf(state);
        VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.empty();
        if(stack.lower())
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(bar(facing, axis, 0)));
        if(stack.upper()) {
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(bar(facing, axis, TIER)));
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(slab(facing, axis, stack.lower() ? THICK : 0, TIER, 1.5)));
        }
        if(state.hasProperty(LINKED) && state.getValue(LINKED))
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(slab(facing, axis, (stack.upper() ? TIER : 0) + THICK, 16, 1.5)));
        return shape;
    }

    private static VoxelShape box(AABB b) {
        return Block.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /** The tier a click lands on: the upper rail when the hit is nearer it than the lower, or the only rail there is. */
    public static double tierOf(BlockState state, Vec3 local) {
        var stack = stackOf(state);
        if(!stack.upper())
            return 0;
        if(!stack.lower())
            return TIER;
        var facing = state.getValue(FACING);
        double[] p = {local.x * 16, local.y * 16, local.z * 16};
        int m = facing.getAxis().ordinal();
        double depth = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? p[m] : 16 - p[m];
        return depth < (THICK + TIER) / 2 ? 0 : TIER;
    }

    /**
     * The run centre line for a click: snapped along the axis to the nearest slot of the pattern,
     * centred across, and lifted off the channel by the tube's radius so the conduit sits in the clamp.
     */
    public static Vec3 clamp(BlockState state, Vec3 local, double tubeRadiusPx) {
        return clamp(state, local, tubeRadiusPx, Double.NaN);
    }

    /** The same, snapped to the slot nearest {@code alongPx} (block-local pixels along the rail) instead of the click. */
    public static Vec3 clamp(BlockState state, Vec3 local, double tubeRadiusPx, double alongPx) {
        var facing = state.getValue(FACING);
        var axis = axisFor(facing, state.getValue(AXIS));
        double[] p = {local.x * 16, local.y * 16, local.z * 16};
        int a = axis.ordinal(), m = facing.getAxis().ordinal(), across = 3 - a - m;
        var slots = state.hasProperty(SLOTS) ? state.getValue(SLOTS) : Slots.SIX;
        double want = Double.isNaN(alongPx) ? p[a] : alongPx;
        double best = slots.along()[0];
        for(double slot : slots.along())
            if(Math.abs(slot - want) < Math.abs(best - want))
                best = slot;
        p[a] = best;
        p[across] = 8;
        double seat = tierOf(state, local) + THICK + tubeRadiusPx;
        p[m] = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? seat : 16 - seat;
        return new Vec3(p[0] / 16, p[1] / 16, p[2] / 16);
    }

    /** Sneak and an empty hand: the next slot pattern. Runs already clipped stay where they are. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return net.minecraft.world.InteractionResult.PASS;
        if(level.isClientSide)
            return net.minecraft.world.InteractionResult.SUCCESS;
        var next = state.getValue(SLOTS).next();
        level.setBlock(pos, state.setValue(SLOTS, next), Block.UPDATE_ALL);
        player.displayClientMessage(org.patryk3211.powergrid.utility.Lang.builder().translate("message.strut.slots", next.label())
                .style(net.minecraft.ChatFormatting.GRAY).component(), true);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Conduit in hand: the pending run is clipped to the clamp under the click. A strut in hand stacks or unstacks. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() == asItem()) {
            if(level.isClientSide)
                return ItemInteractionResult.SUCCESS;
            var now = stackOf(state);
            var next = now.next();
            int cost = next.struts() - now.struts();
            if(cost > 0 && !player.isCreative()) {
                if(stack.getCount() < cost)
                    return ItemInteractionResult.FAIL;
                stack.shrink(cost);
            } else if(cost < 0 && !player.isCreative()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(asItem(), -cost));
            }
            level.setBlock(pos, state.setValue(STACK, next), Block.UPDATE_ALL);
            player.displayClientMessage(org.patryk3211.powergrid.utility.Lang.builder().translate("message.strut.stack." + next.getSerializedName())
                    .style(net.minecraft.ChatFormatting.GRAY).component(), true);
            return ItemInteractionResult.SUCCESS;
        }
        var size = ConduitItem.sizeOf(stack);
        if(size == null)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var context = new UseOnContext(player, hand, hit);
        var result = ConduitPlacement.strutClick(context, state, local, size.tubeThickness() * 16 / 2);
        return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }

    /** A double drops both its struts. */
    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = new java.util.ArrayList<>(super.getDrops(state, params));
        if(stackOf(state) == Stack.DOUBLE)
            drops.add(new ItemStack(asItem()));
        return drops;
    }
}
