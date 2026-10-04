package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
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
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.ac.source.RfConnectorBlock.*;

/**
 * Power Grid's Device Connector for three-phase. Three loads in star from L1, L2, L3 to the
 * neutral are sized every tick to draw just the power the Forge Energy side is taking: the FE
 * missing from the buffer, at Power Grid's FE per watt, is the demand. The real power they draw
 * is banked as FE and handed out each tick to every neighbour that accepts it, up to the output
 * limit. One way only: the FE side gives and never takes, so an FE loop back through an inverter
 * can only lose what both lose.
 */
public class RfConnectorBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost {
    /** The loads when nothing is wanted: as good as open. */
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
    private DeviceSpliceHost deviceHubs;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ElectricWire[] loads;
    private AcReadings.Filter[] readings;

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
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, RfConnectorBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        loads = new ElectricWire[3];
        readings = new AcReadings.Filter[3];
        var neutral = builder.terminalNode(N);
        for(int k = 0; k < 3; ++k) {
            loads[k] = builder.connect(OPEN, builder.terminalNode(L1 + k), neutral);
            readings[k] = new AcReadings.Filter();
        }
        appliedResistance = OPEN;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(loads == null || level == null)
            return;
        double v = 0, i = 0, p = 0;
        for(int k = 0; k < 3; ++k) {
            readings[k].sample(loads[k]);
            double rv = readings[k].rmsVoltage(), ri = readings[k].rmsCurrent();
            v += rv;
            i += ri;
            p += rv * ri;
        }
        volts = (float) (v / 3);
        amps = (float) (i / 3);
        watts = (float) Math.max(0, p);

        // Bank what the loads drew this tick.
        double rate = PgmConfig.fePerWattTick();
        double efficiency = Math.max(0.05, PgmConfig.RF_EFFICIENCY.get());
        feFraction += watts * rate * efficiency;
        int whole = (int) feFraction;
        if(whole > 0) {
            energy.add(whole);
            feFraction -= whole;
        }

        // Hand it out, then size the loads for what is still wanted.
        supplied = push();
        int wanted = Math.min(energy.getMaxEnergyStored() - energy.getEnergyStored(), PgmConfig.RF_MAX_OUTPUT.get());
        double targetWatts = rate > 0 ? wanted / rate / efficiency : 0;
        float r;
        if(targetWatts <= 0 || volts < 1)
            r = OPEN;
        else
            r = (float) Mth.clamp(volts * volts / (targetWatts / 3), Math.max(0.001f, resistance("load")), OPEN);
        if(Math.abs(r - appliedResistance) > appliedResistance * 1e-3) {
            for(var load : loads)
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
        deviceHubs().lazyTick();
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
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
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
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
        Lang.builder().translate("gui.rf_connector.drawing", String.format("%.2f", watts / 1000), String.format("%.0f", volts), String.format("%.1f", amps))
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.rf_connector.buffer", String.format("%,d", energy.getEnergyStored()), String.format("%,d", energy.getMaxEnergyStored()))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
