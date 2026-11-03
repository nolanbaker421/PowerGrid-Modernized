package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * The operator at the foot of a pole-top switch: a handle thrown by hand, or a servo that follows
 * the redstone signal reaching it. Throwing it throws the contact head at the top of the stack of
 * insulators above it, and every operator standing beside it in a row across the line with it,
 * so three stacks over three operators are one gang-operated switch. A handle takes a breaker
 * lockout while open.
 */
public class SwitchOperatorBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    private static final VoxelShape SHAPE = Shapes.or(box(3, 0, 3, 13, 6, 13), box(6, 6, 6, 10, 14, 10));
    /** Operators this far apart in a row still throw together. */
    private static final int ROW_LIMIT = 8;

    private final boolean servo;

    public SwitchOperatorBlock(Properties properties, boolean servo) {
        super(properties);
        this.servo = servo;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(LOCKED, false));
    }

    public boolean isServo() {
        return servo;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, LOCKED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean on = servo && ctx.getLevel().hasNeighborSignal(ctx.getClickedPos());
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(ON, on);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if(!level.isClientSide && !oldState.is(this))
            throwRow(level, pos, state.getValue(ON), null);
    }

    /** A servo follows its signal. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if(!servo || level.isClientSide)
            return;
        boolean signal = level.hasNeighborSignal(pos);
        if(signal != state.getValue(ON))
            throwRow(level, pos, signal, null);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerLockItem && !servo) {
            if(!level.isClientSide) {
                if(state.getValue(LOCKED))
                    player.displayClientMessage(Lang.translate("message.disconnect.already_locked").style(ChatFormatting.RED).component(), true);
                else if(state.getValue(ON))
                    player.displayClientMessage(Lang.translate("message.disconnect.lock_open").style(ChatFormatting.RED).component(), true);
                else {
                    level.setBlock(pos, state.setValue(LOCKED, true), Block.UPDATE_ALL);
                    if(!player.isCreative())
                        stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
                    player.displayClientMessage(Lang.translate("message.disconnect.locked_on").style(ChatFormatting.GRAY).component(), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** A bare hand throws a handle, or sneaking takes its lockout off; a servo only answers its signal. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(servo) {
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.switch_operator.servo").style(ChatFormatting.GRAY).component(), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()) {
            if(state.getValue(LOCKED)) {
                level.setBlock(pos, state.setValue(LOCKED, false), Block.UPDATE_ALL);
                var hasp = ModItems.BREAKER_LOCK.asStack();
                if(!player.isCreative() && !player.getInventory().add(hasp))
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, hasp);
                level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
                player.displayClientMessage(Lang.translate("message.disconnect.unlocked").style(ChatFormatting.GRAY).component(), true);
            }
            return InteractionResult.SUCCESS;
        }
        if(state.getValue(LOCKED)) {
            player.displayClientMessage(Lang.translate("message.disconnect.locked").style(ChatFormatting.RED).component(), true);
            return InteractionResult.SUCCESS;
        }
        throwRow(level, pos, !state.getValue(ON), player);
        return InteractionResult.SUCCESS;
    }

    /** Every operator in the row this one stands in: itself and its neighbours across the line, facing the same way. */
    public static List<BlockPos> row(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var out = new ArrayList<BlockPos>();
        out.add(pos);
        if(!(state.getBlock() instanceof SwitchOperatorBlock))
            return out;
        var facing = state.getValue(FACING);
        for(var side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            var at = pos;
            for(int i = 0; i < ROW_LIMIT; ++i) {
                at = at.relative(side);
                var next = level.getBlockState(at);
                if(!(next.getBlock() instanceof SwitchOperatorBlock) || next.getValue(FACING) != facing)
                    break;
                out.add(at);
            }
        }
        return out;
    }

    /** Throws the whole row, each operator's handle and the head at the top of its stack. Server side. */
    public static void throwRow(Level level, BlockPos pos, boolean closed, @Nullable Player player) {
        for(var at : row(level, pos)) {
            var state = level.getBlockState(at);
            if(state.getValue(ON) != closed)
                level.setBlock(at, state.setValue(ON, closed), Block.UPDATE_ALL);
            throwStack(level, at, closed, player);
        }
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, closed ? 0.9f : 0.7f);
    }

    /** The head at the top of the insulators above an operator, if there is one. */
    private static void throwStack(Level level, BlockPos operator, boolean closed, @Nullable Player player) {
        var at = operator.above();
        for(int i = 0; i < 8 && level.getBlockState(at).getBlock() instanceof SwitchInsulatorBlock; ++i)
            at = at.above();
        if(level.getBlockEntity(at) instanceof SwitchContactBlockEntity head)
            head.setClosed(closed, player, false);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
