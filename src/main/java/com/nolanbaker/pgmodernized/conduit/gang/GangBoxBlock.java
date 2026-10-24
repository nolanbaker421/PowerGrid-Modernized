package com.nolanbaker.pgmodernized.conduit.gang;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import org.patryk3211.powergrid.electricity.base.ISocketElectric;
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
 * into the plate and lands on the first receptacle.
 */
public class GangBoxBlock extends Rotation4ElectricBlock implements IBE<GangBoxBlockEntity>, ISocketElectric {
    private final int gangs;
    private final Map<BlockState, ITerminalPlacement> sockets = new HashMap<>();

    public GangBoxBlock(Properties properties, int gangs) {
        super(properties);
        this.gangs = gangs;
        setTerminalCollection(rotation4DownTerminals(this, GangLayout.terminals(gangs), GangLayout.shape(gangs)));
    }

    public int gangs() {
        return gangs;
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

    /** The plate is the cord's socket; the cord lands on terminals 0 and 1, which the entity wires to its first receptacle. */
    @Override
    public ITerminalPlacement socket(BlockState state) {
        return sockets.computeIfAbsent(state, s -> new TerminalBoundingBox(IDecoratedTerminal.SOCKET, GangFrame.worldBox(s, GangLayout.plate(gangs))));
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
        if(conduit)
            return InteractionResult.PASS;
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
