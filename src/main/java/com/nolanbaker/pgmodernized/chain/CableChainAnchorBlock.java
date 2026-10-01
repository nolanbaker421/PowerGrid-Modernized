package com.nolanbaker.pgmodernized.chain;

import net.minecraft.world.item.ItemStack;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One end of a cable chain: a small pull box. A 4" conduit knockout on one side, the chain post on
 * the front, four studs (L1, L2, L3, N) on the back edge for landing wire directly, and a network
 * jack. Conductors pulled through the chain, conductors in the conduit and the studs all meet in
 * the splice editor (empty hand on the body), exactly as in a pull box.
 */
public class CableChainAnchorBlock extends Rotation4ElectricBlock implements IBE<CableChainAnchorBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    public static final int JACK = 4;
    /** Hub 0 is the conduit knockout, hub 1 the chain post. */
    public static final int CONDUIT_HUB = 0, CHAIN_HUB = 1;
    public static final AABB[] HUBS = {new AABB(0, 0, 6, 2, 2, 10), new AABB(5, 2, 2, 11, 8, 6)};
    private static final AABB HIDDEN = new AABB(7, 0.5, 7, 9, 1.5, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(5, HUBS.length, new int[] {L1, L2, L3, N});

    private static final VoxelShape SHAPE_DOWN = Shapes.or(box(2, 0, 2, 14, 2, 14), box(14, 0, 7, 16, 2, 9),
            box(2, 2, 11, 4, 4, 13), box(5, 2, 11, 7, 4, 13), box(9, 2, 11, 11, 4, 13), box(12, 2, 11, 14, 4, 13));

    /** The studs take any real conductor, THHN included; the knockout and the jack have their own rules. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    public CableChainAnchorBlock(Properties properties) {
        super(properties);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("cable_chain.l1", ChatFormatting.RED), 2, 2, 11, 4, 4, 13).withColor(0xD03030),
                new TerminalBoundingBox(name("cable_chain.l2", ChatFormatting.GOLD), 5, 2, 11, 7, 4, 13).withColor(0xE0A020),
                new TerminalBoundingBox(name("cable_chain.l3", ChatFormatting.BLUE), 9, 2, 11, 11, 4, 13).withColor(0x3060D0),
                new TerminalBoundingBox(name("cable_chain.n", ChatFormatting.WHITE), 12, 2, 11, 14, 4, 13).withColor(0xE8E8E8),
                JackTerminals.jack(14, 0, 7, 16, 2, 9)
        };
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    public static int chainHubTerminal() {
        return LAYOUT.hubTerminal(CHAIN_HUB);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        // The chain post takes the cable chain only; WireGuard brings those clicks here before Power Grid sees them.
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(terminal == chainHubTerminal() && ConduitItem.isConduit(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.cable_chain.post_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return DeviceHubs.onWire(this, LAYOUT, state, context,
                (s, c) -> JackTerminals.onWire(this, JACK, s, c, super::onWire));
    }

    /** Empty hand on the body opens the splice editor. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<CableChainAnchorBlockEntity> getBlockEntityClass() {
        return CableChainAnchorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableChainAnchorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CABLE_CHAIN_ANCHOR.get();
    }
}
