package com.nolanbaker.pgmodernized.device.light;

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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A two-foot section of strip light: a white channel hung from the ceiling with one socket that
 * takes any Power Grid bulb, a knockout on its end cap for conduit, and a line and neutral behind
 * it. Two in a row along the same axis are a four-foot two-lamp fixture: they wire themselves
 * together through hidden links, so the pair is fed once. North frame: the channel runs
 * north-south under the ceiling, the knockout on the north end.
 */
public class StripLightBlock extends HorizontalElectricBlock implements IBE<StripLightBlockEntity> {
    public static final int L = 0, N = 1, BASE_COUNT = 2, HUB = 0;
    public static final AABB[] HUBS = {new AABB(7, 13.5, 0, 9, 15.5, 1)};
    private static final AABB HIDDEN = new AABB(7.99, 13.99, 7.99, 8.01, 14.01, 8.01);
    private static final VoxelShape BODY = Shapes.or(box(4, 12, 0, 12, 16, 16), box(5.5, 6, 5.5, 10.5, 12, 10.5));
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, new int[] {L, N});

    public StripLightBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(BODY, HUBS)));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("strip_light.l", HIDDEN).withColor(0x202020),
                terminal("strip_light.n", HIDDEN).withColor(0xE8E8E8)};
    }

    private static TerminalBoundingBox terminal(String key, AABB px) {
        Component name = Lang.builder().translate(key).component();
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    /** Under a ceiling, running the way the player looks, knockout at the far end; sneak to put it at the near end. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getHorizontalDirection();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        var state = defaultBlockState().setValue(HORIZONTAL_FACING, facing);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var above = pos.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if(!level.isClientSide && !state.canSurvive(level, pos))
            level.destroyBlock(pos, true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StripLightBlockEntity be) {
            be.dropBulb();
            be.removeGlow();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** Conduit on the knockout; nothing else lands on it. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            IElectric.sendMessage(c, Lang.builder().translate("message.strip_light.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        });
    }

    /** A bulb goes in the socket; anything else is left to the wire handler or the hand. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof ILightBulb) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof StripLightBlockEntity be)
                be.fitBulb(player, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand: sneaking takes the bulb out, otherwise the readings and splice editor open. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof StripLightBlockEntity be)
                be.takeBulb(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<StripLightBlockEntity> getBlockEntityClass() {
        return StripLightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StripLightBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STRIP_LIGHT.get();
    }
}
