package com.nolanbaker.pgmodernized.device.sync;

import com.nolanbaker.pgmodernized.device.motor.PhaseSenseWire;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.sync.SynchroscopeBlock.*;

/**
 * Two high-resistance sense branches time the zero crossings of the bus and the incoming voltage
 * sub-tick by sub-tick. Each gives its frequency and where in its cycle it stands at the end of
 * the tick; the difference of the two positions is the phase angle, the difference of the two
 * frequencies the slip. Ready to close when the slip is under a tenth of a hertz, the angle within
 * ten degrees and the voltages within five percent.
 */
public class SynchroscopeBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    public static final float SENSE = 1_000_000f;
    public static final float SYNC_SLIP_HZ = 0.1f, SYNC_ANGLE_DEG = 10f, SYNC_VOLTS_FRACTION = 0.05f;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private PhaseSenseWire busSense, incomingSense;
    private AcReadings.Filter busReading, incomingReading;

    // State, synced to the client.
    private float busHz, incomingHz, busVolts, incomingVolts, angle, slip;
    private boolean inSync;
    private long syncedAt;

    public SynchroscopeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        busSense = new PhaseSenseWire(SENSE, builder.terminalNode(BUS), builder.terminalNode(BUS_N));
        incomingSense = new PhaseSenseWire(SENSE, builder.terminalNode(INCOMING), builder.terminalNode(INCOMING_N));
        builder.add(busSense);
        builder.add(incomingSense);
        busReading = new AcReadings.Filter();
        incomingReading = new AcReadings.Filter();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(busSense == null)
            return;
        busReading.sample(busSense);
        incomingReading.sample(incomingSense);
        busHz = (float) busSense.frequency();
        incomingHz = (float) incomingSense.frequency();
        busVolts = (float) busReading.rmsVoltage();
        incomingVolts = (float) incomingReading.rmsVoltage();
        slip = incomingHz - busHz;
        double busPhase = busSense.phaseFraction(), incomingPhase = incomingSense.phaseFraction();
        if(busPhase >= 0 && incomingPhase >= 0) {
            double turns = incomingPhase - busPhase;
            angle = (float) Mth.wrapDegrees(turns * 360);
        }
        boolean ready = busHz > 0 && incomingHz > 0
                && Math.abs(slip) <= SYNC_SLIP_HZ
                && Math.abs(angle) <= SYNC_ANGLE_DEG
                && Math.abs(incomingVolts - busVolts) <= SYNC_VOLTS_FRACTION * Math.max(busVolts, 1);
        if(ready != inSync) {
            inSync = ready;
            var state = getBlockState();
            if(level != null && !level.isClientSide && state.getBlock() instanceof SynchroscopeBlock)
                level.setBlock(worldPosition, state.setValue(SYNCED, inSync), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            sendData();
    }

    // ---- readings ----

    public float busFrequency() {
        return busHz;
    }

    public float incomingFrequency() {
        return incomingHz;
    }

    public float busVoltage() {
        return busVolts;
    }

    public float incomingVoltage() {
        return incomingVolts;
    }

    /** Hertz the incoming runs faster than the bus (negative: slower). */
    public float slip() {
        return slip;
    }

    /** Degrees the incoming leads the bus, -180..180. */
    public float angle() {
        return angle;
    }

    public boolean inSync() {
        return inSync;
    }

    /** The needle on the client, between packets: the last angle carried on at the slip. */
    public float angleNow(float partialTicks) {
        if(level == null)
            return angle;
        float ticks = level.getGameTime() - syncedAt + partialTicks;
        return Mth.wrapDegrees(angle + slip * 360f * ticks / 20f);
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if(!clientPacket)
            return;
        tag.putFloat("BusHz", busHz);
        tag.putFloat("IncHz", incomingHz);
        tag.putFloat("BusV", busVolts);
        tag.putFloat("IncV", incomingVolts);
        tag.putFloat("Angle", angle);
        tag.putFloat("Slip", slip);
        tag.putBoolean("InSync", inSync);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if(!clientPacket)
            return;
        busHz = tag.getFloat("BusHz");
        incomingHz = tag.getFloat("IncHz");
        busVolts = tag.getFloat("BusV");
        incomingVolts = tag.getFloat("IncV");
        angle = tag.getFloat("Angle");
        slip = tag.getFloat("Slip");
        inSync = tag.getBoolean("InSync");
        syncedAt = level == null ? 0 : level.getGameTime();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.synchroscope.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.synchroscope.bus", String.format("%.2f", busHz), String.format("%.0f", busVolts)).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.synchroscope.incoming", String.format("%.2f", incomingHz), String.format("%.0f", incomingVolts)).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.synchroscope.slip", String.format("%+.2f", slip), String.format("%+.0f", angle)).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate(inSync ? "gui.synchroscope.ready" : "gui.synchroscope.wait").style(inSync ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        return true;
    }
}
