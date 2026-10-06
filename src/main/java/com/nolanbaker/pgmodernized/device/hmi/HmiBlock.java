package com.nolanbaker.pgmodernized.device.hmi;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A flat screen on the wall with a Cat6 jack under its corner. Panels placed flush on one wall
 * merge into one screen, like OpenComputers screens, up to eight by eight. Over the cable it
 * finds a controls cabinet and shows that PLC's tags the way its editor laid them out; its
 * buttons write tags. Right-click a button on the face to press it, anywhere else to open the
 * screen large, and sneak-right-click to edit the layout. Built facing north, screen on the north
 * face, and turned.
 */
public class HmiBlock extends Block implements IBE<HmiBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The screen surface and the jack, in 16ths of the north frame. */
    public static final double FACE_Z = 14;
    public static final double[] JACK_BOX = {13, 0, 14, 15, 1, 16};   // a flush socket in the bottom edge
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(
            box(0, 0, FACE_Z, 16, 16, 16), Direction.NORTH);
    /** Grid cell size on a panel, in 16ths. */
    public static final double CELL = 16.0 / HmiLayout.PANEL;

    public HmiBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall the back goes on the wall; on a floor or ceiling it faces the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(facing(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(facing(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(facing(state)));
    }

    // ---- screens of several panels ----

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(!level.isClientSide)
            HmiGroups.onPlaced(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if(!state.is(newState.getBlock()) && !level.isClientSide)
            HmiGroups.onRemoved(level, pos, facing(state));
        IBE.onRemove(state, level, pos, newState);
    }

    // ---- frames ----

    /** A block-local point (0..1) turned back into the north frame, in 16ths. */
    public static Vec3 toNorthFrame(BlockState state, Vec3 local) {
        double lx = local.x, ly = local.y, lz = local.z;
        double nx, nz;
        switch(facing(state)) {
            case SOUTH -> { nx = 1 - lx; nz = 1 - lz; }
            case EAST -> { nx = lz; nz = 1 - lx; }
            case WEST -> { nx = 1 - lz; nz = lx; }
            default -> { nx = lx; nz = lz; }
        }
        return new Vec3(nx * 16, ly * 16, nz * 16);
    }

    /** A north-frame point in 16ths turned to the block's facing, block-local (0..1). */
    public static Vec3 fromNorthFrame(BlockState state, double nx, double ny, double nz) {
        double x = nx / 16, y = ny / 16, z = nz / 16;
        return switch(facing(state)) {
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case EAST -> new Vec3(1 - z, y, x);
            case WEST -> new Vec3(z, y, 1 - x);
            default -> new Vec3(x, y, z);
        };
    }

    /** Where a Cat6 cable attaches: the socket in the bottom edge, at the viewer's left. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var local = fromNorthFrame(state, (JACK_BOX[0] + JACK_BOX[3]) / 2, (JACK_BOX[1] + JACK_BOX[4]) / 2, (JACK_BOX[2] + JACK_BOX[5]) / 2);
        return Vec3.atLowerCornerOf(pos).add(local);
    }

    /**
     * The screen's grid cell under a block-local hit on this panel's face, as {column, row} from
     * the whole screen's top left, or null off the face.
     */
    public static int @Nullable [] cellAt(BlockState state, Vec3 local, HmiBlockEntity panel) {
        var p = toNorthFrame(state, local);
        if(p.z > FACE_Z + 0.6)
            return null;
        int col = (int) ((16 - p.x) / CELL), row = (int) ((16 - p.y) / CELL);
        col = Math.max(0, Math.min(HmiLayout.PANEL - 1, col));
        row = Math.max(0, Math.min(HmiLayout.PANEL - 1, row));
        var offset = panel.offset();
        return new int[] {offset[0] * HmiLayout.PANEL + col, (panel.height() - 1 - offset[1]) * HmiLayout.PANEL + row};
    }

    /** Where inside a widget a hit landed, 0 at its left edge to 1 at its right, in the viewer's frame. */
    public static double fractionAcross(BlockState state, Vec3 local, HmiBlockEntity panel, HmiLayout.Widget widget) {
        var p = toNorthFrame(state, local);
        double hitCol = panel.offset()[0] * HmiLayout.PANEL + (16 - p.x) / CELL;
        return Math.max(0, Math.min(1, (hitCol - widget.col) / widget.w));
    }

    // ---- interaction ----

    /** Empty hand: a button on the face works it; elsewhere opens the screen; sneaking opens the editor. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof HmiBlockEntity panel))
            return InteractionResult.PASS;
        var head = panel.head();
        var headPos = head == null ? pos : head.getBlockPos();
        if(player.isShiftKeyDown()) {
            if(level.isClientSide)
                ClientHooks.openHmiEditor(headPos);
            return InteractionResult.SUCCESS;
        }
        if(hit.getDirection() == facing(state) && head != null) {
            var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            var cell = cellAt(state, local, panel);
            if(cell != null) {
                var layout = head.layout();
                int index = layout.indexAt(cell[0], cell[1]);
                if(index >= 0 && layout.widgets.get(index).kind.writes()) {
                    var widget = layout.widgets.get(index);
                    int action = widget.kind == HmiLayout.Kind.SETPOINT
                            ? (fractionAcross(state, local, panel, widget) < 0.5 ? HmiBlockEntity.MINUS : HmiBlockEntity.PLUS) : HmiBlockEntity.PRESS;
                    if(!level.isClientSide)
                        head.press(index, action, player);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        if(level.isClientSide)
            ClientHooks.openHmi(headPos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof HmiBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    public Class<HmiBlockEntity> getBlockEntityClass() {
        return HmiBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HmiBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HMI.get();
    }
}
