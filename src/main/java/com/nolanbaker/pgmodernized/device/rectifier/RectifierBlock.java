package com.nolanbaker.pgmodernized.device.rectifier;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A three-phase bridge rectifier in a wall cabinet: L1, L2, L3 low on the door, DC+ and DC- high
 * on it, four conduit knockouts and a value box for the smoothing capacitor. Feed any two of the
 * AC lugs for single-phase. North frame: the door faces north, the cabinet against z = 16.
 * Mirrored by {@code tools/gen_power_quality_assets.py}.
 */
public class RectifierBlock extends HorizontalElectricBlock implements IBE<RectifierBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, DC_PLUS = 3, DC_MINUS = 4;
    /** The door, in 16ths from the block's south edge, where the value box sits. */
    public static final double DOOR = 6;
    public static final AABB BODY = new AABB(2, 1, 10, 14, 15, 16);
    public static final AABB[] LUGS = {
            new AABB(11.5, 2, 9, 13.5, 4, 10), new AABB(8.5, 2, 9, 10.5, 4, 10), new AABB(5.5, 2, 9, 7.5, 4, 10),
            new AABB(11.5, 12, 9, 13.5, 14, 10), new AABB(2.5, 12, 9, 4.5, 14, 10),
    };
    public static final AABB[] HUBS = {
            new AABB(4, 15, 12.5, 6, 16, 14.5), new AABB(10, 15, 12.5, 12, 16, 14.5),
            new AABB(4, 0, 12.5, 6, 1, 14.5), new AABB(10, 0, 12.5, 12, 1, 14.5),
    };
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(5, HUBS.length, new int[] {L1, L2, L3, DC_PLUS, DC_MINUS});

    public RectifierBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[5];
        for(int k = 0; k < 3; ++k)
            terminals[k] = TerminalBoxes.px(ConductorColors.hotLabel("rectifier.ac", k, k + 1), LUGS[k]).withColor(ConductorColors.hotRgb(k));
        terminals[DC_PLUS] = TerminalBoxes.px(Lang.builder().translate("rectifier.dc_plus").style(ChatFormatting.RED).component(), LUGS[DC_PLUS]).withColor(IDecoratedTerminal.RED);
        terminals[DC_MINUS] = TerminalBoxes.px(Lang.builder().translate("rectifier.dc_minus").style(ChatFormatting.BLUE).component(), LUGS[DC_MINUS]).withColor(IDecoratedTerminal.BLUE);
        return terminals;
    }

    private static VoxelShape shape() {
        var shape = box(BODY);
        for(var lug : LUGS)
            shape = Shapes.or(shape, box(lug));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, super::onWire);
    }

    /** Empty hand opens the splice editor for the knockouts. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<RectifierBlockEntity> getBlockEntityClass() {
        return RectifierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RectifierBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RECTIFIER.get();
    }
}
