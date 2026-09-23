package com.nolanbaker.pgmodernized.device.drive;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.WattmeterWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlock.*;

/**
 * A rectifier and inverter in one block. The output is three alternating sources 120° apart at the
 * commanded frequency, holding the rated volts per hertz up to the rated voltage, ramped at a set
 * rate. The real power the output delivers (metered per phase with a wattmeter element) is drawn
 * from the input side through three conductances across L1-L2, L2-L3 and L3-L1 that are re-tuned
 * every tick, so the input sees the load and sags accordingly. The output voltage is capped at
 * what the input can supply, a third of a volt per volt of line input.
 */
public class ThreePhaseDriveBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, INetworkJack, IDeviceSpliceHost {
    public static final int MAX_HZ = 30;
    public static final float MAX_VOLTS = 1000f;
    public static final float OUTPUT_R = 0.05f;
    public static final float SERIES_R = 0.005f;
    public static final float SENSE = 1_000_000f;
    public static final float EFFICIENCY = 0.95f;
    public static final float IDLE_WATTS = 2f;
    private static final float INPUT_OPEN = 1_000_000f;
    private static final double SQRT3 = Math.sqrt(3);
    private static final double THIRD_TURN = 2 * Math.PI / 3;

    private DeviceSpliceHost deviceHubs;
    private DriveFrequencyBehaviour frequencyBox;
    private final JackSupport jack = new JackSupport(this, false);

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ACVoltageSourceCoupling[] sources;
    private WattmeterWire[] meters;
    private ElectricWire[] senses;
    private ElectricWire[] inputWires;
    private AcReadings.Filter[] outputReadings;
    private AcReadings.Filter[] inputReadings;

    // Settings
    private float computerHz = -1;
    private float ratedVolts = 120;
    private float ratedHz = 10;
    private float rampHzPerSecond = 5;
    private boolean enabled = true;
    private boolean reversed;
    private boolean appliedReverse;

    // State
    private float outputHz, outputVolts, outputAmps, inputVolts, inputAmps, power;
    private float syncedPower;

    public ThreePhaseDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, ThreePhaseDriveBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        frequencyBox = new DriveFrequencyBehaviour(this, new DriveFrequencyBehaviour.TopBox());
        frequencyBox.withCallback(i -> setChanged());
        behaviours.add(frequencyBox);
        super.addBehaviours(behaviours);
    }

    @Override
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    // ---- circuit ----

    private double phaseOffset(int k) {
        int order = reversed ? (3 - k) % 3 : k;
        return -THIRD_TURN * order;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        sources = new ACVoltageSourceCoupling[3];
        meters = new WattmeterWire[3];
        senses = new ElectricWire[3];
        inputWires = new ElectricWire[3];
        outputReadings = new AcReadings.Filter[3];
        inputReadings = new AcReadings.Filter[3];
        var star = builder.addInternalNode();
        for(int k = 0; k < 3; ++k) {
            var out = builder.terminalNode(U + k);
            var inner = builder.addInternalNode();
            sources[k] = new ACVoltageSourceCoupling(inner, star, (double) OUTPUT_R, 0, 0);
            sources[k].setPhaseOffset(phaseOffset(k));
            builder.add(sources[k]);
            senses[k] = builder.connect(SENSE, out, star);
            meters[k] = new WattmeterWire(SERIES_R, senses[k], inner, out);
            builder.add(meters[k]);
            inputWires[k] = builder.connect(INPUT_OPEN, builder.terminalNode(L1 + k), builder.terminalNode(L1 + (k + 1) % 3));
            outputReadings[k] = new AcReadings.Filter();
            inputReadings[k] = new AcReadings.Filter();
        }
        appliedReverse = reversed;
    }

    /** The commanded frequency: a computer's setting when it gave one, else the value box. */
    public float frequencySetpoint() {
        if(computerHz >= 0)
            return computerHz;
        return frequencyBox == null ? 0 : frequencyBox.getValue();
    }

    @Override
    public void electricalTick() {
        if(sources == null)
            return;
        // Ramp the output frequency towards the setpoint.
        float target = enabled ? Mth.clamp(frequencySetpoint(), 0, MAX_HZ) : 0;
        float step = rampHzPerSecond * 0.05f;
        outputHz += Mth.clamp(target - outputHz, -step, step);
        if(Math.abs(outputHz) < 1e-3f)
            outputHz = 0;

        // Input: RMS of each line pair, and what the bus can support.
        double sumSquares = 0, lineMax = 0, inAmps = 0;
        for(int k = 0; k < 3; ++k) {
            inputReadings[k].sample(inputWires[k]);
            double v = inputReadings[k].rmsVoltage();
            sumSquares += v * v;
            lineMax = Math.max(lineMax, v);
            inAmps += inputReadings[k].rmsCurrent();
        }
        inputVolts = (float) Math.sqrt(sumSquares / 3);
        inputAmps = (float) (inAmps / 3 * SQRT3);

        // Volts per hertz up to the rated voltage, capped by the input.
        float volts = ratedHz > 0 ? Math.min(ratedVolts, ratedVolts * outputHz / ratedHz) : ratedVolts;
        volts = (float) Math.min(volts, lineMax / SQRT3);
        if(outputHz <= 0)
            volts = 0;
        outputVolts = volts;

        if(appliedReverse != reversed) {
            for(int k = 0; k < 3; ++k)
                sources[k].setPhaseOffset(phaseOffset(k));
            appliedReverse = reversed;
        }
        double watts = 0, outAmps = 0;
        for(int k = 0; k < 3; ++k) {
            sources[k].setFrequency(outputHz);
            sources[k].setRmsVoltage(volts);
            outputReadings[k].sample(senses[k], meters[k], meters[k].drainRealPower());
            watts += outputReadings[k].realPower();
            outAmps += outputReadings[k].rmsCurrent();
        }
        power = (float) Math.max(0, watts);
        outputAmps = (float) (outAmps / 3);

        // Draw the delivered power from the input, spread over the three line pairs.
        double draw = enabled ? power / EFFICIENCY + IDLE_WATTS : 0;
        float resistance = draw > 0 && sumSquares > 1 ? (float) Mth.clamp(sumSquares / draw, 0.01, INPUT_OPEN) : INPUT_OPEN;
        for(var wire : inputWires)
            wire.setResistance(resistance);

        if(thermalBehaviour != null)
            thermalBehaviour.applyTickPower(power * (1 - EFFICIENCY));
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(Math.abs(power - syncedPower) > Math.max(1f, syncedPower * 0.02f) || outputHz != frequencySetpoint())
            sendData();
    }

    // ---- settings ----

    /** A computer's frequency command; negative hands control back to the value box. */
    public void setFrequency(float hz) {
        computerHz = Float.isFinite(hz) && hz >= 0 ? Math.min(hz, MAX_HZ) : -1;
        setChanged();
    }

    public void setRatedVoltage(float volts) {
        ratedVolts = Float.isFinite(volts) ? Mth.clamp(volts, 0, MAX_VOLTS) : 120;
        setChanged();
    }

    public float ratedVoltage() {
        return ratedVolts;
    }

    public void setRatedFrequency(float hz) {
        ratedHz = Float.isFinite(hz) ? Mth.clamp(hz, 0.5f, MAX_HZ) : 10;
        setChanged();
    }

    public float ratedFrequency() {
        return ratedHz;
    }

    public void setRampRate(float hzPerSecond) {
        rampHzPerSecond = Float.isFinite(hzPerSecond) ? Mth.clamp(hzPerSecond, 0.1f, 1000) : 5;
        setChanged();
    }

    public float rampRate() {
        return rampHzPerSecond;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        setChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setReversed(boolean value) {
        reversed = value;
        setChanged();
    }

    public boolean isReversed() {
        return reversed;
    }

    // ---- readings ----

    public float outputFrequency() {
        return outputHz;
    }

    public float outputVoltage() {
        return outputVolts;
    }

    public float outputCurrent() {
        return outputAmps;
    }

    public float inputVoltage() {
        return inputVolts;
    }

    public float inputCurrent() {
        return inputAmps;
    }

    public float power() {
        return power;
    }

    // ---- jack ----

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

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putFloat("ComputerHz", computerHz);
        tag.putFloat("RatedVolts", ratedVolts);
        tag.putFloat("RatedHz", ratedHz);
        tag.putFloat("Ramp", rampHzPerSecond);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Reversed", reversed);
        if(clientPacket) {
            tag.putFloat("OutHz", outputHz);
            tag.putFloat("OutV", outputVolts);
            tag.putFloat("OutA", outputAmps);
            tag.putFloat("InV", inputVolts);
            tag.putFloat("InA", inputAmps);
            tag.putFloat("Power", power);
            syncedPower = power;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        computerHz = tag.contains("ComputerHz") ? tag.getFloat("ComputerHz") : -1;
        ratedVolts = tag.contains("RatedVolts") ? tag.getFloat("RatedVolts") : 120;
        ratedHz = tag.contains("RatedHz") ? tag.getFloat("RatedHz") : 10;
        rampHzPerSecond = tag.contains("Ramp") ? tag.getFloat("Ramp") : 5;
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        reversed = tag.getBoolean("Reversed");
        if(clientPacket) {
            outputHz = tag.getFloat("OutHz");
            outputVolts = tag.getFloat("OutV");
            outputAmps = tag.getFloat("OutA");
            inputVolts = tag.getFloat("InV");
            inputAmps = tag.getFloat("InA");
            power = tag.getFloat("Power");
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.three_phase_drive.output").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(!enabled) {
            Lang.builder().translate("gui.three_phase_drive.disabled").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            var line = Lang.builder().text(String.format("%.1f Hz  %.1f ", outputHz, outputVolts)).add(Unit.VOLTAGE.get())
                    .text(String.format("  %.2f ", outputAmps)).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD);
            if(reversed)
                line.text("  ").add(Lang.builder().translate("gui.three_phase_drive.reversed").style(ChatFormatting.YELLOW));
            line.forGoggles(tooltip, 1);
            Lang.builder().translate("gui.three_phase_drive.setpoint", String.format("%.1f", frequencySetpoint()), String.format("%.0f", ratedVolts), String.format("%.0f", ratedHz))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        Lang.builder().translate("gui.three_phase_drive.input").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().text(String.format("%.1f ", inputVolts)).add(Unit.VOLTAGE.get()).text(String.format("  %.2f ", inputAmps)).add(Unit.CURRENT.get())
                .text(String.format("  %.0f ", power)).add(Unit.POWER.get()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
