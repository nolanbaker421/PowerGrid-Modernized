package com.nolanbaker.pgmodernized.conduit.trough;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.trough.TroughGeometry.Pattern;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The head of a wire trough row: a gutter on the wall that conduit lands on from above, below and
 * both ends, spliced inside like a pull box, with no terminals of its own. Troughs placed to its
 * right, as the viewer sees it, join the row as {@link WireTroughExtensionBlock}s; the row is one
 * splice host with one editor, opened from any section. Each face's knockouts are a pattern the
 * player cycles with a sneak-click: two 4", four 3", six 2" or twelve 3/4". Conduit to a panel above or
 * below is laid by hand like any run.
 */
public class WireTroughBlock extends HorizontalElectricBlock implements IBE<WireTroughBlockEntity> {
    public static final IntegerProperty SECTIONS = IntegerProperty.create("sections", 1, TroughGeometry.MAX_SECTIONS);
    public static final EnumProperty<Pattern> TOP = EnumProperty.create("top", Pattern.class);
    public static final EnumProperty<Pattern> BOTTOM = EnumProperty.create("bottom", Pattern.class);

    public WireTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SECTIONS, 1).setValue(TOP, Pattern.SIX).setValue(BOTTOM, Pattern.SIX));
        // The head's collection is its own section; the others answer for themselves through their block entities.
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> TroughGeometry.terminals(state.getValue(HORIZONTAL_FACING), state.getValue(TOP), state.getValue(BOTTOM)))
                .withShapeMapper(state -> TroughGeometry.shaper(state.getValue(TOP), state.getValue(BOTTOM)).get(state.getValue(HORIZONTAL_FACING)))
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SECTIONS, TOP, BOTTOM);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    public static int sectionsOf(BlockState state) {
        return state.hasProperty(SECTIONS) ? state.getValue(SECTIONS) : 1;
    }

    /** The way a row grows: the viewer's right, standing at the lid. */
    public static Direction rowDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Whether the block at that position is a trough section, head or extension, with that facing. */
    public static boolean isSection(BlockGetter level, BlockPos pos, Direction facing) {
        var state = level.getBlockState(pos);
        return (state.getBlock() instanceof WireTroughBlock && facing(state) == facing)
                || (state.getBlock() instanceof WireTroughExtensionBlock && WireTroughExtensionBlock.facing(state) == facing);
    }

    /** The head of the row a section at that position belongs to, walking to the viewer's left, or null when it has none. */
    @Nullable
    public static BlockPos headPos(BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if(state.getBlock() instanceof WireTroughBlock)
            return pos;
        if(state.getBlock() instanceof WireTroughExtensionBlock)
            return headPos(level, pos, WireTroughExtensionBlock.facing(state));
        return null;
    }

    /**
     * The head of the row for an extension at that position with that facing, walking to the
     * viewer's left without reading the block at the position itself: a block being removed is
     * already air when its removal hook runs.
     */
    @Nullable
    public static BlockPos headPos(BlockGetter level, BlockPos pos, Direction facing) {
        var step = rowDirection(facing).getOpposite();
        var at = pos;
        for(int i = 1; i < TroughGeometry.MAX_SECTIONS; ++i) {
            at = at.relative(step);
            var there = level.getBlockState(at);
            if(there.getBlock() instanceof WireTroughBlock && facing(there) == facing)
                return at;
            if(!(there.getBlock() instanceof WireTroughExtensionBlock) || WireTroughExtensionBlock.facing(there) != facing)
                return null;
        }
        return null;
    }

    @Nullable
    public static WireTroughBlockEntity head(BlockGetter level, BlockPos pos) {
        var headPos = headPos(level, pos);
        return headPos != null && level.getBlockEntity(headPos) instanceof WireTroughBlockEntity be ? be : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(SECTIONS, 1);
    }

    /** Only the head's own section lives in its collection. */
    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        return index >= TroughGeometry.SECTION_TERMINALS ? null : super.terminal(state, index);
    }

    /** The head takes its row with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide) {
            var next = pos.relative(rowDirection(facing(state)));
            if(level.getBlockState(next).getBlock() instanceof WireTroughExtensionBlock)
                level.destroyBlock(next, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        return onWire(pos, terminal, context);
    }

    /** Shared with the extension: a knockout takes conduit, nothing else lands anywhere. */
    static InteractionResult onWire(BlockPos pos, int terminal, UseOnContext context) {
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(TroughGeometry.isHub(terminal)) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            var routed = ConduitPlacement.groundClick(context);
            if(routed != InteractionResult.PASS)
                return routed;
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        IElectric.sendMessage(context, Lang.builder().translate("message.trough.no_terminals").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand: the lid opens the editor; sneaking on the top or bottom cycles that face's knockouts. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        return interact(level, pos, state, player, hit, TOP, BOTTOM);
    }

    /** The empty-hand gestures of any section, head or extension, on its own state. */
    static InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, BlockHitResult hit,
                                      EnumProperty<Pattern> top, EnumProperty<Pattern> bottom) {
        if(player.isShiftKeyDown()) {
            if(hit.getDirection() != Direction.UP && hit.getDirection() != Direction.DOWN) {
                if(!level.isClientSide)
                    player.displayClientMessage(Lang.builder().translate("message.trough.pattern_hint").style(ChatFormatting.GRAY).component(), true);
                return InteractionResult.SUCCESS;
            }
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            var property = hit.getDirection() == Direction.UP ? top : bottom;
            int face = hit.getDirection() == Direction.UP ? 0 : 1;
            if(level.getBlockEntity(pos) instanceof ISpliceHost host && level.getBlockEntity(pos) instanceof ITroughSection section) {
                int base = section.section() * TroughGeometry.HUBS_PER_SECTION;
                for(int local : TroughGeometry.hubsOn(face)) {
                    if(host.hubRun(base + local) != null) {
                        player.displayClientMessage(Lang.builder().translate("message.trough.face_in_use").style(ChatFormatting.RED).component(), true);
                        return InteractionResult.FAIL;
                    }
                }
            }
            var next = state.getValue(property).next();
            level.setBlock(pos, state.setValue(property, next), Block.UPDATE_ALL);
            player.displayClientMessage(Lang.builder().translate("message.trough.pattern", next.label()).style(ChatFormatting.GRAY).component(), true);
            return InteractionResult.SUCCESS;
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<WireTroughBlockEntity> getBlockEntityClass() {
        return WireTroughBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WireTroughBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WIRE_TROUGH.get();
    }
}
