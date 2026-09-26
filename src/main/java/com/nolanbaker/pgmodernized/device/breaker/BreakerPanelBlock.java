package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Wall-mounted load centre. A line lug feeds the main breaker space at the top, the bus behind
 * the two breaker columns feeds one terminal per branch space (on the side of the enclosure next
 * to that space), and the neutral bar along the bottom is a plain junction for the return conductors.
 * <p>
 * Right-click a space with a breaker item to plug it in, right-click an installed breaker with an
 * empty hand to flip it (a tripped breaker resets to OFF first), and shift-right-click to pull it.
 * Panel Extension blocks placed directly below add rows on the same bus; {@link #SECTIONS} counts
 * the head and its extensions, and every click on an extension comes here with the head's position.
 */
public class BreakerPanelBlock extends HorizontalElectricBlock implements IBE<BreakerPanelBlockEntity> {
    /** The head plus its extensions below. */
    public static final IntegerProperty SECTIONS = IntegerProperty.create("sections", 1, 1 + PanelLayout.MAX_EXT);

    private final PanelSpec spec;

    public BreakerPanelBlock(Properties properties, PanelSpec spec) {
        super(properties);
        this.spec = spec;
        registerDefaultState(defaultBlockState().setValue(SECTIONS, 1));
        setTerminalCollection(horizontalNorthTerminals(this, PanelLayout.terminals(spec), PanelLayout.shape(spec)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SECTIONS);
    }

    public PanelSpec spec() {
        return spec;
    }

    public static int sectionsOf(BlockState state) {
        return state.hasProperty(SECTIONS) ? state.getValue(SECTIONS) : 1;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall the back goes on the wall; on a floor or ceiling it faces the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(SECTIONS, 1);
    }

    /** Terminals of extensions the panel does not have, and the bottom knockouts an extension covers, do not exist. */
    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        int sections = sectionsOf(state);
        if(PanelLayout.sectionOf(spec, index) >= sections)
            return null;
        if(sections > 1 && PanelLayout.isHeadBottomHub(PanelLayout.hubAt(spec, index)))
            return null;
        return super.terminal(state, index);
    }

    /** The head takes its extensions with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide) {
            var below = pos.below();
            if(level.getBlockState(below).getBlock() instanceof PanelExtensionBlock)
                level.destroyBlock(below, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Only conduit lands on a panel, and only on a knockout; everything else is spliced inside. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        return onWire(this, spec, state, pos, terminal, context);
    }

    /** Shared with the extension block, whose knockouts are the head's terminals at its own position. */
    static InteractionResult onWire(IElectric electric, PanelSpec spec, BlockState state, BlockPos pos, int terminal, UseOnContext context) {
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(PanelLayout.hubAt(spec, terminal) >= 0) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit)
            return InteractionResult.PASS;
        if(context.getClickedFace() == state.getValue(HORIZONTAL_FACING) && WireAcceptance.electrical(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.breaker_panel.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    /** The space under a hit, with the hit taken relative to the head block (an extension's hit lands a block or two lower). */
    private int slotAt(BlockState state, BlockPos headPos, BlockHitResult hit, Level level) {
        var local = hit.getLocation().subtract(headPos.getX(), headPos.getY(), headPos.getZ());
        int count = level.getBlockEntity(headPos) instanceof BreakerPanelBlockEntity be ? be.slotCount() : spec.slots();
        return PanelLayout.slotAt(spec, state.getValue(HORIZONTAL_FACING), hit.getDirection(), local, count);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interactItem(stack, state, level, pos, player, hand, hit);
    }

    /** Item clicks on the head or on one of its extensions; {@code headPos} is always the head. */
    public ItemInteractionResult interactItem(ItemStack stack, BlockState state, Level level, BlockPos headPos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerItem breaker) {
            int slot = slotAt(state, headPos, hit, level);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, headPos, be ->
                    be.install(slot, breaker, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.getItem() instanceof BreakerLockItem) {
            int slot = slotAt(state, headPos, hit, level);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, headPos, be ->
                    be.lock(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.is(Items.NAME_TAG)) {
            int slot = slotAt(state, headPos, hit, level);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, headPos, be ->
                    be.label(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        // Empty hand: fall through to use() so the handles can be flipped. Anything else (wires,
        // wrench) keeps its own behaviour without toggling breakers underneath it.
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(state, level, pos, player, hit);
    }

    /** Empty-hand clicks on the head or on one of its extensions; {@code headPos} is always the head. */
    public InteractionResult interact(BlockState state, Level level, BlockPos headPos, Player player, BlockHitResult hit) {
        int slot = slotAt(state, headPos, hit, level);
        if(slot == PanelLayout.NO_SLOT) {
            // The front outside the breaker spaces opens the splice editor for the conduit knockouts.
            if(hit.getDirection() != facing(state) || player.isShiftKeyDown())
                return InteractionResult.PASS;
            if(level.isClientSide)
                ClientHooks.openSplices(headPos);
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, headPos, be -> {
            boolean handled = player.isShiftKeyDown() ? be.pull(slot, player) : be.toggle(slot, player);
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }

    /** Direction the open front faces. */
    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public Class<BreakerPanelBlockEntity> getBlockEntityClass() {
        return BreakerPanelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BreakerPanelBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BREAKER_PANEL.get();
    }
}
