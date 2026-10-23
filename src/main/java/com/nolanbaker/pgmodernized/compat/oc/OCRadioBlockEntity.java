package com.nolanbaker.pgmodernized.compat.oc;

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
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_radio");
    private int lastPeers = -1;

    public OCRadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || ocNode.network() == null)
            return;
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
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
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
    @Override public void onMessage(Message message) {}

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
