package com.nolanbaker.pgmodernized.device.hvac;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The outdoor unit of a split air conditioner: compressor and condenser fan in a louvred box with
 * a fan grille on top. Its points are L1 and L2, the 240 V feed, and Y and C, the 24 V call from
 * the air handler that pulls the contactor in. Two knockouts on one side take the power and the
 * control conduit; two ports on the other side take the liquid and suction lines to the air
 * handler. Nothing cools until both lines are laid.
 * <p>
 * North frame, pixels: the front faces north, +x on the viewer's left, so the line ports on the
 * +x side are on their left and the knockouts on their right. Mirrored by
 * {@code tools/gen_hvac_assets.py}.
 */
public class CondenserBlock extends HorizontalElectricBlock implements IBE<CondenserBlockEntity> {
    public static final int L1 = 0, L2 = 1, Y = 2, C = 3, BASE_COUNT = 4;
    public static final int HUB_POWER = 0, HUB_CONTROL = 1, HUB_LIQUID = 2, HUB_SUCTION = 3;
    /** Knockouts on the −x side, line ports on the +x side, each thin on its own axis so a run leaves it square. */
    public static final AABB[] HUBS = {
            new AABB(0, 3, 6, 1, 5, 8), new AABB(0, 8, 6, 1, 10, 8),
            new AABB(15, 3, 5, 16, 5, 7), new AABB(15, 3, 9, 16, 6, 12)};
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);
    private static final VoxelShape BODY = box(1, 0, 1, 15, 13, 15);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, new int[] {L1, L2, Y, C});

    public CondenserBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(BODY, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("hvac.l1", HIDDEN).withColor(0x202020),
                terminal("hvac.l2", HIDDEN).withColor(0xC62828),
                terminal("hvac.y", HIDDEN).withColor(0xE0C020),
                terminal("hvac.c", HIDDEN).withColor(0x2F6FD6)};
    }

    static TerminalBoundingBox terminal(String key, AABB px) {
        Component name = Lang.builder().translate(key).component();
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /** On the ground, its front to the player; sneak to turn it round. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** Conduit on the knockouts, lines on the ports, nothing else lands anywhere. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            IElectric.sendMessage(c, Lang.builder().translate("message.hvac.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        });
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
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<CondenserBlockEntity> getBlockEntityClass() {
        return CondenserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CondenserBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONDENSER.get();
    }
}
