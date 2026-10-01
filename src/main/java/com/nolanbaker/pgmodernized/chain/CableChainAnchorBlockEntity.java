package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;
import java.util.UUID;

/** Four plain terminals and a jack; the chain between two anchors does the rest. Remembers which chain it belongs to. */
public class CableChainAnchorBlockEntity extends ElectricBlockEntity implements INetworkJack, IHaveGoggleInformation {
    private final JackSupport jack = new JackSupport(this, false);
    @Nullable
    private UUID chain;

    public CableChainAnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(CableChainAnchorBlock.TERMINALS);
    }

    @Nullable
    public UUID chain() {
        return chain;
    }

    public void setChain(@Nullable UUID chain) {
        this.chain = chain;
        setChanged();
        sendData();
    }

    /** The chain entity this anchor holds, if it is loaded and alive. */
    @Nullable
    public CableChainEntity chainEntity() {
        if(chain == null || !(level instanceof ServerLevel server))
            return null;
        return server.getEntity(chain) instanceof CableChainEntity entity && entity.isAlive() ? entity : null;
    }

    public Vec3 mountPosition() {
        return IElectric.getTerminalPos(level, worldPosition, CableChainAnchorBlock.MOUNT);
    }

    // ---- jack ----

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return IElectric.getTerminalPos(level, worldPosition, CableChainAnchorBlock.JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return CableChainAnchorBlock.JACK;
    }

    // ---- save ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if(chain != null)
            tag.putUUID("Chain", chain);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        chain = tag.hasUUID("Chain") ? tag.getUUID("Chain") : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.cable_chain.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(chain == null ? "gui.cable_chain.free" : "gui.cable_chain.chained")
                .style(chain == null ? ChatFormatting.GRAY : ChatFormatting.AQUA).forGoggles(tooltip, 1);
        return true;
    }
}
