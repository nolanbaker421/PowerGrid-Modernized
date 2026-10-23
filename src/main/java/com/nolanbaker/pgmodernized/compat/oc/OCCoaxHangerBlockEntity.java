package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.radio.CoaxHangerBlock;
import com.nolanbaker.pgmodernized.radio.CoaxHangerBlockEntity;
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

/** A hanger on OpenComputers: one invisible node per clip, so a Cat6 in and out of a clip is one cable. */
public class OCCoaxHangerBlockEntity extends CoaxHangerBlockEntity implements Environment {
    private final Node ocNode = Network.newNode(this, Visibility.None).create();
    private final Node[] clipNodes = new Node[CoaxHangerBlock.CLIPS];

    public OCCoaxHangerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The node of the clip a port belongs to. */
    public Node nodeForPort(int port) {
        int clip = Math.max(0, Math.min(CoaxHangerBlock.CLIPS - 1, port / 2));
        if(clipNodes[clip] == null) {
            clipNodes[clip] = Network.newNode(this, Visibility.None).create();
            Network.joinNewNetwork(clipNodes[clip]);
        }
        return clipNodes[clip];
    }

    private void dropClips() {
        for(int i = 0; i < clipNodes.length; ++i) {
            if(clipNodes[i] != null) {
                OCNodeSupport.remove(clipNodes[i]);
                clipNodes[i] = null;
            }
        }
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
        dropClips();
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
        dropClips();
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
}
