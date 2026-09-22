package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * An eight-port network switch. All ports share one network node, so anything plugged into it is on
 * the same wired network, and cables or modems placed against it join too. Ports are numbered 1 to 8
 * from the viewer's left; a port's block state flag lights it while a cable is plugged in.
 * <p>
 * It attaches like a button: flat on a floor, hanging from a ceiling, or as a vertical plate on a wall
 * with the ports facing out. Geometry is defined in a north-facing frame (ports toward -z) and
 * transformed per {@link #FACE} and {@link #FACING}; the blockstate uses the matching model rotations.
 */
public class NetworkSwitchBlock extends Block implements IBE<NetworkSwitchBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final int PORTS = 8;
    public static final BooleanProperty[] PORT = new BooleanProperty[PORTS];

    static {
        for(int i = 0; i < PORTS; ++i)
            PORT[i] = BooleanProperty.create("port" + i);
    }

    /** Port geometry in 16ths (north-facing frame). Port 0 is on the viewer's left, which is the east side. */
    private static final double PORT_PITCH = 2;
    private static final double PORT_WIDTH = 1.5;
    private static final double PORT0_MAX_X = 15.75;
    private static final double FLOOR_PORT_Y = 2, FLOOR_PORT_Z = 1;
    private static final double WALL_PORT_Y = 7, WALL_PORT_Z = 11;
    private static final AABB FLOOR_SHAPE = new AABB(0, 0, 1 / 16.0, 1, 6 / 16.0, 14 / 16.0);
    private static final AABB WALL_SHAPE = new AABB(0, 2 / 16.0, 11 / 16.0, 1, 14 / 16.0, 1);
    private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(AttachFace.class);

    static {
        for(var face : AttachFace.values()) {
            var byFacing = new EnumMap<Direction, VoxelShape>(Direction.class);
            for(var facing : Direction.Plane.HORIZONTAL) {
                var frame = face == AttachFace.WALL ? WALL_SHAPE : FLOOR_SHAPE;
                var a = toLocal(new Vec3(frame.minX, frame.minY, frame.minZ), face, facing);
                var b = toLocal(new Vec3(frame.maxX, frame.maxY, frame.maxZ), face, facing);
                byFacing.put(facing, Shapes.create(new AABB(a, b)));
            }
            SHAPES.put(face, byFacing);
        }
    }

    public NetworkSwitchBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR);
        for(var port : PORT)
            state = state.setValue(port, false);
        registerDefaultState(state);
    }

    private static double portCenterX(int port) {
        return PORT0_MAX_X - PORT_WIDTH / 2 - port * PORT_PITCH;
    }

    /** Port centre in the north-facing frame, in blocks. */
    private static Vec3 portInFrame(int port, AttachFace face) {
        var x = portCenterX(Math.max(0, Math.min(PORTS - 1, port))) / 16.0;
        return face == AttachFace.WALL
                ? new Vec3(x, WALL_PORT_Y / 16.0, WALL_PORT_Z / 16.0)
                : new Vec3(x, FLOOR_PORT_Y / 16.0, FLOOR_PORT_Z / 16.0);
    }

    /** Frame (north-facing) to block-local, matching the blockstate rotations (ceiling = 180 degrees about z). */
    static Vec3 toLocal(Vec3 p, AttachFace face, Direction facing) {
        if(face == AttachFace.CEILING)
            p = new Vec3(1 - p.x, 1 - p.y, p.z);
        return switch(facing) {
            case EAST -> new Vec3(1 - p.z, p.y, p.x);
            case SOUTH -> new Vec3(1 - p.x, p.y, 1 - p.z);
            case WEST -> new Vec3(p.z, p.y, 1 - p.x);
            default -> p;
        };
    }

    /** Block-local back into the north-facing frame. */
    static Vec3 toFrame(Vec3 p, AttachFace face, Direction facing) {
        p = switch(facing) {
            case EAST -> new Vec3(p.z, p.y, 1 - p.x);
            case SOUTH -> new Vec3(1 - p.x, p.y, 1 - p.z);
            case WEST -> new Vec3(1 - p.z, p.y, p.x);
            default -> p;
        };
        return face == AttachFace.CEILING ? new Vec3(1 - p.x, 1 - p.y, p.z) : p;
    }

    public static Vec3 jackPosition(BlockState state, BlockPos pos, int port) {
        var local = toLocal(portInFrame(port, state.getValue(FACE)), state.getValue(FACE), state.getValue(FACING));
        return Vec3.atLowerCornerOf(pos).add(local);
    }

    /** Nearest port to a block-local click position. */
    public static int portAt(BlockState state, Vec3 local) {
        var frame = toFrame(local, state.getValue(FACE), state.getValue(FACING));
        int port = (int) Math.round((portCenterX(0) - frame.x * 16) / PORT_PITCH);
        return Math.max(0, Math.min(PORTS - 1, port));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
        builder.add(PORT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var clicked = context.getClickedFace();
        var state = defaultBlockState();
        if(clicked == Direction.UP)
            return state.setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection().getOpposite());
        if(clicked == Direction.DOWN)
            return state.setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection().getOpposite());
        // On a wall the ports face away from it.
        return state.setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACE)).get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<NetworkSwitchBlockEntity> getBlockEntityClass() {
        return NetworkSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends NetworkSwitchBlockEntity> getBlockEntityType() {
        return ModBlockEntities.NETWORK_SWITCH.get();
    }
}
