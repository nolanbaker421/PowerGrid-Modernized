package com.nolanbaker.pgmodernized.device.drive;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Three-phase variable frequency drive. Three-phase (or DC) in on L1, L2, L3 at one end; a
 * three-phase output at the set frequency and volts-per-hertz on U, V, W at the other; a Cat6 jack
 * and two conduit knockouts on the sides. The frequency is set on the value box on top or by a
 * computer. Same floor-mounted, wrench-rotatable form as the DC VFD.
 */
public class ThreePhaseDriveBlock extends Rotation4ElectricBlock implements IBE<ThreePhaseDriveBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2;
    public static final int U = 3, V = 4, W = 5;
    /** Built-in Cat6 jack. Not an electrical terminal: it has no node in the circuit. */
    public static final int JACK = 6;

    public static final AABB[] HUBS = {new AABB(1, 1, 3.5, 2, 3, 5.5), new AABB(1, 1, 10.5, 2, 3, 12.5)};
    private static final AABB HIDDEN = new AABB(7, 1, 7, 9, 2, 9);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(7, HUBS.length, new int[] {L1, L2, L3, U, V, W});

    private static final VoxelShape SHAPE_DOWN = box(1, 0, 1, 15, 9, 15);

    public ThreePhaseDriveBlock(Properties settings) {
        super(settings);
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(SHAPE_DOWN, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("three_phase_drive.l1", 0, 2.5, 0, 0, 4.5, 2, 2),
                terminal("three_phase_drive.l2", 1, 7, 0, 0, 9, 2, 2),
                terminal("three_phase_drive.l3", 2, 11.5, 0, 0, 13.5, 2, 2),
                terminal("three_phase_drive.u", 0, 2.5, 0, 14, 4.5, 2, 16),
                terminal("three_phase_drive.v", 1, 7, 0, 14, 9, 2, 16),
                terminal("three_phase_drive.w", 2, 11.5, 0, 14, 13.5, 2, 16),
                JackTerminals.jack(14, 0, 7, 16, 2, 9)
        };
    }

    private static TerminalBoundingBox terminal(String key, int phase, double x1, double y1, double z1, double x2, double y2, double z2) {
        Component name = Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(ConductorColors.textRgb(phase)));
        return new TerminalBoundingBox(name, x1, y1, z1, x2, y2, z2).withColor(ConductorColors.rgb(phase));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context,
                (s, c) -> JackTerminals.onWire(this, JACK, s, c, super::onWire));
    }

    /** Empty hand on the body opens the splice editor for the knockouts; the value box on top is Create's. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<ThreePhaseDriveBlockEntity> getBlockEntityClass() {
        return ThreePhaseDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ThreePhaseDriveBlockEntity> getBlockEntityType() {
        return ModBlockEntities.THREE_PHASE_DRIVE.get();
    }
}
