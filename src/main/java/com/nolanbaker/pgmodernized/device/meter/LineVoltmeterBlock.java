package com.nolanbaker.pgmodernized.device.meter;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/** Two-probe digital voltmeter with a very high input resistance; accepts any wire type so it can hang on transmission lines. */
public class LineVoltmeterBlock extends Rotation4ElectricBlock implements IBE<LineVoltmeterBlockEntity> {
    public static final int PROBE_POSITIVE = 0;
    public static final int PROBE_NEGATIVE = 1;
    /** Built-in Cat6 jack. Not an electrical terminal: it has no node in the circuit. */
    public static final int JACK = 2;

    /** Conduit knockouts, appended after the device's own terminals. */
    public static final AABB[] HUBS = {new AABB(4, 1, 13, 6, 3, 14), new AABB(10, 1, 13, 12, 3, 14)};
    private static final AABB HIDDEN = new AABB(7, 1, 7, 9, 2, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(3, HUBS.length, new int[] {PROBE_POSITIVE, PROBE_NEGATIVE});

    private static final VoxelShape SHAPE_DOWN = box(2, 0, 3, 14, 9, 13);

    public LineVoltmeterBlock(Properties settings) {
        super(settings);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("line_voltmeter.probe_positive", ChatFormatting.RED), 0, 0, 6, 2, 3, 10)
                        .withColor(IDecoratedTerminal.RED),
                new TerminalBoundingBox(name("line_voltmeter.probe_negative", ChatFormatting.BLUE), 14, 0, 6, 16, 3, 10)
                        .withColor(IDecoratedTerminal.BLUE),
                JackTerminals.jack(7, 3, 1, 9, 5, 3)
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
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Empty hand on the body opens the splice editor for the knockouts. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Empty hand only: anything held (wrench, wire, goggles) keeps its own behaviour.
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<LineVoltmeterBlockEntity> getBlockEntityClass() {
        return LineVoltmeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LineVoltmeterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINE_VOLTMETER.get();
    }
}
