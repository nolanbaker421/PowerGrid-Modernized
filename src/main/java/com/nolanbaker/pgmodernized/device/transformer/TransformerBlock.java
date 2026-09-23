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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One nameplate of transformer. Pole cans stand on the ground or hang on a pole, and take hanging
 * wire on their bushings and studs; tanks stand on a skid with bushings on the lid; the dry-type
 * cabinet is wired through knockouts and spliced in its editor. The base block carries the model
 * and the wiring; the unit's other cells are {@link TransformerFillerBlock}s placed with it and
 * taken with it. Taps on the two value boxes on the front move each winding ten percent either way.
 */
public class TransformerBlock extends HorizontalElectricBlock implements IBE<TransformerBlockEntity> {
    /** A pole can hung on the pole behind it rather than standing on the ground. */
    public static final BooleanProperty HUNG = BooleanProperty.create("hung");

    private final TransformerSpec spec;
    private final DeviceHubs.Layout layout;

    public TransformerBlock(Properties properties, TransformerSpec spec) {
        super(properties);
        this.spec = spec;
        registerDefaultState(defaultBlockState().setValue(HUNG, false));
        var size = spec.size();
        var hubs = TransformerGeometry.hubs(size);
        var points = new int[spec.kind().pointCount()];
        for(int i = 0; i < points.length; ++i)
            points[i] = i;
        layout = new DeviceHubs.Layout(points.length, hubs.length, points);
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> {
                    var base = TransformerGeometry.terminals(spec, hung(state));
                    var all = hubs.length > 0 ? DeviceHubs.withHubs(base, TransformerGeometry.hidden(), hubs) : base;
                    return BlockStateTerminalCollection.each(all, terminal -> switch(state.getValue(HORIZONTAL_FACING)) {
                        case SOUTH -> terminal.rotateAroundY(180);
                        case EAST -> terminal.rotateAroundY(90);
                        case WEST -> terminal.rotateAroundY(-90);
                        default -> terminal;
                    });
                })
                .withShapeMapper(state -> TransformerGeometry.rotate(
                        TransformerGeometry.shape(size, spec.kind(), hung(state)), facing(state)))
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HUNG);
    }

    public TransformerSpec spec() {
        return spec;
    }

    public DeviceHubs.Layout layout() {
        return layout;
    }

    /** Direction the front faces. */
    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    public static boolean hung(BlockState state) {
        return state.hasProperty(HUNG) && state.getValue(HUNG);
    }

    // ---- placement with fillers ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        boolean onWall = face.getAxis().isHorizontal();
        boolean hung = spec.size().isPole() && onWall;
        // Hung: the can hangs on the pole behind it and faces away. Standing, or a tank against a
        // wall: the front faces the player, or the wall's outward side.
        var facing = hung || onWall ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() && !hung)
            facing = facing.getOpposite();
        for(var cell : TransformerGeometry.cells(spec.size(), spec.kind(), hung)) {
            var pos = ctx.getClickedPos().offset(TransformerGeometry.rotateCell(cell, facing));
            if(!ctx.getLevel().getBlockState(pos).canBeReplaced(ctx))
                return null;
        }
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(HUNG, hung);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(level.isClientSide)
            return;
        var facing = facing(state);
        for(var cell : TransformerGeometry.cells(spec.size(), spec.kind(), hung(state))) {
            var offset = TransformerGeometry.rotateCell(cell, facing);
            var filler = TransformerFillerBlock.forOffset(ModBlocks.TRANSFORMER_FILLER.getDefaultState(), offset);
            level.setBlock(pos.offset(offset), filler, Block.UPDATE_ALL);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock())) {
            var facing = facing(state);
            for(var cell : TransformerGeometry.cells(spec.size(), spec.kind(), hung(state))) {
                var at = pos.offset(TransformerGeometry.rotateCell(cell, facing));
                if(level.getBlockState(at).getBlock() instanceof TransformerFillerBlock)
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        if(!spec.size().hasHubs())
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

    /** Empty hand on a cabinet opens the splice editor; the tap boxes on the front are handled by Create before this. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(!spec.size().hasHubs() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
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
