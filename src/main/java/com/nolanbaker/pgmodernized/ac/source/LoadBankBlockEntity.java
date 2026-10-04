package com.nolanbaker.pgmodernized.ac.source;

import com.nolanbaker.pgmodernized.network.packets.LoadBankPayload;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.ac.source.LoadBankBlock.*;

/**
 * Three resistors in star. The door sets a load and the voltage it is rated at, which fixes the
 * resistance of each phase: a third of the load at that voltage. At any other voltage the bank
 * draws what a resistor does, and the goggles show what it really draws. It never overheats: a
 * load bank is built to burn its power off.
 */
public class LoadBankBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost, LoadBankPayload.ILoadBankSettings {
    private LoadBankBehaviour loadBox, voltsBox;
    private DeviceSpliceHost deviceHubs;
    /** Typed settings; zero means the value box rules. */
    private double exactWatts, exactVolts;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ElectricWire[] resistors;
    private AcReadings.Filter[] readings;

    private float appliedResistance = -1;
    private float volts, amps, watts;
    private float syncedWatts;

    public LoadBankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        loadBox = new LoadBankBehaviour(this, true);
        voltsBox = new LoadBankBehaviour(this, false);
        loadBox.withCallback(i -> {
            exactWatts = 0;
            setChanged();
        });
        voltsBox.withCallback(i -> {
            exactVolts = 0;
            setChanged();
        });
        behaviours.add(loadBox);
        behaviours.add(voltsBox);
        super.addBehaviours(behaviours);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LoadBankBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    /** Load setting, in watts for the three phases together: the typed figure, else the box. */
    public double ratedWatts() {
        if(exactWatts > 0)
            return exactWatts;
        return loadBox == null ? 0 : LoadBankBehaviour.kilowattsOf(loadBox.getValue()) * 1000;
    }

    public float ratedVolts() {
        if(exactVolts > 0)
            return (float) exactVolts;
        return voltsBox == null ? 0 : AcSourceBehaviour.voltsOf(voltsBox.getValue());
    }

    @Override
    public void setExact(double watts, double volts) {
        exactWatts = Math.max(0, Math.min(watts, 1e9));
        exactVolts = Math.max(0, Math.min(volts, 1e6));
        setChanged();
        sendData();
    }

    @Override
    public double exactWatts() {
        return exactWatts;
    }

    @Override
    public double exactVolts() {
        return exactVolts;
    }

    /** Resistance of each phase for the setting: a third of the load at the rated line-to-neutral voltage. */
    public float phaseResistance() {
        double perPhase = ratedWatts() / 3;
        float v = ratedVolts();
        return perPhase > 0 && v > 0 ? (float) (v * v / perPhase) : 1e6f;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        resistors = new ElectricWire[3];
        readings = new AcReadings.Filter[3];
        var neutral = builder.terminalNode(N);
        float r = phaseResistance();
        for(int k = 0; k < 3; ++k) {
            resistors[k] = builder.connect(r, builder.terminalNode(L1 + k), neutral);
            readings[k] = new AcReadings.Filter();
        }
        appliedResistance = r;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(resistors == null)
            return;
        float r = phaseResistance();
        if(Math.abs(r - appliedResistance) > appliedResistance * 1e-4) {
            for(var resistor : resistors)
                resistor.setResistance(r);
            appliedResistance = r;
        }
        double v = 0, i = 0, p = 0;
        for(int k = 0; k < 3; ++k) {
            readings[k].sample(resistors[k]);
            double rv = readings[k].rmsVoltage(), ri = readings[k].rmsCurrent();
            v += rv;
            i += ri;
            p += rv * ri;
        }
        volts = (float) (v / 3);
        amps = (float) (i / 3);
        watts = (float) p;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(Math.abs(watts - syncedWatts) > Math.max(5, syncedWatts * 0.05))
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putDouble("ExactWatts", exactWatts);
        tag.putDouble("ExactVolts", exactVolts);
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putFloat("Amps", amps);
            tag.putFloat("Watts", watts);
            syncedWatts = watts;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        exactWatts = tag.getDouble("ExactWatts");
        exactVolts = tag.getDouble("ExactVolts");
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            amps = tag.getFloat("Amps");
            watts = tag.getFloat("Watts");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.load_bank.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.load_bank.setting", String.format("%.2f", ratedWatts() / 1000), String.format("%.0f", ratedVolts()),
                String.format("%.3f", phaseResistance())).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(exactWatts > 0 || exactVolts > 0)
            Lang.builder().translate("gui.load_bank.typed").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.load_bank.drawing", String.format("%.1f", watts / 1000), String.format("%.0f", volts), String.format("%.1f", amps))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
