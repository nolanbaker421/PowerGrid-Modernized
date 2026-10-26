package com.nolanbaker.pgmodernized.conduit.gang;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.powercord.AutoCordEndpoint;
import org.patryk3211.powergrid.electricity.wire.powercord.CordEntity;
import org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.HashMap;
import java.util.Map;

/**
 * A device box of one to four gangs on the end of conduit runs. Each gang takes a device item: a
 * switch, a 3-way, a 4-way, a push button, a receptacle or a blank. Nothing lands by itself; the
 * splice screen (sneak-click an empty gang or the plate) wires the run's conductors to the devices'
 * points. Mounted like the conduit switch: on any face, turned with a wrench. A power cord plugs
 * into any receptacle gang and lands on that gang's line and neutral: each gang is its own cord
 * endpoint, so a box of receptacles takes a cord in every one.
 */
public class GangBoxBlock extends Rotation4ElectricBlock implements IBE<GangBoxBlockEntity>, IAcceptCord {
    /** The gang of the first receptacle, 1 to 4, or 0 with none: where a power cord plugs in. Kept by the block entity. */
    public static final IntegerProperty SOCKET = IntegerProperty.create("socket", 0, GangLayout.MAX_GANGS);
    private final int gangs;

    public GangBoxBlock(Properties properties, int gangs) {
        super(properties);
        this.gangs = gangs;
        setTerminalCollection(com.nolanbaker.pgmodernized.util.Rotation4Frame.collection(this, GangLayout.terminals(gangs), GangLayout.shape(gangs)));
        registerDefaultState(defaultBlockState().setValue(SOCKET, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SOCKET);
    }

    public int gangs() {
        return gangs;
    }

    /** On a wall the default turn stands the box on end; a row of gangs runs across, so it is turned a quarter. Floors and ceilings keep the player's facing. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        if(state != null && state.getValue(FACING).getAxis().isHorizontal())
            state = state.setValue(ROTATION, (state.getValue(ROTATION) + 3) % 4);   // devices upright, the first gang on the viewer's left
        return state;
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** No terminal is ever reported as clickable: wires go through the conduit. The knockouts are checked in onWire. */
    @Override
    public int terminalIndexAt(BlockState state, Vec3 pos) {
        return -1;
    }

    @Override
    public ITerminalPlacement terminalAt(BlockState state, Vec3 pos) {
        for(int h = 0; h < GangLayout.hubCount(gangs); ++h) {
            var hub = terminal(state, GangLayout.hubTerminal(h));
            if(hub != null && hub.check(pos))
                return hub;
        }
        return null;
    }

    /** The first receptacle's face is the cord's socket; the cord lands on terminals 0 and 1, which the entity wires to that receptacle. */
    @Override
    public boolean renderPlug() {
        return true;
    }

    /** A cord clicked on a receptacle gang: its own endpoint on that gang's line and neutral, three pixels off the plate. */
    @Override
    public @Nullable AutoCordEndpoint getEndpoint(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if(!(level.getBlockEntity(pos) instanceof GangBoxBlockEntity be))
            return null;
        int slot = slotAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(slot < 0 || be.device(slot) != GangDevice.RECEPTACLE)
            return null;
        return cordEndpoint(pos, state, slot);
    }

    /** The cord endpoint of a receptacle gang: on its line and neutral, three pixels off the plate, plug pointing out. */
    private AutoCordEndpoint cordEndpoint(BlockPos pos, BlockState state, int slot) {
        var face = GangLayout.slot(gangs, slot);
        var centre = GangFrame.toWorld(state, new Vec3((face.minX + face.maxX) / 32, GangLayout.PLATE_Y1 / 16, 0.5));
        var out = GangFrame.toWorld(state, new Vec3(0.5, 1, 0.5)).subtract(GangFrame.toWorld(state, new Vec3(0.5, 0, 0.5))).normalize();
        var placement = Vec3.atLowerCornerOf(pos).add(centre).add(out.scale(3 / 16.0));
        // An auto endpoint's plug facing is the way the plug points out of its socket; Power Grid turns it round to draw.
        return new AutoCordEndpoint(pos, GangLayout.point(slot, 0), GangLayout.point(slot, 1), placement, Direction.getNearest(out.x, out.y, out.z));
    }

    /** The cord plugged into a receptacle gang, or null. Server side. */
    @Nullable
    private static CordEntity cordOn(GangBoxBlockEntity be, int slot, AutoCordEndpoint endpoint) {
        var behaviour = be.getBehaviour(ElectricBehaviour.TYPE);
        if(behaviour != null) {
            for(var entry : behaviour.getConnections().entrySet()) {
                if(entry.getKey().getTerminal() != GangLayout.point(slot, 0))
                    continue;
                for(var wire : entry.getValue()) {
                    if(wire instanceof CordEntity cord && !cord.isRemoved() && (endpoint.equals(cord.getEndpoint1()) || endpoint.equals(cord.getEndpoint2())))
                        return cord;
                }
            }
        }
        var level = be.getLevel();
        if(level == null)
            return null;
        for(var cord : level.getEntitiesOfClass(CordEntity.class, new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(24))) {
            if(!cord.isRemoved() && (endpoint.equals(cord.getEndpoint1()) || endpoint.equals(cord.getEndpoint2())))
                return cord;
        }
        return null;
    }

    /** The plate of the receptacle gang under the cursor, for the cord's aim outline; nothing elsewhere. */
    @Override
    public @Nullable ITerminalPlacement cordTerminal(BlockState state, Level level, BlockHitResult hit) {
        var pos = hit.getBlockPos();
        if(!(level.getBlockEntity(pos) instanceof GangBoxBlockEntity be))
            return null;
        int slot = slotAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(slot < 0 || be.device(slot) != GangDevice.RECEPTACLE)
            return null;
        var face = GangLayout.slot(gangs, slot);
        var raised = new net.minecraft.world.phys.AABB(face.minX, face.minY, face.minZ, face.maxX, face.maxY + 0.8, face.maxZ);
        return new TerminalBoundingBox(IDecoratedTerminal.SOCKET, GangFrame.worldBox(state, raised));
    }

    /** The gang under a block-local point on the plate, or -1 off the plate. */
    public int slotAt(BlockState state, Vec3 local) {
        var frame = GangFrame.toFrame(state, local).scale(16);
        if(frame.y < GangLayout.BODY_Y1 - 0.5 || frame.z < 5 || frame.z > 11)
            return -1;
        int slot = (int) Math.floor((frame.x - GangLayout.left(gangs)) / 4);
        return slot >= 0 && slot < gangs ? slot : -1;
    }

    private int hubClicked(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for(int h = 0; h < GangLayout.hubCount(gangs); ++h) {
            int t = GangLayout.hubTerminal(h);
            var hub = terminal(state, t);
            if(hub != null && hub.check(local))
                return t;
        }
        return -1;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        int terminal = hubClicked(state, context);
        if(terminal >= 0) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            var routed = ConduitPlacement.groundClick(context);
            return routed != InteractionResult.PASS ? routed : InteractionResult.PASS;
        }
        IElectric.sendMessage(context, Lang.builder().translate("message.gang.conduit_only").style(ChatFormatting.RED).component());
        return InteractionResult.FAIL;
    }

    /** A device item goes into the gang under the click, or the first empty one. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(stack.getItem() instanceof GangDeviceItem item) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof GangBoxBlockEntity be) {
                int slot = slotAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
                be.install(slot, item.device(), player, stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand: a click flips the switch or presses the button in that gang; sneaking takes the device out, or opens the splice screen on an empty gang. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        int slot = slotAt(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(!(level.getBlockEntity(pos) instanceof GangBoxBlockEntity be))
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(slot >= 0 && be.device(slot) != null) {
                if(!level.isClientSide)
                    be.remove(slot, player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        var device = slot >= 0 ? be.device(slot) : null;
        if(device == GangDevice.RECEPTACLE) {
            // A cord in the receptacle comes out in the hand, as on a socket.
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            var endpoint = cordEndpoint(pos, state, slot);
            var cord = cordOn(be, slot, endpoint);
            if(cord == null)
                return InteractionResult.PASS;
            boolean second = endpoint.equals(cord.getEndpoint2()) && !endpoint.equals(cord.getEndpoint1());
            return cord.cordDetach(player, second) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if(device == null || !device.toggles())
            return InteractionResult.PASS;
        if(!level.isClientSide)
            be.toggle(slot, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GangBoxBlockEntity be)
            be.dropDevices();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<GangBoxBlockEntity> getBlockEntityClass() {
        return GangBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GangBoxBlockEntity> getBlockEntityType() {
        return ModBlockEntities.GANG_BOX.get();
    }
}
