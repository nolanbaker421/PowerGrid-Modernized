package com.nolanbaker.pgmodernized.device.ctcabinet;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A wall cabinet with four current transformers. Each channel is a pass-through: the feeder goes in
 * on "CT n In" and out on "CT n Out" through a tiny shunt, and the cabinet reports that channel's
 * current, its voltage against the shared Reference point, power, and accumulated energy. Wiring
 * enters through conduit knockouts (four top, four bottom) and is spliced to the points in the
 * cabinet's editor, which also shows the live readings. A built-in jack puts it on the computer network.
 * <p>
 * North frame like the breaker panel: enclosure against the south wall, front facing north, +x on the
 * viewer's left. Mirrored by {@code tools/gen_ct_cabinet_assets.py}.
 */
public class CtCabinetBlock extends HorizontalElectricBlock implements IBE<CtCabinetBlockEntity> {
    public static final int CHANNELS = 4;
    public static final int TERMINAL_REFERENCE = 0;
    /** Terminal count of the points plus the jack; knockouts and landings follow. */
    public static final int JACK = 1 + CHANNELS * 2;
    public static final int BASE_COUNT = JACK + 1;

    public static final AABB[] HUBS = new AABB[8];
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final AABB JACK_BOX = new AABB(14, 7, 12.5, 15, 9, 14.5);
    private static final VoxelShape BODY = box(2, 1, 10, 14, 15, 16);
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};

    static {
        for(int i = 0; i < 4; ++i) {
            double x = 16 - HUB_U[i];
            HUBS[i] = new AABB(x - 1, 15, 12.5, x + 1, 16, 14.5);
            HUBS[4 + i] = new AABB(x - 1, 0, 12.5, x + 1, 1, 14.5);
        }
    }

    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, points());

    public static int inTerminal(int channel) {
        return 1 + channel * 2;
    }

    public static int outTerminal(int channel) {
        return 2 + channel * 2;
    }

    private static int[] points() {
        var points = new int[1 + CHANNELS * 2];
        for(int i = 0; i < points.length; ++i)
            points[i] = i;
        return points;
    }

    public CtCabinetBlock(Properties properties) {
        super(properties);
        var shape = DeviceHubs.withHubs(BODY, HUBS);
        shape = DeviceHubs.withHubs(shape, JACK_BOX);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), shape));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[BASE_COUNT];
        terminals[TERMINAL_REFERENCE] = terminal(name("ct_cabinet.reference", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        for(int n = 0; n < CHANNELS; ++n) {
            terminals[inTerminal(n)] = terminal(Lang.builder().translate("ct_cabinet.in", n + 1).style(ChatFormatting.RED).component(), HIDDEN)
                    .withColor(IDecoratedTerminal.RED);
            terminals[outTerminal(n)] = terminal(Lang.builder().translate("ct_cabinet.out", n + 1).style(ChatFormatting.GOLD).component(), HIDDEN)
                    .withColor(0xE0A030);
        }
        terminals[JACK] = JackTerminals.jack(JACK_BOX.minX, JACK_BOX.minY, JACK_BOX.minZ, JACK_BOX.maxX, JACK_BOX.maxY, JACK_BOX.maxZ);
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit on the knockouts, Cat6 on the jack, nothing else lands anywhere. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context,
                (s, c) -> JackTerminals.onWire(this, JACK, s, c, (s2, c2) -> {
                    if(c2.getClickedFace() == facing(s2) && WireAcceptance.electrical(c2.getItemInHand())) {
                        IElectric.sendMessage(c2, Lang.builder().translate("message.ct_cabinet.conduit_only").style(ChatFormatting.RED).component());
                        return InteractionResult.FAIL;
                    }
                    return InteractionResult.PASS;
                }));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand opens the readings and splice editor. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            // Sneak with an empty hand zeroes the energy counters, the reset button of a real meter.
            if(!level.isClientSide) {
                withBlockEntityDo(level, pos, be -> {
                    be.resetEnergy();
                    player.displayClientMessage(Lang.builder().translate("message.ct_cabinet.zeroed").style(ChatFormatting.GRAY).component(), true);
                });
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<CtCabinetBlockEntity> getBlockEntityClass() {
        return CtCabinetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CtCabinetBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CT_CABINET.get();
    }
}
