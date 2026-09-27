package com.nolanbaker.pgmodernized.conduit;

import java.util.ArrayList;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlock;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Ten hubs, no points, splices inside. Every second it also looks for a breaker panel directly
 * above or below on the same wall and, given a free knockout on each, joins the two with a 4"
 * nipple: a run the player never lays, but pulls wire through like any other.
 */
public class PullBoxBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    /** Box knockout to panel knockout, nearest first: a box under the panel uses its top hubs and the panel's bottom ones. */
    private static final int[][] PAIRS_BELOW = {{0, 5}, {0, 4}, {1, 6}, {1, 7}};
    private static final int[][] PAIRS_ABOVE = {{2, 1}, {2, 0}, {3, 2}, {3, 3}};

    public PullBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        builder.setTerminalCount(PullBoxGeometry.TERMINAL_COUNT);
        splices().buildCircuit(builder);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(!(level instanceof ServerLevel server))
            return;
        splices().prune();
        linkPanel(server, Direction.UP);
        linkPanel(server, Direction.DOWN);
    }

    // ---- gutter nipples ----

    /** The nipple between this box and the panel at that position, if there is one. */
    @Nullable
    private ConduitRunEntity nippleTo(BlockPos panelPos) {
        for(int h = 0; h < hubCount(); ++h) {
            var run = hubRun(h);
            if(run != null && run.isNipple() && run.endsAt(worldPosition, panelPos))
                return run;
        }
        return null;
    }

    private int freeHub(int[] hubs) {
        for(int h : hubs) {
            if(hubRun(h) == null)
                return h;
        }
        return -1;
    }

    private void linkPanel(ServerLevel server, Direction dir) {
        var panelPos = worldPosition.relative(dir);
        if(!(server.getBlockEntity(panelPos) instanceof BreakerPanelBlockEntity panel))
            return;
        var state = getBlockState();
        if(!(state.getBlock() instanceof PullBoxBlock) || BreakerPanelBlock.facing(panel.getBlockState()) != PullBoxBlock.facing(state))
            return;
        if(nippleTo(panelPos) != null)
            return;
        // The box knockout and panel knockout that line up best, so the nipple runs straight and covers nothing.
        int myHub = -1, panelHub = -1;
        for(var pair : dir == Direction.UP ? PAIRS_BELOW : PAIRS_ABOVE) {
            if(hubRun(pair[0]) == null && panel.hubRun(pair[1]) == null) {
                myHub = pair[0];
                panelHub = pair[1];
                break;
            }
        }
        if(myHub < 0 || panelHub < 0)
            return;
        var mine = new BlockWireEndpoint(worldPosition, hubTerminal(myHub));
        var theirs = new BlockWireEndpoint(panelPos, PanelLayout.hubTerminal(panel.spec(), panelHub));
        var nipple = ConduitRunEntity.createNipple(server, mine, theirs);
        if(nipple != null)
            server.tryAddFreshEntityWithPassengers(nipple);
    }

    // ---- splice host ----

    private List<SplicePoint> points;

    private boolean hasLugs() {
        return getBlockState().getBlock() instanceof PullBoxBlock box && box.hasLugs();
    }

    @Override
    public List<SplicePoint> points() {
        if(!hasLugs())
            return List.of();
        if(points == null) {
            points = new ArrayList<>();
            for(int k = 0; k < PullBoxGeometry.FRONT_COUNT; ++k) {
                var name = Lang.builder().translate("gui.terminal_cabinet.lug").style(ChatFormatting.GRAY).text(" ").add(ConductorColors.name(k)).component();
                points.add(new SplicePoint(PullBoxGeometry.FRONT_BASE + k, name, ConductorColors.rgb(k)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return hasLugs() && PullBoxGeometry.isFront(terminal);
    }

    @Override
    public int hubCount() {
        return PullBoxGeometry.HUB_COUNT;
    }

    @Override
    public Component hubName(int hub) {
        return PullBoxGeometry.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return PullBoxGeometry.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return PullBoxGeometry.hubOf(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return PullBoxGeometry.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return PullBoxGeometry.conductorHub(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return PullBoxGeometry.conductorOf(terminal);
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
        Lang.builder().translate(hasLugs() ? "gui.terminal_cabinet.title" : "gui.pull_box.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        splices().addGoggleLines(tooltip);
        return true;
    }
}
