package com.nolanbaker.pgmodernized.rail;

import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import com.nolanbaker.pgmodernized.util.Rotation4Shapes;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
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
 * The collector trolley: a pull box on the moving body with a spring arm and shoes out of its
 * front. While the shoes sit in a rail block of a fed run, the box's four studs are the rail's
 * four bars and its jack is on the rail's data channel. The shoe terminal is hidden out in front;
 * it only exists to say where the shoes are.
 */
public class RailCollectorBlock extends Rotation4ElectricBlock implements IBE<RailCollectorBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    public static final int JACK = 4;
    /** Hidden, one block out of the front: where the shoes point. The arm reaches up to {@link #MAX_REACH} blocks that way. */
    public static final int SHOE = 5;
    public static final int MAX_REACH = 3;
    public static final AABB[] HUBS = {new AABB(14, 0, 3, 16, 2, 7)};
    private static final AABB HIDDEN = new AABB(7, 0.5, 7, 9, 1.5, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(6, HUBS.length, new int[] {L1, L2, L3, N});

    private static final VoxelShape SHAPE_DOWN = Shapes.or(box(2, 0, 2, 14, 6, 14), box(6, 2, 0, 10, 5, 2), box(14, 0, 9, 16, 2, 11),
            box(3, 6, 9, 5, 8, 11), box(6, 6, 9, 8, 8, 11), box(9, 6, 9, 11, 8, 11), box(12, 6, 9, 14, 8, 11));

    /** The studs take any real conductor, THHN included. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** How many blocks out the arm reaches to the rail it found: the model's arm follows. */
    public static final IntegerProperty REACH = IntegerProperty.create("reach", 1, MAX_REACH);

    public RailCollectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(REACH, 1));
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REACH);
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("rail.l1", ChatFormatting.RED), 3, 6, 9, 5, 8, 11).withColor(0xD03030),
                new TerminalBoundingBox(name("rail.l2", ChatFormatting.GOLD), 6, 6, 9, 8, 8, 11).withColor(0xE0A020),
                new TerminalBoundingBox(name("rail.l3", ChatFormatting.BLUE), 9, 6, 9, 11, 8, 11).withColor(0x3060D0),
                new TerminalBoundingBox(name("rail.n", ChatFormatting.WHITE), 12, 6, 9, 14, 8, 11).withColor(0xE8E8E8),
                JackTerminals.jack(14, 0, 9, 16, 2, 11),
                new TerminalBoundingBox(name("rail.shoe", ChatFormatting.GRAY), 7, 2, -10, 9, 4, -8).withColor(0x505050)
        };
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Rotation4Shapes.of(state, new AABB(2, 0, 2, 14, 6, 14), new AABB(6, 2, 0, 10, 5, 2), new AABB(14, 0, 9, 16, 2, 11),
                new AABB(3, 6, 9, 5, 8, 11), new AABB(6, 6, 9, 8, 8, 11), new AABB(9, 6, 9, 11, 8, 11), new AABB(12, 6, 9, 14, 8, 11), HUBS[0]);
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
    public Class<RailCollectorBlockEntity> getBlockEntityClass() {
        return RailCollectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RailCollectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RAIL_COLLECTOR.get();
    }
}
