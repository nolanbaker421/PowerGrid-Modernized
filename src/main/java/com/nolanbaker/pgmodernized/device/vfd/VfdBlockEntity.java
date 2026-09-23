package com.nolanbaker.pgmodernized.device.vfd;

import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.vfd.VfdBlock.*;

/**
 * Computer-controlled ideal converter (on an alternating feed, a variable transformer holding the
 * output RMS at the setpoint): an isolating transformer coupling whose ratio is
 * re-tuned every tick so the output holds the commanded voltage regardless of the input.
 * Power is conserved (the input side draws whatever the load takes, plus a small conversion loss).
 *
 * The coupling's series resistance is never changed at runtime: the 2-primary coupling stamps it
 * with the opposite sign from the base class' setResistance(), which corrupts the matrix. Enabling
 * and disabling therefore rebuilds the internal circuit with or without the coupling instead.
 */
public class VfdBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, INetworkJack, IDeviceSpliceHost {
    private DeviceSpliceHost deviceHubs;

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, VfdBlock.LAYOUT);
        return deviceHubs;
    }

    public static final float MAX_VOLTAGE = 2000.0f;
    public static final float MAX_CURRENT = 3.0f;
    private static final float MAX_RATIO = 500.0f;
    private static final float MIN_RATIO = 0.001f;
    private static final float MIN_INPUT_VOLTAGE = 0.5f;
    private static final float CONVERSION_LOSS = 0.02f;
    private static final float RATIO_SLEW = 0.35f;
    private static final float CAP_MARGIN = 0.98f;   // land just under the current limit
    private static final float CAP_RECOVERY = 1.02f; // ~2 % per tick ceiling recovery
    private static final float INPUT_SAG_FRACTION = 0.5f;
    private static final float SENSE_RESISTANCE = 1_000_000f;  // voltmeter branches across input and output
    private static final float SHUNT_RESISTANCE = 0.001f;      // ammeter in series with the output      // back off when the input drops below half its unloaded voltage

    private float setpoint = 0;
    private float currentLimit = MAX_CURRENT;
    private boolean enabled = true;

    // No initializers here: SmartBlockEntity's constructor calls buildCircuit() before they would run.
    private float ratio;
    private float ratioCap; // 0 means "not initialised yet" (see electricalTick)
    private float inputReference;
    private float inputVoltage, outputVoltage, outputCurrent;

    private TransformerCoupling coupling;
    private FloatingNode inPos, inNeg, outPos, outNeg;
    private ElectricWire inputSense, outputSense, outputShunt;
    private final AcReadings.Filter inputReading = new AcReadings.Filter();
    private final AcReadings.Filter outputReading = new AcReadings.Filter();

    private final JackSupport jack = new JackSupport(this, false);

    public VfdBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return IElectric.getTerminalPos(level, worldPosition, JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return JACK;
    }

    @Override
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        inPos = builder.terminalNode(INPUT_POSITIVE);
        inNeg = builder.terminalNode(INPUT_NEGATIVE);
        outPos = builder.terminalNode(OUTPUT_POSITIVE);
        outNeg = builder.terminalNode(OUTPUT_NEGATIVE);
        if(ratio == 0)
            ratio = MIN_RATIO;
        // Meters read the RMS a branch accumulated over the tick; a node voltage sampled once per
        // tick on the AC build is whichever point of the waveform the tick happened to land on.
        inputSense = builder.connect(SENSE_RESISTANCE, inPos, inNeg);
        outputSense = builder.connect(SENSE_RESISTANCE, outPos, outNeg);
        if(enabled) {
            var outMid = builder.addInternalNode();
            coupling = builder.couple(ratio, resistance("output"), inPos, inNeg, outMid, outNeg);
            outputShunt = builder.connect(SHUNT_RESISTANCE, outMid, outPos);
        } else {
            coupling = null;
            outputShunt = null;
        }
    }

    @Override
    public void electricalTick() {
        if(inputSense != null) {
            inputReading.sample(inputSense);
            inputVoltage = finite((float) inputReading.signedRmsVoltage());
        }
        if(coupling == null || outputShunt == null) {
            outputVoltage = 0;
            outputCurrent = 0;
            return;
        }
        outputReading.sample(outputSense, outputShunt, Double.NaN);
        outputVoltage = finite((float) outputReading.signedRmsVoltage());
        outputCurrent = finite((float) outputReading.rmsCurrent());

        // Current limiter: a ceiling on the ratio that drops in proportion to the overshoot and
        // recovers slowly, so the output settles at the limit instead of bouncing around it.
        if(ratioCap <= 0)
            ratioCap = MAX_RATIO;
        // Remember the input's unloaded voltage so a source that sags under load can be recognised and held
        // at its maximum-power point instead of collapsing. Measured directly whenever the drive is unloaded;
        // while loaded it only tracks rises (a weakening source pushes the ratio down until unloaded, which re-measures).
        float absVin = Math.abs(inputVoltage);
        boolean unloaded = Math.abs(ratio) <= MIN_RATIO * 1.5f;
        inputReference = unloaded ? absVin : Math.max(absVin, inputReference);
        boolean inputSagging = absVin < inputReference * INPUT_SAG_FRACTION;
        boolean overCurrent = outputCurrent > currentLimit;
        if((overCurrent || inputSagging) && Math.abs(ratio) > MIN_RATIO) {
            float factor = overCurrent ? currentLimit / outputCurrent : absVin / (inputReference * INPUT_SAG_FRACTION);
            ratioCap = Math.max(MIN_RATIO, Math.min(ratioCap, Math.abs(ratio)) * factor * CAP_MARGIN);
        } else {
            ratioCap = Math.min(MAX_RATIO, ratioCap * CAP_RECOVERY + MIN_RATIO);
        }

        float target;
        if(setpoint == 0 || Math.abs(inputVoltage) < MIN_INPUT_VOLTAGE) {
            target = Math.copySign(MIN_RATIO, ratio);
        } else {
            float magnitude = Mth.clamp(Math.abs(setpoint / inputVoltage), MIN_RATIO, MAX_RATIO);
            magnitude = Math.min(magnitude, ratioCap);
            target = Math.copySign(magnitude, setpoint * inputVoltage);
        }

        float newRatio;
        if(Math.signum(target) != Math.signum(ratio)) {
            newRatio = Math.copySign(MIN_RATIO, target);   // reverse: pass through zero first
        } else if(Math.abs(target) < Math.abs(ratio)) {
            newRatio = target;                              // reduce immediately (current limit, lower setpoint)
        } else {
            newRatio = ratio + (target - ratio) * RATIO_SLEW; // raise gradually
        }
        if(Math.abs(newRatio - ratio) > 1e-5f) {
            ratio = newRatio;
            coupling.setRatio(ratio);
        }

        if(thermalBehaviour != null) {
            float loss = outputCurrent * outputCurrent * resistance("output")
                    + CONVERSION_LOSS * Math.abs(outputVoltage * outputCurrent);
            thermalBehaviour.applyTickPower(loss);
        }
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
        if(level != null && !level.isClientSide)
            sendData();
    }

    public void setVoltage(float voltage) {
        voltage = Float.isFinite(voltage) ? Mth.clamp(voltage, -MAX_VOLTAGE, MAX_VOLTAGE) : 0;
        if(setpoint == voltage)
            return;
        setpoint = voltage;
        setChanged();
    }

    public float getVoltage() {
        return setpoint;
    }

    public void setCurrentLimit(float limit) {
        currentLimit = Float.isFinite(limit) ? Mth.clamp(limit, 0, MAX_CURRENT) : MAX_CURRENT;
        setChanged();
    }

    public float getCurrentLimit() {
        return currentLimit;
    }

    public void setEnabled(boolean value) {
        if(enabled == value)
            return;
        enabled = value;
        ratio = MIN_RATIO; // soft start when re-enabled
        if(electricBehaviour != null && level != null && !level.isClientSide)
            electricBehaviour.rebuildCircuit(false);
        setChanged();
        sendData();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getInputVoltage() {
        return inputVoltage;
    }

    public float getOutputVoltage() {
        return outputVoltage;
    }

    public float getOutputCurrent() {
        return outputCurrent;
    }

    public float getInputCurrent() {
        return outputCurrent * Math.abs(ratio);
    }

    public float getPower() {
        return Math.abs(outputVoltage) * outputCurrent;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        boolean wasEnabled = enabled;
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        setpoint = tag.getFloat("Setpoint");
        currentLimit = tag.contains("CurrentLimit") ? tag.getFloat("CurrentLimit") : MAX_CURRENT;
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        if(clientPacket) {
            inputVoltage = tag.getFloat("VIn");
            outputVoltage = tag.getFloat("VOut");
            outputCurrent = tag.getFloat("IOut");
            ratio = tag.getFloat("Ratio");
        } else if(enabled != wasEnabled && electricBehaviour != null && level != null) {
            // Loaded from disk with a different enable state than the circuit was built with.
            electricBehaviour.rebuildCircuit(false);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putFloat("Setpoint", setpoint);
        tag.putFloat("CurrentLimit", currentLimit);
        tag.putBoolean("Enabled", enabled);
        if(clientPacket) {
            tag.putFloat("VIn", inputVoltage);
            tag.putFloat("VOut", outputVoltage);
            tag.putFloat("IOut", outputCurrent);
            tag.putFloat("Ratio", ratio);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.vfd.input").style(ChatFormatting.GRAY).forGoggles(tooltip);
        line(tooltip, inputVoltage, Unit.VOLTAGE, ChatFormatting.AQUA);
        line(tooltip, getInputCurrent(), Unit.CURRENT, ChatFormatting.AQUA);
        Lang.builder().translate("gui.vfd.output").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(!enabled) {
            Lang.builder().translate("gui.vfd.disabled").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            line(tooltip, outputVoltage, Unit.VOLTAGE, ChatFormatting.GOLD);
            line(tooltip, outputCurrent, Unit.CURRENT, ChatFormatting.GOLD);
            line(tooltip, getPower(), Unit.POWER, ChatFormatting.GOLD);
        }
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }

    private static void line(List<Component> tooltip, float value, Unit unit, ChatFormatting color) {
        Lang.builder()
                .text(String.format("%.2f ", value))
                .add(unit.get())
                .style(color)
                .forGoggles(tooltip, 1);
    }
}
