package com.nolanbaker.pgmodernized.compat.cc.network;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A radio link in relay mode on CC: Tweaked. The two wired nodes stay separate networks, so no
 * peripheral crosses, and a modem packet transmitted on either side is repeated on the other.
 * Repeated packets carry a sender that names the bridge, so the far receiver does not send them
 * back; a chain of relays works because each bridge is its own name.
 */
public final class CCPacketBridge {
    private record Key(WiredNode a, WiredNode b) {}

    private static final Map<Key, CCPacketBridge> BRIDGES = new HashMap<>();

    private final Forwarder forwardAB, forwardBA;

    private CCPacketBridge(WiredNode a, WiredNode b) {
        forwardAB = new Forwarder(a, b);
        forwardBA = new Forwarder(b, a);
        a.addReceiver(forwardAB);
        b.addReceiver(forwardBA);
    }

    private static Key key(WiredNode a, WiredNode b) {
        return System.identityHashCode(a) <= System.identityHashCode(b) ? new Key(a, b) : new Key(b, a);
    }

    public static void link(WiredNode a, WiredNode b) {
        if(a == b)
            return;
        BRIDGES.computeIfAbsent(key(a, b), k -> new CCPacketBridge(k.a(), k.b()));
    }

    public static void unlink(WiredNode a, WiredNode b) {
        var bridge = BRIDGES.remove(key(a, b));
        if(bridge != null)
            bridge.close();
    }

    /** The node is going away: drop every bridge it is part of. */
    public static void unlinkAll(WiredNode node) {
        BRIDGES.entrySet().removeIf(e -> {
            if(e.getKey().a() != node && e.getKey().b() != node)
                return false;
            e.getValue().close();
            return true;
        });
    }

    private void close() {
        forwardAB.from.removeReceiver(forwardAB);
        forwardBA.from.removeReceiver(forwardBA);
    }

    /** A packet as it goes out of the far side: the original sender, stamped with the bridge it came through. */
    private record Relayed(PacketSender origin, CCPacketBridge bridge) implements PacketSender {
        @Override
        public Level getLevel() {
            return origin.getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return origin.getPosition();
        }

        @Override
        public String getSenderID() {
            return origin.getSenderID();
        }
    }

    private final class Forwarder implements PacketReceiver {
        private final WiredNode from, to;

        private Forwarder(WiredNode from, WiredNode to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Level getLevel() {
            return from.getElement().getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return from.getElement().getPosition();
        }

        @Override
        public double getRange() {
            return Double.MAX_VALUE;
        }

        @Override
        public boolean isInterdimensional() {
            return false;
        }

        @Override
        public void receiveSameDimension(Packet packet, double distance) {
            if(packet.sender() instanceof Relayed relayed && relayed.bridge() == CCPacketBridge.this)
                return;
            var sender = packet.sender() instanceof Relayed r ? r.origin() : packet.sender();
            to.transmitSameDimension(new Packet(packet.channel(), packet.replyChannel(), packet.payload(), new Relayed(Objects.requireNonNull(sender), CCPacketBridge.this)), Double.MAX_VALUE);
        }

        @Override
        public void receiveDifferentDimension(Packet packet) {}
    }
}
