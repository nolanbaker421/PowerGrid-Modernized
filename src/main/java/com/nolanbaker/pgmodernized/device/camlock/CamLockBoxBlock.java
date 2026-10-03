package com.nolanbaker.pgmodernized.device.camlock;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.Rotation4Shapes;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A cam-lock connector box: five receptacles, L1, L2, L3, N and G, that take ordinary wire, and
 * two conduit knockouts whose conductors splice onto the same five poles, so a portable generator
 * set plugs into a building's conduit with five cables. Each pole passes straight through a
 * contact rated for the box's current; pull much more than that through a pole and the box
 * overheats like any Power Grid device. The 100 A and 400 A boxes share this class.
 */
public class CamLockBoxBlock extends Rotation4ElectricBlock implements IBE<CamLockBoxBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3, G = 4;
    /** The hidden inner end of each pole, where the knockout conductors splice on. */
    public static final int INNER = 5;
    public static final AABB[] HUBS = {new AABB(0, 1, 5, 1, 4, 8), new AABB(15, 1, 5, 16, 4, 8)};
    private static final AABB HIDDEN = new AABB(7, 0.5, 11, 9, 1.5, 13);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(10, HUBS.length, new int[] {INNER, INNER + 1, INNER + 2, INNER + 3, INNER + 4});

    private static final AABB BODY = new AABB(1, 0, 1, 15, 8, 15);
    private static final AABB[] RECEPTACLES = {
            new AABB(1.5, 8, 5, 3.5, 10, 7), new AABB(4, 8, 5, 6, 10, 7), new AABB(6.5, 8, 5, 8.5, 10, 7),
            new AABB(9, 8, 5, 11, 10, 7), new AABB(11.5, 8, 5, 13.5, 10, 7),
    };

    private final int ratedAmps;

    public CamLockBoxBlock(Properties properties, int ratedAmps) {
        super(properties);
        this.ratedAmps = ratedAmps;
        setTerminalCollection(rotation4DownTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    public int ratedAmps() {
        return ratedAmps;
    }

    private static VoxelShape shape() {
        var shape = box(BODY);
        for(var r : RECEPTACLES)
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(r));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static TerminalBoundingBox[] terminals() {
        var names = new String[] {"cam_lock.l1", "cam_lock.l2", "cam_lock.l3", "cam_lock.n", "cam_lock.g"};
        var styles = new ChatFormatting[] {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.BLUE, ChatFormatting.WHITE, ChatFormatting.GREEN};
        var colours = new int[] {0xD03030, 0xE0A020, 0x3060D0, 0xE8E8E8, 0x30B040};
        var all = new TerminalBoundingBox[10];
        for(int k = 0; k < 5; ++k) {
            var r = RECEPTACLES[k];
            all[k] = new TerminalBoundingBox(name(names[k], styles[k]), r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ).withColor(colours[k]);
            // The inner end: a speck deep inside the body no click can reach; the splice editor names it like its pole.
            double x = 3 + 2 * k;
            all[INNER + k] = new TerminalBoundingBox(name(names[k], styles[k]), x, 3, 9, x + 0.5, 3.5, 9.5).withColor(colours[k]);
        }
        return all;
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    /** The receptacles take any real conductor, THHN included. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var boxes = new AABB[1 + RECEPTACLES.length + HUBS.length];
        boxes[0] = BODY;
        System.arraycopy(RECEPTACLES, 0, boxes, 1, RECEPTACLES.length);
        System.arraycopy(HUBS, 0, boxes, 1 + RECEPTACLES.length, HUBS.length);
        return Rotation4Shapes.of(state, boxes);
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
    public Class<CamLockBoxBlockEntity> getBlockEntityClass() {
        return CamLockBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CamLockBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CAM_LOCK_BOX.get();
    }
}
