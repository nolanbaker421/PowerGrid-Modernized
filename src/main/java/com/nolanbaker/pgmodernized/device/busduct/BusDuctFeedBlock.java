package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * The end feed of a bus duct run: a box with four lugs on its front (L1, L2, L3, N) and a knockout
 * on top and underneath, so a run is fed by hanging wire or by conduit spliced to the lugs in the
 * editor. A duct pointing at any face but the front joins the lugs.
 * <p>
 * North frame: the front faces north, the box against z = 16. Mirrored by {@code tools/gen_busduct_assets.py}.
 */
public class BusDuctFeedBlock extends HorizontalElectricBlock implements IBE<BusDuctFeedBlockEntity> {
    public static final int LUGS = BusDuctBlock.BUS_COUNT;
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(LUGS, 2, new int[] {0, 1, 2, 3});
    public static final AABB BODY = new AABB(2, 1, 6, 14, 15, 16);
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 10, 8.5, 8.5, 11);
    public static final AABB[] HUBS = {new AABB(7, 15, 7, 9, 16, 9), new AABB(7, 0, 7, 9, 1, 9)};
    public static final double[] LUG_X = {4, 6.5, 9, 11.5};

    public BusDuctFeedBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    public static AABB lug(int k) {
        double x = LUG_X[k];
        return new AABB(x - 0.5, 7.5, 5, x + 0.5, 8.5, 6);
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[LUGS];
        for(int k = 0; k < LUGS; ++k)
            terminals[k] = TerminalBoxes.px(BusDuctBlock.busName(k), lug(k)).withColor(BusDuctBlock.busRgb(k));
        return terminals;
    }

    private static VoxelShape shape() {
        var shape = box(BODY);
        for(int k = 0; k < LUGS; ++k)
            shape = Shapes.or(shape, box(lug(k)));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall the back goes on the wall; on a floor or ceiling the lugs face the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit on the knockouts, hanging wire on the lugs. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, super::onWire);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || hit.getDirection() != facing(state))
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<BusDuctFeedBlockEntity> getBlockEntityClass() {
        return BusDuctFeedBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BusDuctFeedBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BUS_DUCT_FEED.get();
    }
}
