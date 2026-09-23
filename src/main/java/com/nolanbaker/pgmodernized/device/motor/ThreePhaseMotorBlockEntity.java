package com.nolanbaker.pgmodernized.device.motor;

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
public class ThreePhaseMotorBlockEntity extends GeneratingKineticBlockEntity implements IElectricEntity {
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

    public ThreePhaseMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(5);
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
        builder.setTerminalCount(3);
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
        if(dynamicResistance() && windings != null) {
            // Loaded windings draw more: the same rule Power Grid's motor uses, with L preserved.
            for(var winding : windings)
                winding.setResistance(resistance("winding") / load);
        }
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
        generatedSpeed = tag.getFloat("GeneratedSpeed");
        if(clientPacket) {
            frequency = tag.getFloat("Frequency");
            phaseVolts = tag.getFloat("PhaseVolts");
            phaseAmps = tag.getFloat("PhaseAmps");
            sequence = tag.getInt("Sequence");
        }
        updateGeneratedRotation();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("GeneratedSpeed", generatedSpeed);
        if(clientPacket) {
            tag.putFloat("Frequency", frequency);
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
