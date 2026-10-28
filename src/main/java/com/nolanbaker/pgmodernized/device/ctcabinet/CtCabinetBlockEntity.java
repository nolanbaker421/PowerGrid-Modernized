package com.nolanbaker.pgmodernized.device.ctcabinet;

import net.minecraft.util.Mth;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceReadings;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.radio.ISettingsHost;
import com.nolanbaker.pgmodernized.radio.MeterRegistry;
import com.nolanbaker.pgmodernized.radio.MeterReport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.util.BodySpace;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import java.util.Map;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlock.*;

/**
 * Four metered pass-throughs. Per channel: RMS current through the shunt (signed In to Out when it
 * is one-way), RMS voltage of the In point against the Reference point, real power from a
 * wattmeter element that multiplies the two waveforms sub-tick by sub-tick, the power factor, and
 * energy integrated from the real power every tick. Readings are synced to clients for the editor
 * and goggles; energy is saved with the block.
 */
public class CtCabinetBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, ISpliceReadings, INetworkJack, ISettingsHost, IHaveGoggleInformation {
    /** Shunt resistance per channel, ohms. */
    public static final float SHUNT = 0.001f;
    /** Voltage sense branch from each In point to Reference, ohms. */
    public static final float SENSE = 1_000_000f;
    private static final double TICK_SECONDS = 1 / 20.0;

    private DeviceSpliceHost deviceHubs;
    private final JackSupport jack = new JackSupport(this, false);
    // No initialisers: buildCircuit runs from the superclass constructor, before field initialisers.
    private ElectricWire[] shunts;
    private ElectricWire[] senses;
    private AcReadings.Filter[] readings;
    private final float[] volts = new float[CHANNELS];
    private final float[] amps = new float[CHANNELS];
    private final float[] watts = new float[CHANNELS];
    private final float[] powerFactors = new float[CHANNELS];
    private final float[] hertz = new float[CHANNELS];
    private final float[] angles = new float[CHANNELS];
    /** Joules, per channel. */
    private final double[] energy = new double[CHANNELS];
    private final float[] syncedWatts = new float[CHANNELS];
    private double syncedEnergy;
    /** The meter antenna module: who the collectors list this cabinet as, and when it last spoke. */
    private String meterId = "", utility = "";
    private int reportTimer;
    private long lastReport = -1;
    /** The utility's lock sealing the cabinet, or null. */
    private com.nolanbaker.pgmodernized.device.lock.Lock lock;

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
        if(shunts == null) {
            shunts = new ElectricWire[CHANNELS];
            senses = new ElectricWire[CHANNELS];
            readings = new AcReadings.Filter[CHANNELS];
            for(int n = 0; n < CHANNELS; ++n)
                readings[n] = new AcReadings.Filter();
        }
        var reference = builder.terminalNode(TERMINAL_REFERENCE);
        for(int n = 0; n < CHANNELS; ++n) {
            var in = builder.terminalNode(inTerminal(n));
            // The sense branch is built first because the fork's wattmeter shunt holds a reference
            // to it: it accumulates its own current times the sense voltage as the solver steps.
            // On stock Power Grid the shunt is a plain wire and power is volts times amps.
            senses[n] = ForkHooks.get().phaseSense(builder, SENSE, in, reference);
            shunts[n] = ForkHooks.get().shunt(builder, SHUNT, senses[n], in, builder.terminalNode(outTerminal(n)));
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level instanceof ServerLevel server && hasAntenna() && ++reportTimer >= PgmConfig.METER_REPORT_SECONDS.get() * 20) {
            reportTimer = 0;
            report(server);
        }
    }

    @Override
    public void electricalTick() {
        if(shunts == null)
            return;
        for(int n = 0; n < CHANNELS; ++n) {
            var shunt = shunts[n];
            var sense = senses[n];
            if(shunt == null || sense == null || !shunt.isConverged())
                continue;
            var reading = readings[n];
            reading.sample(sense, shunt, ForkHooks.get().drainRealPower(shunt));
            amps[n] = (float) reading.signedRmsCurrent();
            volts[n] = (float) reading.signedRmsVoltage();
            watts[n] = (float) reading.realPower();
            powerFactors[n] = (float) reading.powerFactor();
            energy[n] += reading.realPower() * TICK_SECONDS;
            double hz = ForkHooks.get().frequency(sense);
            hertz[n] = Double.isFinite(hz) ? (float) hz : 0;
        }
        // Phase of each channel's voltage against channel 1's: how far apart the lines are.
        double reference = senses[0] == null ? -1 : ForkHooks.get().phaseFraction(senses[0]);
        for(int n = 0; n < CHANNELS; ++n) {
            double phase = senses[n] == null ? -1 : ForkHooks.get().phaseFraction(senses[n]);
            angles[n] = reference >= 0 && phase >= 0 ? (float) Mth.wrapDegrees((phase - reference) * 360) : 0;
        }
    }

    /** Frequency (Hz) on the channel's voltage, 0 when it is not alternating or not metered by the fork. */
    public float frequency(int channel) {
        return hertz[channel];
    }

    /** Degrees the channel's voltage leads channel 1's, -180..180; 0 for channel 1 itself or without the fork. */
    public float phaseAngle(int channel) {
        return angles[channel];
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

    /** RMS, signed when the voltage is one-way. */
    public float voltage(int channel) {
        return volts[channel];
    }

    /** RMS, positive from In to Out when the current is one-way. */
    public float current(int channel) {
        return amps[channel];
    }

    /** Real power, watts. */
    public float power(int channel) {
        return watts[channel];
    }

    /** Real over apparent power, in [-1, 1]; 1 on a direct current or an unloaded channel. */
    public float powerFactor(int channel) {
        return powerFactors[channel];
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
                    .add(Lang.builder().text(String.format("%.1f V  %.2f A  %.0f W  PF %.2f  ", volts[n], amps[n], watts[n], powerFactors[n]) + wh(energyWh(n))).style(ChatFormatting.WHITE))
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
        if(lock != null)
            Lang.builder().translate("gui.lock.cabinet", lock.serial(), lock.ownerName()).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        for(var line : readings())
            Lang.builder().add(line).forGoggles(tooltip, 1);
        if(hasAntenna()) {
            Lang.builder().translate("gui.meter_antenna.id", meterId(), utility.isBlank() ? "-" : utility).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            if(lastReport >= 0 && level != null)
                Lang.builder().translate("gui.meter_antenna.last_report", (level.getGameTime() - lastReport) / 20).style(ChatFormatting.DARK_AQUA).forGoggles(tooltip, 1);
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    // ---- the utility's lock ----

    @Nullable
    public com.nolanbaker.pgmodernized.device.lock.Lock lock() {
        return lock;
    }

    /** Whether the seal keeps this player out of the cabinet. */
    public boolean lockedAgainst(@Nullable Player player) {
        return lock != null && !lock.mayOpen(player);
    }

    /** Server side: the lock in the hand seals the cabinet. */
    public void fitLock(Player player, ItemStack stack) {
        if(level == null || level.isClientSide)
            return;
        if(lock != null) {
            player.displayClientMessage(Lang.translate("message.lock.already").style(ChatFormatting.RED).component(), true);
            return;
        }
        lock = com.nolanbaker.pgmodernized.device.lock.Lock.fit(stack, player, level);
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
        player.displayClientMessage(Lang.builder().translate("message.lock.fitted", lock.serial()).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    /** Server side: the seal comes off into the hand, the same numbered lock. The block checks who may. */
    public void removeLock(Player player) {
        if(level == null || level.isClientSide || lock == null)
            return;
        var taken = lock;
        lock = null;
        var stack = taken.item();
        if(!player.getInventory().add(stack))
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
        player.displayClientMessage(Lang.builder().translate("message.lock.removed", taken.serial()).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    // ---- the meter antenna module ----

    public boolean hasAntenna() {
        var state = getBlockState();
        return state.hasProperty(CtCabinetBlock.ANTENNA) && state.getValue(CtCabinetBlock.ANTENNA);
    }

    /** The ID collectors list this meter under: the typed one, else one made from its position. */
    public String meterId() {
        if(!meterId.isBlank())
            return meterId;
        return "meter_" + coord(worldPosition.getX()) + "_" + coord(worldPosition.getY()) + "_" + coord(worldPosition.getZ());
    }

    private static String coord(int v) {
        return v < 0 ? "n" + (-v) : Integer.toString(v);
    }

    public String utility() {
        return utility;
    }

    /** One report to the level's meter register. */
    public void report(ServerLevel server) {
        var v = new double[CHANNELS];
        var a = new double[CHANNELS];
        var w = new double[CHANNELS];
        var pf = new double[CHANNELS];
        var wh = new double[CHANNELS];
        for(int k = 0; k < CHANNELS; ++k) {
            v[k] = volts[k];
            a[k] = amps[k];
            w[k] = watts[k];
            pf[k] = powerFactors[k];
            wh[k] = energyWh(k);
        }
        MeterRegistry.get(server).put(new MeterReport(meterId(), utility, BodySpace.live(server, worldPosition), server.getGameTime(), CHANNELS, v, a, w, pf, wh));
        lastReport = server.getGameTime();
    }

    /** The module came out: the register forgets this meter. */
    public void antennaRemoved() {
        if(level instanceof ServerLevel server)
            MeterRegistry.get(server).forget(meterId());
        lastReport = -1;
        setChanged();
        sendData();
    }

    @Override
    public List<Field> settingsFields() {
        return List.of(new Field("meter_id", "powergrid.gui.meter_antenna.meter_id", false, meterId),
                new Field("utility", "powergrid.gui.meter_antenna.utility", false, utility));
    }

    @Override
    public void applySettings(Map<String, String> values) {
        var oldId = meterId();
        if(values.containsKey("meter_id"))
            meterId = values.get("meter_id").trim();
        if(values.containsKey("utility"))
            utility = values.get("utility").trim();
        if(level instanceof ServerLevel server && hasAntenna()) {
            if(!oldId.equals(meterId()))
                MeterRegistry.get(server).forget(oldId);
            report(server);
        }
        setChanged();
        sendData();
    }

    @Override
    public void destroy() {
        super.destroy();
        if(level instanceof ServerLevel server && hasAntenna()) {
            MeterRegistry.get(server).forget(meterId());
            var center = worldPosition.getCenter();
            Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.METER_ANTENNA.asStack());
        }
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
        tag.putString("MeterId", meterId);
        tag.putString("Utility", utility);
        tag.putLong("LastReport", lastReport);
        if(lock != null)
            tag.put("Lock", lock.write());
        if(clientPacket) {
            var list = new net.minecraft.nbt.ListTag();
            for(int n = 0; n < CHANNELS; ++n) {
                list.add(net.minecraft.nbt.FloatTag.valueOf(volts[n]));
                list.add(net.minecraft.nbt.FloatTag.valueOf(amps[n]));
                list.add(net.minecraft.nbt.FloatTag.valueOf(watts[n]));
                list.add(net.minecraft.nbt.FloatTag.valueOf(powerFactors[n]));
                syncedWatts[n] = watts[n];
            }
            syncedEnergy = totalEnergy();
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
        meterId = tag.getString("MeterId");
        utility = tag.getString("Utility");
        lastReport = tag.contains("LastReport") ? tag.getLong("LastReport") : -1;
        lock = tag.contains("Lock") ? com.nolanbaker.pgmodernized.device.lock.Lock.read(tag.getCompound("Lock")) : null;
        if(clientPacket && tag.contains("Readings")) {
            var list = tag.getList("Readings", net.minecraft.nbt.Tag.TAG_FLOAT);
            for(int n = 0; n < CHANNELS && n * 4 + 3 < list.size(); ++n) {
                volts[n] = list.getFloat(n * 4);
                amps[n] = list.getFloat(n * 4 + 1);
                watts[n] = list.getFloat(n * 4 + 2);
                powerFactors[n] = list.getFloat(n * 4 + 3);
            }
        }
    }
}
