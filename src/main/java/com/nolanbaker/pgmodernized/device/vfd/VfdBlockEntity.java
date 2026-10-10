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
 * <p>
 * The enable flag is stored inverted, as {@code disabled}, because buildCircuit runs from the
 * superclass constructor before any field initialiser: a flag that defaulted to true would still
 * read false there, and every regulator would be built without its coupling until toggled.
 * <p>
 * Braking: a motor coil keeps its current flowing when the output drops, and through the
 * coupling that current would go back into the supply multiplied by the ratio, where the
 * supply's own inductance turns it into a spike. So a reverse current opens the converter path
 * and closes a braking resistor across the output instead, sized so the coil's current stays
 * within the current limit; its energy is burnt here and the supply sees none of it.
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

    /** The 2 kV unit's ceiling; the block carries the rating of the unit actually placed. */
    public static final float MAX_VOLTAGE = 2000.0f;
    public static final float MAX_CURRENT = 20.0f;
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
    private static final float BLOCK_RESISTANCE = 100_000f;    // the converter path while braking: as good as a blocking diode
    private static final float BRAKE_OPEN = 1_000_000f;        // the braking resistor when not braking
    private static final int MAX_BRAKE_TICKS = 40;             // two seconds is longer than any coil takes to die away

    private float setpoint = 0;
    private float currentLimit = MAX_CURRENT;
    /** Inverted so that the default (false) is "enabled" when buildCircuit runs from the superclass constructor. */
    private boolean disabled;

    // No initializers here: SmartBlockEntity's constructor calls buildCircuit() before they would run.
    private float ratio;
    private float ratioCap; // 0 means "not initialised yet" (see electricalTick)
    private float inputReference;
    private float inputVoltage, outputVoltage, outputCurrent;
    private Status status = Status.OK;
    // Braking state; no initialisers (see buildCircuit).
    private ElectricWire brakeWire;
    private boolean braking;
    private int brakeTicks;
    private float brakeResistance;
    private float brakeCurrent;
    /** Heat a drive with its input wired backwards takes, watts. */
    private static final float REVERSE_WATTS = 150f;

    /** Why the output is what it is, for the goggles and the computer. */
    public enum Status {
        OK("ok"), DISABLED("disabled"), NO_INPUT("no_input"), REVERSED("reversed"), SETPOINT_ZERO("setpoint_zero"),
        INPUT_LOW("input_low"), CURRENT_LIMIT("current_limit"), INPUT_SAG("input_sag"), BRAKING("braking");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public Status status() {
        return status;
    }

    private TransformerCoupling coupling;
    private FloatingNode inPos, inNeg, outPos, outNeg;
    private ElectricWire inputSense, outputSense, outputShunt;
    private final AcReadings.Filter inputReading = new AcReadings.Filter();
    private final AcReadings.Filter outputReading = new AcReadings.Filter();
    private final AcReadings.Filter brakeReading = new AcReadings.Filter();

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
        if(!disabled) {
            var outMid = builder.addInternalNode();
            coupling = builder.couple(ratio, resistance("output"), inPos, inNeg, outMid, outNeg);
            outputShunt = builder.connect(SHUNT_RESISTANCE, outMid, outPos);
            // Across the output, open until a reverse current closes it.
            brakeWire = builder.connect(BRAKE_OPEN, outPos, outNeg);
        } else {
            coupling = null;
            outputShunt = null;
            // Disabled, the output is dead and a connected coil discharges into the braking resistor.
            brakeWire = builder.connect(brakeResistanceFor(), outPos, outNeg);
        }
        braking = false;
        brakeTicks = 0;
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
            status = Status.DISABLED;
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
        // Reverse current: the load's coil pushing back. Open the converter path and burn it off here.
        float signedOut = finite((float) outputReading.signedRmsCurrent());
        brakeReading.sample(brakeWire);
        brakeCurrent = finite((float) brakeReading.rmsCurrent());
        if(!braking) {
            if(signedOut < -Math.max(0.05f, currentLimit * 0.05f)) {
                braking = true;
                brakeTicks = 0;
                brakeResistance = brakeResistanceFor();
                outputShunt.setResistance(BLOCK_RESISTANCE);
                brakeWire.setResistance(brakeResistance);
            }
        } else {
            ++brakeTicks;
            if(thermalBehaviour != null)
                thermalBehaviour.applyTickPower(brakeCurrent * brakeCurrent * brakeResistance);
            if((brakeTicks >= 2 && brakeCurrent < Math.max(0.02f, currentLimit * 0.02f)) || brakeTicks > MAX_BRAKE_TICKS) {
                braking = false;
                outputShunt.setResistance(SHUNT_RESISTANCE);
                brakeWire.setResistance(BRAKE_OPEN);
            }
        }

        float absVin = Math.abs(inputVoltage);
        boolean unloaded = Math.abs(ratio) <= MIN_RATIO * 1.5f;
        inputReference = unloaded ? absVin : Math.max(absVin, inputReference);
        boolean inputSagging = absVin < inputReference * INPUT_SAG_FRACTION;
        // The limiter acts on current the regulator is pushing out; a reverse current is the brake's business.
        boolean overCurrent = signedOut > currentLimit;
        if((overCurrent || inputSagging) && Math.abs(ratio) > MIN_RATIO) {
            float factor = overCurrent ? currentLimit / outputCurrent : absVin / (inputReference * INPUT_SAG_FRACTION);
            ratioCap = Math.max(MIN_RATIO, Math.min(ratioCap, Math.abs(ratio)) * factor * CAP_MARGIN);
        } else {
            ratioCap = Math.min(MAX_RATIO, ratioCap * CAP_RECOVERY + MIN_RATIO);
        }

        // Wired backwards: nothing comes out and the input stage cooks until someone notices.
        boolean reversed = inputVoltage < -MIN_INPUT_VOLTAGE;
        if(reversed && thermalBehaviour != null)
            thermalBehaviour.applyTickPower(REVERSE_WATTS);
        status = reversed ? Status.REVERSED
                : absVin < MIN_INPUT_VOLTAGE ? Status.NO_INPUT
                : braking ? Status.BRAKING
                : setpoint == 0 ? Status.SETPOINT_ZERO
                : overCurrent ? Status.CURRENT_LIMIT
                : inputSagging ? Status.INPUT_SAG
                : Math.abs(setpoint) > absVin * MAX_RATIO ? Status.INPUT_LOW
                : Status.OK;

        float target;
        if(setpoint == 0 || reversed || Math.abs(inputVoltage) < MIN_INPUT_VOLTAGE) {
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

    /** The braking resistor: at most the unit's ceiling voltage across it at the current limit. */
    private float brakeResistanceFor() {
        return Math.max(resistance("output"), maxVoltage() / Math.max(0.1f, currentLimit));
    }

    public boolean isBraking() {
        return braking;
    }

    /** Current through the braking resistor, amps. */
    public float getBrakeCurrent() {
        return brakeCurrent;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
        if(level != null && !level.isClientSide)
            sendData();
    }

    public float maxVoltage() {
        return getBlockState().getBlock() instanceof VfdBlock block ? block.maxVoltage() : MAX_VOLTAGE;
    }

    public void setVoltage(float voltage) {
        float max = maxVoltage();
        voltage = Float.isFinite(voltage) ? Mth.clamp(voltage, -max, max) : 0;
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
        if(!disabled == value)
            return;
        disabled = !value;
        ratio = MIN_RATIO; // soft start when re-enabled
        if(electricBehaviour != null && level != null && !level.isClientSide)
            electricBehaviour.rebuildCircuit(false);
        setChanged();
        sendData();
    }

    public boolean isEnabled() {
        return !disabled;
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
        boolean wasEnabled = !disabled;
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        setpoint = tag.getFloat("Setpoint");
        currentLimit = tag.contains("CurrentLimit") ? tag.getFloat("CurrentLimit") : MAX_CURRENT;
        int statusIndex = tag.getByte("Status");
        status = statusIndex >= 0 && statusIndex < Status.values().length ? Status.values()[statusIndex] : Status.OK;
        disabled = tag.contains("Enabled") && !tag.getBoolean("Enabled");
        if(clientPacket) {
            inputVoltage = tag.getFloat("VIn");
            outputVoltage = tag.getFloat("VOut");
            outputCurrent = tag.getFloat("IOut");
            ratio = tag.getFloat("Ratio");
            braking = tag.getBoolean("Braking");
            brakeCurrent = tag.getFloat("IBrake");
        } else if(!disabled != wasEnabled && electricBehaviour != null && level != null) {
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
        tag.putByte("Status", (byte) status.ordinal());
        tag.putBoolean("Enabled", !disabled);
        if(clientPacket) {
            tag.putFloat("VIn", inputVoltage);
            tag.putFloat("VOut", outputVoltage);
            tag.putFloat("IOut", outputCurrent);
            tag.putFloat("Ratio", ratio);
            tag.putBoolean("Braking", braking);
            tag.putFloat("IBrake", brakeCurrent);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.vfd.input").style(ChatFormatting.GRAY).forGoggles(tooltip);
        line(tooltip, inputVoltage, Unit.VOLTAGE, ChatFormatting.AQUA);
        line(tooltip, getInputCurrent(), Unit.CURRENT, ChatFormatting.AQUA);
        Lang.builder().translate("gui.vfd.output").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.vfd.status." + status.key())
                .style(status == Status.OK ? ChatFormatting.GREEN : status == Status.CURRENT_LIMIT || status == Status.INPUT_SAG ? ChatFormatting.GOLD : ChatFormatting.RED)
                .forGoggles(tooltip, 1);
        if(disabled) {
            Lang.builder().translate("gui.vfd.disabled").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            line(tooltip, outputVoltage, Unit.VOLTAGE, ChatFormatting.GOLD);
            line(tooltip, outputCurrent, Unit.CURRENT, ChatFormatting.GOLD);
            line(tooltip, getPower(), Unit.POWER, ChatFormatting.GOLD);
            if(braking)
                Lang.builder().translate("gui.vfd.braking", String.format("%.2f", brakeCurrent), String.format("%.0f", brakeCurrent * brakeCurrent * brakeResistance))
                        .style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
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
