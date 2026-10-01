package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One end of a cable chain: a bracket with four conductor studs (L1, L2, L3, N) and a network
 * jack. Two anchors joined by a {@link CableChainItem} carry all four conductors and a Cat6 link
 * between them, so a crane trolley or any other Create Aeronautics body can be fed from the
 * structure it runs on. The chain mount is a hidden terminal inside the post: it only serves to
 * place the chain's ends.
 */
public class CableChainAnchorBlock extends Rotation4ElectricBlock implements IBE<CableChainAnchorBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    /** The chain itself hangs here; not reachable by a click, no ordinary wire lands on it. */
    public static final int MOUNT = 4;
    public static final int JACK = 5;
    public static final int TERMINALS = 6;

    private static final VoxelShape SHAPE_DOWN = Shapes.or(box(2, 0, 2, 14, 2, 14), box(5, 2, 2, 11, 8, 6), box(14, 0, 7, 16, 2, 9));

    public CableChainAnchorBlock(Properties properties) {
        super(properties);
        setTerminalCollection(rotation4DownTerminals(this, terminals(), SHAPE_DOWN));
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                new TerminalBoundingBox(name("cable_chain.l1", ChatFormatting.RED), 2, 2, 11, 4, 4, 13).withColor(0xD03030),
                new TerminalBoundingBox(name("cable_chain.l2", ChatFormatting.GOLD), 5, 2, 11, 7, 4, 13).withColor(0xE0A020),
                new TerminalBoundingBox(name("cable_chain.l3", ChatFormatting.BLUE), 9, 2, 11, 11, 4, 13).withColor(0x3060D0),
                new TerminalBoundingBox(name("cable_chain.n", ChatFormatting.WHITE), 12, 2, 11, 14, 4, 13).withColor(0xE8E8E8),
                new TerminalBoundingBox(name("cable_chain.mount", ChatFormatting.GRAY), 7, 4, 3, 9, 6, 5).withColor(0x505050),
                JackTerminals.jack(14, 0, 7, 16, 2, 9)
        };
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return JackTerminals.onWire(this, JACK, state, context, super::onWire);
    }

    @Override
    public Class<CableChainAnchorBlockEntity> getBlockEntityClass() {
        return CableChainAnchorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableChainAnchorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CABLE_CHAIN_ANCHOR.get();
    }
}
