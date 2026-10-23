package com.nolanbaker.pgmodernized.device.rectifier;

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
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.CapacitorWire;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.rectifier.RectifierBlock.*;

/**
 * Six power diodes in a bridge: each AC lug through a diode up to the positive rail and down from
 * the negative rail, a smoothing capacitor across the rails, and the rails out on DC+ and DC-.
 * On three phases the output is six-pulse direct current at about 1.35 times the line-to-line
 * voltage; on two lugs it is a full-wave single-phase rectifier, with the ripple the capacitor
 * leaves. On a direct-current feed it simply passes it, with the two diode drops.
 */
public class RectifierBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost {
    /** Power diode: a microamp of leakage, two milliohms of silicon, 1 kV before it breaks over. */
    private static final double DIODE_IS = 1e-6, DIODE_RS = 0.002, DIODE_T = 22, DIODE_N = 1.5, DIODE_VBR = 1000, DIODE_IBR = 1e-6;
    private static final float SENSE = 1_000_000f;
    /** Heat the bridge can shed: a kilowatt of diode loss before it cooks. */
    private static final float MAX_LOSS_WATTS = 1000;

    private SmoothingBehaviour smoothing;
    private DeviceSpliceHost deviceHubs;
    // No initialisers: buildCircuit runs from the superclass constructor.
    private PNJunctionWire[] diodes;
    private CapacitorWire capacitor;
    private ElectricWire outPlus, outMinus, sense;
    private AcReadings.Filter voltsReading, ampsReading;
    private double builtFarads = -1;
    private boolean needsRebuild;

    private float volts, amps, lossWatts;
    private float syncedAmps;

    public RectifierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        smoothing = new SmoothingBehaviour(this);
        smoothing.withCallback(i -> onSmoothing());
        behaviours.add(smoothing);
        super.addBehaviours(behaviours);
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.forMaxPower(this, MAX_LOSS_WATTS, 1.2f);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    public double farads() {
        return smoothing == null ? SmoothingBehaviour.farads(SmoothingBehaviour.DEFAULT) : smoothing.farads();
    }

    /** The capacitor's value is fixed when it is made: a new setting lays the circuit again. */
    private void onSmoothing() {
        setChanged();
        if(Math.abs(farads() - builtFarads) < 1e-9)
            return;
        if(level == null || level.isClientSide) {
            needsRebuild = level == null;
            return;
        }
        if(electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
    }

    private static PNJunctionWire diode(IElectricNode anode, IElectricNode cathode) {
        return new PNJunctionWire(DIODE_IS, DIODE_RS, DIODE_T, DIODE_N, DIODE_VBR, DIODE_IBR, anode, cathode);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var plus = builder.addInternalNode();
        var minus = builder.addInternalNode();
        diodes = new PNJunctionWire[6];
        for(int k = 0; k < 3; ++k) {
            var ac = builder.terminalNode(L1 + k);
            diodes[2 * k] = diode(ac, plus);
            diodes[2 * k + 1] = diode(minus, ac);
            builder.add(diodes[2 * k]);
            builder.add(diodes[2 * k + 1]);
        }
        double farads = farads();
        builtFarads = farads;
        capacitor = null;
        if(farads > 0) {
            capacitor = new CapacitorWire(farads, plus, minus);
            builder.add(capacitor);
        }
        outPlus = builder.connect(resistance("output"), plus, builder.terminalNode(DC_PLUS));
        outMinus = builder.connect(resistance("output"), minus, builder.terminalNode(DC_MINUS));
        sense = builder.connect(SENSE, plus, minus);
        voltsReading = new AcReadings.Filter();
        ampsReading = new AcReadings.Filter();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if(needsRebuild && level != null && !level.isClientSide) {
            needsRebuild = false;
            onSmoothing();
        }
    }

    @Override
    public void electricalTick() {
        if(diodes == null || sense == null || !sense.isConverged())
            return;
        voltsReading.sample(sense);
        ampsReading.sample(outPlus);
        volts = (float) voltsReading.rmsVoltage();
        amps = (float) ampsReading.rmsCurrent();
        // The diodes' loss heats the cabinet; the output resistors are a bookkeeping detail.
        double loss = 0;
        for(var diode : diodes) {
            double p = Math.abs(diode.power());
            if(Double.isFinite(p))
                loss += p;
            applyPower(diode);
        }
        lossWatts = (float) loss;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(Math.abs(amps - syncedAmps) > Math.max(0.05f, syncedAmps * 0.05f))
            sendData();
    }

    // ---- readings ----

    /** RMS of the rail-to-rail voltage, which on a smoothed output is near its mean. */
    public float volts() {
        return volts;
    }

    public float amps() {
        return amps;
    }

    public float lossWatts() {
        return lossWatts;
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putFloat("Amps", amps);
            tag.putFloat("Loss", lossWatts);
            syncedAmps = amps;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            amps = tag.getFloat("Amps");
            lossWatts = tag.getFloat("Loss");
        } else if(Math.abs(farads() - builtFarads) > 1e-9) {
            needsRebuild = true;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.rectifier.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.rectifier.output").style(ChatFormatting.GRAY).text(" ")
                .add(Lang.builder().text(String.format("%.1f ", volts)).add(Unit.VOLTAGE.get()).text(String.format("  %.2f ", amps)).add(Unit.CURRENT.get())
                        .text(String.format("  %.0f W", volts * amps)).style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);
        Lang.builder().translate("gui.rectifier.smoothing_is", Math.round(farads() * 1000)).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(lossWatts >= 1)
            Lang.builder().translate("gui.rectifier.loss", String.format("%.0f", lossWatts)).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
