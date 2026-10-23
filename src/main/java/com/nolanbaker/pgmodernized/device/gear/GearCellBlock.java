package com.nolanbaker.pgmodernized.device.gear;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One cell of custom switchgear: a cabinet with a 3 x 3 grid of slots behind its open front.
 * Right-click a slot with a copper bus bar, a neutral bar, a lug block, a 3-pole breaker, a fuse
 * set, a switch or a contactor to mount it; a bare hand throws its handle, a sneaking bare hand
 * pulls it, a wrench turns it, a lockout and a name tag do what they do on a panel. Cells placed
 * next to each other facing the same way are one lineup. Conduit lands on the knockouts; the
 * splice editor (bare hand on the frame) and hanging wire reach the lugs of lug blocks.
 */
public class GearCellBlock extends HorizontalElectricBlock implements IBE<GearCellBlockEntity> {
    public GearCellBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, GearLayout.terminals(), GearLayout.shape()));
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // On the floor the front faces the player; against a wall the back goes on the wall.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    private static int slotAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return GearLayout.slotAt(facing(state), hit.getDirection(), local);
    }

    private static int slotAt(BlockState state, BlockPos pos, Vec3 location, Direction face) {
        return GearLayout.slotAt(facing(state), face, location.subtract(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit on the knockouts; hanging wire on the lugs of a lug block and nowhere else. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        var level = context.getLevel();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(GearLayout.hubAt(terminal) >= 0) {
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
        if(terminal < 0)
            return InteractionResult.PASS;
        if(!GearLayout.isLug(terminal) || !(level.getBlockEntity(pos) instanceof GearCellBlockEntity cell) || !cell.lugLive(terminal)) {
            IElectric.sendMessage(context, Lang.builder().translate("message.gear_cell.lugs_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int slot = slotAt(state, pos, hit);
        if(GearCellBlockEntity.isComponent(stack)) {
            if(slot < 0)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.install(slot, stack, player) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.getItem() instanceof BreakerLockItem) {
            if(slot < 0)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.lock(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        if(stack.is(Items.NAME_TAG)) {
            if(slot < 0)
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            return onBlockEntityUseItemOn(level, pos, be -> be.label(slot, player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** A wrench on a component turns it; on the frame it turns the cell like any block. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int slot = slotAt(state, pos, context.getClickLocation(), context.getClickedFace());
        if(slot >= 0 && context.getPlayer() != null) {
            var player = context.getPlayer();
            var result = new InteractionResult[] {InteractionResult.PASS};
            withBlockEntityDo(context.getLevel(), pos, be -> {
                if(be.rotate(slot, player))
                    result[0] = InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            });
            if(result[0] != InteractionResult.PASS)
                return result[0];
        }
        return super.onWrenched(state, context);
    }

    /** A bare hand throws what is in a slot, pulls it when sneaking, and opens the editor anywhere else on the front. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hit.getDirection() != facing(state))
            return InteractionResult.PASS;
        int slot = slotAt(state, pos, hit);
        if(slot >= 0) {
            var handled = new boolean[1];
            withBlockEntityDo(level, pos, be -> handled[0] = player.isShiftKeyDown() ? be.pull(slot, player) : be.toggle(slot, player));
            if(handled[0])
                return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(player.isShiftKeyDown())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, GearCellBlockEntity::updateRedstone);
    }

    @Override
    public Class<GearCellBlockEntity> getBlockEntityClass() {
        return GearCellBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GearCellBlockEntity> getBlockEntityType() {
        return ModBlockEntities.GEAR_CELL.get();
    }
}
