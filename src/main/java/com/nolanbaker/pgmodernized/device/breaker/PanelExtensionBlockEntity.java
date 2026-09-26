package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.util.IElectricDelegate;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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

import java.util.List;

/**
 * The extension's half of the bargain: Power Grid asks it for terminals and gets the head's, moved
 * up into this cell; the splice editor and goggles opened on it read the head; and its twelve
 * wrench value boxes are mirrors of the head's for the rows this section carries.
 */
public class PanelExtensionBlockEntity extends SmartBlockEntity implements IElectric, IElectricDelegate, ISpliceHost, IHaveGoggleInformation {
    public PanelExtensionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        for(int local = 0; local < ExtensionRatingBehaviour.PER_SECTION; ++local)
            behaviours.add(new ExtensionRatingBehaviour(this, local));
    }

    // ---- the head ----

    @Nullable
    public BreakerPanelBlockEntity head() {
        return level == null ? null : PanelExtensionBlock.head(level, worldPosition);
    }

    @Override
    public BlockPos headPos() {
        var pos = level == null ? null : PanelExtensionBlock.headPos(level, worldPosition);
        return pos == null ? worldPosition : pos;
    }

    /** 1 for the extension right under the head, 2 for the next. */
    public int section() {
        return headPos().getY() - worldPosition.getY();
    }

    /** The head's space index of this section's local space. */
    public int globalSlot(int local) {
        var head = head();
        return head == null ? -1 : section() * head.spec().slots() + local;
    }

    // ---- electrical face ----

    @Override
    public int terminalCount() {
        var head = head();
        return head == null ? 0 : PanelLayout.terminalCount(head.spec());
    }

    /** Only this section's terminals, moved from the head's frame up into this block. */
    @Override
    public @Nullable ITerminalPlacement terminal(BlockState state, int index) {
        var head = head();
        if(head == null || level == null)
            return null;
        int section = section();
        if(PanelLayout.sectionOf(head.spec(), index) != section)
            return null;
        var headPos = headPos();
        if(!(level.getBlockState(headPos).getBlock() instanceof BreakerPanelBlock block))
            return null;
        var placement = block.terminal(level.getBlockState(headPos), index);
        return placement instanceof TerminalBoundingBox box ? box.offset(0, section, 0) : placement;
    }

    @Override
    public @Nullable ElectricBehaviour getBehaviour(Level world, BlockPos pos, BlockState state) {
        var headPos = headPos();
        return world.getBlockState(headPos).getBlock() instanceof BreakerPanelBlock block
                ? block.getBehaviour(world, headPos, world.getBlockState(headPos)) : null;
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        var head = head();
        return head != null && head.getBlockState().getBlock() instanceof BreakerPanelBlock block && block.accepts(wireStack);
    }

    /** Power Grid's wire items land here through the item; conduit comes through the block. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var head = head();
        if(head == null)
            return InteractionResult.PASS;
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        return BreakerPanelBlock.onWire(this, head.spec(), head.getBlockState(), pos, terminal, context);
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
        return ConduitSize.TWO;
    }

    @Override
    public int hubCount() {
        var head = head();
        return head == null ? 0 : head.hubCount();
    }

    @Override
    public Component hubName(int hub) {
        return PanelLayout.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        var head = head();
        return head == null ? -1 : head.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        var head = head();
        return head == null ? -1 : head.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        var head = head();
        return head == null ? -1 : head.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        var head = head();
        return head == null ? -1 : head.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        var head = head();
        return head == null ? -1 : head.conductorOf(terminal);
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
