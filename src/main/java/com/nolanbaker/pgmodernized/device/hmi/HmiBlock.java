package com.nolanbaker.pgmodernized.device.hmi;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * A flat screen on the wall with a Cat6 jack under its corner. Over the cable it finds a controls
 * cabinet and shows that PLC's tags the way its editor laid them out; its buttons write tags.
 * Right-click a button on the face to press it, anywhere else to open the screen large, and
 * sneak-right-click to edit the layout. Built facing north, screen on the north face, and turned.
 */
public class HmiBlock extends Block implements IBE<HmiBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The screen surface and the jack, in 16ths of the north frame. */
    public static final double FACE_Z = 14;
    public static final double[] JACK_BOX = {13, 0, 12.5, 15, 2, 14};
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(
            Shapes.or(box(0, 0, FACE_Z, 16, 16, 16), box(JACK_BOX[0], JACK_BOX[1], JACK_BOX[2], JACK_BOX[3], JACK_BOX[4], JACK_BOX[5])), Direction.NORTH);
    /** Grid cell size on the face, in 16ths. */
    public static final double CELL = 16.0 / HmiLayout.COLS;

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

    /** Where a Cat6 cable attaches: the jack under the viewer's right corner. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var local = fromNorthFrame(state, (JACK_BOX[0] + JACK_BOX[3]) / 2, (JACK_BOX[1] + JACK_BOX[4]) / 2, (JACK_BOX[2] + JACK_BOX[5]) / 2);
        return Vec3.atLowerCornerOf(pos).add(local);
    }

    /** The grid cell under a block-local hit on the screen face, as {column, row} from the viewer's top left, or null. */
    public static int @Nullable [] cellAt(BlockState state, Vec3 local) {
        var p = toNorthFrame(state, local);
        if(p.z > FACE_Z + 0.6)
            return null;
        int col = (int) ((16 - p.x) / CELL), row = (int) ((16 - p.y) / CELL);
        return new int[] {Math.max(0, Math.min(HmiLayout.COLS - 1, col)), Math.max(0, Math.min(HmiLayout.ROWS - 1, row))};
    }

    /** Where inside a widget a hit landed, 0 at its left edge to 1 at its right, in the viewer's frame. */
    public static double fractionAcross(BlockState state, Vec3 local, HmiLayout.Widget widget) {
        var p = toNorthFrame(state, local);
        double left = 16 - widget.col * CELL;
        return Math.max(0, Math.min(1, (left - p.x) / (widget.w * CELL)));
    }

    // ---- interaction ----

    /** Empty hand: a button on the face works it; elsewhere opens the screen; sneaking opens the editor. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(level.isClientSide)
                ClientHooks.openHmiEditor(pos);
            return InteractionResult.SUCCESS;
        }
        if(hit.getDirection() == facing(state) && level.getBlockEntity(pos) instanceof HmiBlockEntity hmi) {
            var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            var cell = cellAt(state, local);
            if(cell != null) {
                int index = hmi.layout().indexAt(cell[0], cell[1]);
                if(index >= 0 && hmi.layout().widgets.get(index).kind.writes()) {
                    var widget = hmi.layout().widgets.get(index);
                    int action = widget.kind == HmiLayout.Kind.SETPOINT ? (fractionAcross(state, local, widget) < 0.5 ? HmiBlockEntity.MINUS : HmiBlockEntity.PLUS) : HmiBlockEntity.PRESS;
                    if(!level.isClientSide)
                        hmi.press(index, action, player);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        if(level.isClientSide)
            ClientHooks.openHmi(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof HmiBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
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
