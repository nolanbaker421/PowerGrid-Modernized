package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * An antenna: passive, no power, no network, one coax port on its mount. It stands on a block,
 * hangs off the side of one on a bracket, or hangs under one. The block model is only the mount;
 * the dish or panel is drawn by {@code AntennaRenderer} turned to the aim, so it moves as you aim
 * it (sneak-click with an empty hand). The omni whip has no aim and is part of the model.
 * <p>
 * North frame: the antenna faces north. Floor: mast up the middle, pivot at its top. Wall: the
 * wall behind at z = 16, an arm out to the pivot. Ceiling: mast down from the top, pivot under it.
 * Mirrored by {@code tools/gen_radio_assets.py}.
 */
public class AntennaBlock extends Block implements IBE<AntennaBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    private final AntennaKind kind;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public AntennaBlock(Properties properties, AntennaKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR));
    }

    public AntennaKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    /** On top of a block it faces you; on a side it hangs off the bracket facing out; under a block it hangs. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var clicked = context.getClickedFace();
        if(clicked == Direction.UP)
            return defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection().getOpposite());
        if(clicked == Direction.DOWN)
            return defaultBlockState().setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection().getOpposite());
        return defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
    }

    // ---- geometry, north frame, sixteenths ----

    /** Where the head turns about. */
    public static Vec3 pivotInFrame(AttachFace face) {
        return switch(face) {
            case WALL -> new Vec3(8, 8, 9);
            case CEILING -> new Vec3(8, 6, 8);
            default -> new Vec3(8, 10, 8);
        };
    }

    /** Where coax lands: low on the mast, or on the arm by the wall. */
    public static Vec3 portInFrame(AttachFace face) {
        return switch(face) {
            case WALL -> new Vec3(8, 6.5, 14.5);
            case CEILING -> new Vec3(8, 14, 10);
            default -> new Vec3(8, 2, 10);
        };
    }

    /** The mount's own boxes: the mast or the arm. */
    public static AABB[] mountBoxes(AttachFace face) {
        return switch(face) {
            case WALL -> new AABB[] {new AABB(6, 6, 14, 10, 10, 16), new AABB(7, 7, 9, 9, 9, 14)};
            case CEILING -> new AABB[] {new AABB(6, 6, 6, 10, 16, 10)};
            default -> new AABB[] {new AABB(6, 0, 6, 10, 10, 10)};
        };
    }

    /** A box around the head at rest, for the outline: the renderer draws the real thing turned to the aim. */
    public static AABB headBox(AntennaKind kind, AttachFace face) {
        var p = pivotInFrame(face);
        return switch(kind) {
            case DISH -> new AABB(p.x - 7, p.y - 6, p.z - 5, p.x + 7, p.y + 6, p.z);
            case SECTOR -> new AABB(p.x - 5, p.y - 7, p.z - 3, p.x + 5, p.y + 7, p.z);
            case OMNI -> face == AttachFace.CEILING ? new AABB(p.x - 1, p.y - 13, p.z - 1, p.x + 1, p.y, p.z + 1) : new AABB(p.x - 1, p.y, p.z - 1, p.x + 1, p.y + 13, p.z + 1);
        };
    }

    /** A point of the north frame (in blocks) turned to the given facing. */
    public static Vec3 turn(Vec3 p, Direction facing) {
        return switch(facing) {
            case EAST -> new Vec3(1 - p.z, p.y, p.x);
            case SOUTH -> new Vec3(1 - p.x, p.y, 1 - p.z);
            case WEST -> new Vec3(p.z, p.y, 1 - p.x);
            default -> p;
        };
    }

    private static AABB turn(AABB px, Direction facing) {
        var a = turn(new Vec3(px.minX / 16, px.minY / 16, px.minZ / 16), facing);
        var b = turn(new Vec3(px.maxX / 16, px.maxY / 16, px.maxZ / 16), facing);
        return new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    public static Vec3 portPosition(BlockState state, BlockPos pos) {
        var p = portInFrame(state.getValue(FACE));
        return Vec3.atLowerCornerOf(pos).add(turn(new Vec3(p.x / 16, p.y / 16, p.z / 16), state.getValue(FACING)));
    }

    /** The pivot in the block's local frame, blocks. */
    public static Vec3 pivot(BlockState state) {
        var p = pivotInFrame(state.getValue(FACE));
        return turn(new Vec3(p.x / 16, p.y / 16, p.z / 16), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, s -> {
            var face = s.getValue(FACE);
            var facing = s.getValue(FACING);
            var shape = Shapes.create(turn(headBox(kind, face), facing));
            for(var box : mountBoxes(face))
                shape = Shapes.or(shape, Shapes.create(turn(box, facing)));
            return shape;
        });
    }

    /** Sneak with an empty hand opens the aim; the omni has nothing to set. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!kind.aimed() || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSettings(pos, "powergrid.gui.antenna.title");
        return InteractionResult.SUCCESS;
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<AntennaBlockEntity> getBlockEntityClass() {
        return AntennaBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AntennaBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ANTENNA.get();
    }
}
