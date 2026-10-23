package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * The indoor unit of a radio link: a small wall box with a line and neutral, a Cat6 jack and a
 * coax port on top. What it is, a point-to-point dish radio, a sector base station or a cell
 * client, is decided by the antenna at the far end of its coax. Sneak-click with an empty hand
 * for its link ID. North frame: the box against z = 16, its face north. Mirrored by
 * {@code tools/gen_radio_assets.py}; the collector shares the box.
 */
public class RadioBlock extends HorizontalElectricBlock implements IBE<RadioBlockEntity> {
    public static final int LINE = 0, NEUTRAL = 1, JACK = 2;
    public static final AABB BODY = new AABB(3, 2, 10, 13, 14, 16);
    public static final AABB LUG_LINE = new AABB(9.5, 3, 9, 11.5, 5, 10), LUG_NEUTRAL = new AABB(4.5, 3, 9, 6.5, 5, 10);
    public static final AABB JACK_BOX = new AABB(10, 10.5, 9, 12.5, 12.5, 10);
    public static final AABB COAX_STUB = new AABB(4.5, 14, 12, 6.5, 15.5, 14);
    /** One conduit knockout on top, beside the coax stub. */
    public static final AABB[] HUBS = {new AABB(8.5, 14, 12, 10.5, 16, 14)};
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 12, 8.5, 8.5, 13);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(3, HUBS.length, new int[] {LINE, NEUTRAL});
    /** Where coax attaches, in the north frame, blocks. */
    public static final Vec3 COAX_PORT = new Vec3(5.5 / 16.0, 15.5 / 16.0, 13 / 16.0);

    public RadioBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                TerminalBoxes.px(ConductorColors.hotLabel("radio.line", 0), LUG_LINE).withColor(ConductorColors.hotRgb(0)),
                TerminalBoxes.px(ConductorColors.neutralLabel("radio.neutral"), LUG_NEUTRAL).withColor(ConductorColors.neutralRgb()),
                JackTerminals.jack(JACK_BOX.minX, JACK_BOX.minY, JACK_BOX.minZ, JACK_BOX.maxX, JACK_BOX.maxY, JACK_BOX.maxZ),
        };
    }

    static VoxelShape shape() {
        return Shapes.or(box(BODY), box(LUG_LINE), box(LUG_NEUTRAL), box(JACK_BOX), box(COAX_STUB));
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static Vec3 coaxPosition(BlockState state, BlockPos pos) {
        return Vec3.atLowerCornerOf(pos).add(AntennaBlock.turn(COAX_PORT, state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Coax goes to the port on top wherever the box is clicked; conduit to the knockout; Cat6 to the jack; wire to the lugs. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return coaxOrWire(this, LAYOUT, JACK, state, context, super::onWire);
    }

    static InteractionResult coaxOrWire(IElectric block, DeviceHubs.Layout layout, int jack, BlockState state, UseOnContext context,
                                        java.util.function.BiFunction<BlockState, UseOnContext, InteractionResult> fallback) {
        if(CoaxCableItem.isCoax(context.getItemInHand()))
            return CoaxPlacement.click(context, new CoaxEndpoint(context.getClickedPos(), 0));
        return DeviceHubs.onWire(block, layout, state, context, (s, c) -> JackTerminals.onWire(block, jack, s, c, fallback));
    }

    /** Sneak with an empty hand: the link ID. A plain empty hand opens the splice editor for the knockout. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide) {
            if(player.isShiftKeyDown())
                ClientHooks.openSettings(pos, "powergrid.gui.radio.title");
            else
                ClientHooks.openSplices(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<RadioBlockEntity> getBlockEntityClass() {
        return RadioBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadioBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RADIO.get();
    }
}
