package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.bus.IBusJoin;
import com.nolanbaker.pgmodernized.bus.IBusLinkHost;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import static com.nolanbaker.pgmodernized.device.busduct.BusDuctFeedBlock.LAYOUT;
import static com.nolanbaker.pgmodernized.device.busduct.BusDuctFeedBlock.LUGS;

/** Four lugs, two knockouts spliced to them in the editor, and a bus a duct joins from behind, above or below. */
public class BusDuctFeedBlockEntity extends ElectricBlockEntity implements ISpliceHost, IBusJoin, IBusLinkHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    private List<SplicePoint> points;

    public BusDuctFeedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        builder.setTerminalCount(LAYOUT.terminalCount());
        splices().buildCircuit(builder);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private Direction facing() {
        var state = getBlockState();
        return state.getBlock() instanceof BusDuctFeedBlock ? BusDuctFeedBlock.facing(state) : Direction.NORTH;
    }

    @Override
    public int busJoin(Direction side, int k) {
        return side != facing() && k >= 0 && k < LUGS ? k : -1;
    }

    @Override
    public boolean busLinkValid(int terminal, BlockPos other, int otherTerminal) {
        return level != null && BusLinks.joinValid(level, worldPosition, terminal, other, otherTerminal);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            splices().prune();
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            var list = new ArrayList<SplicePoint>();
            for(int k = 0; k < LUGS; ++k)
                list.add(new SplicePoint(k, BusDuctBlock.busName(k), BusDuctBlock.busRgb(k)));
            points = List.copyOf(list);
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return LAYOUT.isPoint(terminal);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public int hubCount() {
        return LAYOUT.hubCount();
    }

    @Override
    public Component hubName(int hub) {
        return DeviceHubs.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return LAYOUT.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return LAYOUT.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return LAYOUT.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return LAYOUT.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return LAYOUT.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= LAYOUT.hubCount() ? null : SpliceSupport.runAt(this, hubTerminal(hub));
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
        Lang.builder().translate("gui.bus_duct_feed.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
