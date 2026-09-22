package com.nolanbaker.pgmodernized.device.ctcabinet;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceReadings;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlock.*;

/**
 * Four metered pass-throughs. Per channel: current through the shunt (signed In to Out), voltage of
 * the In point against the Reference point, power, and energy integrated every tick. Readings are
 * synced to clients for the editor and goggles; energy is saved with the block.
 */
public class CtCabinetBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, ISpliceReadings, INetworkJack, IHaveGoggleInformation {
    /** Shunt resistance per channel, ohms. */
    public static final float SHUNT = 0.001f;
    private static final double TICK_SECONDS = 1 / 20.0;

    private DeviceSpliceHost deviceHubs;
    private final JackSupport jack = new JackSupport(this, false);
    // No initialiser: buildCircuit runs from the superclass constructor, before field initialisers.
    private ElectricWire[] shunts;
    private final float[] volts = new float[CHANNELS];
    private final float[] amps = new float[CHANNELS];
    private final float[] watts = new float[CHANNELS];
    /** Joules, per channel. */
    private final double[] energy = new double[CHANNELS];
    private final float[] syncedWatts = new float[CHANNELS];
    private double syncedEnergy;

    public CtCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, CtCabinetBlock.LAYOUT);
        return deviceHubs;
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        if(shunts == null)
            shunts = new ElectricWire[CHANNELS];
        for(int n = 0; n < CHANNELS; ++n)
            shunts[n] = builder.connect(SHUNT, builder.terminalNode(inTerminal(n)), builder.terminalNode(outTerminal(n)));
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void electricalTick() {
        var behaviour = getElectricBehaviour();
        if(behaviour == null || shunts == null)
            return;
        var reference = behaviour.getTerminal(TERMINAL_REFERENCE);
        double refVolts = reference == null ? 0 : reference.getVoltage();
        if(!Double.isFinite(refVolts))
            refVolts = 0;
        for(int n = 0; n < CHANNELS; ++n) {
            var shunt = shunts[n];
            if(shunt == null || !shunt.isConverged())
                continue;
            double current = shunt.current();
            var in = behaviour.getTerminal(inTerminal(n));
            double voltage = (in == null ? 0 : in.getVoltage()) - refVolts;
            if(!Double.isFinite(current))
                current = 0;
            if(!Double.isFinite(voltage))
                voltage = 0;
            amps[n] = (float) current;
            volts[n] = (float) voltage;
            watts[n] = (float) (voltage * current);
            energy[n] += voltage * current * TICK_SECONDS;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        boolean changed = Math.abs(totalEnergy() - syncedEnergy) > 5;
        for(int n = 0; n < CHANNELS && !changed; ++n)
            changed = Math.abs(watts[n] - syncedWatts[n]) > Math.max(0.5f, Math.abs(syncedWatts[n]) * 0.02f);
        if(changed)
            sendData();
    }

    // ---- readings ----

    public int channels() {
        return CHANNELS;
    }

    public float voltage(int channel) {
        return volts[channel];
    }

    /** Signed, positive from In to Out. */
    public float current(int channel) {
        return amps[channel];
    }

    public float power(int channel) {
        return watts[channel];
    }

    /** Watt-hours. */
    public double energyWh(int channel) {
        return energy[channel] / 3600.0;
    }

    public float totalPower() {
        float total = 0;
        for(float w : watts)
            total += w;
        return total;
    }

    /** Joules. */
    private double totalEnergy() {
        double total = 0;
        for(double j : energy)
            total += j;
        return total;
    }

    public double totalEnergyWh() {
        return totalEnergy() / 3600.0;
    }

    public void resetEnergy() {
        java.util.Arrays.fill(energy, 0);
        setChanged();
        sendData();
    }

    private static String wh(double wattHours) {
        return Math.abs(wattHours) >= 1000 ? String.format("%.2f kWh", wattHours / 1000) : String.format("%.1f Wh", wattHours);
    }

    @Override
    public List<Component> readings() {
        var lines = new ArrayList<Component>();
        for(int n = 0; n < CHANNELS; ++n) {
            lines.add(Lang.builder().translate("gui.ct_cabinet.channel", n + 1).style(ChatFormatting.GRAY).text(" ")
                    .add(Lang.builder().text(String.format("%.1f V  %.2f A  %.0f W  ", volts[n], amps[n], watts[n]) + wh(energyWh(n))).style(ChatFormatting.WHITE))
                    .component());
        }
        lines.add(Lang.builder().translate("gui.ct_cabinet.total").style(ChatFormatting.GRAY).text(" ")
                .add(Lang.builder().text(String.format("%.0f W  ", totalPower()) + wh(totalEnergyWh())).style(ChatFormatting.GOLD))
                .component());
        return lines;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.ct_cabinet.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(var line : readings())
            Lang.builder().add(line).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
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
        var joules = new long[CHANNELS];
        for(int n = 0; n < CHANNELS; ++n)
            joules[n] = Math.round(energy[n]);
        tag.putLongArray("EnergyJ", joules);
        if(clientPacket) {
            var readings = new float[CHANNELS * 3];
            for(int n = 0; n < CHANNELS; ++n) {
                readings[n * 3] = volts[n];
                readings[n * 3 + 1] = amps[n];
                readings[n * 3 + 2] = watts[n];
                syncedWatts[n] = watts[n];
            }
            syncedEnergy = totalEnergy();
            var list = new net.minecraft.nbt.ListTag();
            for(float f : readings)
                list.add(net.minecraft.nbt.FloatTag.valueOf(f));
            tag.put("Readings", list);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        var joules = tag.getLongArray("EnergyJ");
        for(int n = 0; n < CHANNELS && n < joules.length; ++n)
            energy[n] = joules[n];
        if(clientPacket && tag.contains("Readings")) {
            var list = tag.getList("Readings", net.minecraft.nbt.Tag.TAG_FLOAT);
            for(int n = 0; n < CHANNELS && n * 3 + 2 < list.size(); ++n) {
                volts[n] = list.getFloat(n * 3);
                amps[n] = list.getFloat(n * 3 + 1);
                watts[n] = list.getFloat(n * 3 + 2);
            }
        }
    }
}
