package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.util.ShapeRotation;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A small wall box that ends conduit runs. Up to twelve runs land on its edge hubs and are spliced
 * to each other in the editor. Placed open; a blank cover plate closes it, a node plate closes it
 * with twelve cover terminals that ordinary wires land on and that the editor offers as points.
 */
public class ConduitBoxBlock extends DirectionalElectricBlock implements IBE<ConduitBoxBlockEntity> {
    public static final EnumProperty<ConduitCover> COVER = EnumProperty.create("cover", ConduitCover.class);

    public ConduitBoxBlock(Properties properties) {
        super(properties);
        // Boxes saved before cover plates existed keep their terminals: the default is the node plate.
        registerDefaultState(defaultBlockState().setValue(COVER, ConduitCover.NODE));
        var plain = VoxelShaper.forDirectional(ConduitBoxGeometry.shape(false), Direction.NORTH).withVerticalShapes(ShapeRotation.northToUp(ConduitBoxGeometry.shape(false)));
        var node = VoxelShaper.forDirectional(ConduitBoxGeometry.shape(true), Direction.NORTH).withVerticalShapes(ShapeRotation.northToUp(ConduitBoxGeometry.shape(true)));
        var terminals = ConduitBoxGeometry.terminals();
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> BlockStateTerminalCollection.each(terminals, terminal -> switch(state.getValue(FACING)) {
                    case NORTH -> terminal;
                    case SOUTH -> terminal.rotateAroundY(180);
                    case EAST -> terminal.rotateAroundY(90);
                    case WEST -> terminal.rotateAroundY(-90);
                    case UP -> terminal.rotateAroundX(-90);
                    case DOWN -> terminal.rotateAroundX(90);
                }))
                .withShapeMapper(state -> (cover(state).hasTerminals() ? node : plain).get(state.getValue(FACING)))
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COVER);
    }

    public static ConduitCover cover(BlockState state) {
        return state.getValue(COVER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getClickedFace();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(COVER, ConduitCover.OPEN);
    }

    /** The cover terminals only exist under a node plate. */
    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        if(ConduitBoxGeometry.isFront(index) && !cover(state).hasTerminals())
            return null;
        return super.terminal(state, index);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(ConduitBoxGeometry.isHub(terminal)) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            if(terminal >= 0) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if(ConduitBoxGeometry.isConductor(terminal))
            return InteractionResult.FAIL;
        if(!cover(state).hasTerminals() && WireAcceptance.electrical(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit_box.needs_node_plate").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return super.onWire(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof ConduitCoverItem plate) {
            if(cover(state).isPlate()) {
                if(!level.isClientSide)
                    player.displayClientMessage(Lang.builder().translate("message.conduit_box.has_plate").style(ChatFormatting.RED).component(), true);
                return ItemInteractionResult.FAIL;
            }
            if(!level.isClientSide) {
                level.setBlock(pos, state.setValue(COVER, plate.cover()), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
                if(!player.isCreative())
                    stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand opens the editor; sneaking with an empty hand takes the cover plate off. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(player.isShiftKeyDown()) {
            var cover = cover(state);
            if(!cover.isPlate())
                return InteractionResult.PASS;
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            if(cover.hasTerminals() && level.getBlockEntity(pos) instanceof ConduitBoxBlockEntity be && be.hasCoverWires()) {
                player.displayClientMessage(Lang.builder().translate("message.conduit_box.wires_attached").style(ChatFormatting.RED).component(), true);
                return InteractionResult.FAIL;
            }
            level.setBlock(pos, state.setValue(COVER, ConduitCover.OPEN), Block.UPDATE_ALL);
            var plate = cover == ConduitCover.NODE ? ModItems.CONDUIT_COVER_NODE.asStack() : ModItems.CONDUIT_COVER_BLANK.asStack();
            if(!player.getInventory().add(plate))
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, plate);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<ConduitBoxBlockEntity> getBlockEntityClass() {
        return ConduitBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConduitBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONDUIT_BOX.get();
    }
}
