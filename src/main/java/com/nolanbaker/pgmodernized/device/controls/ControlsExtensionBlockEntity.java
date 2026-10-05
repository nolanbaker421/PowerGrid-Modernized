package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.util.IElectricDelegate;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The extension's half of the bargain: Power Grid asks it for terminals and gets the head's,
 * moved up into this cell; the splice editor and goggles opened on it read the head.
 */
public class ControlsExtensionBlockEntity extends SmartBlockEntity implements IElectric, IElectricDelegate, ISpliceHost, IHaveGoggleInformation {
    public ControlsExtensionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- the head ----

    @Nullable
    public ControlsCabinetBlockEntity head() {
        return level == null ? null : ControlsExtensionBlock.head(level, worldPosition);
    }

    @Override
    public BlockPos headPos() {
        var pos = level == null ? null : ControlsExtensionBlock.headPos(level, worldPosition);
        return pos == null ? worldPosition : pos;
    }

    /** 1 for the extension right under the head, 2 for the next. */
    public int section() {
        return headPos().getY() - worldPosition.getY();
    }

    // ---- electrical face ----

    @Override
    public int terminalCount() {
        return head() == null ? 0 : CabinetLayout.TERMINAL_COUNT;
    }

    /** Only this section's terminals, moved from the head's frame up into this block. */
    @Override
    public @Nullable ITerminalPlacement terminal(BlockState state, int index) {
        var head = head();
        if(head == null || level == null)
            return null;
        int section = section();
        if(CabinetLayout.sectionOf(index) != section)
            return null;
        var headPos = headPos();
        if(!(level.getBlockState(headPos).getBlock() instanceof ControlsCabinetBlock block))
            return null;
        var placement = block.terminal(level.getBlockState(headPos), index);
        return placement instanceof TerminalBoundingBox box ? box.offset(0, section, 0) : placement;
    }

    @Override
    public @Nullable ElectricBehaviour getBehaviour(Level world, BlockPos pos, BlockState state) {
        var headPos = headPos();
        return world.getBlockState(headPos).getBlock() instanceof ControlsCabinetBlock block
                ? block.getBehaviour(world, headPos, world.getBlockState(headPos)) : null;
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        var head = head();
        return head != null && head.getBlockState().getBlock() instanceof ControlsCabinetBlock block && block.accepts(wireStack);
    }

    /** Power Grid's wire items land here through the item; conduit comes through the block. Only conduit on a knockout lands. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        if(head() == null)
            return InteractionResult.PASS;
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(CabinetLayout.hubAt(terminal) >= 0) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            if(terminal >= 0) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if(WireAcceptance.electrical(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.controls.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    // ---- splice host: the head's ----

    @Override
    public SpliceSupport splices() {
        var head = head();
        return head == null ? null : head.splices();
    }

    @Override
    public List<SplicePoint> points() {
        var head = head();
        return head == null ? List.of() : head.points();
    }

    @Override
    public boolean isPoint(int terminal) {
        var head = head();
        return head != null && head.isPoint(terminal);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public int hubCount() {
        var head = head();
        return head == null ? 0 : head.hubCount();
    }

    @Override
    public Component hubName(int hub) {
        return DeviceHubs.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return CabinetLayout.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return CabinetLayout.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return CabinetLayout.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return CabinetLayout.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return CabinetLayout.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        var head = head();
        return head == null ? null : head.hubRun(hub);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var head = head();
        return head != null && head.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }
}
