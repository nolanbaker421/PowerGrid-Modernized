package com.nolanbaker.pgmodernized.device.analogio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

public class AnalogIOBlock extends Rotation4ElectricBlock implements IBE<AnalogIOBlockEntity> {
    public static final int CHANNELS = 4;
    public static final int OUTPUT_TERMINAL = 0;
    public static final int INPUT_TERMINAL = CHANNELS;
    public static final int COMMON_TERMINAL = CHANNELS * 2;
    /** Built-in Cat6 jack. Not an electrical terminal: it has no node in the circuit. */
    public static final int JACK = CHANNELS * 2 + 1;

    private static final double[] PIN_X = { 2.5, 5.5, 8.5, 11.5 };
    /** Conduit knockouts, appended after the device's own terminals. */
    public static final AABB[] HUBS = {new AABB(1, 0.5, 3, 2, 2.5, 5), new AABB(1, 0.5, 11, 2, 2.5, 13)};
    private static final AABB HIDDEN = new AABB(7, 1, 7, 9, 2, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(10, HUBS.length, java.util.stream.IntStream.range(0, CHANNELS * 2 + 1).toArray());

    private static final VoxelShape SHAPE_DOWN = box(2, 0, 2, 14, 3, 14);

    public AnalogIOBlock(Properties settings) {
        super(settings);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[CHANNELS * 2 + 2];
        for(int i = 0; i < CHANNELS; ++i) {
            var output = Lang.builder().translate("analog_io.output", i + 1).style(ChatFormatting.RED).component();
            terminals[OUTPUT_TERMINAL + i] = new TerminalBoundingBox(output, PIN_X[i], 0, 0, PIN_X[i] + 2, 2, 2)
                    .withColor(IDecoratedTerminal.RED);
            var input = Lang.builder().translate("analog_io.input", i + 1).style(ChatFormatting.DARK_GREEN).component();
            terminals[INPUT_TERMINAL + i] = new TerminalBoundingBox(input, PIN_X[i], 0, 14, PIN_X[i] + 2, 2, 16)
                    .withColor(IDecoratedTerminal.GREEN);
        }
        terminals[COMMON_TERMINAL] = new TerminalBoundingBox(IDecoratedTerminal.COMMON, 0, 0, 7, 2, 2, 9)
                .withColor(IDecoratedTerminal.BLUE);
        terminals[JACK] = JackTerminals.jack(14, 0, 7, 16, 2, 9);
        return terminals;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context,
                (s, c) -> JackTerminals.onWire(this, JACK, s, c, super::onWire));
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
    public Class<AnalogIOBlockEntity> getBlockEntityClass() {
        return AnalogIOBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AnalogIOBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ANALOG_IO.get();
    }
}
