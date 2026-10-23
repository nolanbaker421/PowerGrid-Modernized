package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One block of bus duct: a steel housing carrying L1, L2, L3 and a neutral along its axis.
 * Segments join end to end through hidden bus bars: a segment joins whatever its axis points at,
 * so a corner is just a segment turned the other way, and a run ending at an end feed, a
 * switchgear section or a switchgear cell joins that block's bus. Plug-in units snap onto any
 * other face. The duct itself has nothing to splice or hang wire on. Rated 2000 A, the bus
 * bar's limit.
 * <p>
 * North frame: the housing runs along z. FACING only means an axis; north and south look alike.
 * Mirrored by {@code tools/gen_busduct_assets.py}; change both.
 */
public class BusDuctBlock extends DirectionalElectricBlock implements IBE<BusDuctBlockEntity> {
    public static final int BUS_COUNT = BusLinks.CONDUCTORS;
    public static final AABB HOUSING = new AABB(3, 4, 0, 13, 12, 16);
    public static final AABB FLANGE_A = new AABB(2.5, 3.5, 0, 13.5, 12.5, 1);
    public static final AABB FLANGE_B = new AABB(2.5, 3.5, 15, 13.5, 12.5, 16);
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 7.5, 8.5, 8.5, 8.5);

    public BusDuctBlock(Properties properties) {
        super(properties);
        setTerminalCollection(directionalNorthTerminals(this, terminals(), shape()));
    }

    public static Direction.Axis axis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** L1, L2, L3 and N in the line colours, shared by everything a duct joins. */
    public static Component busName(int k) {
        return k == 3 ? ConductorColors.neutralLabel("bus_duct.neutral") : ConductorColors.hotLabel("bus_duct.line", k, k + 1);
    }

    public static int busRgb(int k) {
        return k == 3 ? ConductorColors.neutralRgb() : ConductorColors.hotRgb(k);
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[BUS_COUNT];
        for(int k = 0; k < BUS_COUNT; ++k)
            terminals[k] = new TerminalBoundingBox(busName(k), HIDDEN).withColor(busRgb(k));
        return terminals;
    }

    private static VoxelShape shape() {
        return Shapes.or(box(HOUSING), box(FLANGE_A), box(FLANGE_B));
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /**
     * Clicking the end of a duct continues the run; anywhere else the duct runs the way the player
     * looks, up or down included. Sneaking lays it along the clicked face instead.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        Direction facing;
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = face;
        else if(against.getBlock() instanceof BusDuctBlock && axis(against) == face.getAxis())
            facing = face;
        else
            facing = ctx.getNearestLookingDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Nothing lands on a duct: it joins its neighbours by itself. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        IElectric.sendMessage(context, Lang.builder().translate("message.bus_duct.no_wire").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    @Override
    public Class<BusDuctBlockEntity> getBlockEntityClass() {
        return BusDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BusDuctBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BUS_DUCT.get();
    }
}
