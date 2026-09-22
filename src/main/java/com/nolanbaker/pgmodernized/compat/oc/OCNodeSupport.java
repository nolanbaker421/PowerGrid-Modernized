package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared lifecycle for block entities that are OpenComputers network nodes themselves (no adapter needed). */
final class OCNodeSupport {
    private static final String TAG = "OCNode";

    private OCNodeSupport() {}

    static Node create(Environment host, String componentName) {
        return Network.newNode(host, Visibility.Network).withComponent(componentName).create();
    }

    /** Call every server tick: joins the network formed by adjacent cables/computers once the block is in the world. */
    static void tick(BlockEntity be, Node node) {
        if(node == null || be.getLevel() == null || be.getLevel().isClientSide || be.isRemoved())
            return;
        if(node.network() == null)
            Network.joinOrCreateNetwork(be);
    }

    static void remove(Node node) {
        if(node != null)
            node.remove();
    }

    static void save(CompoundTag tag, HolderLookup.Provider registries, Node node, boolean clientPacket) {
        if(clientPacket || node == null)
            return;
        var nodeTag = new CompoundTag();
        node.saveData(nodeTag, registries);
        tag.put(TAG, nodeTag);
    }

    static void load(CompoundTag tag, HolderLookup.Provider registries, Node node, boolean clientPacket) {
        if(clientPacket || node == null || !tag.contains(TAG))
            return;
        try {
            node.loadData(tag.getCompound(TAG), registries);
        } catch(Exception e) {
            // Node keeps a fresh address; only component addresses in running Lua programs would change.
            com.nolanbaker.pgmodernized.PowerGridModernized.LOGGER.warn("Could not restore OpenComputers node address", e);
        }
    }

    static Object[] result(Object... values) {
        return values;
    }
}
