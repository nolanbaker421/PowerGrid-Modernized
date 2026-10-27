package com.nolanbaker.pgmodernized.conduit;

import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ISocketElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.powercord.SocketEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A small wall box that ends conduit runs. Up to twelve runs land on its edge hubs and are spliced
 * to each other in the editor. Placed open; a blank cover plate closes it, a node plate closes it
 * with twelve cover terminals that ordinary wires land on and that the editor offers as points.
 */
public class ConduitBoxBlock extends DirectionalElectricBlock implements IBE<ConduitBoxBlockEntity>, ISocketElectric {
    public static final EnumProperty<ConduitCover> COVER = EnumProperty.create("cover", ConduitCover.class);
    /** Where a cord would go on a box that has no receptacle: nowhere. */
    private static final ITerminalPlacement NO_SOCKET = new ITerminalPlacement() {
        @Override
        public boolean check(Vec3 pos) {
            return false;
        }

        @Override
        public Vec3 getOrigin() {
            return Vec3.ZERO;
        }
    };

    public ConduitBoxBlock(Properties properties) {
        super(properties);
        // Boxes saved before cover plates existed keep their terminals: the default is the node plate.
        registerDefaultState(defaultBlockState().setValue(COVER, ConduitCover.NODE));
        var shapes = new java.util.EnumMap<ConduitCover, VoxelShaper>(ConduitCover.class);
        for(var cover : ConduitCover.values()) {
            var shape = ConduitBoxGeometry.shape(cover, java.util.function.UnaryOperator.identity());
            shapes.put(cover, VoxelShaper.forDirectional(shape, Direction.NORTH).withVerticalShapes(ShapeRotation.northToUp(shape)));
        }
        var byCover = new java.util.EnumMap<ConduitCover, TerminalBoundingBox[]>(ConduitCover.class);
        for(var cover : ConduitCover.values())
            byCover.put(cover, ConduitBoxGeometry.terminals(java.util.function.UnaryOperator.identity(), cover));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> BlockStateTerminalCollection.each(byCover.get(cover(state)), terminal -> switch(front(state)) {
                    case NORTH -> terminal;
                    case SOUTH -> terminal.rotateAroundY(180);
                    case EAST -> terminal.rotateAroundY(90);
                    case WEST -> terminal.rotateAroundY(-90);
                    case UP -> terminal.rotateAroundX(-90);
                    case DOWN -> terminal.rotateAroundX(90);
                }))
                .withShapeMapper(state -> shapes.get(cover(state)).get(front(state)))
                .build());
    }

    /** The receptacle's face, when the cover has one; a cord plugged there lands on cover terminals 0 and 1. */
    @Override
    public ITerminalPlacement socket(BlockState state) {
        if(cover(state) != ConduitCover.RECEPTACLE)
            return NO_SOCKET;
        var placement = super.terminal(state, ConduitBoxGeometry.TERMINAL_RECEPTACLE);
        if(placement == null)
            return NO_SOCKET;
        // Power Grid draws the cord's end three pixels against the block's facing and the plug along
        // it, which puts the plug in the receptacle when the facing points into the mount, as this
        // box's does. The face centre comes from the terminal's own rotated shape.
        var origin = placement instanceof TerminalBoundingBox box ? box.getShape().bounds().getCenter() : placement.getOrigin();
        return new ITerminalPlacement() {
            @Override
            public boolean check(Vec3 pos) {
                return placement.check(pos);
            }

            @Override
            public Vec3 getOrigin() {
                return origin;
            }
        };
    }

    /**
     * The way the front faces: the facing points into the mount, as Power Grid's sockets do, so
     * the cord's plug draws into the receptacle. The box's frame has the front to the north.
     */
    public static Direction front(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** The receptacle is never a wire terminal: Power Grid's cord looks for a clicked terminal before it looks for a socket. */
    @Override
    public int terminalIndexAt(BlockState state, Vec3 pos) {
        int terminal = super.terminalIndexAt(state, pos);
        if(terminal == ConduitBoxGeometry.TERMINAL_RECEPTACLE)
            return -1;
        if(terminal >= 0)
            return terminal;
        // The cover nubs are 1.2 px across; a click within a pixel of one counts, nearest first.
        int best = -1;
        double bestDistance = 1.0 / 16;
        for(int k = 0; k < ConduitBoxGeometry.FRONT_COUNT; ++k) {
            if(!(terminal(state, k) instanceof TerminalBoundingBox nub))
                continue;
            double d = pos.distanceTo(nub.getOutline().getCenter());
            if(d < bestDistance) {
                best = k;
                bestDistance = d;
            }
        }
        return best;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COVER);
    }

    public static ConduitCover cover(BlockState state) {
        return state.getValue(COVER);
    }

    /** A lit light socket lights the room; a strobe going lights it some. */
    public static int lightOf(BlockState state) {
        if(!state.hasProperty(COVER))
            return 0;
        var cover = state.getValue(COVER);
        return cover == ConduitCover.LIGHT_ON ? 15 : cover == ConduitCover.HORN_ON ? 10 : 0;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Mounted on the clicked face, so the facing is into it; sneaking mounts it the other way round.
        var facing = ctx.getClickedFace().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(COVER, ConduitCover.OPEN);
    }

    /** The cover terminals only exist on a plate that exposes them: all twelve on a node plate, two on a light socket or a receptacle. */
    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        if(ConduitBoxGeometry.isFront(index) && !cover(state).exposes(index))
            return null;
        if(index == ConduitBoxGeometry.TERMINAL_RECEPTACLE && cover(state) != ConduitCover.RECEPTACLE)
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
            // A pending run routes across the box like any block; only landing needs a hub.
            var routed = ConduitPlacement.groundClick(context);
            if(routed != InteractionResult.PASS)
                return routed;
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
        if(stack.getItem() instanceof ILightBulb && cover(state).isLight()) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof ConduitBoxBlockEntity be && be.fitBulb(player, stack))
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.getItem() instanceof ConduitCoverItem plate) {
            if(cover(state).isPlate()) {
                if(!level.isClientSide)
                    player.displayClientMessage(Lang.builder().translate("message.conduit_box.has_plate").style(ChatFormatting.RED).component(), true);
                return ItemInteractionResult.FAIL;
            }
            if(!level.isClientSide) {
                level.setBlock(pos, state.setValue(COVER, plate.cover().itemCover()), Block.UPDATE_ALL);
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
            // A light socket gives up its bulb first, then its plate.
            if(cover.isLight() && level.getBlockEntity(pos) instanceof ConduitBoxBlockEntity be && be.takeBulb(player)) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
                return InteractionResult.SUCCESS;
            }
            if(cover.hasTerminals() && level.getBlockEntity(pos) instanceof ConduitBoxBlockEntity be && be.hasCoverWires()) {
                player.displayClientMessage(Lang.builder().translate("message.conduit_box.wires_attached").style(ChatFormatting.RED).component(), true);
                return InteractionResult.FAIL;
            }
            if(cover == ConduitCover.RECEPTACLE && new SocketEndpoint(pos).hasConnection(level)) {
                player.displayClientMessage(Lang.builder().translate("message.conduit_box.cord_attached").style(ChatFormatting.RED).component(), true);
                return InteractionResult.FAIL;
            }
            level.setBlock(pos, state.setValue(COVER, ConduitCover.OPEN), Block.UPDATE_ALL);
            var plate = ModItems.coverPlate(cover);
            if(!player.getInventory().add(plate))
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, plate);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        // A pull station: a bare hand pulls it; a wrench resets it.
        if(cover(state).isPull() && hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof ConduitBoxBlockEntity be)
                be.pull(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // A cord in the receptacle comes out in the hand, as on Power Grid's own socket.
        if(cover(state) == ConduitCover.RECEPTACLE && hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            var endpoint = new SocketEndpoint(pos);
            var cord = endpoint.getConnection(level);
            if(cord != null) {
                if(cord.getEndpoint1().equals(endpoint))
                    return cord.cordDetach(player, false) ? InteractionResult.SUCCESS : InteractionResult.PASS;
                if(cord.getEndpoint2().equals(endpoint))
                    return cord.cordDetach(player, true) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    /** A wrench resets a pulled station; on anything else it does what it does to any box. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if(cover(state) == ConduitCover.PULL_PULLED) {
            var level = context.getLevel();
            if(!level.isClientSide && level.getBlockEntity(context.getClickedPos()) instanceof ConduitBoxBlockEntity be && context.getPlayer() != null)
                be.resetPull(context.getPlayer());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.onWrenched(state, context);
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
