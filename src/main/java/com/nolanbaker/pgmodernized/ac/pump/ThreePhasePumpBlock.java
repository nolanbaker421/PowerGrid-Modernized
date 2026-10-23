package com.nolanbaker.pgmodernized.ac.pump;

import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.util.ShapeRotation;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.pump.ElectricPumpBlock;
import org.patryk3211.powergrid.electricity.pump.ElectricPumpBlockEntity;

/**
 * Power Grid's electric pump with a three-phase motor on it: the same pipe connections and
 * placement, a terminal box on top with U, V, W and a knockout each side. It pumps out of the
 * front at a flow set by the real power its windings draw, in the direction the phase sequence
 * dictates, and reaches further with every {@link PumpStageBlock} bolted onto its sides.
 * North frame: the axis along z, the front at z = 0. Mirrored by {@code tools/gen_power_quality_assets.py}.
 */
public class ThreePhasePumpBlock extends ElectricPumpBlock {
    public static final int U = 0, V = 1, W = 2;
    public static final AABB BODY = new AABB(2, 0, 3, 14, 14, 13);
    public static final AABB STUB_FRONT = new AABB(2, 2, 0, 14, 14, 3), STUB_BACK = new AABB(2, 2, 13, 14, 14, 16);
    public static final AABB TERMINAL_BOX = new AABB(3, 13, 4, 13, 15, 12);
    /** Studs the motor's size on top of the terminal box: U on the viewer's left (+x), then V, then W. */
    public static final AABB[] LUGS = {new AABB(9.5, 15, 7, 11.5, 16, 9), new AABB(7, 15, 7, 9, 16, 9), new AABB(4.5, 15, 7, 6.5, 16, 9)};
    public static final AABB[] HUBS = {new AABB(2, 13.5, 7, 3, 14.5, 9), new AABB(13, 13.5, 7, 14, 14.5, 9)};
    /** The arrows on the body point the way the fluid goes: out of the front, or into it on the reverse sequence. */
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 7, 8.5, 8.5, 8);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(3, HUBS.length, new int[] {U, V, W});
    private static final VoxelShaper SHAPER = VoxelShaper.forDirectional(DeviceHubs.withHubs(northShape(), HUBS), Direction.NORTH)
            .withVerticalShapes(ShapeRotation.northToUp(DeviceHubs.withHubs(northShape(), HUBS)));

    public ThreePhasePumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(REVERSED, false));
        var shape = DeviceHubs.withHubs(northShape(), HUBS);
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), shape, ShapeRotation.northToUp(shape)));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[3];
        String[] keys = {"three_phase_motor.u", "three_phase_motor.v", "three_phase_motor.w"};
        for(int k = 0; k < 3; ++k)
            terminals[k] = TerminalBoxes.px(ConductorColors.hotLabel(keys[k], k), LUGS[k]).withColor(ConductorColors.hotRgb(k));
        return terminals;
    }

    private static VoxelShape northShape() {
        var shape = Shapes.or(box(BODY), box(STUB_FRONT), box(STUB_BACK), box(TERMINAL_BOX));
        for(var lug : LUGS)
            shape = Shapes.or(shape, box(lug));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Shapes.box(px.minX / 16, px.minY / 16, px.minZ / 16, px.maxX / 16, px.maxY / 16, px.maxZ / 16);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPER.get(state.getValue(FACING));
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
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends ElectricPumpBlockEntity> getBlockEntityType() {
        return AcContent.THREE_PHASE_PUMP_BE.get();
    }
}
