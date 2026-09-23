package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A split-phase or three-phase transformer. The cabinets (dry-type, pad-mount) are wired through
 * conduit knockouts and spliced in their editor like a panel; the pole can has bushings that
 * hanging wire lands on. The turns ratio is set on the value box on the front. The base block
 * carries the model and the wiring; the unit's other cells are {@link TransformerFillerBlock}s
 * placed with it and taken with it.
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

    /** A north-frame shape turned to face that way, as Power Grid turns the terminals. */
    public static VoxelShape rotateShape(VoxelShape north, Direction facing) {
        int turns = switch(facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        var shape = north;
        for(int i = 0; i < turns; ++i) {
            VoxelShape[] out = {Shapes.empty()};
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    out[0] = Shapes.joinUnoptimized(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2), BooleanOp.OR));
            shape = out[0].optimize();
        }
        return shape;
    }

    // ---- placement with fillers ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Against a wall or pole the back goes on it; on a floor or ceiling it faces the player.
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        for(var part : TransformerGeometry.parts(mount, kind)) {
            var pos = TransformerFillerBlock.partPos(ctx.getClickedPos(), facing, part);
            if(!ctx.getLevel().getBlockState(pos).canBeReplaced(ctx))
                return null;
        }
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(level.isClientSide)
            return;
        var facing = facing(state);
        for(var part : TransformerGeometry.parts(mount, kind)) {
            var filler = ModBlocks.TRANSFORMER_FILLER.getDefaultState()
                    .setValue(TransformerFillerBlock.FACING, facing)
                    .setValue(TransformerFillerBlock.PART, part);
            level.setBlock(TransformerFillerBlock.partPos(pos, facing, part), filler, Block.UPDATE_ALL);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock())) {
            var facing = facing(state);
            for(var part : TransformerGeometry.parts(mount, kind)) {
                var at = TransformerFillerBlock.partPos(pos, facing, part);
                if(level.getBlockState(at).getBlock() instanceof TransformerFillerBlock)
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
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
