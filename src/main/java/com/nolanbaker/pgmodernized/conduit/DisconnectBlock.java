package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A heavy-duty disconnect: the grey wall box with the red handle on its right that isolates a
 * load. Three poles, each a contact between a Line lug on the top edge and a Load lug on the bottom
 * edge; the handle throws all three. Wire reaches it either as conduit on the two knockouts top
 * and bottom (spliced to the lugs in the editor, like a panel) or hung straight on the lugs. A
 * breaker lockout on an open switch freezes the handle until it is taken off again.
 * <p>
 * North frame, pixels, the back against the wall at z = 16: a viewer at the front has +x on their
 * left, so the handle at x = 1.5..3 is on their right. Mirrored by {@code tools/gen_disconnect_assets.py}.
 */
public class DisconnectBlock extends HorizontalElectricBlock implements IBE<DisconnectBlockEntity> {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public static final int POLES = 3, HUBS = 4;
    public static final int LINE_BASE = 0, LOAD_BASE = POLES, HUB_BASE = 2 * POLES;
    public static final int CONDUCTOR_BASE = HUB_BASE + HUBS;
    public static final int TERMINAL_COUNT = CONDUCTOR_BASE + HUBS * ConductorColors.COUNT;

    private static final AABB BODY = new AABB(3, 1, 11, 13, 15, 16);
    private static final AABB HANDLE_SWEEP = new AABB(1.5, 4, 12, 3, 13, 13.5);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 13.49, 8.01, 8.01, 13.51);   // a speck: a landing has no outline to show
    /** Knockouts: two on the top edge, two on the bottom, near the wall. */
    private static final AABB[] HUB_BOXES = {
            new AABB(4, 15, 13.5, 6, 16, 15.5), new AABB(10, 15, 13.5, 12, 16, 15.5),
            new AABB(4, 0, 13.5, 6, 1, 15.5), new AABB(10, 0, 13.5, 12, 1, 15.5),
    };
    private static final double[] LUG_X = {5.5, 8, 10.5};

    public DisconnectBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ON, false).setValue(LOCKED, false));
        var shape = Shapes.or(box(BODY), box(HANDLE_SWEEP));
        for(var hub : HUB_BOXES)
            shape = Shapes.or(shape, box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub)));
        for(int k = 0; k < POLES; ++k)
            shape = Shapes.or(shape, box(lineLug(k)), box(loadLug(k)));
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape));
    }

    public static AABB lineLug(int k) {
        return new AABB(LUG_X[k] - 0.5, 15, 11.5, LUG_X[k] + 0.5, 15.8, 12.5);
    }

    public static AABB loadLug(int k) {
        return new AABB(LUG_X[k] - 0.5, 0.2, 11.5, LUG_X[k] + 0.5, 1, 12.5);
    }

    /** Pole k's line and load both wear line k's colour: black, red, blue. */
    public static Component lineName(int k) {
        return ConductorColors.hotLabel("disconnect.line", k, k + 1);
    }

    public static Component loadName(int k) {
        return ConductorColors.hotLabel("disconnect.load", k, k + 1);
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate(hub < 2 ? "disconnect.hub.top" : "disconnect.hub.bottom", hub % 2 + 1).style(ChatFormatting.AQUA).component();
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        for(int k = 0; k < POLES; ++k) {
            terminals[LINE_BASE + k] = terminal(lineName(k), lineLug(k)).withColor(ConductorColors.hotRgb(k));
            terminals[LOAD_BASE + k] = terminal(loadName(k), loadLug(k)).withColor(ConductorColors.hotRgb(k));
        }
        for(int h = 0; h < HUBS; ++h) {
            terminals[HUB_BASE + h] = terminal(hubName(h), HUB_BOXES[h]).withColor(com.nolanbaker.pgmodernized.conduit.ConduitSize.TWO.bandColor());
            for(int k = 0; k < ConductorColors.COUNT; ++k) {
                var name = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[CONDUCTOR_BASE + h * ConductorColors.COUNT + k] = terminal(name, HIDDEN).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static boolean isHub(int terminal) {
        return terminal >= HUB_BASE && terminal < CONDUCTOR_BASE;
    }

    public static boolean isLug(int terminal) {
        return terminal >= 0 && terminal < HUB_BASE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON, LOCKED);
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

    /** Hanging wire lands on the six lugs; conduit lands on the knockouts. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(isHub(terminal)) {
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
        if(terminal >= 0 && !isLug(terminal))
            return InteractionResult.FAIL;
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerLockItem)
            return onBlockEntityUseItemOn(level, pos, be -> be.lock(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand throws the handle; sneaking takes a lock off, or opens the splice editor when there is none. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(state.getValue(LOCKED)) {
                if(!level.isClientSide)
                    withBlockEntityDo(level, pos, be -> be.unlock(player));
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, be -> be.toggle(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<DisconnectBlockEntity> getBlockEntityClass() {
        return DisconnectBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DisconnectBlockEntity> getBlockEntityType() {
        return ModBlockEntities.DISCONNECT.get();
    }
}
