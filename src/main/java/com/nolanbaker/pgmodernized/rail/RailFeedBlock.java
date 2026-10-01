package com.nolanbaker.pgmodernized.rail;

import net.minecraft.world.item.ItemStack;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.nolanbaker.pgmodernized.client.ClientHooks;
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
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The feed box of a conductor rail run: a pull box whose four studs (L1, L2, L3, N) are the four
 * bars, with a 4" knockout, a Cat6 jack that becomes the run's data channel, and bars out of both
 * sides so it sits anywhere in the run. Every collector riding the run connects to this block.
 */
public class RailFeedBlock extends Rotation4ElectricBlock implements IBE<RailFeedBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    public static final int JACK = 4;
    public static final AABB[] HUBS = {new AABB(14, 0, 3, 16, 2, 7)};
    private static final AABB HIDDEN = new AABB(7, 0.5, 7, 9, 1.5, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(5, HUBS.length, new int[] {L1, L2, L3, N});

    private static final VoxelShape SHAPE_DOWN = Shapes.or(box(2, 0, 2, 14, 6, 14), box(0, 0, 0, 16, 5, 16), box(14, 0, 9, 16, 2, 11),
            box(3, 6, 3, 5, 8, 5), box(6, 6, 3, 8, 8, 5), box(9, 6, 3, 11, 8, 5), box(12, 6, 3, 14, 8, 5));

    /** The studs take any real conductor, THHN included. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    public RailFeedBlock(Properties properties) {
        super(properties);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("rail.l1", ChatFormatting.RED), 3, 6, 3, 5, 8, 5).withColor(0xD03030),
                new TerminalBoundingBox(name("rail.l2", ChatFormatting.GOLD), 6, 6, 3, 8, 8, 5).withColor(0xE0A020),
                new TerminalBoundingBox(name("rail.l3", ChatFormatting.BLUE), 9, 6, 3, 11, 8, 5).withColor(0x3060D0),
                new TerminalBoundingBox(name("rail.n", ChatFormatting.WHITE), 12, 6, 3, 14, 8, 5).withColor(0xE8E8E8),
                JackTerminals.jack(14, 0, 9, 16, 2, 11)
        };
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context,
                (s, c) -> JackTerminals.onWire(this, JACK, s, c, super::onWire));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<RailFeedBlockEntity> getBlockEntityClass() {
        return RailFeedBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RailFeedBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RAIL_FEED.get();
    }
}
