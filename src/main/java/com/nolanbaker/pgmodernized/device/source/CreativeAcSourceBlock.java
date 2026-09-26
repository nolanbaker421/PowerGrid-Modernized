package com.nolanbaker.pgmodernized.device.source;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * A creative-only alternating source: L1, L2, L3 and a neutral on top, any current, at the
 * line-to-neutral voltage and frequency set on the two value boxes on top. Three-phase by
 * default (the lines a third of a turn apart); sneak-click with an empty hand for split-phase,
 * where L1 and L2 are half a turn apart and L3 repeats L1.
 */
public class CreativeAcSourceBlock extends HorizontalElectricBlock implements IBE<CreativeAcSourceBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    public static final double TOP = 10;
    private static final AABB BODY = new AABB(2, 0, 2, 14, TOP, 14);
    private static final AABB[] LUGS = {
            new AABB(11.5, TOP, 2.5, 13.5, TOP + 2, 4.5),   // L1, viewer's left front
            new AABB(7, TOP, 2.5, 9, TOP + 2, 4.5),         // L2
            new AABB(2.5, TOP, 2.5, 4.5, TOP + 2, 4.5),     // L3
            new AABB(7, TOP, 11.5, 9, TOP + 2, 13.5),       // neutral, back
    };

    public CreativeAcSourceBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape()));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("creative_ac_source.l1", LUGS[L1], ConductorColors.rgb(0), ConductorColors.textRgb(0)),
                terminal("creative_ac_source.l2", LUGS[L2], ConductorColors.rgb(2), ConductorColors.textRgb(2)),
                terminal("creative_ac_source.l3", LUGS[L3], ConductorColors.rgb(3), ConductorColors.textRgb(3)),
                terminal("creative_ac_source.n", LUGS[N], IDecoratedTerminal.BLUE, IDecoratedTerminal.BLUE),
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

    /** Sneak with an empty hand: three-phase or split-phase. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, be -> be.toggleSplitPhase(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<CreativeAcSourceBlockEntity> getBlockEntityClass() {
        return CreativeAcSourceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreativeAcSourceBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CREATIVE_AC_SOURCE.get();
    }
}
