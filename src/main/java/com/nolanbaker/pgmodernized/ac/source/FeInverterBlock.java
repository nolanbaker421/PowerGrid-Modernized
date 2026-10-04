package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.device.transformer.TransformerFillerBlock;
import com.nolanbaker.pgmodernized.device.transformer.TransformerGeometry;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.nolanbaker.pgmodernized.util.IFillerBase;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A three-phase inverter fed with Forge Energy, the same two-block cabinet as the dry-type
 * transformer: FE in on any side from any mod's cable; L1, L2, L3 and a neutral out through eight
 * conduit knockouts, four low on the front and four underneath, whose conductors splice onto the
 * four points in the splice editor; hanging wire does not land on it. Voltage and frequency on
 * two value boxes on the front. The cell above is
 * a transformer filler placed and taken with the base. Real power drawn from the lines is taken
 * out of the FE buffer each tick; an empty buffer is a brownout.
 * <p>
 * North frame like the transformers: cabinet against the south wall, front facing north.
 */
public class FeInverterBlock extends HorizontalElectricBlock implements IBE<FeInverterBlockEntity>, IFillerBase {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    /** The front face, in 16ths from the block's south edge in the south frame, where the value boxes sit. */
    public static final double DOOR = 12;
    /** Height of the value boxes on the front, in 16ths. */
    public static final double BOX_Y = 12;
    private static final Vec3i ABOVE = new Vec3i(0, 1, 0);
    private static final AABB BODY = new AABB(2, 0, 4, 14, 16, 16);
    private static final double[] HUB_X = {12.5, 9.5, 6.5, 3.5};
    /** Knockouts 0-3 underneath, 4-7 low on the front, the dry-type's layout. */
    public static final AABB[] HUBS = new AABB[8];
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);

    static {
        for(int i = 0; i < 4; ++i) {
            double x = HUB_X[i];
            HUBS[i] = new AABB(x - 1, 0, 9, x + 1, 1, 11);     // underneath
            HUBS[4 + i] = new AABB(x - 1, 3, 3, x + 1, 5, 4);  // on the front, low
        }
    }

    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(4, HUBS.length, new int[] {L1, L2, L3, N});

    public FeInverterBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
    }

    /** The four points live inside the cabinet, reached only through the splice editor. */
    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("fe_inverter.l1", HIDDEN, ConductorColors.rgb(0), ConductorColors.textRgb(0)),
                terminal("fe_inverter.l2", HIDDEN, ConductorColors.rgb(2), ConductorColors.textRgb(2)),
                terminal("fe_inverter.l3", HIDDEN, ConductorColors.rgb(3), ConductorColors.textRgb(3)),
                terminal("fe_inverter.n", HIDDEN, IDecoratedTerminal.BLUE, IDecoratedTerminal.BLUE),
        };
    }

    private static TerminalBoundingBox terminal(String key, AABB px, int rgb, int textRgb) {
        Component name = Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(textRgb));
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ).withColor(rgb);
    }

    private static VoxelShape shape() {
        return box(BODY);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    // ---- the cell above ----

    @Override
    public List<Vec3i> fillerCells(BlockState baseState) {
        return List.of(ABOVE);
    }

    @Override
    public VoxelShape fillerShape(BlockState baseState, Vec3i worldOffset) {
        return TransformerGeometry.rotate(box(2, 0, 4, 14, 14, 16), baseState.getValue(HORIZONTAL_FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var state = super.getStateForPlacement(ctx);
        if(state == null || !ctx.getLevel().getBlockState(ctx.getClickedPos().offset(ABOVE)).canBeReplaced(ctx))
            return null;
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(!level.isClientSide)
            level.setBlock(pos.offset(ABOVE), TransformerFillerBlock.forOffset(ModBlocks.TRANSFORMER_FILLER.getDefaultState(), ABOVE), Block.UPDATE_ALL);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock())) {
            var at = pos.offset(ABOVE);
            if(level.getBlockState(at).getBlock() instanceof TransformerFillerBlock)
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ---- wiring ----

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit lands on a knockout; any other wire is turned away, as on the dry-type. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            if(WireAcceptance.electrical(c.getItemInHand())) {
                IElectric.sendMessage(c, Lang.builder().translate("message.fe_inverter.conduit_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return super.onWire(s, c);
        });
    }

    /** Empty hand opens the splice editor; sneaking instead flips the voltage figure between line-to-neutral and line-to-line. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide)
                withBlockEntityDo(level, pos, be -> be.toggleLineToLine(player));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<FeInverterBlockEntity> getBlockEntityClass() {
        return FeInverterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FeInverterBlockEntity> getBlockEntityType() {
        return AcContent.FE_INVERTER_BE.get();
    }
}
