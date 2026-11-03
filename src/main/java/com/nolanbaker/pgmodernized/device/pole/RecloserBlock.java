package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A pole-mounted recloser: a three-pole breaker in a tank that trips on a fault, recloses after a
 * moment, tries a set number of shots and then locks out. Three line lugs on top, three load
 * lugs underneath, hanging wire on all six. A wrench on the box on its front sets the pickup
 * current; a sneaking wrench sets the shots. A bare hand, or the hot stick from the ground,
 * trips it or resets it.
 * <p>
 * North frame, pixels, the back against the pole at z = 16. Mirrored by {@code tools/gen_switch_assets.py}.
 */
public class RecloserBlock extends HorizontalElectricBlock implements IBE<RecloserBlockEntity>, IHotStickTarget {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty LOCKOUT = BooleanProperty.create("lockout");
    public static final int POLES = 3, LINE_BASE = 0, LOAD_BASE = 3, TERMINAL_COUNT = 6;
    private static final double[] LUG_X = {4, 8, 12};
    private static final AABB TANK = new AABB(2.5, 2, 6, 13.5, 14, 16);

    public RecloserBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(LOCKOUT, false));
        var shape = box(TANK);
        for(int k = 0; k < POLES; ++k)
            shape = Shapes.or(shape, box(lineLug(k)), box(loadLug(k)));
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape));
    }

    public static AABB lineLug(int k) {
        return new AABB(LUG_X[k] - 0.75, 14, 9.5, LUG_X[k] + 0.75, 15.5, 11.5);
    }

    public static AABB loadLug(int k) {
        return new AABB(LUG_X[k] - 0.75, 0.5, 9.5, LUG_X[k] + 0.75, 2, 11.5);
    }

    private static TerminalBoundingBox[] terminals() {
        var out = new TerminalBoundingBox[TERMINAL_COUNT];
        for(int k = 0; k < POLES; ++k) {
            out[LINE_BASE + k] = terminal(ConductorColors.hotLabel("recloser.line", k, k + 1), lineLug(k)).withColor(ConductorColors.hotRgb(k));
            out[LOAD_BASE + k] = terminal(ConductorColors.hotLabel("recloser.load", k, k + 1), loadLug(k)).withColor(ConductorColors.hotRgb(k));
        }
        return out;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, LOCKOUT);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    /** On a pole's side the back goes to the pole; on the ground it faces the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(OPEN, false).setValue(LOCKOUT, false);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        if(ConduitItem.isConduit(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.cutout.hanging_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof HotStickItem) {
            if(!level.isClientSide)
                stick(state, level, pos, player, hit);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void stick(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(level.getBlockEntity(pos) instanceof RecloserBlockEntity be)
            be.manual(player);
    }

    /** A bare hand trips a closed recloser, and closes or resets an open or locked-out one. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof RecloserBlockEntity be)
            be.manual(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A sneaking wrench steps the shots before lockout, one to four. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        if(level.getBlockEntity(context.getClickedPos()) instanceof RecloserBlockEntity be && context.getPlayer() != null) {
            if(!level.isClientSide)
                be.cycleShots(context.getPlayer());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.onSneakWrenched(state, context);
    }

    @Override
    public Class<RecloserBlockEntity> getBlockEntityClass() {
        return RecloserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RecloserBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RECLOSER.get();
    }
}
