package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.world.level.block.Block;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.nolanbaker.pgmodernized.client.ClientHooks;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A full-block pull box: ten knockouts that take conduit of any trade size, spliced inside like a
 * blank-covered box. It has no terminals of its own. Set one directly under or over a breaker
 * panel on the same wall and the two link through a 4" nipple by themselves, the gutter that
 * brings the feeders into the panel. The door faces the player when placed.
 */
public class PullBoxBlock extends HorizontalElectricBlock implements IBE<PullBoxBlockEntity> {
    private final boolean lugs;

    public PullBoxBlock(Properties properties) {
        this(properties, false);
    }

    /** @param lugs a terminal cabinet: twelve lugs on the door that hanging wire lands on and the editor offers as points */
    public PullBoxBlock(Properties properties, boolean lugs) {
        super(properties);
        this.lugs = lugs;
        var shape = Shapes.block();
        if(lugs) {
            for(int k = 0; k < PullBoxGeometry.FRONT_COUNT; ++k) {
                var lug = PullBoxGeometry.frontLug(k);
                shape = Shapes.or(shape, Block.box(lug.minX, lug.minY, lug.minZ, lug.maxX, lug.maxY, lug.maxZ));
            }
        }
        setTerminalCollection(horizontalNorthTerminals(this, PullBoxGeometry.terminals(lugs), shape));
    }

    public boolean hasLugs() {
        return lugs;
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return lugs && WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(PullBoxGeometry.isHub(terminal)) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        if(lugs && PullBoxGeometry.isFront(terminal))
            return super.onWire(state, context);
        IElectric.sendMessage(context, Lang.builder().translate(lugs ? "message.terminal_cabinet.lugs_only" : "message.pull_box.no_terminals").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand on the door opens the splice editor. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<PullBoxBlockEntity> getBlockEntityClass() {
        return PullBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PullBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntities.PULL_BOX.get();
    }
}
