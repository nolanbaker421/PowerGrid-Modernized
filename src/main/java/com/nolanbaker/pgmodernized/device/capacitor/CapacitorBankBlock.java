package com.nolanbaker.pgmodernized.device.capacitor;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
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
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * A power-factor correction capacitor bank in a wall cabinet: three capacitors in delta between
 * L1, L2 and L3, a value box on the door for their size, and a handle that puts the bank on the
 * line or takes it off. Three lugs low on the door, four conduit knockouts. North frame: the door
 * faces north, the cabinet against z = 16. Mirrored by {@code tools/gen_power_quality_assets.py}.
 */
public class CapacitorBankBlock extends HorizontalElectricBlock implements IBE<CapacitorBankBlockEntity> {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final int L1 = 0, L2 = 1, L3 = 2;
    /** The door, in 16ths from the block's south edge, where the value box sits. */
    public static final double DOOR = 6;
    public static final AABB BODY = new AABB(2, 1, 10, 14, 15, 16);
    public static final AABB[] LUGS = {new AABB(11.5, 2, 9, 13.5, 4, 10), new AABB(8.5, 2, 9, 10.5, 4, 10), new AABB(5.5, 2, 9, 7.5, 4, 10)};
    public static final AABB HANDLE_ON = new AABB(2.5, 10, 9, 4.5, 14, 10), HANDLE_OFF = new AABB(2.5, 5, 9, 4.5, 9, 10);
    public static final AABB[] HUBS = {
            new AABB(4, 15, 12.5, 6, 16, 14.5), new AABB(10, 15, 12.5, 12, 16, 14.5),
            new AABB(4, 0, 12.5, 6, 1, 14.5), new AABB(10, 0, 12.5, 12, 1, 14.5),
    };
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(3, HUBS.length, new int[] {L1, L2, L3});
    private static final VoxelShaper HANDLES_ON = VoxelShaper.forHorizontal(box(HANDLE_ON), Direction.NORTH);
    private static final VoxelShaper HANDLES_OFF = VoxelShaper.forHorizontal(box(HANDLE_OFF), Direction.NORTH);

    public CapacitorBankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ON, false));
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[3];
        for(int k = 0; k < 3; ++k)
            terminals[k] = new TerminalBoundingBox(ConductorColors.hotLabel("capacitor_bank.line", k, k + 1), LUGS[k]).withColor(ConductorColors.hotRgb(k));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(HORIZONTAL_FACING);
        return Shapes.or(super.getShape(state, level, pos, context), (state.getValue(ON) ? HANDLES_ON : HANDLES_OFF).get(facing));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, super::onWire);
    }

    /** Empty hand on the door throws the handle; sneaking opens the splice editor for the knockouts. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, be -> be.toggle(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<CapacitorBankBlockEntity> getBlockEntityClass() {
        return CapacitorBankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CapacitorBankBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CAPACITOR_BANK.get();
    }
}
