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
 * way it runs. Sneak-click with an empty hand to cycle the slots.
 */
public class StrutBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Slots> SLOTS = EnumProperty.create("slots", Slots.class);
    public static final double THICK = 3, WIDTH = 3;

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
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN).setValue(AXIS, Direction.Axis.X).setValue(SLOTS, Slots.SIX));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS, SLOTS);
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
        Direction.Axis axis;
        if(facing.getAxis() == Direction.Axis.Y) {
            axis = ctx.getHorizontalDirection().getAxis();
        } else {
            boolean along = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
            axis = along ? (facing.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(AXIS, axisFor(facing, axis));
    }

    /** The rail in pixels: the mount's thickness off its face, the width across, the full block along. */
    public static AABB bar(Direction facing, Direction.Axis axis) {
        double[] lo = {0, 0, 0}, hi = {16, 16, 16};
        int m = facing.getAxis().ordinal();
        if(facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            hi[m] = THICK;
        } else {
            lo[m] = 16 - THICK;
        }
        int across = 3 - m - axisFor(facing, axis).ordinal();
        lo[across] = 8 - WIDTH / 2;
        hi[across] = 8 + WIDTH / 2;
        return new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var b = bar(state.getValue(FACING), state.getValue(AXIS));
        return Block.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /**
     * The run centre line for a click: snapped along the axis to the nearest slot of the pattern,
     * centred across, and lifted off the channel by the tube's radius so the conduit sits in the clamp.
     */
    public static Vec3 clamp(BlockState state, Vec3 local, double tubeRadiusPx) {
        var facing = state.getValue(FACING);
        var axis = axisFor(facing, state.getValue(AXIS));
        double[] p = {local.x * 16, local.y * 16, local.z * 16};
        int a = axis.ordinal(), m = facing.getAxis().ordinal(), across = 3 - a - m;
        var slots = state.hasProperty(SLOTS) ? state.getValue(SLOTS) : Slots.SIX;
        double best = slots.along()[0];
        for(double slot : slots.along())
            if(Math.abs(slot - p[a]) < Math.abs(best - p[a]))
                best = slot;
        p[a] = best;
        p[across] = 8;
        p[m] = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? THICK + tubeRadiusPx : 16 - THICK - tubeRadiusPx;
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

    /** Conduit in hand: the pending run is clipped to the clamp under the click. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var size = ConduitItem.sizeOf(stack);
        if(size == null)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var clamp = clamp(state, local, size.tubeThickness() * 16 / 2);
        var context = new UseOnContext(player, hand, hit);
        var result = ConduitPlacement.strutClick(context, clamp, axisFor(state.getValue(FACING), state.getValue(AXIS)));
        return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
    }
}
