package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/** Terminals plus splices; which runs are attached is read from the wire entities on the hubs. */
public class ConduitBoxBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    private List<SplicePoint> points;

    public ConduitBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(ConduitBoxGeometry.TERMINAL_COUNT);
        splices().buildCircuit(builder);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            splices().prune();
    }

    // ---- splice host ----

    private boolean coverTerminals() {
        var state = getBlockState();
        return state.getBlock() instanceof ConduitBoxBlock && ConduitBoxBlock.cover(state).hasTerminals();
    }

    /** Any hanging wire on a cover terminal, which a node plate cannot be taken off under. */
    public boolean hasCoverWires() {
        var behaviour = getElectricBehaviour();
        if(behaviour == null)
            return false;
        for(var entry : behaviour.getConnections().entrySet()) {
            if(ConduitBoxGeometry.isFront(entry.getKey().getTerminal()) && !entry.getValue().isEmpty())
                return true;
        }
        return false;
    }

    /** The cover changed: the points come and go with the node plate. */
    @Override
    public void setBlockState(BlockState state) {
        boolean before = coverTerminals();
        super.setBlockState(state);
        if(before != coverTerminals()) {
            points = null;
            if(level != null && !level.isClientSide)
                splices().prune();
        }
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            if(coverTerminals()) for(int k = 0; k < ConduitBoxGeometry.FRONT_COUNT; ++k) {
                var name = Lang.builder().translate("gui.conduit_box.cover").style(ChatFormatting.GRAY).text(" ").add(ConductorColors.name(k)).component();
                points.add(new SplicePoint(k, name, ConductorColors.rgb(k)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return ConduitBoxGeometry.isFront(terminal) && coverTerminals();
    }

    @Override
    public int hubCount() {
        return ConduitBoxGeometry.HUB_COUNT;
    }

    @Override
    public Component hubName(int hub) {
        return ConduitBoxGeometry.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return ConduitBoxGeometry.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return ConduitBoxGeometry.hubOf(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return ConduitBoxGeometry.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return ConduitBoxGeometry.conductorHub(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return ConduitBoxGeometry.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= hubCount() ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        splices().write(tag);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        splices().read(tag);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.conduit_box.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        splices().addGoggleLines(tooltip);
        return true;
    }
}
