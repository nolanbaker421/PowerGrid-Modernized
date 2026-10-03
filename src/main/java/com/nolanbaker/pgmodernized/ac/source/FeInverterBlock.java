package com.nolanbaker.pgmodernized.ac.source;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * A three-phase inverter fed with Forge Energy: FE in on any side from any mod's cable, L1, L2,
 * L3 and a neutral out on top at the line-to-neutral voltage and frequency set on the two value
 * boxes. Real power drawn from the lines is taken out of its FE buffer each tick; an empty buffer
 * is a brownout and the lines go dead until it has refilled a little.
 */
public class FeInverterBlock extends HorizontalElectricBlock implements IBE<FeInverterBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    /** A dry-type cabinet, taller than the creative box, with the lugs on its lid. */
    public static final double TOP = 12;
    private static final AABB BODY = new AABB(2, 0, 2, 14, TOP, 14);
    private static final AABB[] LUGS = {
            new AABB(11.5, TOP, 2.5, 13.5, TOP + 2, 4.5),   // L1, viewer's left front
            new AABB(7, TOP, 2.5, 9, TOP + 2, 4.5),         // L2
            new AABB(2.5, TOP, 2.5, 4.5, TOP + 2, 4.5),     // L3
            new AABB(7, TOP, 11.5, 9, TOP + 2, 13.5),       // neutral, back
    };

    public FeInverterBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape()));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("fe_inverter.l1", LUGS[L1], ConductorColors.rgb(0), ConductorColors.textRgb(0)),
                terminal("fe_inverter.l2", LUGS[L2], ConductorColors.rgb(2), ConductorColors.textRgb(2)),
                terminal("fe_inverter.l3", LUGS[L3], ConductorColors.rgb(3), ConductorColors.textRgb(3)),
                terminal("fe_inverter.n", LUGS[N], IDecoratedTerminal.BLUE, IDecoratedTerminal.BLUE),
        };
    }

    private static TerminalBoundingBox terminal(String key, AABB px, int rgb, int textRgb) {
        Component name = Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(textRgb));
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ).withColor(rgb);
    }

    private static VoxelShape shape() {
        var shape = box(BODY);
        for(var lug : LUGS)
            shape = Shapes.or(shape, box(lug));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Sneak with an empty hand: the voltage figure is line-to-neutral or line-to-line. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, be -> be.toggleLineToLine(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
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
