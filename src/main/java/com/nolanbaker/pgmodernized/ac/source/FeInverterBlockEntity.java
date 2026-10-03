package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.WattmeterWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.ac.source.FeInverterBlock.*;

/**
 * Three alternating sources from each line to the neutral, like the creative source, but paid
 * for: every tick the real power the lines deliver, measured through a series resistor per phase,
 * is taken out of the FE buffer at the configured FE per joule and efficiency. When the buffer
 * cannot cover a tick the lines go dead (a brownout) and stay dead until it has refilled enough
 * to run for a second, so a starved inverter does not flicker at twenty hertz.
 */
public class FeInverterBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    public static final float SOURCE_R = 0.001f;
    public static final float SENSE = 1_000_000f;
    private static final double THIRD_TURN = 2 * Math.PI / 3;

    /** The FE side: fills from any side, never gives anything back out. */
    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(PgmConfig.INVERTER_BUFFER.get(), PgmConfig.INVERTER_MAX_INPUT.get(), 0);
        }

        void drain(int fe) {
            energy = Math.max(0, energy - fe);
        }

        void set(int fe) {
            energy = Mth.clamp(fe, 0, capacity);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int got = super.receiveEnergy(toReceive, simulate);
            if(got > 0 && !simulate)
                setChanged();
            return got;
        }
    }

    private final Buffer energy = new Buffer();
    private AcSourceBehaviour voltageBox, frequencyBox;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ACVoltageSourceCoupling[] sources;
    private WattmeterWire[] meters;
    private ElectricWire[] senses;
    private AcReadings.Filter[] readings;

    private float watts;
    private boolean brownout;
    private int lastFe;
    private int syncTimer;
    private float syncedWatts;
    private int syncedStored;
    private boolean syncedBrownout;

    public FeInverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        voltageBox = new AcSourceBehaviour(this, true);
        frequencyBox = new AcSourceBehaviour(this, false);
        voltageBox.withCallback(i -> setChanged());
        frequencyBox.withCallback(i -> setChanged());
        behaviours.add(voltageBox);
        behaviours.add(frequencyBox);
        super.addBehaviours(behaviours);
    }

    @Override
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        sources = new ACVoltageSourceCoupling[3];
        meters = new WattmeterWire[3];
        senses = new ElectricWire[3];
        readings = new AcReadings.Filter[3];
        var neutral = builder.terminalNode(N);
        float seriesR = resistance("output");
        for(int k = 0; k < 3; ++k) {
            var out = builder.terminalNode(L1 + k);
            var inner = builder.addInternalNode();
            sources[k] = new ACVoltageSourceCoupling(inner, neutral, (double) SOURCE_R, 0, 0);
            sources[k].setPhaseOffset(-THIRD_TURN * k);
            builder.add(sources[k]);
            senses[k] = builder.connect(SENSE, out, neutral);
            meters[k] = new WattmeterWire(seriesR, senses[k], inner, out);
            builder.add(meters[k]);
            readings[k] = new AcReadings.Filter();
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(sources == null)
            return;
        double total = 0;
        for(int k = 0; k < 3; ++k) {
            readings[k].sample(senses[k], meters[k], meters[k].drainRealPower());
            total += readings[k].realPower();
        }
        watts = (float) Math.max(0, total);

        // What this tick's delivery costs, and whether the buffer can pay it.
        double joulesPerTick = watts / 20.0 / Math.max(0.05, PgmConfig.INVERTER_EFFICIENCY.get());
        int fe = (int) Math.ceil(joulesPerTick * PgmConfig.INVERTER_FE_PER_JOULE.get());
        if(brownout) {
            int reserve = Math.max(lastFe * 20, energy.getMaxEnergyStored() / 20);
            if(energy.getEnergyStored() >= reserve)
                brownout = false;
        } else if(energy.getEnergyStored() < fe) {
            brownout = true;
        }
        if(!brownout && fe > 0) {
            energy.drain(fe);
            lastFe = fe;
        }

        float volts = brownout ? 0 : volts();
        float hz = hertz();
        for(var source : sources) {
            source.setFrequency(hz);
            source.setRmsVoltage(volts);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        if(Math.abs(watts - syncedWatts) > Math.max(5, syncedWatts * 0.05) || Math.abs(energy.getEnergyStored() - syncedStored) > energy.getMaxEnergyStored() / 100
                || brownout != syncedBrownout)
            sendData();
    }

    // ---- settings ----

    public float volts() {
        return voltageBox == null ? 0 : AcSourceBehaviour.voltsOf(voltageBox.getValue());
    }

    public float hertz() {
        return frequencyBox == null ? 0 : frequencyBox.getValue();
    }

    /** Snaps to the nearest nameplate voltage. Server side. */
    public void setVolts(double volts) {
        if(voltageBox == null)
            return;
        int best = 0;
        for(int i = 1; i < AcSourceBehaviour.VOLTS.length; ++i) {
            if(Math.abs(AcSourceBehaviour.VOLTS[i] - volts) < Math.abs(AcSourceBehaviour.VOLTS[best] - volts))
                best = i;
        }
        voltageBox.setValue(best);
        setChanged();
        sendData();
    }

    /** Server side. */
    public void setHertz(double hz) {
        if(frequencyBox == null)
            return;
        frequencyBox.setValue(Mth.clamp((int) Math.round(hz), 1, AcSourceBehaviour.MAX_HZ));
        setChanged();
        sendData();
    }

    public float watts() {
        return watts;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public int capacity() {
        return energy.getMaxEnergyStored();
    }

    public boolean brownedOut() {
        return brownout;
    }

    // ---- save and sync ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Brownout", brownout);
        tag.putInt("LastFe", lastFe);
        tag.putFloat("Watts", watts);
        if(clientPacket) {
            syncedWatts = watts;
            syncedStored = energy.getEnergyStored();
            syncedBrownout = brownout;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy.set(tag.getInt("Energy"));
        brownout = tag.getBoolean("Brownout");
        lastFe = tag.getInt("LastFe");
        watts = tag.getFloat("Watts");
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.fe_inverter.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.fe_inverter.setting", String.format("%.0f", volts()), String.format("%.0f", hertz()))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.fe_inverter.power", String.format("%.0f", watts)).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.fe_inverter.buffer", String.format("%,d", energy.getEnergyStored()), String.format("%,d", energy.getMaxEnergyStored()))
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(brownout)
            Lang.builder().translate("gui.fe_inverter.brownout").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        return true;
    }
}
