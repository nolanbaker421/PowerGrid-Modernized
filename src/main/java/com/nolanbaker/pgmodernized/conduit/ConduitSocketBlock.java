package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ISocketElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.powercord.SocketEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A 6 x 6 x 3 px fitting that ends one conduit run in a cord socket: the first two wires pulled
 * through the run land on the socket's two poles by themselves, and a Power Grid cord plugs in with
 * one click. Meant for the equipment end of a run, where a full box would be in the way.
 * <p>
 * Built like Power Grid's own socket: FACING is the face it sits on, ROTATION turns it about that
 * face so the knockout can point any of four ways (wrench the socket face to cycle). The geometry is
 * written in the floor-mounted frame: body on y = 0, socket face up, knockout on the north edge.
 * The cord code expects the two poles to be terminals 0 and 1.
 */
public class ConduitSocketBlock extends Rotation4ElectricBlock implements IBE<ConduitSocketBlockEntity>, ISocketElectric {
    public static final int TERMINAL_POLE_A = 0, TERMINAL_POLE_B = 1, TERMINAL_HUB = 2, TERMINAL_SOCKET = 3, CONDUCTOR_BASE = 4;
    public static final int TERMINAL_COUNT = CONDUCTOR_BASE + ConductorColors.COUNT;

    private static final AABB BODY = new AABB(5, 0, 5, 11, 3, 11);
    private static final AABB HUB = new AABB(7, 0.5, 4, 9, 2.5, 5);
    private static final AABB SOCKET = new AABB(6.5, 3, 6.5, 9.5, 4, 9.5);
    private static final AABB HIDDEN = new AABB(7.5, 1, 7.5, 8.5, 2, 8.5);

    public ConduitSocketBlock(Properties properties) {
        super(properties);
        setTerminalCollection(rotation4DownTerminals(this, terminals(), Shapes.or(box(BODY), box(HUB), box(SOCKET))));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        terminals[TERMINAL_POLE_A] = terminal(name("conduit_socket.pole_a", ChatFormatting.RED), HIDDEN).withColor(IDecoratedTerminal.RED);
        terminals[TERMINAL_POLE_B] = terminal(name("conduit_socket.pole_b", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        terminals[TERMINAL_HUB] = terminal(name("conduit_socket.hub", ChatFormatting.AQUA), HUB).withColor(0x2FB8D6);
        terminals[TERMINAL_SOCKET] = terminal(IDecoratedTerminal.SOCKET, SOCKET);
        for(int k = 0; k < ConductorColors.COUNT; ++k)
            terminals[CONDUCTOR_BASE + k] = terminal(ConductorColors.name(k), HIDDEN).withColor(ConductorColors.rgb(k));
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /** The socket placement is kept as a terminal so the collection rotates it with the block. */
    @Override
    public ITerminalPlacement socket(BlockState state) {
        return terminal(state, TERMINAL_SOCKET);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /**
     * Power Grid's cord code looks for a clicked terminal before it looks for a socket, and its wire
     * code would try to land wires on the knockout, so no terminal is ever reported as clickable here.
     * The knockout is checked directly in {@link #onWire}; the hover preview still gets it below.
     */
    @Override
    public int terminalIndexAt(BlockState state, Vec3 pos) {
        return -1;
    }

    @Override
    public ITerminalPlacement terminalAt(BlockState state, Vec3 pos) {
        var hub = terminal(state, TERMINAL_HUB);
        return hub != null && hub.check(pos) ? hub : null;
    }

    private boolean onHub(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        var hub = terminal(state, TERMINAL_HUB);
        return hub != null && hub.check(context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = onHub(state, context) ? TERMINAL_HUB : -1;
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(terminal == TERMINAL_HUB) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit)
            return InteractionResult.PASS;
        IElectric.sendMessage(context, Lang.builder().translate("message.conduit_socket.cord_only").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand on the socket unplugs the cord, as on Power Grid's own socket. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        var endpoint = new SocketEndpoint(pos);
        var cord = endpoint.getConnection(level);
        if(cord == null)
            return InteractionResult.PASS;
        if(cord.getEndpoint1().equals(endpoint))
            return cord.cordDetach(player, false) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if(cord.getEndpoint2().equals(endpoint))
            return cord.cordDetach(player, true) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return InteractionResult.FAIL;
    }

    @Override
    public Class<ConduitSocketBlockEntity> getBlockEntityClass() {
        return ConduitSocketBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConduitSocketBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONDUIT_SOCKET.get();
    }
}
