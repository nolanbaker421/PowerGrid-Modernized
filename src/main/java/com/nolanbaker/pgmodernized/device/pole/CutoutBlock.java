package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.StateTerminals;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A fused cutout, one to three poles: the drop-out fuse holders on a pole or a crossarm. Each
 * pole is an insulator with a line lug on top, a load lug at the foot and a fuse tube hinged
 * between them that stands closed or hangs open. Hanging wire lands on the lugs; nothing else
 * does. One item makes a one-pole cutout and another of the same class clicked on it adds a pole,
 * up to three. The class, 1 kV, 15 kV or 38 kV, is the holder's; any fuse link fits.
 * <p>
 * North frame, pixels: the mounting plate against the pole at z = 16, the tubes to the front.
 * Standing on a crossarm the same plate sits on the arm. Mirrored by {@code tools/gen_pole_assets.py}.
 */
public class CutoutBlock extends HorizontalElectricBlock implements IBE<CutoutBlockEntity>, IHotStickTarget {
    public static final IntegerProperty POLES = IntegerProperty.create("poles", 1, 3);
    public static final int MAX_POLES = 3, LINE_BASE = 0, LOAD_BASE = 3, TERMINAL_COUNT = 6;

    private final CutoutClass cutoutClass;

    public CutoutBlock(Properties properties, CutoutClass cutoutClass) {
        super(properties);
        this.cutoutClass = cutoutClass;
        registerDefaultState(defaultBlockState().setValue(POLES, 1));
        setTerminalCollection(StateTerminals.horizontalNorth(this, state -> shape(cutoutClass, state.getValue(POLES)),
                state -> StateTerminals.present(terminals(cutoutClass, state.getValue(POLES)), index -> index % MAX_POLES < state.getValue(POLES))));
    }

    public CutoutClass cutoutClass() {
        return cutoutClass;
    }

    // ---- geometry, north frame ----

    /** The x of each pole's centre line, the viewer's left first, for that many poles. */
    public static double[] centres(int poles) {
        return switch(poles) {
            case 1 -> new double[] {8};
            case 2 -> new double[] {11, 5};
            default -> new double[] {13, 8, 3};
        };
    }

    public static AABB lineLug(CutoutClass c, double x) {
        return new AABB(x - 0.75, c.top(), 8.5, x + 0.75, c.top() + 1.5, 10.5);
    }

    public static AABB loadLug(double x) {
        return new AABB(x - 0.75, 1, 4, x + 0.75, 2.5, 6);
    }

    public static AABB insulator(CutoutClass c, double x) {
        return new AABB(x - 1, 1, 9, x + 1, c.top(), 11);
    }

    /** The fuse tube as it stands closed; the renderer swings it on its hinge. */
    public static AABB tube(CutoutClass c, double x) {
        return new AABB(x - 0.75, 2.5, 4.5, x + 0.75, c.top() - 0.5, 6);
    }

    /** The steel bracket under one pole: a rail from the load lug back to the pole, and a strap up the pole behind it. */
    public static AABB rail(double x) {
        return new AABB(x - 1.5, 0, 3, x + 1.5, 1, 16);
    }

    public static AABB strap(double x) {
        return new AABB(x - 1.5, 1, 15, x + 1.5, 6, 16);
    }

    private static VoxelShape shape(CutoutClass c, int poles) {
        var shape = Shapes.empty();
        for(double x : centres(poles))
            shape = Shapes.or(shape, box(rail(x)), box(strap(x)), box(insulator(c, x)), box(lineLug(c, x)), box(loadLug(x)), box(tube(c, x)));
        return shape;
    }

    private static TerminalBoundingBox[] terminals(CutoutClass c, int poles) {
        var out = new TerminalBoundingBox[TERMINAL_COUNT];
        var xs = centres(poles);
        for(int k = 0; k < MAX_POLES; ++k) {
            double x = xs[Math.min(k, xs.length - 1)];
            out[LINE_BASE + k] = terminal(ConductorColors.hotLabel("cutout.line", k, k + 1), lineLug(c, x)).withColor(ConductorColors.hotRgb(k));
            out[LOAD_BASE + k] = terminal(ConductorColors.hotLabel("cutout.load", k, k + 1), loadLug(x)).withColor(ConductorColors.hotRgb(k));
        }
        return out;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static boolean isLug(int terminal) {
        return terminal >= 0 && terminal < TERMINAL_COUNT;
    }

    /** The pole under a block-local point, or -1 off the cutout. */
    public static int poleAt(BlockState state, Vec3 local) {
        var north = PanelLayout.toNorthFrame(local.scale(16), facing(state));
        var xs = centres(state.getValue(POLES));
        int best = -1;
        double bestDistance = 3.5;
        for(int k = 0; k < xs.length; ++k) {
            double d = Math.abs(north.x - xs[k]);
            if(d < bestDistance) {
                bestDistance = d;
                best = k;
            }
        }
        return best;
    }

    // ---- state ----

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POLES);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    /** On a pole's side the plate goes on the pole; on a crossarm or any top, it stands with the tubes to the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(POLES, 1);
    }

    // ---- wire ----

    /** Hanging wire on the lugs; overhead gear takes no conduit. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        if(ConduitItem.isConduit(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.cutout.hanging_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(terminal >= 0 && !isLug(terminal))
            return InteractionResult.FAIL;
        return super.onWire(state, context);
    }

    // ---- hands, links and the stick ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int pole = poleAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(stack.getItem() instanceof FuseLinkItem) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof CutoutBlockEntity be)
                be.loadLink(pole < 0 ? 0 : pole, stack, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.getItem() instanceof HotStickItem) {
            if(!level.isClientSide)
                stick(state, level, pos, player, hit);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.getItem() == asItem()) {
            // Another holder of the class clicked on: a pole more, up to three.
            if(!level.isClientSide) {
                int poles = state.getValue(POLES);
                if(poles >= MAX_POLES) {
                    player.displayClientMessage(Lang.translate("message.cutout.full").style(ChatFormatting.RED).component(), true);
                } else {
                    level.setBlock(pos, state.setValue(POLES, poles + 1), Block.UPDATE_ALL);
                    if(!player.isCreative())
                        stack.shrink(1);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.4f);
                    player.displayClientMessage(Lang.builder().translate("message.cutout.pole_added", poles + 1).style(ChatFormatting.GRAY).component(), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** A hot stick, from the ground or up close: the pole under the hit is worked, with whatever is in the other hand. */
    @Override
    public void stick(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int pole = poleAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(level.getBlockEntity(pos) instanceof CutoutBlockEntity be)
            be.stick(pole < 0 ? 0 : pole, player, player.getOffhandItem());
    }

    /**
     * Bare hand on a pole closes or opens its tube, by hand, which on a live line is a shock.
     * Sneaking takes the link out of the pole, or with no link in it, the last pole off a
     * holder of several.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof CutoutBlockEntity be))
            return InteractionResult.PASS;
        int pole = Math.max(0, poleAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())));
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()) {
            if(be.hasLink(pole)) {
                be.pullLink(pole, player);
                return InteractionResult.SUCCESS;
            }
            int poles = state.getValue(POLES);
            if(poles > 1) {
                int last = poles - 1;
                be.pullLink(last, player);
                level.setBlock(pos, state.setValue(POLES, last), Block.UPDATE_ALL);
                be.poleRemoved(last);
                if(!player.isCreative())
                    player.getInventory().placeItemBackInInventory(new ItemStack(asItem()));
                player.displayClientMessage(Lang.builder().translate("message.cutout.pole_removed", last).style(ChatFormatting.GRAY).component(), true);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Lang.translate("message.cutout.no_link").style(ChatFormatting.RED).component(), true);
            return InteractionResult.SUCCESS;
        }
        be.toggle(pole, player, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof CutoutBlockEntity be)
            be.dropLinks();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<CutoutBlockEntity> getBlockEntityClass() {
        return CutoutBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CutoutBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CUTOUT.get();
    }
}
