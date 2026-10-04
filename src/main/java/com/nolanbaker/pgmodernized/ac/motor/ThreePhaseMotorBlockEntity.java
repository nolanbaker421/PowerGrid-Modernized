package com.nolanbaker.pgmodernized.ac.motor;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.advancements.PGAdvancementBehaviour;
import org.patryk3211.powergrid.collections.ModdedAdvancements;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.AlternatorPolePairsBehaviour;
import org.patryk3211.powergrid.mixin.KineticBlockEntityAccessor;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

/**
 * Three star-connected windings, each an inductive coil like Power Grid's motor coil. The supply
 * frequency and phase sequence are read off the windings sub-tick by sub-tick; the shaft turns at
 * the synchronous speed for the pole-pair setting less a little slip under load, in the direction
 * the sequence dictates. Under-excited (too few volts per hertz) it stalls. With one phase missing,
 * or on direct current, there is no sequence and it does not turn.
 */
public class ThreePhaseMotorBlockEntity extends GeneratingKineticBlockEntity implements IElectricEntity, IDeviceSpliceHost {
    public static final double STRESS_CAPACITY = 64;
    /** Full excitation: 120 V per phase at 10 Hz. Below a fifth of this the motor stalls. */
    public static final float RATED_VOLTS_PER_HZ = 12f;
    public static final float MIN_EXCITATION = 0.2f;
    public static final float FULL_LOAD_SLIP = 0.05f;
    public static final float SENSE = 1_000_000f;
    public static final float CONVERSION_CONSTANT = (float) (60 * Math.PI / 2);

    protected ElectricBehaviour electricBehaviour;
    @Nullable
    protected ThermalBehaviour thermalBehaviour;
    private AlternatorPolePairsBehaviour polePairs;
    private DeviceSpliceHost deviceHubs;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private LRSeriesWire[] windings;
    private PhaseSenseWire[] senses;
    private AcReadings.Filter[] readings;

    private float generatedSpeed;
    private float speedSum;
    private int speedSamples;
    private float load = 0.05f;

    private float frequency, phaseVolts, phaseAmps;
    private int sequence;
    private float syncedAmps, syncedFrequency;
    private float notifiedCapacity = -1;
    /** No-load draw as a share of the full-load draw: magnetising and friction. */
    public static final float IDLE_FRACTION = 0.03f;
    /** Stress this motor carries right now (SU), and the electrical power that is worth. */
    private float carriedStress, demandWatts;

    public ThreePhaseMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(5);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, ThreePhaseMotorBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        polePairs = new AlternatorPolePairsBehaviour(this, new BackBox());
        polePairs.withCallback(i -> setChanged());
        behaviours.add(polePairs);
        electricBehaviour = new ElectricBehaviour(this);
        behaviours.add(electricBehaviour);

        var awards = new PGAdvancementBehaviour(this, ModdedAdvancements.ELECTRIC_MOTOR);
        behaviours.add(awards);
        var maxPower = PowerGrid.maxRPM() * torque() / CONVERSION_CONSTANT;
        thermalBehaviour = ThermalBehaviour.simple(this, 3.5f, ThermalBehaviour.dissipationFactor(maxPower, 150));
        if(thermalBehaviour != null) {
            behaviours.add(thermalBehaviour);
            awards.add(ModdedAdvancements.BLOW_UP);
        }
    }

    /**
     * The stress this motor can carry, per rpm: what its full-load electrical draw buys at the
     * configured watts per stress unit, less the motor's losses. This is what keeps the books
     * straight: a Create New Age generator turns stress back into FE at a fixed rate, and an FE
     * inverter charges FE for every joule, so stress that costs no watts would be free energy.
     */
    public float energyCapacity() {
        float rpm = Math.max(1, Math.abs(generatedSpeed));
        float r = resistance("winding");
        double fullLoadWatts = r > 0 ? 3.0 * phaseVolts * phaseVolts / r : 0;
        double stress = fullLoadWatts * PgmConfig.MOTOR_EFFICIENCY.get() / PgmConfig.wattsPerSu();
        return (float) Math.min(PgmConfig.MOTOR_MAX_CAPACITY.get(), stress / rpm);
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = energyCapacity();
        lastCapacityProvided = capacity;
        return capacity;
    }

    public float torque() {
        var configs = ModdedConfigs.server();
        float perStress = configs == null ? 1 : configs.kinetics.torqueForStress.getF();
        return (float) (BlockStressValues.getCapacity(getBlockState().getBlock()) * perStress);
    }

    private static float timeConstant() {
        var configs = ModdedConfigs.server();
        return configs == null ? 0.01f : configs.electricity.motorTimeConstant.getF();
    }

    private static boolean dynamicResistance() {
        var configs = ModdedConfigs.server();
        return configs == null || configs.electricity.motorDynamicResistance.get();
    }

    public int polePairs() {
        return polePairs == null ? 1 : polePairs.getPolePairs();
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var star = builder.addInternalNode();
        float R = resistance("winding");
        float L = R * timeConstant();
        windings = new LRSeriesWire[3];
        senses = new PhaseSenseWire[3];
        readings = new AcReadings.Filter[3];
        for(int k = 0; k < 3; ++k) {
            windings[k] = new LRSeriesWire(L, R, builder.terminalNode(k), star);
            builder.add(windings[k]);
            senses[k] = new PhaseSenseWire(SENSE, builder.terminalNode(k), star);
            builder.add(senses[k]);
            readings[k] = new AcReadings.Filter();
        }
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);
        load = maxStress != 0 ? Math.max(currentStress / maxStress, 0.05f) : 0.05f;
        // The motor's share of the network's load, by its share of the capacity. Stalled by an
        // overstressed network it carries everything it has, like a locked rotor.
        float mine = Math.abs(generatedSpeed) * Math.max(0, energyCapacity());
        float share = maxStress > 0 ? Math.min(1, mine / maxStress) : 0;
        carriedStress = currentStress > maxStress ? mine : currentStress * share;
        demandWatts = (float) (carriedStress * PgmConfig.wattsPerSu() / Math.max(0.05, PgmConfig.MOTOR_EFFICIENCY.get()));
        applyWindingResistance();
    }

    /**
     * Sets the windings so the motor draws just what the stress it carries is worth at the
     * configured watts per stress unit, over its efficiency: never less than its no-load draw,
     * never more than its full-load draw. Redone as the supply voltage moves, so the power, not
     * the resistance, is what holds.
     */
    private void applyWindingResistance() {
        if(!dynamicResistance() || windings == null)
            return;
        float r = resistance("winding");
        double volts = Math.max(1, phaseVolts);
        double full = 3.0 * volts * volts / r;
        double want = Math.max(demandWatts, full * IDLE_FRACTION);
        double target = Math.max(r, Math.min(r / IDLE_FRACTION, 3.0 * volts * volts / want));
        for(var winding : windings)
            winding.setResistance((float) target);
    }

    protected void applyPower(AbstractElectricWire wire) {
        if(thermalBehaviour != null)
            thermalBehaviour.applyWirePower(wire);
    }

    // ---- ticking ----

    @Override
    public void tick() {
        if((!level.isClientSide || isVirtual()) && windings != null) {
            float freq = 0;
            double volts = 0, amps = 0;
            for(int k = 0; k < 3; ++k) {
                applyPower(windings[k]);
                readings[k].sample(windings[k]);
                volts += readings[k].rmsVoltage();
                amps += readings[k].rmsCurrent();
                freq = Math.max(freq, (float) senses[k].frequency());
            }
            frequency = freq;
            phaseVolts = (float) (volts / 3);
            phaseAmps = (float) (amps / 3);
            sequence = PhaseSenseWire.sequence(senses[0], senses[1]);

            float speed = 0;
            if(sequence != 0 && frequency > 0.2f) {
                float excitation = Math.min(1, phaseVolts / frequency / RATED_VOLTS_PER_HZ);
                if(excitation >= MIN_EXCITATION)
                    speed = sequence * synchronousSpeed() * (1 - FULL_LOAD_SLIP * load);
            }
            speedSum += speed;
            ++speedSamples;
        }
        super.tick();
    }

    /** Synchronous speed in rpm at the present supply frequency. */
    public float synchronousSpeed() {
        return 60f * frequency / polePairs();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || (level.isClientSide && !isVirtual()))
            return;
        deviceHubs().lazyTick();
        int newSpeed = speedSamples == 0 ? 0 : Math.round(speedSum / speedSamples);
        speedSum = 0;
        speedSamples = 0;
        int max = PowerGrid.maxRPM();
        newSpeed = Math.max(-max, Math.min(max, newSpeed));
        if(newSpeed != generatedSpeed) {
            generatedSpeed = newSpeed;
            updateGeneratedRotation();
            if(newSpeed != 0) {
                var awards = getBehaviour(PGAdvancementBehaviour.TYPE);
                if(awards != null)
                    awards.awardPlayer(ModdedAdvancements.ELECTRIC_MOTOR);
            }
        }
        // The supply voltage sets how much stress the motor may carry; tell the network when that moves.
        float capacity = energyCapacity();
        if(generatedSpeed != 0 && Math.abs(capacity - notifiedCapacity) > Math.max(0.5f, notifiedCapacity * 0.1f)) {
            notifiedCapacity = capacity;
            notifyStressCapacityChange(capacity);
        }
        applyWindingResistance();
        if(Math.abs(phaseAmps - syncedAmps) > Math.max(0.05f, syncedAmps * 0.05f) || Math.abs(frequency - syncedFrequency) > 0.1f)
            sendData();
    }

    @Override
    public void applyNewSpeed(float prevSpeed, float speed) {
        super.applyNewSpeed(prevSpeed, speed);
        if(Math.signum(prevSpeed) == Math.signum(speed)) {
            // Same hack as Power Grid's motor: a wandering supply must not flicker the network to death.
            for(var entry : getOrCreateNetwork().members.keySet())
                ((KineticBlockEntityAccessor) entry).setFlickerTally(Math.max(entry.getFlickerScore() - 5, 0));
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(generatedSpeed, getBlockState().getValue(ThreePhaseMotorBlock.FACING));
    }

    @Override
    public void remove() {
        super.remove();
        if(electricBehaviour != null)
            electricBehaviour.remove();
    }

    // ---- readings ----

    public float frequency() {
        return frequency;
    }

    public float phaseVoltage() {
        return phaseVolts;
    }

    public float phaseCurrent() {
        return phaseAmps;
    }

    /** +1 for U-V-W, -1 for the reverse sequence, 0 when there is no usable three-phase supply. */
    public int sequence() {
        return sequence;
    }

    public float generatedRpm() {
        return generatedSpeed;
    }

    // ---- persistence ----

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        generatedSpeed = tag.getFloat("GeneratedSpeed");
        if(clientPacket) {
            frequency = tag.getFloat("Frequency");
            carriedStress = tag.getFloat("Carried");
            demandWatts = tag.getFloat("Demand");
            phaseVolts = tag.getFloat("PhaseVolts");
            phaseAmps = tag.getFloat("PhaseAmps");
            sequence = tag.getInt("Sequence");
        }
        updateGeneratedRotation();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putFloat("GeneratedSpeed", generatedSpeed);
        if(clientPacket) {
            tag.putFloat("Frequency", frequency);
            tag.putFloat("Carried", carriedStress);
            tag.putFloat("Demand", demandWatts);
            tag.putFloat("PhaseVolts", phaseVolts);
            tag.putFloat("PhaseAmps", phaseAmps);
            tag.putInt("Sequence", sequence);
            syncedAmps = phaseAmps;
            syncedFrequency = frequency;
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        var seq = switch(sequence) {
            case 1 -> Lang.builder().translate("gui.three_phase_motor.forward").style(ChatFormatting.GREEN);
            case -1 -> Lang.builder().translate("gui.three_phase_motor.reverse").style(ChatFormatting.GOLD);
            default -> Lang.builder().translate("gui.three_phase_motor.none").style(ChatFormatting.RED);
        };
        Lang.builder().translate("gui.three_phase_motor.supply").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().text(String.format("%.1f Hz  ", frequency)).style(ChatFormatting.AQUA).add(seq).forGoggles(tooltip, 1);
        Lang.builder().text(String.format("%.1f ", phaseVolts)).add(Unit.VOLTAGE.get()).text(String.format("  %.2f ", phaseAmps)).add(Unit.CURRENT.get())
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.three_phase_motor.sync", polePairs(), Math.round(synchronousSpeed())).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.three_phase_motor.carrying", String.format("%.0f", carriedStress), String.format("%.0f", demandWatts))
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }

    /** Pole-pair value box on the terminal-box end of the motor. */
    public static class BackBox extends CenteredSideValueBoxTransform {
        public BackBox() {
            super((state, dir) -> dir == state.getValue(ThreePhaseMotorBlock.FACING).getOpposite());
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8 / 16.0, 8 / 16.0, 15.5 / 16.0);
        }
    }
}
