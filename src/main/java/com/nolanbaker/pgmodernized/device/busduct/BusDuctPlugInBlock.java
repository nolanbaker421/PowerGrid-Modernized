package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.Rotation4Shapes;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.ArrayList;

/**
 * A plug-in disconnect that snaps onto any face of a bus duct: three contacts from the duct's
 * L1, L2, L3 to three load lugs, the neutral straight through to a fourth, a handle on the side
 * that throws the three, and two knockouts so the load leaves by conduit spliced to the lugs in
 * the editor; hanging wire lands on the lugs too. A breaker lockout freezes an open handle.
 * <p>
 * Floor frame: the unit stands on the duct below it (FACING points at the duct), the handle on
 * +x, the lugs along the back and the knockouts along the front of its top. Mirrored by
 * {@code tools/gen_busduct_assets.py}.
 */
public class BusDuctPlugInBlock extends Rotation4ElectricBlock implements IBE<BusDuctPlugInBlockEntity> {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public static final int POLES = 3;
    public static final int STAB_BASE = 0, LOAD_BASE = BusDuctBlock.BUS_COUNT, BASE_COUNT = 2 * BusDuctBlock.BUS_COUNT;
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, 2, new int[] {LOAD_BASE, LOAD_BASE + 1, LOAD_BASE + 2, LOAD_BASE + 3});

    public static final AABB BODY = new AABB(2, 0, 2, 14, 8, 14);
    public static final AABB HANDLE_OFF = new AABB(14, 1, 6, 15.5, 4.5, 8);
    public static final AABB HANDLE_ON = new AABB(14, 4, 6, 15.5, 7.5, 8);
    public static final AABB[] HUBS = {new AABB(4, 8, 3, 6, 9, 5), new AABB(10, 8, 3, 12, 9, 5)};
    public static final double[] LUG_X = {4, 6.5, 9, 11.5};
    private static final AABB STABS = new AABB(7.5, 0.5, 7.5, 8.5, 1.5, 8.5);
    private static final AABB HIDDEN = new AABB(7.5, 4, 7.5, 8.5, 5, 8.5);

    public BusDuctPlugInBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ON, false).setValue(LOCKED, false));
        setTerminalCollection(com.nolanbaker.pgmodernized.util.Rotation4Frame.collection(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    public static AABB lug(int k) {
        double x = LUG_X[k];
        return new AABB(x - 0.5, 8, 11, x + 0.5, 9, 12);
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[BASE_COUNT];
        for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k) {
            terminals[STAB_BASE + k] = TerminalBoxes.px(BusDuctBlock.busName(k), STABS).withColor(BusDuctBlock.busRgb(k));
            terminals[LOAD_BASE + k] = TerminalBoxes.px(loadName(k), lug(k)).withColor(BusDuctBlock.busRgb(k));
        }
        return terminals;
    }

    public static net.minecraft.network.chat.Component loadName(int k) {
        return k == 3 ? com.nolanbaker.pgmodernized.conduit.ConductorColors.neutralLabel("bus_duct_plug_in.load_n")
                : com.nolanbaker.pgmodernized.conduit.ConductorColors.hotLabel("bus_duct_plug_in.load", k, k + 1);
    }

    private static VoxelShape shape() {
        var shape = box(BODY);
        shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(HANDLE_OFF));
        for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k)
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(lug(k)));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static boolean isLug(int terminal) {
        return terminal >= LOAD_BASE && terminal < BASE_COUNT;
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON, LOCKED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var boxes = new ArrayList<AABB>();
        boxes.add(BODY);
        boxes.add(state.getValue(ON) ? HANDLE_ON : HANDLE_OFF);
        for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k)
            boxes.add(lug(k));
        boxes.addAll(java.util.Arrays.asList(HUBS));
        return Rotation4Shapes.of(state, boxes.toArray(AABB[]::new));
    }

    /** Only onto a duct: the face clicked must belong to one. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var state = super.getStateForPlacement(ctx);
        if(state == null)
            return null;
        return onDuct(ctx.getLevel(), ctx.getClickedPos(), state) ? state : null;
    }

    public static boolean onDuct(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.relative(facing(state))).getBlock() instanceof BusDuctBlock;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return onDuct(level, pos, state);
    }

    /** The duct it sits on going away drops the unit. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if(!level.isClientSide && neighborPos.equals(pos.relative(facing(state))) && !onDuct(level, pos, state))
            level.destroyBlock(pos, true);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit on the knockouts, hanging wire on the four load lugs and nowhere else. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            var pos = c.getClickedPos();
            int terminal = terminalIndexAt(s, c.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
            if(terminal >= 0 && !isLug(terminal))
                return InteractionResult.FAIL;
            return super.onWire(s, c);
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof BreakerLockItem)
            return onBlockEntityUseItemOn(level, pos, be -> be.lock(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL);
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand throws the handle; sneaking takes a lock off, or opens the splice editor when there is none. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(state.getValue(LOCKED)) {
                if(!level.isClientSide)
                    withBlockEntityDo(level, pos, be -> be.unlock(player));
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, be -> be.toggle(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<BusDuctPlugInBlockEntity> getBlockEntityClass() {
        return BusDuctPlugInBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BusDuctPlugInBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BUS_DUCT_PLUG_IN.get();
    }
}
