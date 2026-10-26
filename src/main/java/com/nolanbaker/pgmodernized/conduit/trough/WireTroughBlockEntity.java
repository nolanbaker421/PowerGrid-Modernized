package com.nolanbaker.pgmodernized.conduit.trough;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.conduit.trough.TroughGeometry.Pattern;
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
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The row's one host. Knockouts are numbered across the sections, {@link TroughGeometry} style;
 * the splices, the circuit and the editor are all here, and the extensions answer for their own
 * knockouts at their own positions. Conduit between a trough and a panel is laid by hand, like
 * any run.
 */
public class WireTroughBlockEntity extends ElectricBlockEntity implements ISpliceHost, ITroughSection, IHaveGoggleInformation {
    private SpliceSupport splices;

    public WireTroughBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public int section() {
        return 0;
    }

    public int sections() {
        return WireTroughBlock.sectionsOf(getBlockState());
    }

    public Direction facing() {
        var state = getBlockState();
        return state.getBlock() instanceof WireTroughBlock ? WireTroughBlock.facing(state) : Direction.NORTH;
    }

    /** Where a section of this row stands. */
    public BlockPos sectionPos(int section) {
        return worldPosition.relative(WireTroughBlock.rowDirection(facing()), section);
    }

    /** A section's top and bottom patterns, from its own block state. */
    private Pattern pattern(int section, boolean top) {
        if(level == null)
            return Pattern.TWO;
        var state = level.getBlockState(sectionPos(section));
        if(state.getBlock() instanceof WireTroughBlock)
            return state.getValue(top ? WireTroughBlock.TOP : WireTroughBlock.BOTTOM);
        if(state.getBlock() instanceof WireTroughExtensionBlock)
            return state.getValue(top ? WireTroughExtensionBlock.TOP : WireTroughExtensionBlock.BOTTOM);
        return Pattern.TWO;
    }

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(TroughGeometry.TERMINAL_COUNT);
        splices().buildCircuit(builder);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(!(level instanceof ServerLevel server))
            return;
        splices().prune();
    }

    // ---- splice host ----

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
        if(hub < 0 || hub >= hubCount())
            return null;
        int section = hub / TroughGeometry.HUBS_PER_SECTION;
        return TroughGeometry.maxConduit(pattern(section, true), pattern(section, false), hub % TroughGeometry.HUBS_PER_SECTION);
    }

    @Override
    public int landings(int hub) {
        return TroughGeometry.LANDINGS;
    }

    @Override
    public int hubCount() {
        return sections() * TroughGeometry.HUBS_PER_SECTION;
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
        int hub = TroughGeometry.hubAt(terminal);
        return hub < hubCount() ? hub : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return TroughGeometry.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        int hub = TroughGeometry.hubOf(terminal);
        return hub < hubCount() ? hub : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return TroughGeometry.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        if(hub < 0 || hub >= hubCount())
            return null;
        return SpliceSupport.runAt(this, hubTerminal(hub), sectionPos(hub / TroughGeometry.HUBS_PER_SECTION));
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
        Lang.builder().translate("gui.trough.title", sections()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(int s = 0; s < sections(); ++s)
            Lang.builder().translate("gui.trough.faces", s + 1).text(" ").add(pattern(s, true).label()).text(", ").add(pattern(s, false).label())
                    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
