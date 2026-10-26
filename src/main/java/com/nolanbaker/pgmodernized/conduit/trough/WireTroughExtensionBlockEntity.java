package com.nolanbaker.pgmodernized.conduit.trough;

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

import java.util.List;

/**
 * A trough section's half of the bargain: Power Grid asks it for terminals and gets this section's
 * own knockouts, numbered in the row's space, placed by its own patterns and facing; the splice
 * editor and goggles opened on it read the head.
 */
public class WireTroughExtensionBlockEntity extends SmartBlockEntity implements IElectric, IElectricDelegate, ISpliceHost, ITroughSection, IHaveGoggleInformation {
    public WireTroughExtensionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Nullable
    public WireTroughBlockEntity head() {
        return level == null ? null : WireTroughBlock.head(level, worldPosition);
    }

    @Override
    public BlockPos headPos() {
        var pos = level == null ? null : WireTroughBlock.headPos(level, worldPosition);
        return pos == null ? worldPosition : pos;
    }

    @Override
    public int section() {
        return headPos().distManhattan(worldPosition);
    }

    // ---- electrical face ----

    @Override
    public int terminalCount() {
        return TroughGeometry.TERMINAL_COUNT;
    }

    /** Only this section's terminals, from its own state. */
    @Override
    public @Nullable ITerminalPlacement terminal(BlockState state, int index) {
        if(index < 0 || index >= TroughGeometry.TERMINAL_COUNT || TroughGeometry.sectionOf(index) != section())
            return null;
        if(!(state.getBlock() instanceof WireTroughExtensionBlock))
            return null;
        var all = TroughGeometry.terminals(WireTroughExtensionBlock.facing(state), state.getValue(WireTroughExtensionBlock.TOP), state.getValue(WireTroughExtensionBlock.BOTTOM));
        return all[TroughGeometry.localOf(index)];
    }

    @Override
    public @Nullable ElectricBehaviour getBehaviour(Level world, BlockPos pos, BlockState state) {
        var headPos = headPos();
        return world.getBlockState(headPos).getBlock() instanceof WireTroughBlock block
                ? block.getBehaviour(world, headPos, world.getBlockState(headPos)) : null;
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        var pos = context.getClickedPos();
        int terminal = terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        return WireTroughBlock.onWire(pos, terminal, context);
    }

    // ---- splice host: the head's ----

    @Override
    public SpliceSupport splices() {
        var head = head();
        return head == null ? null : head.splices();
    }

    @Override
    public List<SplicePoint> points() {
        return List.of();
    }

    @Override
    public boolean isPoint(int terminal) {
        return false;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        var head = head();
        return head == null ? null : head.maxConduit(hub);
    }

    @Override
    public int landings(int hub) {
        return TroughGeometry.LANDINGS;
    }

    @Override
    public int hubCount() {
        var head = head();
        return head == null ? 0 : head.hubCount();
    }

    @Override
    public Component hubName(int hub) {
        return TroughGeometry.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return TroughGeometry.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        var head = head();
        return head == null ? -1 : head.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return TroughGeometry.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        var head = head();
        return head == null ? -1 : head.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return TroughGeometry.conductorOf(terminal);
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
