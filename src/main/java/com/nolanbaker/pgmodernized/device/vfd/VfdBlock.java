package com.nolanbaker.pgmodernized.device.vfd;

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

public class VfdBlock extends Rotation4ElectricBlock implements IBE<VfdBlockEntity> {
    public static final int INPUT_POSITIVE = 0;
    public static final int INPUT_NEGATIVE = 1;
    public static final int OUTPUT_POSITIVE = 2;
    public static final int OUTPUT_NEGATIVE = 3;
    /** Built-in Cat6 jack. Not an electrical terminal: it has no node in the circuit. */
    public static final int JACK = 4;

    /** Conduit knockouts, appended after the device's own terminals. */
    public static final AABB[] HUBS = {new AABB(1, 1, 3.5, 2, 3, 5.5), new AABB(1, 1, 10.5, 2, 3, 12.5)};
    private static final AABB HIDDEN = new AABB(7, 1, 7, 9, 2, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(5, HUBS.length, new int[] {INPUT_POSITIVE, INPUT_NEGATIVE, OUTPUT_POSITIVE, OUTPUT_NEGATIVE});

    private static final VoxelShape SHAPE_DOWN = box(2, 0, 2, 14, 7, 14);

    public VfdBlock(Properties settings) {
        super(settings);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("vfd.input_positive", ChatFormatting.RED), 3, 0, 0, 5, 2, 2)
                        .withColor(IDecoratedTerminal.RED),
                new TerminalBoundingBox(name("vfd.input_negative", ChatFormatting.BLUE), 11, 0, 0, 13, 2, 2)
                        .withColor(IDecoratedTerminal.BLUE),
                new TerminalBoundingBox(name("vfd.output_positive", ChatFormatting.RED), 3, 0, 14, 5, 2, 16)
                        .withColor(IDecoratedTerminal.RED),
                new TerminalBoundingBox(name("vfd.output_negative", ChatFormatting.BLUE), 11, 0, 14, 13, 2, 16)
                        .withColor(IDecoratedTerminal.BLUE),
                JackTerminals.jack(14, 0, 7, 16, 2, 9)
        };
    }

    private static net.minecraft.network.chat.Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
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
    public Class<VfdBlockEntity> getBlockEntityClass() {
        return VfdBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends VfdBlockEntity> getBlockEntityType() {
        return ModBlockEntities.VFD.get();
    }
}
