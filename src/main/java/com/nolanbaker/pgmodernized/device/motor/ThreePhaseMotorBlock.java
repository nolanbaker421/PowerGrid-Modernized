package com.nolanbaker.pgmodernized.device.motor;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.kinetics.base.ElectricKineticBlock;

/**
 * A three-phase induction motor: the shaft comes out of the front, the terminal box with U, V and W
 * sits at the back. Same footprint and placement rules as Power Grid's electric motor. Its speed
 * follows the supply frequency and pole pairs; the phase sequence sets the direction.
 */
public class ThreePhaseMotorBlock extends ElectricKineticBlock implements IBE<ThreePhaseMotorBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final int U = 0, V = 1, W = 2;

    public static final VoxelShape NORTH_SHAPE = Shapes.or(
            box(3, 3, 0.5, 13, 13, 13.5),
            box(2.5, 2.5, 10.5, 13.5, 13.5, 15.5),
            box(0.5, 0, 3, 15.5, 3, 13)
    );
    public static final VoxelShape UP_SHAPE = Shapes.or(
            box(3, 2.5, 3, 13, 15.5, 13),
            box(2.5, 0.5, 2.5, 13.5, 5.5, 13.5)
    );

    private static final TerminalBoundingBox[] NORTH_TERMINALS = {
            terminal("three_phase_motor.u", 0, 3.5, 13.5, 14, 5.5, 14.5, 16),
            terminal("three_phase_motor.v", 1, 7, 13.5, 14, 9, 14.5, 16),
            terminal("three_phase_motor.w", 2, 10.5, 13.5, 14, 12.5, 14.5, 16),
    };

    public ThreePhaseMotorBlock(Properties properties) {
        super(properties);
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, NORTH_TERMINALS, NORTH_SHAPE, UP_SHAPE));
    }

    private static TerminalBoundingBox terminal(String key, int phase, double x1, double y1, double z1, double x2, double y2, double z2) {
        Component name = Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(ConductorColors.textRgb(phase)));
        return new TerminalBoundingBox(name, x1, y1, z1, x2, y2, z2).withColor(ConductorColors.rgb(phase));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    public Direction getPreferredFacing(BlockPlaceContext context) {
        Direction preferredSide = null;
        for(Direction side : Iterate.directions) {
            BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if(blockState.getBlock() instanceof IRotate rotate) {
                if(rotate.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), blockState, side.getOpposite())) {
                    if(preferredSide != null && preferredSide.getAxis() != side.getAxis()) {
                        preferredSide = null;
                        break;
                    }
                    preferredSide = side;
                }
            }
        }
        return preferredSide == null ? null : preferredSide.getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        if(preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            Direction nearestLookingDirection = context.getNearestLookingDirection();
            return defaultBlockState().setValue(FACING, context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                    ? nearestLookingDirection : nearestLookingDirection.getOpposite());
        }
        return defaultBlockState().setValue(FACING, preferred.getOpposite());
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<ThreePhaseMotorBlockEntity> getBlockEntityClass() {
        return ThreePhaseMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ThreePhaseMotorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.THREE_PHASE_MOTOR.get();
    }
}
