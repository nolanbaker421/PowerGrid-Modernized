package com.nolanbaker.pgmodernized.compat.cc.network;

import com.nolanbaker.pgmodernized.network.JackSupport;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A jack's presence on the ComputerCraft wired network. The jack is a node in CC's network graph;
 * Cat6 cables add edges between nodes, and a device jack publishes the device's peripheral so any
 * computer on the same network sees it as a remote peripheral, just as it would through a wired modem.
 */
public final class CCJackLink implements JackSupport.Link, WiredElement {
    public static final String KIND = "computercraft";

    private final BlockEntity owner;
    private final JackSupport jack;
    @Nullable
    private final IPeripheral peripheral;
    private final WiredNode node;

    public CCJackLink(BlockEntity owner, JackSupport jack, @Nullable IPeripheral peripheral) {
        this.owner = owner;
        this.jack = jack;
        this.peripheral = peripheral;
        this.node = ComputerCraftAPI.createWiredNodeForElement(this);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void connect(JackSupport.Link other) {
        if(other instanceof CCJackLink remote)
            node.connectTo(remote.node);
    }

    @Override
    public void disconnect(JackSupport.Link other) {
        if(other instanceof CCJackLink remote)
            node.disconnectFrom(remote.node);
    }

    @Override
    public void onLoad() {
        node.updatePeripherals(peripheral == null ? Map.of() : Map.of(jack.networkName(), peripheral));
    }

    @Override
    public void scanNeighbours() {
        var level = owner.getLevel();
        if(level == null)
            return;
        var pos = owner.getBlockPos();
        for(var direction : Direction.values()) {
            var element = level.getCapability(WiredElementCapability.get(), pos.relative(direction), direction.getOpposite());
            if(element != null && element != this)
                node.connectTo(element.getNode());
        }
    }

    @Override
    public void remove() {
        node.remove();
    }

    // --- WiredElement ---

    @Override
    public WiredNode getNode() {
        return node;
    }

    @Override
    public Level getLevel() {
        return owner.getLevel();
    }

    @Override
    public Vec3 getPosition() {
        return Vec3.atCenterOf(owner.getBlockPos());
    }

    @Override
    public String getSenderID() {
        return jack.networkName();
    }
}
