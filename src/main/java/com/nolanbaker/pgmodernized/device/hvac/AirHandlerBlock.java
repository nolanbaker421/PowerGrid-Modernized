package com.nolanbaker.pgmodernized.device.hvac;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The indoor unit: blower, evaporator coil and an electric heat strip in a sheet-metal cabinet,
 * with the 24 V control transformer every thermostat circuit runs off. Points: L1 and L2, the
 * 240 V feed, and the thermostat terminals R, C, Y, W and G. The thermostat's four wires land on
 * R, Y, W and G; a two-wire run to the condenser lands on Y and C, which is why a point takes
 * more than one splice. Three knockouts on one side (power, thermostat, condenser control), two
 * line ports on the other, at the same heights as the condenser's.
 * <p>
 * North frame, pixels, the front facing north. Mirrored by {@code tools/gen_hvac_assets.py}.
 */
public class AirHandlerBlock extends HorizontalElectricBlock implements IBE<AirHandlerBlockEntity> {
    public static final int L1 = 0, L2 = 1, R = 2, C = 3, Y = 4, W = 5, G = 6, BASE_COUNT = 7;
    public static final int HUB_POWER = 0, HUB_THERMOSTAT = 1, HUB_CONDENSER = 2, HUB_LIQUID = 3, HUB_SUCTION = 4;
    public static final AABB[] HUBS = {
            new AABB(1, 3, 6, 2, 5, 8), new AABB(1, 8, 6, 2, 10, 8), new AABB(1, 12, 6, 2, 14, 8),
            new AABB(14, 3, 5, 15, 5, 7), new AABB(14, 3, 9, 15, 6, 12)};
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);
    private static final VoxelShape BODY = box(2, 0, 2, 14, 16, 14);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, new int[] {L1, L2, R, C, Y, W, G});

    public AirHandlerBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(BODY, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                CondenserBlock.terminal("hvac.l1", HIDDEN).withColor(0x202020),
                CondenserBlock.terminal("hvac.l2", HIDDEN).withColor(0xC62828),
                CondenserBlock.terminal("hvac.r", HIDDEN).withColor(0xC62828),
                CondenserBlock.terminal("hvac.c", HIDDEN).withColor(0x2F6FD6),
                CondenserBlock.terminal("hvac.y", HIDDEN).withColor(0xE0C020),
                CondenserBlock.terminal("hvac.w", HIDDEN).withColor(0xF0F0F0),
                CondenserBlock.terminal("hvac.g", HIDDEN).withColor(0x2E8B3A)};
    }

    /** On the floor, its front to the player; sneak to turn it round. */
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
    public Class<AirHandlerBlockEntity> getBlockEntityClass() {
        return AirHandlerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirHandlerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.AIR_HANDLER.get();
    }
}
