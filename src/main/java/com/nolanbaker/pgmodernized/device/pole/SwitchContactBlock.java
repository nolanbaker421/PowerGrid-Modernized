package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
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
import org.patryk3211.powergrid.utility.Lang;

/**
 * The contact head of a pole-top switch: one pole's blade on a base, a line lug at the back
 * and a load lug at the front, standing on a stack of insulators over an operator at the foot
 * of the stack, which throws it. Three heads side by side on a crossarm over three operators in
 * a row make a gang-operated three-phase switch. A bare hand throws a head too, up the pole,
 * with the shock that goes with it; the hot stick throws it from the ground.
 * <p>
 * North frame, pixels: the front, the load end, faces north. Mirrored by {@code tools/gen_switch_assets.py}.
 */
public class SwitchContactBlock extends HorizontalElectricBlock implements IBE<SwitchContactBlockEntity>, IHotStickTarget {
    public static final int LINE = 0, LOAD = 1, TERMINAL_COUNT = 2;
    private static final AABB BASE = new AABB(4, 0, 2, 12, 2, 14);
    private static final AABB HINGE = new AABB(6.5, 2, 3, 9.5, 6, 6);
    private static final AABB POST = new AABB(6.5, 2, 10, 9.5, 9, 13);
    private static final AABB LINE_LUG = new AABB(7.25, 9, 11, 8.75, 10.5, 13);
    private static final AABB LOAD_LUG = new AABB(7.25, 2, 2.5, 8.75, 3.5, 4.5);

    public SwitchContactBlock(Properties properties) {
        super(properties);
        var shape = Shapes.or(box(BASE), box(HINGE), box(POST), box(LINE_LUG), box(LOAD_LUG));
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal(ConductorColors.hotLabel("switch_contact.line", 0), LINE_LUG).withColor(ConductorColors.hotRgb(0)),
                terminal(ConductorColors.hotLabel("switch_contact.load", 0), LOAD_LUG).withColor(0xE0A030)};
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    /** The load end faces the player; sneak to turn it round. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Hanging wire on the two lugs; no conduit. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        if(ConduitItem.isConduit(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.cutout.hanging_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof HotStickItem) {
            if(!level.isClientSide)
                stick(state, level, pos, player, hit);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void stick(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(level.getBlockEntity(pos) instanceof SwitchContactBlockEntity be)
            be.setClosed(!be.isClosed(), player, false);
    }

    /** A bare hand on the head throws it, up the pole and on the live parts. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof SwitchContactBlockEntity be)
            be.setClosed(!be.isClosed(), player, true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<SwitchContactBlockEntity> getBlockEntityClass() {
        return SwitchContactBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SwitchContactBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SWITCH_CONTACT.get();
    }
}
