package com.nolanbaker.pgmodernized.radio;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A bracket of eight cable clips. Each clip is a pass-through with a port on either side, so a
 * coax or a Cat6 lands on one side and leaves by the other, and eight lines ride one bracket up a
 * tower leg. On a wall the clips sit in a row with their ports up and down; on a floor or ceiling
 * the ports face the way the player stood when placing it.
 * <p>
 * Geometry lives in a wall frame: the bracket against the south face of the block (z = 16),
 * facing north, clips along x. {@link #FACING} is the way the bracket faces out from what it is
 * mounted on; {@link #HORIZONTAL} is only read for floor and ceiling mounts. Mirrored by
 * {@code tools/gen_radio_assets.py}, which applies the same blockstate rotations.
 */
public class CoaxHangerBlock extends Block implements IBE<CoaxHangerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty HORIZONTAL = DirectionProperty.create("horizontal", Direction.Plane.HORIZONTAL);
    public static final int CLIPS = 8, PORTS = 2 * CLIPS;
    public static final double PITCH = 1.75, FIRST = 1.875;
    private static final double CLIP_HALF = 0.6, CLIP_Y1 = 6.5, CLIP_Y2 = 9.5, CLIP_Z1 = 13.5, CLIP_Z2 = 15, PORT_Z = 14.25;
    private static final Map<BlockState, VoxelShape> SHAPES = new HashMap<>();

    public CoaxHangerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HORIZONTAL, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HORIZONTAL);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(HORIZONTAL, context.getHorizontalDirection());
    }

    /** Centre x of a clip in the wall frame, sixteenths. */
    public static double clipX(int clip) {
        return FIRST + PITCH * clip;
    }

    /** A port's position in the wall frame, sixteenths: even ports on the upper side of a clip, odd below. */
    public static Vec3 portInFrame(int port) {
        int clip = Math.max(0, Math.min(CLIPS - 1, port / 2));
        return new Vec3(clipX(clip), port % 2 == 0 ? CLIP_Y2 : CLIP_Y1, PORT_Z);
    }

    /**
     * Wall frame to block-local, as the blockstate turns the model: walls by a quarter turn per
     * facing, the floor by x = 270 then the horizontal, the ceiling by x = 90 then the horizontal.
     * Points in sixteenths about the block's centre.
     */
    public static Vec3 toLocal(Vec3 frame, BlockState state) {
        var p = frame.subtract(8, 8, 8);
        var facing = state.getValue(FACING);
        if(facing == Direction.UP)
            p = rotY(new Vec3(p.x, -p.z, p.y), state.getValue(HORIZONTAL));
        else if(facing == Direction.DOWN)
            p = rotY(new Vec3(p.x, p.z, -p.y), state.getValue(HORIZONTAL));
        else
            p = rotY(p, facing);
        return p.add(8, 8, 8).scale(1 / 16.0);
    }

    /** Block-local (in blocks) back into the wall frame, sixteenths. */
    public static Vec3 toFrame(Vec3 local, BlockState state) {
        var p = local.scale(16).subtract(8, 8, 8);
        var facing = state.getValue(FACING);
        if(facing == Direction.UP) {
            p = unrotY(p, state.getValue(HORIZONTAL));
            p = new Vec3(p.x, p.z, -p.y);
        } else if(facing == Direction.DOWN) {
            p = unrotY(p, state.getValue(HORIZONTAL));
            p = new Vec3(p.x, -p.z, p.y);
        } else {
            p = unrotY(p, facing);
        }
        return p.add(8, 8, 8);
    }

    /** The blockstate's y rotation for a horizontal direction: north 0, east 90, south 180, west 270. */
    private static Vec3 rotY(Vec3 p, Direction d) {
        return switch(d) {
            case EAST -> new Vec3(-p.z, p.y, p.x);
            case SOUTH -> new Vec3(-p.x, p.y, -p.z);
            case WEST -> new Vec3(p.z, p.y, -p.x);
            default -> p;
        };
    }

    private static Vec3 unrotY(Vec3 p, Direction d) {
        return switch(d) {
            case EAST -> new Vec3(p.z, p.y, -p.x);
            case SOUTH -> new Vec3(-p.x, p.y, -p.z);
            case WEST -> new Vec3(-p.z, p.y, p.x);
            default -> p;
        };
    }

    public static Vec3 portPosition(BlockState state, BlockPos pos, int port) {
        return Vec3.atLowerCornerOf(pos).add(toLocal(portInFrame(port), state));
    }

    /** Nearest port to a block-local click: the clip by its x in the frame, the side by its y. */
    public static int portAt(BlockState state, Vec3 local) {
        var frame = toFrame(local, state);
        int clip = (int) Math.round((frame.x - FIRST) / PITCH);
        clip = Math.max(0, Math.min(CLIPS - 1, clip));
        return clip * 2 + (frame.y >= 8 ? 0 : 1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, s -> {
            var a = toLocal(new Vec3(0.5, CLIP_Y1, CLIP_Z1), s);
            var b = toLocal(new Vec3(15.5, CLIP_Y2 + 0.5, 16), s);
            return Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        });
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        var facing = state.getValue(FACING);
        var state2 = state.setValue(HORIZONTAL, rotation.rotate(state.getValue(HORIZONTAL)));
        return facing.getAxis().isHorizontal() ? state2.setValue(FACING, rotation.rotate(facing)) : state2;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<CoaxHangerBlockEntity> getBlockEntityClass() {
        return CoaxHangerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CoaxHangerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.COAX_HANGER.get();
    }
}
