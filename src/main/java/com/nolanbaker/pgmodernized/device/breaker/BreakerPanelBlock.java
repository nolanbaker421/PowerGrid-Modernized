package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.context.UseOnContext;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;

/**
 * Wall-mounted load centre. A line lug feeds the main breaker space at the top, the bus behind
 * the two breaker columns feeds one terminal per branch space (on the side of the enclosure next
 * to that space), and the neutral bar along the bottom is a plain junction for the return conductors.
 * <p>
 * Right-click a space with a breaker item to plug it in, right-click an installed breaker with an
 * empty hand to flip it (a tripped breaker resets to OFF first), and shift-right-click to pull it.
 */
public class BreakerPanelBlock extends HorizontalElectricBlock implements IBE<BreakerPanelBlockEntity> {
    private final PanelSpec spec;

    public BreakerPanelBlock(Properties properties, PanelSpec spec) {
        super(properties);
        this.spec = spec;
        setTerminalCollection(horizontalNorthTerminals(this, PanelLayout.terminals(spec), PanelLayout.shape(spec)));
    }

    public PanelSpec spec() {
        return spec;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall the back goes on the wall; on a floor or ceiling it faces the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
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

    private int slotAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return PanelLayout.slotAt(spec, state.getValue(HORIZONTAL_FACING), hit.getDirection(), local);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerItem breaker) {
            int slot = slotAt(state, pos, hit);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be ->
                    be.install(slot, breaker, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.getItem() instanceof BreakerLockItem) {
            int slot = slotAt(state, pos, hit);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be ->
                    be.lock(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.is(Items.NAME_TAG)) {
            int slot = slotAt(state, pos, hit);
            if(slot == PanelLayout.NO_SLOT)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be ->
                    be.label(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        // Empty hand: fall through to use() so the handles can be flipped. Anything else (wires,
        // wrench) keeps its own behaviour without toggling breakers underneath it.
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int slot = slotAt(state, pos, hit);
        if(slot == PanelLayout.NO_SLOT) {
            // The front outside the breaker spaces opens the splice editor for the conduit knockouts.
            if(hit.getDirection() != facing(state) || player.isShiftKeyDown())
                return InteractionResult.PASS;
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, be -> {
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
