package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.ac.source.RfConnectorBlock.*;

/**
 * Power Grid's Device Connector for Power Grid's AC, single phase. One load from line to neutral
 * is sized every tick to draw just the power the Forge Energy side is taking: the FE missing
 * from the buffer, at Power Grid's FE per watt, is the demand. The real power it draws is banked
 * as FE and handed out each tick to every neighbour that accepts it, up to the output limit. One
 * way only: the FE side gives and never takes. Whenever the wires pulled through its run change,
 * the first two are spliced to line and neutral by themselves, so there is nothing to edit.
 */
public class RfConnectorBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, ISpliceHost {
    /** The load when nothing is wanted: as good as open. */
    private static final float OPEN = 1_000_000f;

    /** The FE side: gives to any side, never takes anything in. */
    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(PgmConfig.RF_BUFFER.get(), 0, PgmConfig.RF_MAX_OUTPUT.get());
        }

        void add(int fe) {
            energy = Math.min(capacity, energy + fe);
        }

        void take(int fe) {
            energy = Math.max(0, energy - fe);
        }

        void set(int fe) {
            energy = Mth.clamp(fe, 0, capacity);
        }
    }

    private final Buffer energy = new Buffer();
    private SpliceSupport splices;
    private List<SplicePoint> points;
    private int landedKey = -1;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ElectricWire load;
    private AcReadings.Filter reading;

    private float appliedResistance = -1;
    private double feFraction;
    private float volts, amps, watts;
    private int supplied;
    private float syncedWatts;
    private int syncedSupplied;

    public RfConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(TERMINAL_COUNT);
        splices().buildCircuit(builder);
        load = builder.connect(OPEN, builder.terminalNode(TERMINAL_LINE), builder.terminalNode(TERMINAL_NEUTRAL));
        reading = new AcReadings.Filter();
        appliedResistance = OPEN;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(load == null || level == null)
            return;
        reading.sample(load);
        volts = (float) reading.rmsVoltage();
        amps = (float) reading.rmsCurrent();
        watts = (float) Math.max(0, volts * amps);

        // Bank what the load drew this tick.
        double rate = PgmConfig.fePerWattTick();
        double efficiency = Math.max(0.05, PgmConfig.RF_EFFICIENCY.get());
        feFraction += watts * rate * efficiency;
        int whole = (int) feFraction;
        if(whole > 0) {
            energy.add(whole);
            feFraction -= whole;
        }

        // Hand it out, then size the load for what is still wanted.
        supplied = push();
        int wanted = Math.min(energy.getMaxEnergyStored() - energy.getEnergyStored(), PgmConfig.RF_MAX_OUTPUT.get());
        double targetWatts = rate > 0 ? wanted / rate / efficiency : 0;
        float r;
        if(targetWatts <= 0 || volts < 1)
            r = OPEN;
        else
            r = (float) Mth.clamp(volts * volts / targetWatts, Math.max(0.001f, resistance("load")), OPEN);
        if(Math.abs(r - appliedResistance) > appliedResistance * 1e-3) {
            load.setResistance(r);
            appliedResistance = r;
        }
    }

    /** Gives every neighbour that takes Forge Energy its share of the output limit, and says how much went. */
    private int push() {
        int budget = Math.min(energy.getEnergyStored(), PgmConfig.RF_MAX_OUTPUT.get());
        int total = 0;
        for(var side : Direction.values()) {
            if(budget <= 0)
                break;
            var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(side), side.getOpposite());
            if(storage == null || !storage.canReceive())
                continue;
            int got = storage.receiveEnergy(budget, false);
            if(got > 0) {
                energy.take(got);
                budget -= got;
                total += got;
            }
        }
        if(total > 0)
            setChanged();
        return total;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        // Land the first two conductors on line and neutral whenever the run's wires change.
        var run = hubRun(0);
        int key = 0;
        if(run != null) {
            for(var conductor : run.conductors())
                key |= 1 << conductor.slot();
        }
        if(key != landedKey) {
            landedKey = key;
            if(key != 0)
                splices().land(0);
        }
        if(Math.abs(watts - syncedWatts) > Math.max(5, syncedWatts * 0.05) || Math.abs(supplied - syncedSupplied) > Math.max(5, syncedSupplied * 0.05))
            sendData();
    }

    public float watts() {
        return watts;
    }

    public float volts() {
        return volts;
    }

    public float amps() {
        return amps;
    }

    public int supplied() {
        return supplied;
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = List.of(
                    new SplicePoint(TERMINAL_LINE, Lang.builder().translate("rf_connector.line").style(ChatFormatting.RED).component(), IDecoratedTerminal.RED),
                    new SplicePoint(TERMINAL_NEUTRAL, Lang.builder().translate("rf_connector.neutral").style(ChatFormatting.BLUE).component(), IDecoratedTerminal.BLUE));
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return terminal == TERMINAL_LINE || terminal == TERMINAL_NEUTRAL;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public int hubCount() {
        return 1;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("conduit_socket.hub").style(ChatFormatting.AQUA).component();
    }

    @Override
    public int hubTerminal(int hub) {
        return TERMINAL_HUB;
    }

    @Override
    public int hubAt(int terminal) {
        return terminal == TERMINAL_HUB ? 0 : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return CONDUCTOR_BASE + conductor;
    }

    @Override
    public int hubOf(int terminal) {
        return terminal >= CONDUCTOR_BASE && terminal < TERMINAL_COUNT ? 0 : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return terminal - CONDUCTOR_BASE;
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub == 0 ? SpliceSupport.runAt(this, TERMINAL_HUB) : null;
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        splices().write(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putFloat("Amps", amps);
            tag.putFloat("Watts", watts);
            tag.putInt("Supplied", supplied);
            syncedWatts = watts;
            syncedSupplied = supplied;
        }
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        splices().read(tag);
        energy.set(tag.getInt("Energy"));
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            amps = tag.getFloat("Amps");
            watts = tag.getFloat("Watts");
            supplied = tag.getInt("Supplied");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.rf_connector.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.rf_connector.supplying", String.format("%,d", supplied), String.format("%,d", supplied * 20))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.rf_connector.drawing", String.format("%.0f", watts), String.format("%.0f", volts), String.format("%.2f", amps))
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.rf_connector.buffer", String.format("%,d", energy.getEnergyStored()), String.format("%,d", energy.getMaxEnergyStored()))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip);
        return true;
    }
}
