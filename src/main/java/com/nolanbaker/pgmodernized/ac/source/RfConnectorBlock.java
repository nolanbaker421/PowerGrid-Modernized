package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
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
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Power Grid's Device Connector for Power Grid's AC: a 6 x 6 x 3 px box the size of the conduit
 * switch, on the end of one conduit run. The first two wires pulled through the run land on its
 * line and neutral by themselves; Forge Energy comes out of every side to whatever takes it, and
 * nothing goes the other way. Built like the conduit switch: FACING is the face it sits on,
 * ROTATION turns it about that face so the knockout can point any of four ways (wrench the face
 * to cycle). Geometry in the floor-mounted frame: body on y = 0, port on top, knockout on the
 * north edge. Right-click with an empty hand to open the splice editor.
 */
public class RfConnectorBlock extends Rotation4ElectricBlock implements IBE<RfConnectorBlockEntity> {
    public static final int TERMINAL_LINE = 0, TERMINAL_NEUTRAL = 1, TERMINAL_HUB = 2, CONDUCTOR_BASE = 3;
    public static final int TERMINAL_COUNT = CONDUCTOR_BASE + ConductorColors.COUNT;

    private static final AABB BODY = new AABB(5, 0, 5, 11, 3, 11);
    private static final AABB HUB = new AABB(7, 0.5, 4, 9, 2.5, 5);
    private static final AABB PLATE = new AABB(6, 3, 6, 10, 3.5, 10);
    private static final AABB PORT = new AABB(7, 3.5, 7, 9, 4.5, 9);
    private static final AABB HIDDEN = new AABB(7.5, 1, 7.5, 8.5, 2, 8.5);

    public RfConnectorBlock(Properties properties) {
        super(properties);
        setTerminalCollection(rotation4DownTerminals(this, terminals(), Shapes.or(box(BODY), box(HUB), box(PLATE), box(PORT))));
    }

    private static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        terminals[TERMINAL_LINE] = terminal(name("rf_connector.line", ChatFormatting.RED), HIDDEN).withColor(IDecoratedTerminal.RED);
        terminals[TERMINAL_NEUTRAL] = terminal(name("rf_connector.neutral", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        terminals[TERMINAL_HUB] = terminal(name("conduit_socket.hub", ChatFormatting.AQUA), HUB).withColor(0x2FB8D6);
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

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** No terminal is ever reported as clickable: wires go through the conduit. The knockout is checked in onWire. */
    @Override
    public int terminalIndexAt(BlockState state, Vec3 pos) {
        return -1;
    }

    @Override
    public ITerminalPlacement terminalAt(BlockState state, Vec3 pos) {
        var hub = terminal(state, TERMINAL_HUB);
        return hub != null && hub.check(pos) ? hub : null;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var hub = terminal(state, TERMINAL_HUB);
        if(hub != null && hub.check(local)) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, TERMINAL_HUB));
        }
        if(conduit)
            return InteractionResult.PASS;
        IElectric.sendMessage(context, Lang.builder().translate("message.rf_connector.conduit_only").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand opens the splice editor for the knockout. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide)
            ClientHooks.openSplices(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<RfConnectorBlockEntity> getBlockEntityClass() {
        return RfConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RfConnectorBlockEntity> getBlockEntityType() {
        return AcContent.RF_CONNECTOR_BE.get();
    }
}
