package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
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

/**
 * Terminals plus splices; which runs are attached is read from the wire entities on the hubs. A
 * light socket cover puts a lamp between cover terminals 0 and 1 that lights the block from ten
 * watts and goes dark under five.
 */
public class ConduitBoxBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation {
    public static final float LIT_WATTS = 10, DARK_WATTS = 5;

    private SpliceSupport splices;
    private List<SplicePoint> points;
    /** The lamp of a light socket cover, made in buildCircuit; null under any other cover. */
    private ElectricWire lamp;
    private float lampWatts;

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
        lamp = cover().isLight() ? builder.connect(resistance("lamp"), builder.terminalNode(0), builder.terminalNode(1)) : null;
    }

    private ConduitCover cover() {
        var state = getBlockState();
        return state.getBlock() instanceof ConduitBoxBlock ? ConduitBoxBlock.cover(state) : ConduitCover.OPEN;
    }

    public float lampWatts() {
        return lampWatts;
    }

    @Override
    public void electricalTick() {
        if(lamp == null || level == null || level.isClientSide || !lamp.isConverged())
            return;
        double watts = Math.abs(lamp.power());
        lampWatts = Float.isFinite((float) watts) ? (float) watts : 0;
        var state = getBlockState();
        if(!(state.getBlock() instanceof ConduitBoxBlock))
            return;
        var cover = ConduitBoxBlock.cover(state);
        boolean lit = cover == ConduitCover.LIGHT_ON ? lampWatts >= DARK_WATTS : lampWatts >= LIT_WATTS;
        var next = lit ? ConduitCover.LIGHT_ON : ConduitCover.LIGHT_OFF;
        if(next != cover)
            level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, next), Block.UPDATE_ALL);
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
        return cover().hasTerminals();
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

    /** The cover changed: the points come and go with the plate, and a light socket's lamp with it. */
    @Override
    public void setBlockState(BlockState state) {
        var before = cover();
        super.setBlockState(state);
        var after = cover();
        if(before.terminals() != after.terminals()) {
            points = null;
            if(level != null && !level.isClientSide)
                splices().prune();
        }
        if(before.isLight() != after.isLight() && level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            var cover = cover();
            if(cover.isDevice()) {
                String device = cover.isLight() ? "lamp" : "receptacle";
                points.add(new SplicePoint(0, ConductorColors.hotLabel("conduit_box." + device + "_line", 0), ConductorColors.hotRgb(0)));
                points.add(new SplicePoint(1, ConductorColors.neutralLabel("conduit_box." + device + "_neutral"), ConductorColors.neutralRgb()));
            } else for(int k = 0; k < cover.terminals(); ++k) {
                var name = Lang.builder().translate("gui.conduit_box.cover").style(ChatFormatting.GRAY).text(" ").add(ConductorColors.name(k)).component();
                points.add(new SplicePoint(k, name, ConductorColors.rgb(k)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return ConduitBoxGeometry.isFront(terminal) && cover().exposes(terminal);
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
        if(cover().isLight()) {
            boolean lit = cover() == ConduitCover.LIGHT_ON;
            Lang.builder().translate(lit ? "gui.conduit_box.lamp_lit" : "gui.conduit_box.lamp_dark", String.format("%.0f", lampWatts)).style(lit ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
