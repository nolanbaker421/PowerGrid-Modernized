package com.nolanbaker.pgmodernized.compat.oc;

import org.jetbrains.annotations.Nullable;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.Network;
import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Radio that is an OpenComputers node itself, component "powergrid_radio"; a radio link joins two of these nodes. */
public class OCRadioBlockEntity extends RadioBlockEntity implements Environment {
    private static final String NETWORK_MESSAGE = "network.message";

    private final Node ocNode = OCNodeSupport.create(this, "powergrid_radio");
    /** Relay mode: the air side's own node, in its own network; messages are repeated between it and the component node. */
    @Nullable
    private Node airNode;
    private int lastPeers = -1;

    public OCRadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The node a jack port joins: the component node, or the air node for port 1 in relay mode. */
    public Node nodeForPort(int port) {
        return port == 1 && relay() ? air() : ocNode;
    }

    /** The node a cell card joins: the air node in relay mode, the component node in bridge mode. */
    public Node airNodeForCards() {
        return relay() ? air() : ocNode;
    }

    private Node air() {
        if(airNode == null)
            airNode = Network.newNode(this, Visibility.None).create();
        return airNode;
    }

    @Override
    protected void onModeChanged() {
        OCNodeSupport.remove(airNode);
        airNode = null;
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || ocNode.network() == null)
            return;
        if(relay() && air().network() == null && !isRemoved())
            Network.joinNewNetwork(air());
        int peers = peers().size();
        if(peers != lastPeers) {
            if(lastPeers >= 0)
                ocNode.sendToReachable("computer.signal", "radio_link", peers);
            lastPeers = peers;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
        OCNodeSupport.remove(airNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
        OCNodeSupport.remove(airNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    /** Relay mode: a modem message from the computers goes out over the air, and one from the air to the computers, a hop older. */
    @Override
    public void onMessage(Message message) {
        if(!relay() || airNode == null || !NETWORK_MESSAGE.equals(message.name()))
            return;
        var source = message.source();
        if(source == null || source.network() == null)
            return;
        Node to;
        if(source.network() == ocNode.network() && source != ocNode)
            to = airNode;
        else if(source.network() == airNode.network() && source != airNode)
            to = ocNode;
        else
            return;
        if(to.network() == null)
            return;
        var data = message.data();
        Object[] relayed = data;
        if(data != null && data.length > 0 && data[0] instanceof Packet packet) {
            if(packet.ttl() <= 0)
                return;
            relayed = new Object[] {packet.hop()};
        }
        to.sendToReachable(NETWORK_MESSAGE, relayed);
    }

    @Callback(direct = true, doc = "function():string -- bridge (networks merge over the link) or relay (network messages only).")
    public Object[] getMode(Context context, Arguments args) {
        return result(relay() ? "relay" : "bridge");
    }

    @Callback(doc = "function(mode:string) -- bridge or relay.")
    public Object[] setMode(Context context, Arguments args) {
        setRelay(args.checkString(0).trim().equalsIgnoreCase("relay"));
        return result();
    }

    private static Object[] result(Object... values) {
        return values;
    }

    @Callback(direct = true, doc = "function():string -- The link ID (a dish's pair, a carrier for sectors and cells).")
    public Object[] getLinkId(Context context, Arguments args) {
        return result(linkId());
    }

    @Callback(doc = "function(id:string) -- Sets the link ID.")
    public Object[] setLinkId(Context context, Arguments args) {
        setLinkId(args.checkString(0));
        return result();
    }

    @Callback(direct = true, doc = "function():string -- dish_antenna, sector_antenna, omni_antenna, or empty without an antenna.")
    public Object[] getRole(Context context, Arguments args) {
        return result(role() == null ? "" : role().id());
    }

    @Callback(direct = true, doc = "function():boolean -- Powered, with an antenna and an ID.")
    public Object[] isUp(Context context, Arguments args) {
        return result(up());
    }

    @Callback(direct = true, doc = "function():boolean -- Joined to at least one radio.")
    public Object[] isLinked(Context context, Arguments args) {
        return result(!peers().isEmpty());
    }

    @Callback(direct = true, doc = "function():table -- The antenna: kind, coax length, range, azimuth, elevation, beamwidth.")
    public Object[] getAntenna(Context context, Arguments args) {
        var table = new LinkedHashMap<String, Object>();
        table.put("kind", role() == null ? "" : role().id());
        table.put("coax", coaxLength());
        table.put("range", range());
        var antenna = antenna();
        if(antenna != null) {
            table.put("azimuth", antenna.azimuth());
            table.put("elevation", antenna.elevation());
            table.put("beamwidth", antenna.beamwidth());
        }
        return result(table);
    }

    @Callback(direct = true, doc = "function():table -- Joined radios: {id, x, y, z, distance, offAxis} from 1.")
    public Object[] getPeers(Context context, Arguments args) {
        var table = new LinkedHashMap<Integer, Map<String, Object>>();
        int i = 1;
        for(var peer : peers()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("id", peer.linkId());
            entry.put("x", (double) peer.pos().getX());
            entry.put("y", (double) peer.pos().getY());
            entry.put("z", (double) peer.pos().getZ());
            entry.put("distance", peer.distance());
            entry.put("offAxis", peer.offAxis());
            table.put(i++, entry);
        }
        return result(table);
    }
}
