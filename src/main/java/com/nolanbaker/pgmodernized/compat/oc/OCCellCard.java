package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.radio.CellCardItem;
import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import li.cil.oc.api.Network;
import li.cil.oc.api.UnrecoverablePersistanceException;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * The Cell Card as an OpenComputers component, {@code cell_card}. Once a second it looks for the
 * nearest sector of its carrier whose beam covers the host, within the omni reach, with a seat
 * free, and joins that radio's node. In relay mode the card keeps a node of its own on the air side
 * and repeats network messages between the computer and the tower, hop counted, so components never
 * cross; in bridge mode the computer's node joins the tower's network outright. Raises the
 * {@code cell_link} signal with {@code true} or {@code false} as it joins and drops.
 */
public class OCCellCard extends AbstractManagedEnvironment {
    private static final String NETWORK_MESSAGE = "network.message";
    private static final int INTERVAL = 20;

    private final EnvironmentHost host;
    private String carrier;
    private boolean relay;
    @Nullable
    private Node air;
    @Nullable
    private RadioBlockEntity tower;
    private int timer;

    public OCCellCard(ItemStack stack, EnvironmentHost host) {
        this.host = host;
        carrier = CellCardItem.carrier(stack);
        relay = CellCardItem.relay(stack);
        setNode(Network.newNode(this, Visibility.Network).withComponent("cell_card").create());
    }

    // ---- the link ----

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void update() {
        var level = host.getEnvironmentLevel();
        if(level == null || level.isClientSide || node() == null || node().network() == null)
            return;
        if(++timer < INTERVAL)
            return;
        timer = 0;
        if(relay) {
            if(air == null)
                air = Network.newNode(this, Visibility.None).create();
            if(air.network() == null)
                Network.joinNewNetwork(air);
        }
        var pos = new Vec3(host.xPosition(), host.yPosition(), host.zPosition());
        var best = carrier.isBlank() ? null : RadioBlockEntity.nearestSector(level, pos, carrier, PgmConfig.RADIO_CELL_RANGE.get(), this);
        if(best != tower) {
            boolean was = tower != null;
            drop();
            if(best != null)
                join(best);
            if(was != (tower != null))
                node().sendToReachable("computer.signal", "cell_link", tower != null);
        } else if(tower != null) {
            tower.claimSeat(this);
        }
    }

    /** The node that touches the tower: the air node in relay mode, the computer's own in bridge mode. */
    @Nullable
    private Node client() {
        return relay ? air : node();
    }

    private void join(RadioBlockEntity radio) {
        if(!(radio instanceof OCRadioBlockEntity oc))
            return;
        var theirs = oc.airNodeForCards();
        var mine = client();
        if(theirs == null || theirs.network() == null || mine == null || mine.network() == null)
            return;
        try {
            mine.connect(theirs);
        } catch(RuntimeException e) {
            return;
        }
        radio.claimSeat(this);
        tower = radio;
    }

    private void drop() {
        if(tower == null)
            return;
        if(tower instanceof OCRadioBlockEntity oc) {
            var theirs = oc.airNodeForCards();
            var mine = client();
            if(theirs != null && mine != null && mine.network() != null && mine.network() == theirs.network()) {
                try {
                    mine.disconnect(theirs);
                } catch(RuntimeException ignored) {}
            }
        }
        tower.releaseSeat(this);
        tower = null;
    }

    private void setRelay(boolean value) {
        if(relay == value)
            return;
        drop();
        relay = value;
        if(!relay && air != null) {
            air.remove();
            air = null;
        }
        timer = INTERVAL;
    }

    @Override
    public void onDisconnect(Node node) {
        super.onDisconnect(node);
        if(node == node()) {
            drop();
            if(air != null) {
                air.remove();
                air = null;
            }
        }
    }

    /** Relay mode: a modem message on one side goes out of the other, one hop older. */
    @Override
    public void onMessage(Message message) {
        super.onMessage(message);
        if(!relay || air == null || !NETWORK_MESSAGE.equals(message.name()))
            return;
        var source = message.source();
        if(source == null || source.network() == null || node() == null)
            return;
        Node from, to;
        if(source.network() == node().network()) {
            from = node();
            to = air;
        } else if(source.network() == air.network()) {
            from = air;
            to = node();
        } else {
            return;
        }
        if(from == source || to.network() == null)
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

    // ---- persistence: the card's own data component, so the item keeps its carrier when pulled ----

    @Override
    public void loadData(DataComponentHolder holder) throws UnrecoverablePersistanceException {
        super.loadData(holder);
        var tag = holder.get(ModDataComponents.CELL_CARD.get());
        if(tag != null) {
            carrier = tag.getString("Carrier");
            relay = !tag.contains("Relay") || tag.getBoolean("Relay");
        }
    }

    @Override
    public void saveData(MutableDataComponentHolder holder) {
        super.saveData(holder);
        holder.set(ModDataComponents.CELL_CARD.get(), CellCardItem.tag(carrier, relay));
    }

    // ---- Lua ----

    private static Object[] result(Object... values) {
        return values;
    }

    @Callback(direct = true, doc = "function():string -- The carrier this card joins.")
    public Object[] getCarrier(Context context, Arguments args) {
        return result(carrier);
    }

    @Callback(doc = "function(id:string) -- Sets the carrier; the card re-registers within a second.")
    public Object[] setCarrier(Context context, Arguments args) {
        carrier = args.checkString(0).trim();
        drop();
        timer = INTERVAL;
        host.markChanged();
        return result();
    }

    @Callback(direct = true, doc = "function():string -- relay (messages only) or bridge (networks merge).")
    public Object[] getMode(Context context, Arguments args) {
        return result(relay ? "relay" : "bridge");
    }

    @Callback(doc = "function(mode:string) -- relay or bridge.")
    public Object[] setMode(Context context, Arguments args) {
        setRelay(!args.checkString(0).trim().equalsIgnoreCase("bridge"));
        host.markChanged();
        return result();
    }

    @Callback(direct = true, doc = "function():boolean -- Joined to a sector right now.")
    public Object[] isConnected(Context context, Arguments args) {
        return result(tower != null && !tower.isRemoved());
    }

    @Callback(direct = true, doc = "function():table -- The tower: x, y, z, distance, linkId; nil when not joined.")
    public Object[] getTower(Context context, Arguments args) {
        if(tower == null || tower.isRemoved())
            return result((Object) null);
        var pos = tower.getBlockPos();
        var here = new Vec3(host.xPosition(), host.yPosition(), host.zPosition());
        return result(java.util.Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(),
                "distance", here.distanceTo(pos.getCenter()), "linkId", tower.linkId()));
    }
}
