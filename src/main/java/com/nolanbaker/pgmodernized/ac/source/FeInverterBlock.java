package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
 * A three-phase inverter fed with Forge Energy, built as a wall cabinet like the CT cabinet: FE in
 * on any side from any mod's cable; L1, L2, L3 and a neutral out on four lugs low on the door, or
 * through the four conduit knockouts (two top, two bottom) whose conductors splice onto the same
 * four points; the voltage and frequency on two value boxes on the door. Real power drawn from the
 * lines is taken out of its FE buffer each tick; an empty buffer is a brownout.
 * <p>
 * North frame like the CT cabinet: enclosure against the south wall, door facing north.
 */
public class FeInverterBlock extends HorizontalElectricBlock implements IBE<FeInverterBlockEntity> {
    public static final int L1 = 0, L2 = 1, L3 = 2, N = 3;
    /** Where the value boxes sit on the door, in 16ths from the block's south edge in the south frame. */
    public static final double DOOR = 6;
    private static final AABB BODY = new AABB(2, 1, 10, 14, 15, 16);
    private static final AABB[] LUGS = {
            new AABB(11.5, 2, 9, 13.5, 4, 10),   // L1, viewer's left
            new AABB(8.5, 2, 9, 10.5, 4, 10),    // L2
            new AABB(5.5, 2, 9, 7.5, 4, 10),     // L3
            new AABB(2.5, 2, 9, 4.5, 4, 10),     // neutral, viewer's right
    };
    public static final AABB[] HUBS = {
            new AABB(4, 15, 12.5, 6, 16, 14.5), new AABB(10, 15, 12.5, 12, 16, 14.5),   // top
            new AABB(4, 0, 12.5, 6, 1, 14.5), new AABB(10, 0, 12.5, 12, 1, 14.5),       // bottom
    };
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(4, HUBS.length, new int[] {L1, L2, L3, N});

    public FeInverterBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), DeviceHubs.withHubs(shape(), HUBS)));
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

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, super::onWire);
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
