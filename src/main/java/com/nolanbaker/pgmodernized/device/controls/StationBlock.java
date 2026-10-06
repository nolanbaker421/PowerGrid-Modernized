package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import org.patryk3211.powergrid.collections.ModdedTags;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * A control station: a small box on the wall with four cells for the same buttons, lights,
 * dials and displays the cabinet door takes, and two Cat6 ports underneath that share one node,
 * so stations daisy-chain. Over the cable it finds a controls cabinet, and its devices are wired
 * to that cabinet's module channels in its screen exactly like door devices. Built facing north,
 * face on the north side, and turned.
 */
public class StationBlock extends Block implements IBE<StationBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The face the devices stand on, in 16ths of the north frame. */
    public static final double FACE_Z = 13;
    /** Cell columns from the viewer's left (+x) and rows from the top, each 4 px, in 16ths. */
    private static final double CELL_X0 = 12.5, CELL_Y0 = 12.5, CELL = 4;
    /** The two jack pins under the box: port 0 at the viewer's left. */
    public static final double[][] JACKS = {{9, 1, 13.5, 11, 2, 15.5}, {5, 1, 13.5, 7, 2, 15.5}};
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(Shapes.or(box(3, 2, FACE_Z, 13, 14, 16),
            box(JACKS[0][0], JACKS[0][1], JACKS[0][2], JACKS[0][3], JACKS[0][4], JACKS[0][5]),
            box(JACKS[1][0], JACKS[1][1], JACKS[1][2], JACKS[1][3], JACKS[1][4], JACKS[1][5])), Direction.NORTH);

    public StationBlock(Properties properties) {
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

    public static Vec3 fromNorthFrame(BlockState state, double nx, double ny, double nz) {
        double x = nx / 16, y = ny / 16, z = nz / 16;
        return switch(facing(state)) {
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case EAST -> new Vec3(1 - z, y, x);
            case WEST -> new Vec3(z, y, 1 - x);
            default -> new Vec3(x, y, z);
        };
    }

    /** Where a Cat6 cable attaches to a port: the bottom of its pin. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos, int port) {
        var j = JACKS[port == 1 ? 1 : 0];
        return Vec3.atLowerCornerOf(pos).add(fromNorthFrame(state, (j[0] + j[3]) / 2, j[1], (j[2] + j[5]) / 2));
    }

    /** Which port a click means: the viewer's left half is port 0. */
    public static int portAt(BlockState state, Vec3 local) {
        return toNorthFrame(state, local).x >= 8 ? 0 : 1;
    }

    /** The cell under a block-local hit on the face, 0..3 (viewer's top left, top right, bottom left, bottom right), or -1. */
    public static int cellAt(BlockState state, Vec3 local) {
        var p = toNorthFrame(state, local);
        if(p.z > FACE_Z + 0.6)
            return -1;
        double u = CELL_X0 - p.x, v = CELL_Y0 - p.y;
        if(u < 0 || u >= CELL * 2 || v < 0 || v >= CELL * 2)
            return -1;
        return (int) (v / CELL) * 2 + (int) (u / CELL);
    }

    /** Centre of a cell in the north frame, 16ths. */
    public static Vec3 cellCenter(int cell) {
        int column = cell % 2, row = cell / 2;
        return new Vec3(CELL_X0 - CELL * column - CELL / 2, CELL_Y0 - CELL * row - CELL / 2, FACE_Z);
    }

    // ---- interaction ----

    /** Devices go in by hand on their cell; cutters take them out; everything else (cables) falls through. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.isEmpty() || !(level.getBlockEntity(pos) instanceof StationBlockEntity station))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int cell = hit.getDirection() == facing(state) ? cellAt(state, local) : -1;
        var device = PanelDevice.DeviceItem.of(stack);
        if(device != null) {
            if(cell < 0)
                return ItemInteractionResult.FAIL;
            if(!level.isClientSide && station.installDevice(cell, device, player) && !player.isCreative())
                stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            if(!level.isClientSide && cell >= 0)
                station.removeDevice(cell, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand on an input device works it; elsewhere, or sneaking, opens the station's screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof StationBlockEntity station))
            return InteractionResult.PASS;
        if(!player.isShiftKeyDown() && hit.getDirection() == facing(state)) {
            var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int cell = cellAt(state, local);
            if(cell >= 0 && station.device(cell) != null && station.device(cell).isInput()) {
                if(!level.isClientSide)
                    station.operate(cell, player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if(level.isClientSide)
            ClientHooks.openStation(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof StationBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if(!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof StationBlockEntity station)
            station.dropAll();
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<StationBlockEntity> getBlockEntityClass() {
        return StationBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StationBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STATION.get();
    }
}
