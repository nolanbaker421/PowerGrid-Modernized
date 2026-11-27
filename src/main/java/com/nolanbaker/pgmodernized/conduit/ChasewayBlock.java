package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A chaseway: a solid block with sleeves through it, so conduit can land on one side of a wall,
 * floor or ceiling and carry on from the other without the block being gone. The block keeps
 * the look, sound and drop of the block it went through, which the block entity remembers.
 * <p>
 * Power Grid fixes a block's terminals per block state, so a sleeve "anywhere" is one of a grid
 * of slots: three by three on each pair of opposite faces, 27 slots, each a four-pixel plate
 * on both of its faces. A block holds any number of them. Each plate is a knockout that takes
 * conduit up to 2"; the wires in the run on one end of a sleeve come out of the other in the
 * same order, slot for slot, with nothing to splice. The Chaseway item puts a sleeve at the
 * slot nearest where it is clicked.
 */
public class ChasewayBlock extends ElectricBlock implements IBE<ChasewayBlockEntity> {
    public static final int GRID = 3, SLOTS_PER_AXIS = GRID * GRID, SLOT_COUNT = 3 * SLOTS_PER_AXIS, HUB_COUNT = SLOT_COUNT * 2;
    /** Plate centres along a face, in pixels, and the plate's half width; mirrored by {@code tools/gen_chaseway_assets.py}. */
    public static final double[] CENTRES = {3, 8, 13};
    public static final double HALF = 2, PLATE_DEPTH = 0.5;
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(0, HUB_COUNT, new int[0]);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 7.99, 8.01, 8.01, 8.01);

    public ChasewayBlock(Properties properties) {
        super(properties);
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> DeviceHubs.withHubs(new TerminalBoundingBox[0], HIDDEN, hubs()))
                .withShapeMapper(state -> Shapes.block())
                .build());
    }

    // ---- slots and hubs ----

    /** Slot s: axis s / 9, then its row and column over the two in-plane axes in x, y, z order. */
    public static Direction.Axis axisOf(int slot) {
        return Direction.Axis.VALUES[slot / SLOTS_PER_AXIS];
    }

    public static int slot(Direction.Axis axis, int a, int b) {
        return axis.ordinal() * SLOTS_PER_AXIS + a * GRID + b;
    }

    /** Hub 2s is the positive face of slot s, hub 2s+1 the negative. */
    public static int hub(int slot, Direction face) {
        return slot * 2 + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1);
    }

    public static int slotOfHub(int hub) {
        return hub / 2;
    }

    public static Direction faceOfHub(int hub) {
        return Direction.get(hub % 2 == 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axisOf(hub / 2));
    }

    /** The plate of one hub, in pixels. */
    public static AABB hub(int hub) {
        int slot = hub / 2;
        var axis = axisOf(slot);
        double ca = CENTRES[(slot % SLOTS_PER_AXIS) / GRID], cb = CENTRES[slot % GRID];
        boolean positive = hub % 2 == 0;
        double n0 = positive ? 16 - PLATE_DEPTH : 0, n1 = positive ? 16 : PLATE_DEPTH;
        return switch(axis) {
            case X -> new AABB(n0, ca - HALF, cb - HALF, n1, ca + HALF, cb + HALF);   // a = y, b = z
            case Y -> new AABB(ca - HALF, n0, cb - HALF, ca + HALF, n1, cb + HALF);   // a = x, b = z
            case Z -> new AABB(ca - HALF, cb - HALF, n0, ca + HALF, cb + HALF, n1);   // a = x, b = y
        };
    }

    public static AABB[] hubs() {
        var out = new AABB[HUB_COUNT];
        for(int i = 0; i < HUB_COUNT; ++i)
            out[i] = hub(i);
        return out;
    }

    /** The slot nearest a point on a face: the face's axis, and the grid cell the point's two other coordinates fall in. */
    public static int slotAt(Direction face, Vec3 local) {
        return switch(face.getAxis()) {
            case X -> slot(Direction.Axis.X, cell(local.y), cell(local.z));
            case Y -> slot(Direction.Axis.Y, cell(local.x), cell(local.z));
            case Z -> slot(Direction.Axis.Z, cell(local.x), cell(local.y));
        };
    }

    private static int cell(double t) {
        return Mth.clamp((int) (t * GRID), 0, GRID - 1);
    }

    /** The block this chaseway went through, or null before it is known. */
    @Nullable
    public static BlockState material(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChasewayBlockEntity be ? be.material() : null;
    }

    // ---- wearing the block it went through ----

    /** Neighbours with connected textures see the block that was here. */
    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        var material = material(level, pos);
        return material != null ? material : state;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        var material = material(level, pos);
        return material != null ? material.getSoundType(level, pos, entity) : super.getSoundType(state, level, pos, entity);
    }

    /** Broken, it gives the block back and every sleeve but the one the loot table drops. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if(!level.isClientSide && !player.isCreative() && level.getBlockEntity(pos) instanceof ChasewayBlockEntity be) {
            if(be.material() != null)
                popResource(level, pos, new ItemStack(be.material().getBlock()));
            if(be.sleeveCount() > 1)
                popResource(level, pos, new ItemStack(ModItems.CHASEWAY.get(), be.sleeveCount() - 1));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModItems.CHASEWAY.get());
    }

    // ---- the sleeve ends ----

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** Only the plates of sleeves that are there take conduit; the other slots are plain wall. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        int hub = LAYOUT.hubAt(terminal);
        if(hub >= 0 && !(context.getLevel().getBlockEntity(pos) instanceof ChasewayBlockEntity be && be.hasSleeve(slotOfHub(hub)))) {
            if(ConduitItem.isConduit(context.getItemInHand())) {
                IElectric.sendMessage(context, Lang.builder().translate("message.chaseway.no_sleeve").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            IElectric.sendMessage(c, Lang.builder().translate("message.chaseway.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        });
    }

    /** Nothing to open: the wires pass straight through. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public Class<ChasewayBlockEntity> getBlockEntityClass() {
        return ChasewayBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChasewayBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CHASEWAY.get();
    }
}
