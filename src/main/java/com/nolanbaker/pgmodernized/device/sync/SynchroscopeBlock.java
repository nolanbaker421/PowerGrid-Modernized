package com.nolanbaker.pgmodernized.device.sync;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * A synchroscope: a wall meter that compares the running bus with an incoming machine before the
 * tie is closed. Two potential inputs, each a line and its neutral, along the bottom edge. The
 * needle shows the phase angle of the incoming against the bus and turns at the slip; the face
 * lights green when the two are close enough in frequency, angle and voltage to parallel.
 * North frame: the meter stands against the south side of the block, dial facing north; a viewer
 * has +x on their left.
 */
public class SynchroscopeBlock extends HorizontalElectricBlock implements IBE<SynchroscopeBlockEntity> {
    public static final BooleanProperty SYNCED = BooleanProperty.create("synced");
    public static final int BUS = 0, BUS_N = 1, INCOMING = 2, INCOMING_N = 3;

    public static final AABB BODY = new AABB(2, 2, 12, 14, 14, 16);
    /** Dial centre, and the depth the needle turns at, in px. */
    public static final double DIAL_X = 8, DIAL_Y = 8.5, DIAL_Z = 11.9;
    private static final AABB[] LUGS = {
            new AABB(12.5, 0, 13, 14.5, 2, 15),   // bus, viewer's left
            new AABB(9.5, 0, 13, 11.5, 2, 15),    // bus neutral
            new AABB(4.5, 0, 13, 6.5, 2, 15),     // incoming
            new AABB(1.5, 0, 13, 3.5, 2, 15),     // incoming neutral, viewer's right
    };

    public SynchroscopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SYNCED, false));
        setTerminalCollection(horizontalNorthTerminals(this, terminals(), shape()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SYNCED);
    }

    private static TerminalBoundingBox[] terminals() {
        return new TerminalBoundingBox[] {
                terminal("synchroscope.bus", LUGS[BUS], 0xC62828),
                terminal("synchroscope.bus_n", LUGS[BUS_N], IDecoratedTerminal.BLUE),
                terminal("synchroscope.incoming", LUGS[INCOMING], 0xF07F1A),
                terminal("synchroscope.incoming_n", LUGS[INCOMING_N], IDecoratedTerminal.BLUE),
        };
    }

    private static TerminalBoundingBox terminal(String key, AABB px, int rgb) {
        Component name = Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(rgb));
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

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(SYNCED, false);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public Class<SynchroscopeBlockEntity> getBlockEntityClass() {
        return SynchroscopeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SynchroscopeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SYNCHROSCOPE.get();
    }
}
