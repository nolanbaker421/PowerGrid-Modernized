package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nolanbaker.pgmodernized.radio.CollectorBlock.*;

/**
 * Once a second: find the antenna on the coax, then every meter of this utility whose last report
 * came from within range of it. New reports raise an event for the computers listening.
 */
public class CollectorBlockEntity extends ElectricBlockEntity implements INetworkJack, ICoaxPort, ISettingsHost, IDeviceSpliceHost, IHaveGoggleInformation {
    public static final float LOAD_WATTS = 50, RATED_VOLTS = 120, MIN_VOLTS = 60;
    private static final int REFRESH_INTERVAL = 20;

    /** Something that wants to hear of new reports: the CC peripheral, the OC subclass. */
    public interface Listener {
        void onReport(MeterReport report);
    }

    private final JackSupport jack = new JackSupport(this, false);
    private final CoaxSupport coax = new CoaxSupport(this);
    private DeviceSpliceHost deviceHubs;
    private final Set<Listener> listeners = new HashSet<>();
    private ElectricWire load;
    private AcReadings.Filter reading;
    private float volts;
    private String utility = "";
    @Nullable
    private AntennaBlockEntity antenna;
    private double coaxLength;
    private List<MeterReport> meters = List.of();
    private final Map<String, Long> seen = new HashMap<>();
    private int refreshTimer;
    private int clientMeters;
    private String clientRole = "";
    private double clientRange;
    private String synced = "";

    public CollectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, RadioBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        load = builder.connect(RATED_VOLTS * RATED_VOLTS / LOAD_WATTS, builder.terminalNode(LINE), builder.terminalNode(NEUTRAL));
        reading = new AcReadings.Filter();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(load == null || !load.isConverged())
            return;
        reading.sample(load);
        volts = (float) reading.rmsVoltage();
    }

    public boolean powered() {
        return volts >= MIN_VOLTS;
    }

    public String utility() {
        return utility;
    }

    public void setUtility(String id) {
        var clean = id == null ? "" : id.trim();
        if(clean.length() > 32)
            clean = clean.substring(0, 32);
        utility = clean;
        setChanged();
        sendData();
    }

    /** The reach, blocks: the configured range less what the coax run costs; 0 without an antenna. */
    public double range() {
        if(antenna == null)
            return 0;
        return PgmConfig.METER_RANGE.get() * RadioMath.coaxFactor(coaxLength, PgmConfig.COAX_FULL_RANGE_LENGTH.get(), PgmConfig.COAX_HALF_RANGE_LENGTH.get());
    }

    public List<MeterReport> meters() {
        return meters;
    }

    @Nullable
    public MeterReport meter(String id) {
        for(var report : meters) {
            if(report.id().equals(id))
                return report;
        }
        return null;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void tick() {
        super.tick();
        if(!(level instanceof ServerLevel server))
            return;
        jack.tick();
        if(++refreshTimer < REFRESH_INTERVAL)
            return;
        refreshTimer = 0;
        deviceHubs().lazyTick();
        refresh(server);
    }

    private void refresh(ServerLevel server) {
        var run = CoaxRun.trace(server, this, 0);
        antenna = run == null ? null : run.antenna();
        coaxLength = run == null ? 0 : run.length();
        if(antenna != null)
            antenna.claim(worldPosition);
        if(antenna == null || !powered() || utility.isBlank()) {
            meters = List.of();
            syncIfChanged();
            return;
        }
        Vec3 here = antenna.livePosition();
        var list = MeterRegistry.get(server).within(utility, here, range());
        meters = List.copyOf(list);
        for(var report : list) {
            var last = seen.get(report.id());
            if(last == null || last != report.time()) {
                seen.put(report.id(), report.time());
                if(last != null) {
                    for(var listener : new ArrayList<>(listeners))
                        listener.onReport(report);
                }
            }
        }
        syncIfChanged();
    }

    private void syncIfChanged() {
        var signature = utility + "|" + (antenna == null ? "" : antenna.kind().id()) + "|" + Math.round(range()) + "|" + powered() + "|" + meters.size();
        if(!signature.equals(synced)) {
            synced = signature;
            sendData();
        }
    }

    @Override
    public void remove() {
        super.remove();
        coax.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    // ---- jack and coax ----

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
    public CoaxSupport coaxSupport() {
        return coax;
    }

    @Override
    public Vec3 coaxPosition(int port) {
        return CollectorBlock.coaxPosition(getBlockState(), worldPosition);
    }

    // ---- settings ----

    @Override
    public List<Field> settingsFields() {
        return List.of(new Field("utility", "powergrid.gui.meter_antenna.utility", false, utility));
    }

    @Override
    public void applySettings(Map<String, String> values) {
        if(values.containsKey("utility"))
            setUtility(values.get("utility"));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putString("Utility", utility);
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putInt("Meters", meters.size());
            tag.putString("Role", antenna == null ? "" : antenna.kind().id());
            tag.putDouble("Range", range());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        utility = tag.getString("Utility");
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            clientMeters = tag.getInt("Meters");
            clientRole = tag.getString("Role");
            clientRange = tag.getDouble("Range");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.collector.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.meter_antenna.utility").text(": ").text(utility.isBlank() ? "-" : utility).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        boolean client = level != null && level.isClientSide;
        String role = client ? clientRole : antenna == null ? "" : antenna.kind().id();
        if(role.isEmpty())
            Lang.builder().translate("gui.radio.no_antenna").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.collector.range", Lang.builder().translate("gui.antenna." + role).component(), String.format("%.0f", client ? clientRange : range())).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate(powered() ? "gui.radio.powered" : "gui.radio.unpowered", String.format("%.0f", volts)).style(powered() ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.collector.meters", client ? clientMeters : meters.size()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
