package com.nolanbaker.pgmodernized.ac.pump;

import com.nolanbaker.pgmodernized.ac.motor.PhaseSenseWire;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.pump.ElectricPumpBlock;
import org.patryk3211.powergrid.electricity.pump.ElectricPumpBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nolanbaker.pgmodernized.ac.pump.ThreePhasePumpBlock.*;

/**
 * Power Grid's pump driven by three star-connected windings instead of one resistor. The stock
 * pump reads the instantaneous power of its element every tick and so flickers on alternating
 * current; this one reads the RMS of its windings, takes its direction from the phase sequence
 * (U-V-W pushes out of the front, U-W-V pulls into it) and holds a steady speed.
 * <p>
 * Stages: every {@link PumpStageBlock} in a straight line out of one of the pump's sides lowers
 * the winding resistance by a step, so the pump draws that much more power at the same voltage,
 * and raises its pressure range by the same step, for the same flow. The pressure code is Create's
 * pump distribution with the range per pump instead of the configured constant.
 */
public class ThreePhasePumpBlockEntity extends ElectricPumpBlockEntity implements IDeviceSpliceHost {
    public static final int MAX_STAGES = 6;
    public static final float SENSE = 1_000_000f;
    public static final float RATED_VOLTS = 120;

    private DeviceSpliceHost deviceHubs;
    // No initialisers: buildCircuit runs from the superclass constructor.
    private LRSeriesWire[] windings;
    private PhaseSenseWire[] senses;
    private AcReadings.Filter[] readings;

    private int stages;
    private float frequency, phaseVolts, phaseAmps, watts;
    private int sequence;
    private int speed;
    private float syncedAmps;

    public ThreePhasePumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE_HALF;
    }

    /** Full-load draw of a one-stage pump at the rated phase voltage, which a stage adds again each. */
    private float ratedWatts() {
        float r = resistance("winding");
        return r > 0 ? 3 * RATED_VOLTS * RATED_VOLTS / r : 0;
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.forMaxPower(this, ratedWatts() * (1 + MAX_STAGES), 1.2f);
    }

    private static float timeConstant() {
        var configs = ModdedConfigs.server();
        return configs == null ? 0.01f : configs.electricity.motorTimeConstant.getF();
    }

    /** Winding resistance for the present stage count: each stage is another winding set in parallel. */
    private float windingResistance() {
        return resistance("winding") / (1 + stages);
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var star = builder.addInternalNode();
        float r = windingResistance();
        float l = resistance("winding") * timeConstant();
        windings = new LRSeriesWire[3];
        senses = new PhaseSenseWire[3];
        readings = new AcReadings.Filter[3];
        for(int k = 0; k < 3; ++k) {
            windings[k] = new LRSeriesWire(l, r, builder.terminalNode(k), star);
            builder.add(windings[k]);
            senses[k] = new PhaseSenseWire(SENSE, builder.terminalNode(k), star);
            builder.add(senses[k]);
            readings[k] = new AcReadings.Filter();
        }
    }

    @Override
    public void electricalTick() {
        if(windings == null)
            return;
        float freq = 0;
        double volts = 0, amps = 0, power = 0;
        float r = windingResistance();
        for(int k = 0; k < 3; ++k) {
            applyPower(windings[k]);
            readings[k].sample(windings[k]);
            double ri = readings[k].rmsCurrent();
            volts += readings[k].rmsVoltage();
            amps += ri;
            power += ri * ri * r;
            freq = Math.max(freq, (float) senses[k].frequency());
        }
        frequency = freq;
        phaseVolts = (float) (volts / 3);
        phaseAmps = (float) (amps / 3);
        watts = (float) power;
        sequence = PhaseSenseWire.sequence(senses[0], senses[1]);
        speed = computeSpeed();
    }

    /** Create speed from the real power the windings take, as the stock pump does from its element, a stage's worth at a time. */
    private int computeSpeed() {
        if(sequence == 0)
            return 0;
        var configs = ModdedConfigs.server();
        float perWatt = configs == null ? 1 : configs.electricity.electricPumpPower.getF();
        float max = configs == null ? 256 : configs.electricity.electricPumpMaxSpeed.getF();
        int magnitude = (int) Math.min(watts * perWatt / (1 + stages), max);
        return magnitude * sequence;
    }

    @Override
    public int getSpeed() {
        return speed;
    }

    /** U-V-W pushes out of the front as the stock pump does; the reverse sequence pulls into it. */
    @Override
    public boolean isPullingOnSide(boolean isFront) {
        return speed < 0 ? isFront : !isFront;
    }

    // ---- stages ----

    public int stages() {
        return stages;
    }

    /** Blocks of pipe the pump pushes through: the configured range, once more per stage. */
    public int range() {
        return getRange() * (1 + stages);
    }

    private Direction.Axis axis() {
        var state = getBlockState();
        return state.hasProperty(DirectionalElectricBlock.FACING) ? state.getValue(DirectionalElectricBlock.FACING).getAxis() : Direction.Axis.Z;
    }

    /** Counts the stages in straight lines out of the four sides square to the axis. */
    private int countStages() {
        if(level == null)
            return 0;
        var axis = axis();
        int count = 0;
        for(var side : Direction.values()) {
            if(side.getAxis() == axis)
                continue;
            for(int i = 1; i <= MAX_STAGES && count < MAX_STAGES; ++i) {
                if(!PumpStageBlock.isStage(level, worldPosition.relative(side, i), axis))
                    break;
                ++count;
            }
        }
        return count;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        int now = countStages();
        if(now != stages) {
            stages = now;
            if(windings != null) {
                float r = windingResistance();
                for(var winding : windings)
                    winding.setResistance(r);
            }
            updatePressureChange();
            sendData();
        }
        if(Math.abs(phaseAmps - syncedAmps) > Math.max(0.05f, syncedAmps * 0.05f))
            sendData();
        // The arrows on the body follow the direction it last pumped in.
        var state = getBlockState();
        if(speed != 0 && state.hasProperty(ThreePhasePumpBlock.REVERSED) && state.getValue(ThreePhasePumpBlock.REVERSED) != speed < 0)
            level.setBlock(worldPosition, state.setValue(ThreePhasePumpBlock.REVERSED, speed < 0), 3);
    }

    // ---- pressure: Create's pump distribution with this pump's range ----

    @Override
    protected void distributePressureTo(Direction side) {
        if(getSpeed() == 0)
            return;
        BlockFace start = new BlockFace(worldPosition, side);
        boolean pull = isPullingOnSide(isFront(side));
        Set<BlockFace> targets = new HashSet<>();
        Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();
        if(!pull)
            FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());
        if(!reachedEndpoint(level, start, pull)) {
            pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>())).getSecond().put(side, pull);
            pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>())).getSecond().put(side.getOpposite(), !pull);
            List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();
            int maxDistance = range();
            frontier.add(Pair.of(1, start.getConnectedPos()));
            while(!frontier.isEmpty()) {
                Pair<Integer, BlockPos> entry = frontier.remove(0);
                int distance = entry.getFirst();
                BlockPos currentPos = entry.getSecond();
                if(!level.isLoaded(currentPos) || visited.contains(currentPos))
                    continue;
                visited.add(currentPos);
                BlockState currentState = level.getBlockState(currentPos);
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
                if(pipe == null)
                    continue;
                for(Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                    BlockFace blockFace = new BlockFace(currentPos, face);
                    BlockPos connectedPos = blockFace.getConnectedPos();
                    if(!level.isLoaded(connectedPos) || blockFace.isEquivalent(start))
                        continue;
                    if(reachedEndpoint(level, blockFace, pull)) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>())).getSecond().put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }
                    FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
                    if(pipeBehaviour == null || pipeBehaviour.blockEntity instanceof ElectricPumpBlockEntity || visited.contains(connectedPos))
                        continue;
                    if(distance + 1 >= maxDistance) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>())).getSecond().put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }
                    pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>())).getSecond().put(face, pull);
                    pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>())).getSecond().put(face.getOpposite(), !pull);
                    frontier.add(Pair.of(distance + 1, connectedPos));
                }
            }
        }
        Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
        searchForEndpointRecursively(pipeGraph, targets, validFaces, new BlockFace(start.getPos(), start.getOppositeFace()), pull);
        float pressure = Math.abs(getSpeed());
        for(Set<BlockFace> set : validFaces.values()) {
            int parallelBranches = Math.max(1, set.size() - 1);
            for(BlockFace face : set) {
                BlockPos pipePos = face.getPos();
                Direction pipeSide = face.getFace();
                if(pipePos.equals(worldPosition))
                    continue;
                var entry = pipeGraph.get(pipePos);
                if(entry == null)
                    continue;
                Boolean inbound = entry.getSecond().get(pipeSide);
                FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
                if(pipeBehaviour == null || inbound == null)
                    continue;
                pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
            }
        }
    }

    /** The stock pump's private endpoint test, as Create wrote it. */
    private static boolean reachedEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
        BlockPos connectedPos = blockFace.getConnectedPos();
        BlockState connectedState = world.getBlockState(connectedPos);
        var blockEntity = world.getBlockEntity(connectedPos);
        Direction face = blockFace.getFace();
        // Facing a pump: an endpoint when it pumps the other way.
        if(ElectricPumpBlock.isPump(connectedState) && connectedState.getValue(DirectionalElectricBlock.FACING).getAxis() == face.getAxis()
                && blockEntity instanceof ElectricPumpBlockEntity pump) {
            boolean front = connectedState.getValue(DirectionalElectricBlock.FACING) == blockFace.getOppositeFace();
            return pump.isPullingOnSide(front) != pull;
        }
        // Another pipe: no endpoint.
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if(pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
            return false;
        // A tank or machine.
        if(blockEntity != null && platformEndpointCheck(blockEntity, face))
            return true;
        // An open end.
        return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
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

    public float watts() {
        return watts;
    }

    public int sequence() {
        return sequence;
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putInt("Stages", stages);
        if(clientPacket) {
            tag.putFloat("Frequency", frequency);
            tag.putFloat("PhaseVolts", phaseVolts);
            tag.putFloat("PhaseAmps", phaseAmps);
            tag.putFloat("Watts", watts);
            tag.putInt("Sequence", sequence);
            tag.putInt("Speed", speed);
            syncedAmps = phaseAmps;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        stages = tag.getInt("Stages");
        if(clientPacket) {
            frequency = tag.getFloat("Frequency");
            phaseVolts = tag.getFloat("PhaseVolts");
            phaseAmps = tag.getFloat("PhaseAmps");
            watts = tag.getFloat("Watts");
            sequence = tag.getInt("Sequence");
            speed = tag.getInt("Speed");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.three_phase_pump.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        var seq = switch(sequence) {
            case 1 -> Lang.builder().translate("gui.three_phase_pump.forward").style(ChatFormatting.GREEN);
            case -1 -> Lang.builder().translate("gui.three_phase_pump.reverse").style(ChatFormatting.GOLD);
            default -> Lang.builder().translate("gui.three_phase_motor.none").style(ChatFormatting.RED);
        };
        Lang.builder().text(String.format("%.1f Hz  ", frequency)).style(ChatFormatting.AQUA).add(seq).forGoggles(tooltip, 1);
        Lang.builder().text(String.format("%.1f ", phaseVolts)).add(Unit.VOLTAGE.get()).text(String.format("  %.2f ", phaseAmps)).add(Unit.CURRENT.get())
                .text(String.format("  %.0f W", watts)).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.three_phase_pump.speed", Math.abs(speed), stages, range()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
