package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.ShapeRotation;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A small wall box that ends conduit runs. Up to four runs land on its edge hubs, ordinary wires
 * land on the twelve cover terminals, and right-clicking opens the splice editor that joins run
 * conductors to each other and to the cover terminals.
 */
public class ConduitBoxBlock extends DirectionalElectricBlock implements IBE<ConduitBoxBlockEntity> {
    public ConduitBoxBlock(Properties properties) {
        super(properties);
        var north = ConduitBoxGeometry.shape();
        setTerminalCollection(directionalNorthTerminals(this, ConduitBoxGeometry.terminals(), north, ShapeRotation.northToUp(north)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getClickedFace();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(ConduitBoxGeometry.isHub(terminal)) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            if(terminal >= 0) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if(ConduitBoxGeometry.isConductor(terminal))
            return InteractionResult.FAIL;
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<ConduitBoxBlockEntity> getBlockEntityClass() {
        return ConduitBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConduitBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONDUIT_BOX.get();
    }
}
