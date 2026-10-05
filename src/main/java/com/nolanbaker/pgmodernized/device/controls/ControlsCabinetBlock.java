package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.network.JackTerminals;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A wall cabinet with a DIN rail inside and a door of six cells outside. Modules clip onto the
 * rail (power supply, inputs, outputs, relays); buttons, switches, lights and a display mount in
 * the cells and are wired to the modules' channels in the cabinet's screen. Line and neutral feed
 * the power supply through the knockouts and the splice editor; the relay modules' contacts are
 * on the same terminals. One Cat6 jack puts the whole cabinet on the computer network.
 * <p>
 * North frame like the CT cabinet: enclosure against the south wall, front facing north, +x on the
 * viewer's left. Twelve knockouts: four top, four bottom, two each side. Mirrored by
 * {@code tools/gen_controls_assets.py}.
 */
public class ControlsCabinetBlock extends HorizontalElectricBlock implements IBE<ControlsCabinetBlockEntity> {
    public static final int RAIL = 6, CELLS = 6, RELAY_CHANNELS = 2;
    public static final int TERMINAL_LINE = 0, TERMINAL_NEUTRAL = 1, RELAY_BASE = 2;
    public static final int JACK = RELAY_BASE + RAIL * RELAY_CHANNELS * 2;
    /** The PLC's external port, on the other side; port 1 of the cabinet's jack. */
    public static final int PLC_JACK = JACK + 1;
    public static final int BASE_COUNT = PLC_JACK + 1;

    public static final AABB[] HUBS = new AABB[12];
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final AABB JACK_BOX = new AABB(14, 7, 12.5, 15, 9, 14.5);
    private static final AABB PLC_JACK_BOX = new AABB(1, 7, 12.5, 2, 9, 14.5);
    private static final VoxelShape BODY = box(2, 1, 10, 14, 15, 16);
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};
    /** Door cells: three columns across (viewer's left first) by two rows, in 16ths. */
    public static final double CELL_X0 = 12, CELL_W = 3, ROW_TOP_Y1 = 8.5, ROW_TOP_Y2 = 14, ROW_BOTTOM_Y1 = 2, ROW_BOTTOM_Y2 = 7.5;
    public static final double DOOR_Z = 10;

    static {
        for(int i = 0; i < 4; ++i) {
            double x = 16 - HUB_U[i];
            HUBS[i] = new AABB(x - 1, 15, 12.5, x + 1, 16, 14.5);
            HUBS[4 + i] = new AABB(x - 1, 0, 12.5, x + 1, 1, 14.5);
        }
        HUBS[8] = new AABB(14, 3, 12.5, 15, 5, 14.5);
        HUBS[9] = new AABB(14, 11, 12.5, 15, 13, 14.5);
        HUBS[10] = new AABB(1, 3, 12.5, 2, 5, 14.5);
        HUBS[11] = new AABB(1, 11, 12.5, 2, 13, 14.5);
    }

    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, points());

    /** Terminal of a relay contact: side 0 is COM, 1 is NO. */
    public static int relayTerminal(int slot, int channel, int side) {
        return RELAY_BASE + (slot * RELAY_CHANNELS + channel) * 2 + side;
    }

    private static int[] points() {
        var points = new int[JACK];
        for(int i = 0; i < points.length; ++i)
            points[i] = i;
        return points;
    }

    public ControlsCabinetBlock(Properties properties) {
        super(properties);
        var shape = DeviceHubs.withHubs(BODY, HUBS);
        shape = DeviceHubs.withHubs(shape, JACK_BOX, PLC_JACK_BOX);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), shape));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[BASE_COUNT];
        terminals[TERMINAL_LINE] = terminal(name("controls.line", ChatFormatting.RED), HIDDEN).withColor(IDecoratedTerminal.RED);
        terminals[TERMINAL_NEUTRAL] = terminal(name("controls.neutral", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        for(int slot = 0; slot < RAIL; ++slot) {
            for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                terminals[relayTerminal(slot, ch, 0)] = terminal(Lang.builder().translate("controls.relay_com", slot + 1, ch + 1).style(ChatFormatting.GOLD).component(), HIDDEN)
                        .withColor(0xE0A030);
                terminals[relayTerminal(slot, ch, 1)] = terminal(Lang.builder().translate("controls.relay_no", slot + 1, ch + 1).style(ChatFormatting.YELLOW).component(), HIDDEN)
                        .withColor(0xF0E060);
            }
        }
        terminals[JACK] = JackTerminals.jack(JACK_BOX.minX, JACK_BOX.minY, JACK_BOX.minZ, JACK_BOX.maxX, JACK_BOX.maxY, JACK_BOX.maxZ);
        terminals[PLC_JACK] = JackTerminals.jack(PLC_JACK_BOX.minX, PLC_JACK_BOX.minY, PLC_JACK_BOX.minZ, PLC_JACK_BOX.maxX, PLC_JACK_BOX.maxY, PLC_JACK_BOX.maxZ);
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    // ---- the door ----

    /** A block-local point turned back into the north frame, in 16ths. */
    public static Vec3 toNorthFrame(BlockState state, Vec3 local) {
        double lx = local.x, ly = local.y, lz = local.z;
        double nx, nz;
        switch(facing(state)) {
            case SOUTH -> { nx = 1 - lx; nz = 1 - lz; }
            case EAST -> { nx = lz; nz = 1 - lx; }
            case WEST -> { nx = 1 - lz; nz = lx; }
            default -> { nx = lx; nz = lz; }
        }
        return new Vec3(nx * 16, ly * 16, nz * 16);
    }

    /** The door cell under a block-local point, 0..5 (viewer's top left first), or -1 off the door. */
    public static int cellAt(BlockState state, Vec3 local) {
        var p = toNorthFrame(state, local);
        if(p.z > DOOR_Z + 0.6 || p.x < CELL_X0 - CELL_W * 3 || p.x >= CELL_X0)
            return -1;
        int column = (int) ((CELL_X0 - p.x) / CELL_W);
        int row;
        if(p.y >= ROW_TOP_Y1 && p.y <= ROW_TOP_Y2)
            row = 0;
        else if(p.y >= ROW_BOTTOM_Y1 && p.y <= ROW_BOTTOM_Y2)
            row = 1;
        else
            return -1;
        return row * 3 + Math.min(2, column);
    }

    /** Centre of a door cell in the north frame, 16ths. */
    public static Vec3 cellCenter(int cell) {
        int column = cell % 3, row = cell / 3;
        double x = CELL_X0 - CELL_W * column - CELL_W / 2;
        double y = row == 0 ? (ROW_TOP_Y1 + ROW_TOP_Y2) / 2 : (ROW_BOTTOM_Y1 + ROW_BOTTOM_Y2) / 2;
        return new Vec3(x, y, DOOR_Z);
    }

    // ---- wiring ----

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    /** Conduit on the knockouts, Cat6 on either jack (port 0 internal, port 1 the PLC's), nothing else lands anywhere. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> {
            var pos = c.getClickedPos();
            var local = c.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int terminal = terminalIndexAt(s, local);
            boolean cat6 = com.nolanbaker.pgmodernized.network.Cat6CableItem.isCat6(c.getItemInHand());
            if(terminal == JACK || terminal == PLC_JACK) {
                if(!cat6) {
                    IElectric.sendMessage(c, Lang.translate("message.cat6_only").style(ChatFormatting.RED).component());
                    return InteractionResult.FAIL;
                }
                return com.nolanbaker.pgmodernized.network.Cat6Placement.click(c, new com.nolanbaker.pgmodernized.network.JackEndpoint(pos, terminal == JACK ? 0 : 1));
            }
            if(cat6) {
                if(terminal >= 0) {
                    IElectric.sendMessage(c, Lang.translate("message.cat6_needs_jack").style(ChatFormatting.RED).component());
                    return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }
            if(WireAcceptance.electrical(c.getItemInHand())) {
                IElectric.sendMessage(c, Lang.builder().translate("message.controls.conduit_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /** Modules and devices go in by hand; cutters take them out; everything else falls through. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!(level.getBlockEntity(pos) instanceof ControlsCabinetBlockEntity cabinet))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var device = PanelDevice.DeviceItem.of(stack);
        if(device != null) {
            int cell = cellAt(state, local);
            if(cell < 0 || hit.getDirection() != facing(state))
                return ItemInteractionResult.FAIL;
            if(!level.isClientSide && cabinet.installDevice(cell, device, player) && !player.isCreative())
                stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        var module = ControlModule.ModuleItem.of(stack);
        if(module != null) {
            if(!level.isClientSide && cabinet.installModule(module, player) && !player.isCreative())
                stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            if(!level.isClientSide) {
                int cell = hit.getDirection() == facing(state) ? cellAt(state, local) : -1;
                if(player.isShiftKeyDown() || cell < 0)
                    cabinet.removeLastModule(player);
                else
                    cabinet.removeDevice(cell, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand on a device works it; elsewhere on the door opens the cabinet; sneaking opens the splice editor. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int cell = hit.getDirection() == facing(state) ? cellAt(state, local) : -1;
        if(cell >= 0 && level.getBlockEntity(pos) instanceof ControlsCabinetBlockEntity cabinet && cabinet.device(cell) != null && cabinet.device(cell).isInput()) {
            if(!level.isClientSide)
                cabinet.operate(cell, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openControls(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<ControlsCabinetBlockEntity> getBlockEntityClass() {
        return ControlsCabinetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ControlsCabinetBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONTROLS_CABINET.get();
    }
}
