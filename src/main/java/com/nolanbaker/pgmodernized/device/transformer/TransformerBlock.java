package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A split-phase or three-phase transformer in one block. The cabinets (dry-type, pad-mount) are
 * wired through conduit knockouts and spliced in their editor like a panel; the pole can has
 * bushings that hanging wire lands on. The turns ratio is set on the value box on the front.
 */
public class TransformerBlock extends HorizontalElectricBlock implements IBE<TransformerBlockEntity> {
    private final TransformerKind kind;
    private final TransformerMount mount;
    private final DeviceHubs.Layout layout;

    public TransformerBlock(Properties properties, TransformerKind kind, TransformerMount mount) {
        super(properties);
        this.kind = kind;
        this.mount = mount;
        var base = TransformerGeometry.terminals(mount, kind);
        var hubs = TransformerGeometry.hubs(mount);
        var points = new int[base.length];
        for(int i = 0; i < points.length; ++i)
            points[i] = i;
        layout = new DeviceHubs.Layout(base.length, hubs.length, points);
        var terminals = hubs.length > 0 ? DeviceHubs.withHubs(base, TransformerGeometry.hidden(mount), hubs) : base;
        setTerminalCollection(horizontalNorthTerminals(this, terminals, DeviceHubs.withHubs(TransformerGeometry.shape(mount, kind), hubs)));
    }

    public static String id(TransformerMount mount, TransformerKind kind) {
        return "transformer_" + mount.id() + "_" + kind.id();
    }

    public TransformerKind kind() {
        return kind;
    }

    public TransformerMount mount() {
        return mount;
    }

    public DeviceHubs.Layout layout() {
        return layout;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall or pole the back goes on it; on a floor or ceiling it faces the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** Direction the front faces. */
    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        if(!mount.hasHubs())
            return super.onWire(state, context);
        return DeviceHubs.onWire(this, layout, state, context, (s, c) -> {
            if(c.getClickedFace() == facing(s) && WireAcceptance.electrical(c.getItemInHand())) {
                IElectric.sendMessage(c, Lang.builder().translate("message.transformer.conduit_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand on a cabinet opens the splice editor; the value box on the front is handled by Create before this. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(!mount.hasHubs() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<TransformerBlockEntity> getBlockEntityClass() {
        return TransformerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransformerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.TRANSFORMER.get();
    }
}
