package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.network.NetworkSwitchBlock;
import com.nolanbaker.pgmodernized.network.NetworkSwitchBlockEntity;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Network switch on OpenComputers. In switch mode it is one plain node that every port and any
 * adjacent OC cable joins. In relay mode each port has a node of its own in its own network, and
 * a network message that reaches one of them is repeated out of the others, hop counted, the way
 * OpenComputers' relay does; components never cross.
 */
public class OCNetworkSwitchBlockEntity extends NetworkSwitchBlockEntity implements Environment {
    private static final String NETWORK_MESSAGE = "network.message";

    private final Node ocNode = Network.newNode(this, Visibility.None).create();
    private final Node[] portNodes = new Node[NetworkSwitchBlock.PORTS];

    public OCNetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The node a cable in that port joins: the shared node, or the port's own in relay mode. */
    public Node nodeForPort(int port) {
        if(!isIsolated())
            return ocNode;
        int i = Math.max(0, Math.min(NetworkSwitchBlock.PORTS - 1, port));
        if(portNodes[i] == null)
            portNodes[i] = Network.newNode(this, Visibility.None).create();
        return portNodes[i];
    }

    @Override
    protected void onModeChanged() {
        // Drop every edge on both kinds of node; the cables re-link to the right ones within a second.
        OCNodeSupport.remove(ocNode);
        for(int i = 0; i < portNodes.length; ++i) {
            OCNodeSupport.remove(portNodes[i]);
            portNodes[i] = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || isRemoved() || !isIsolated())
            return;
        for(var node : portNodes) {
            if(node != null && node.network() == null)
                Network.joinNewNetwork(node);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
        for(var node : portNodes)
            OCNodeSupport.remove(node);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
        for(var node : portNodes)
            OCNodeSupport.remove(node);
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

    @Override
    public Node node() {
        return ocNode;
    }

    @Override
    public void onConnect(Node node) {}

    @Override
    public void onDisconnect(Node node) {}

    /** Relay mode: repeat a modem message that arrived on one port out of all the others. */
    @Override
    public void onMessage(Message message) {
        if(!isIsolated() || !NETWORK_MESSAGE.equals(message.name()))
            return;
        var source = message.source();
        if(source == null || source.network() == null)
            return;
        int from = -1;
        for(int i = 0; i < portNodes.length; ++i) {
            if(portNodes[i] != null && portNodes[i].network() == source.network())
                from = i;
        }
        if(from < 0)
            return;
        var data = message.data();
        Object[] relayed = data;
        if(data != null && data.length > 0 && data[0] instanceof Packet packet) {
            if(packet.ttl() <= 0)
                return;
            relayed = new Object[] {packet.hop()};
        }
        for(int j = 0; j < portNodes.length; ++j) {
            var node = portNodes[j];
            if(j == from || node == null || node.network() == null)
                continue;
            node.sendToReachable(NETWORK_MESSAGE, relayed);
        }
    }
}
