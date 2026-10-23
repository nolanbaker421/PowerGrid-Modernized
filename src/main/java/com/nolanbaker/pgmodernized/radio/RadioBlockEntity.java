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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.nolanbaker.pgmodernized.radio.RadioBlock.*;

/**
 * A radio. Once a second it follows its coax to an antenna, which gives it a role, and then looks
 * for the radios it should be joined to: a dish takes the nearest other dish with the same link ID
 * that is aimed back at it; a cell antenna takes the nearest sector of its carrier that covers it
 * and has room; a sector waits to be taken. A joined pair's Cat6 jacks are connected exactly as a
 * cable would connect them, so the computer networks merge across the air. A radio that loses
 * power, its antenna or its partner drops the join.
 */
public class RadioBlockEntity extends ElectricBlockEntity implements INetworkJack, ICoaxPort, ISettingsHost, IDeviceSpliceHost, IHaveGoggleInformation {
    public static final float LOAD_WATTS = 50, RATED_VOLTS = 120, MIN_VOLTS = 60;
    private static final int REFRESH_INTERVAL = 20;
    /** Every radio in a level, for the ones looking for a partner. */
    private static final Map<Level, Set<RadioBlockEntity>> RADIOS = new WeakHashMap<>();

    /** What the goggles and computers say about a peer. */
    public record Peer(BlockPos pos, String linkId, double distance, double offAxis) {}

    private final JackSupport jack = new JackSupport(this, false);
    private final CoaxSupport coax = new CoaxSupport(this);
    private DeviceSpliceHost deviceHubs;
    private ElectricWire load;
    private AcReadings.Filter reading;
    private String linkId = "";
    private float volts;

    @Nullable
    private AntennaBlockEntity antenna;
    private double coaxLength;
    /** Radios this one joined, and radios that joined this one. */
    private final Set<RadioBlockEntity> outbound = new HashSet<>(), inbound = new HashSet<>();
    private List<Peer> peers = List.of();
    private int refreshTimer;
    private boolean registered;
    private String synced = "";
    // Client copies for the goggles.
    private String clientRole = "";
    private double clientCoax, clientRange;
    private int clientPeerCount;

    public RadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    // ---- power ----

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
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

    public float volts() {
        return volts;
    }

    // ---- identity ----

    public String linkId() {
        return linkId;
    }

    public void setLinkId(String id) {
        var clean = id == null ? "" : id.trim();
        if(clean.length() > 32)
            clean = clean.substring(0, 32);
        if(clean.equals(linkId))
            return;
        linkId = clean;
        setChanged();
        sendData();
        if(level != null && !level.isClientSide)
            refresh();
    }

    @Nullable
    public AntennaBlockEntity antenna() {
        return antenna;
    }

    @Nullable
    public AntennaKind role() {
        return antenna == null ? null : antenna.kind();
    }

    public double coaxLength() {
        return coaxLength;
    }

    /** The antenna's reach after the coax run has taken its share, blocks; 0 without an antenna. */
    public double range() {
        var role = role();
        if(role == null)
            return 0;
        double base = switch(role) {
            case DISH -> PgmConfig.RADIO_DISH_RANGE.get();
            case SECTOR -> PgmConfig.RADIO_SECTOR_RANGE.get() * RadioMath.beamGain(antenna == null ? 120 : antenna.beamwidth());
            case OMNI -> PgmConfig.RADIO_CELL_RANGE.get();
        };
        return base * RadioMath.coaxFactor(coaxLength, PgmConfig.COAX_FULL_RANGE_LENGTH.get(), PgmConfig.COAX_HALF_RANGE_LENGTH.get());
    }

    /** Powered, with an antenna and an ID: able to link. */
    public boolean up() {
        return antenna != null && !antenna.isRemoved() && powered() && !linkId.isBlank();
    }

    public List<Peer> peers() {
        return peers;
    }

    /** The radios joined to this one, either way round. */
    public Set<RadioBlockEntity> linked() {
        var all = new HashSet<>(outbound);
        all.addAll(inbound);
        return all;
    }

    public int clientCount() {
        return inbound.size();
    }

    // ---- the link ----

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        jack.tick();
        if(level.getGameTime() % 20 == 0)
            deviceHubs().lazyTick();
        if(!registered) {
            registered = true;
            RADIOS.computeIfAbsent(level, l -> new HashSet<>()).add(this);
        }
        if(++refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0;
            refresh();
        }
    }

    private static Set<RadioBlockEntity> radios(Level level) {
        return RADIOS.getOrDefault(level, Collections.emptySet());
    }

    private Vec3 antennaPosition() {
        return antenna == null ? worldPosition.getCenter() : antenna.livePosition();
    }

    /** Server side, once a second: find the antenna, then the partners, and make the jack connections match. */
    public void refresh() {
        if(level == null || level.isClientSide)
            return;
        var run = CoaxRun.trace(level, this, 0);
        var found = run == null ? null : run.antenna();
        coaxLength = run == null ? 0 : run.length();
        antenna = found;
        if(antenna != null)
            antenna.claim(worldPosition);

        var want = new LinkedHashMap<RadioBlockEntity, Peer>();
        if(up()) {
            switch(antenna.kind()) {
                case DISH -> {
                    var best = nearest(true);
                    if(best != null)
                        want.put(best, peerOf(best));
                }
                case OMNI -> {
                    var best = nearestSector();
                    if(best != null)
                        want.put(best, peerOf(best));
                }
                case SECTOR -> {}
            }
        }
        for(var old : List.copyOf(outbound)) {
            if(!want.containsKey(old) || old.isRemoved())
                unlink(old);
        }
        for(var peer : want.keySet())
            link(peer);
        // Inbound peers that died without saying so.
        for(var in : List.copyOf(inbound)) {
            if(in.isRemoved() || !in.outbound.contains(this))
                inbound.remove(in);
        }
        var list = new ArrayList<Peer>();
        for(var peer : outbound)
            list.add(peerOf(peer));
        for(var peer : inbound)
            list.add(peerOf(peer));
        peers = list;
        if(antenna != null)
            antenna.setAim(linkId, aimFor(antenna));
        // Only tell the clients when something they show has moved.
        var signature = linkId + "|" + (antenna == null ? "" : antenna.kind().id()) + "|" + Math.round(coaxLength) + "|" + powered() + "|" + peers;
        if(!signature.equals(synced)) {
            synced = signature;
            sendData();
        }
    }

    private Peer peerOf(RadioBlockEntity other) {
        var mine = antennaPosition();
        var theirs = other.antennaPosition();
        double off = antenna != null && antenna.kind().aimed() ? RadioMath.offAxis(mine, antenna.azimuth(), antenna.elevation(), theirs) : 0;
        return new Peer(other.worldPosition, other.linkId, mine.distanceTo(theirs), off);
    }

    private void link(RadioBlockEntity other) {
        jack.connect(other.jack);
        outbound.add(other);
        other.inbound.add(this);
    }

    private void unlink(RadioBlockEntity other) {
        jack.disconnect(other.jack);
        outbound.remove(other);
        other.inbound.remove(this);
    }

    private void dropAll() {
        for(var other : List.copyOf(outbound))
            unlink(other);
        for(var other : List.copyOf(inbound)) {
            other.jack.disconnect(jack);
            other.outbound.remove(this);
            inbound.remove(other);
        }
        peers = List.of();
    }

    /** The nearest other dish with this ID that we can reach and, when {@code aimed}, that both beams meet. */
    @Nullable
    public RadioBlockEntity nearest(boolean aimed) {
        if(level == null || antenna == null)
            return null;
        var mine = antennaPosition();
        RadioBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double beam = PgmConfig.RADIO_DISH_BEAM.get();
        for(var other : radios(level)) {
            if(other == this || other.isRemoved() || other.antenna == null || other.antenna.kind() != AntennaKind.DISH || !linkId.equals(other.linkId))
                continue;
            if(aimed && !other.up())
                continue;
            var theirs = other.antennaPosition();
            double d = mine.distanceTo(theirs);
            if(aimed) {
                if(d > Math.min(range(), other.range()))
                    continue;
                if(RadioMath.offAxis(mine, antenna.azimuth(), antenna.elevation(), theirs) > beam)
                    continue;
                if(RadioMath.offAxis(theirs, other.antenna.azimuth(), other.antenna.elevation(), mine) > beam)
                    continue;
                if(!lineOfSight(mine, theirs))
                    continue;
            }
            if(d < bestDistance) {
                best = other;
                bestDistance = d;
            }
        }
        return best;
    }

    /** The nearest sector of this carrier whose beam covers us, within both reaches, with a seat free. */
    @Nullable
    private RadioBlockEntity nearestSector() {
        if(level == null || antenna == null)
            return null;
        var mine = antennaPosition();
        RadioBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        int seats = PgmConfig.RADIO_SECTOR_CLIENTS.get();
        for(var other : radios(level)) {
            if(other == this || other.isRemoved() || other.antenna == null || other.antenna.kind() != AntennaKind.SECTOR || !linkId.equals(other.linkId) || !other.up())
                continue;
            var theirs = other.antennaPosition();
            double d = mine.distanceTo(theirs);
            if(d > Math.min(range(), other.range()))
                continue;
            if(!other.covers(theirs, mine))
                continue;
            int taken = other.inbound.contains(this) ? other.inbound.size() - 1 : other.inbound.size();
            if(taken >= seats)
                continue;
            if(!lineOfSight(theirs, mine))
                continue;
            if(d < bestDistance) {
                best = other;
                bestDistance = d;
            }
        }
        return best;
    }

    /** A sector's beam: within half its beamwidth of its azimuth, and not steeply above or below it. */
    private boolean covers(Vec3 from, Vec3 to) {
        if(antenna == null)
            return false;
        if(RadioMath.offBearing(from, antenna.azimuth(), to) > antenna.beamwidth() / 2)
            return false;
        return Math.abs(RadioMath.elevation(from, to) - antenna.elevation()) <= 45;
    }

    private boolean lineOfSight(Vec3 a, Vec3 b) {
        if(!PgmConfig.RADIO_LINE_OF_SIGHT.get() || level == null)
            return true;
        var hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(b) < 1.5;
    }

    @Override
    public void remove() {
        super.remove();
        if(level != null && !level.isClientSide) {
            dropAll();
            radios(level).remove(this);
        }
        coax.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if(level != null && !level.isClientSide) {
            dropAll();
            radios(level).remove(this);
            registered = false;
        }
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
        return RadioBlock.coaxPosition(getBlockState(), worldPosition);
    }

    // ---- settings ----

    @Override
    public List<Field> settingsFields() {
        return List.of(new Field("link_id", "powergrid.gui.radio.link_id", false, linkId));
    }

    @Override
    public void applySettings(Map<String, String> values) {
        if(values.containsKey("link_id"))
            setLinkId(values.get("link_id"));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putString("LinkId", linkId);
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putString("Role", role() == null ? "" : role().id());
            tag.putDouble("Coax", coaxLength);
            tag.putDouble("Range", range());
            var list = new ListTag();
            for(var peer : peers) {
                var p = new CompoundTag();
                p.putLong("Pos", peer.pos().asLong());
                p.putString("Id", peer.linkId());
                p.putDouble("Distance", peer.distance());
                p.putDouble("Off", peer.offAxis());
                list.add(p);
            }
            tag.put("Peers", list);
            tag.putInt("Clients", inbound.size());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        linkId = tag.getString("LinkId");
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            clientRole = tag.getString("Role");
            clientCoax = tag.getDouble("Coax");
            clientRange = tag.getDouble("Range");
            var list = tag.getList("Peers", Tag.TAG_COMPOUND);
            var read = new ArrayList<Peer>();
            for(int i = 0; i < list.size(); ++i) {
                var p = list.getCompound(i);
                read.add(new Peer(BlockPos.of(p.getLong("Pos")), p.getString("Id"), p.getDouble("Distance"), p.getDouble("Off")));
            }
            peers = read;
            clientPeerCount = tag.getInt("Clients");
        }
    }

    // ---- goggles ----

    private String roleId() {
        return level != null && level.isClientSide ? clientRole : role() == null ? "" : role().id();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.radio.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        String role = roleId();
        Lang.builder().translate("gui.radio.link_id").text(": ").text(linkId.isBlank() ? "-" : linkId).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(role.isEmpty()) {
            Lang.builder().translate("gui.radio.no_antenna").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            double coaxNow = level != null && level.isClientSide ? clientCoax : coaxLength;
            double rangeNow = level != null && level.isClientSide ? clientRange : range();
            Lang.builder().translate("gui.radio.antenna", Lang.builder().translate("gui.antenna." + role).component(), String.format("%.1f", coaxNow), String.format("%.0f", rangeNow))
                    .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        }
        Lang.builder().translate(powered() ? "gui.radio.powered" : "gui.radio.unpowered", String.format("%.0f", volts)).style(powered() ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        if(peers.isEmpty()) {
            Lang.builder().translate("gui.radio.no_link").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        } else {
            for(var peer : peers) {
                var line = Lang.builder().translate("gui.radio.peer", peer.linkId(), peer.pos().getX(), peer.pos().getY(), peer.pos().getZ(), String.format("%.0f", peer.distance()));
                if("dish_antenna".equals(role))
                    line.text("  ").add(Lang.builder().translate("gui.radio.off_axis", String.format("%.2f", peer.offAxis())));
                line.style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
            }
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    /** For the antenna's goggles: where its partner, or the nearest candidate, lies and how far off the aim is. */
    private AntennaBlockEntity.Aim aimFor(AntennaBlockEntity antenna) {
        int clients = inbound.size();
        if(!antenna.kind().aimed())
            return new AntennaBlockEntity.Aim("", 0, 0, 0, 0, false, clients);
        var here = antenna.livePosition();
        RadioBlockEntity target = outbound.isEmpty() ? (antenna.kind() == AntennaKind.DISH ? nearest(false) : null) : outbound.iterator().next();
        if(target == null || target.antenna == null)
            return new AntennaBlockEntity.Aim("", 0, 0, 0, 0, false, clients);
        var there = target.antennaPosition();
        return new AntennaBlockEntity.Aim(target.linkId(), Math.round(RadioMath.bearing(here, there) * 10) / 10.0, Math.round(RadioMath.elevation(here, there) * 10) / 10.0,
                Math.round(here.distanceTo(there)), Math.round(RadioMath.offAxis(here, antenna.azimuth(), antenna.elevation(), there) * 100) / 100.0,
                outbound.contains(target), clients);
    }
}
