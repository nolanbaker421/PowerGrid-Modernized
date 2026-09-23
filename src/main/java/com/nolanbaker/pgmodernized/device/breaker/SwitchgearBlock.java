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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One section of a switchgear lineup: a floor-standing cabinet with a 2000 A three-phase bus and
 * one 3-pole breaker space in the door. Sections placed side by side facing the same way join
 * their buses. Wiring comes in through the knockouts on top and underneath and is spliced to the
 * bus (L1, L2, L3, N) or the breaker's load side (Load L1..L3) in the editor. The feed lands on any
 * section's bus, or on a section's load side to use its breaker as the main.
 */
public class SwitchgearBlock extends HorizontalElectricBlock implements IBE<SwitchgearBlockEntity> {
    public SwitchgearBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, SwitchgearLayout.terminals(), SwitchgearLayout.shape()));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Placed on the floor, the door faces the player; against a wall the back goes on the wall.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** Direction the door faces. */
    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Only conduit lands on a section, and only on a knockout. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(SwitchgearLayout.hubAt(terminal) >= 0) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit)
            return InteractionResult.PASS;
        if(WireAcceptance.electrical(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.breaker_panel.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static boolean onBreaker(BlockState state, BlockPos pos, BlockHitResult hit) {
        if(hit.getDirection() != facing(state))
            return false;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var north = PanelLayout.toNorthFrame(local.scale(16), facing(state));
        return SwitchgearLayout.onBreaker(north);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerItem breaker) {
            if(!onBreaker(state, pos, hit))
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.install(breaker, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.getItem() instanceof BreakerLockItem) {
            if(!onBreaker(state, pos, hit))
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.lock(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.is(Items.NAME_TAG)) {
            if(!onBreaker(state, pos, hit))
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.label(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(onBreaker(state, pos, hit)) {
            return onBlockEntityUse(level, pos, be -> {
                boolean handled = player.isShiftKeyDown() ? be.pull(player) : be.toggle(player);
                return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
            });
        }
        if(hit.getDirection() != facing(state) || player.isShiftKeyDown())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<SwitchgearBlockEntity> getBlockEntityClass() {
        return SwitchgearBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SwitchgearBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SWITCHGEAR.get();
    }
}
