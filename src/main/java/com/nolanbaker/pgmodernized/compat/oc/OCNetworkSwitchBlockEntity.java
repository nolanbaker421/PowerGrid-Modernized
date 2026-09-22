package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.network.NetworkSwitchBlockEntity;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Network switch that is also a plain OpenComputers network node, so OC cables next to it connect. */
public class OCNetworkSwitchBlockEntity extends NetworkSwitchBlockEntity implements Environment {
    private final Node ocNode = Network.newNode(this, Visibility.None).create();

    public OCNetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
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

    @Override
    public Node node() {
        return ocNode;
    }

    @Override
    public void onConnect(Node node) {}

    @Override
    public void onDisconnect(Node node) {}

    @Override
    public void onMessage(Message message) {}
}
