package com.nolanbaker.pgmodernized.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The network side of a jack, owned by a block entity. It knows nothing about any particular computer
 * mod: each mod that is present registers a {@link LinkFactory}, and a {@link Link} wraps that mod's
 * network node. Connecting two jacks connects every link kind they have in common, so one Cat6 run
 * carries ComputerCraft and OpenComputers traffic alike.
 * <p>
 * Nodes are created lazily on the first server tick after the block entity loads and dropped again on
 * unload, matching how both computer mods treat their own cables. The cable entity re-links every
 * second, so a jack that reloads with fresh nodes is picked up without any bookkeeping here.
 */
public final class JackSupport {
    public interface Link {
        String kind();
        void connect(Link other);
        void disconnect(Link other);
        /** Nodes exist now: publish peripherals, join networks. */
        default void onLoad() {}
        /** Attach to adjacent cables of the same mod. Only standalone jacks do this. */
        default void scanNeighbours() {}
        void remove();
    }

    public interface LinkFactory {
        @Nullable
        Link create(BlockEntity owner, JackSupport jack);
    }

    private static final List<LinkFactory> FACTORIES = new ArrayList<>();

    public static void registerLinkFactory(LinkFactory factory) {
        FACTORIES.add(factory);
    }

    private final BlockEntity owner;
    private final boolean autoConnect;
    private final Map<String, Link> links = new HashMap<>();
    private final Set<ICat6Cable> cables = new HashSet<>();
    private boolean loaded;
    private boolean removed;
    private boolean neighboursDirty = true;

    /** @param autoConnect whether adjacent cables of the computer mods should join this jack's network. */
    public JackSupport(BlockEntity owner, boolean autoConnect) {
        this.owner = owner;
        this.autoConnect = autoConnect;
    }

    public BlockEntity owner() {
        return owner;
    }

    public BlockPos pos() {
        return owner.getBlockPos();
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Call from the owner's server tick. */
    public void tick() {
        var level = owner.getLevel();
        if(level == null || level.isClientSide || removed)
            return;
        if(!loaded) {
            loaded = true;
            for(var factory : FACTORIES) {
                var link = factory.create(owner, this);
                if(link != null)
                    links.put(link.kind(), link);
            }
            links.values().forEach(Link::onLoad);
        }
        if(autoConnect && neighboursDirty) {
            neighboursDirty = false;
            links.values().forEach(Link::scanNeighbours);
        }
    }

    public void markNeighboursDirty() {
        neighboursDirty = true;
    }

    public void connect(JackSupport other) {
        if(!loaded || !other.loaded || removed || other.removed || other == this)
            return;
        for(var link : links.values()) {
            var remote = other.links.get(link.kind());
            if(remote != null)
                link.connect(remote);
        }
    }

    public void disconnect(JackSupport other) {
        if(other == this)
            return;
        for(var link : links.values()) {
            var remote = other.links.get(link.kind());
            if(remote != null)
                link.disconnect(remote);
        }
    }

    @Nullable
    public Link link(String kind) {
        return links.get(kind);
    }

    public void addCable(ICat6Cable cable) {
        if(cables.add(cable))
            notifyCablesChanged();
    }

    public void removeCable(ICat6Cable cable) {
        if(cables.remove(cable))
            notifyCablesChanged();
    }

    private void notifyCablesChanged() {
        if(!removed && owner instanceof INetworkJack jack)
            jack.onCablesChanged();
    }

    public Collection<ICat6Cable> cables() {
        return cables;
    }

    public boolean isConnectedTo(BlockPos otherJack) {
        for(var cable : cables) {
            if(!cable.asWireEntity().isRemoved() && cable.connectsTo(otherJack))
                return true;
        }
        return false;
    }

    /** Every port takes exactly one cable. */
    public boolean isPortUsed(int port) {
        var pos = pos();
        for(var cable : cables) {
            if(!cable.asWireEntity().isRemoved() && cable.connectsTo(pos, port))
                return true;
        }
        return false;
    }

    /** The block is gone: detach every cable and drop the network nodes. */
    public void remove() {
        removed = true;
        var level = owner.getLevel();
        // Cables are detached on the server only; the resulting entity changes are synced to clients.
        if(level != null && !level.isClientSide) {
            var pos = pos();
            for(var cable : List.copyOf(cables))
                cable.jackLost(pos);
        }
        cables.clear();
        dropLinks();
    }

    /** The chunk unloaded: drop the nodes, they are rebuilt on the next tick after reload. */
    public void unload() {
        dropLinks();
        loaded = false;
        neighboursDirty = true;
    }

    private void dropLinks() {
        links.values().forEach(Link::remove);
        links.clear();
    }

    /** Stable, human-readable name for this jack on a computer network, e.g. {@code vfd_12_64_n5}. */
    public String networkName() {
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
        var type = key == null ? "jack" : key.getPath();
        var pos = owner.getBlockPos();
        return type + "_" + coord(pos.getX()) + "_" + coord(pos.getY()) + "_" + coord(pos.getZ());
    }

    private static String coord(int value) {
        return value < 0 ? "n" + (-value) : Integer.toString(value);
    }
}
