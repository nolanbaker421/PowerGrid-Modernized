package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A pull box with a chain post. The chain is a conduit run as far as the splice system is
 * concerned, but it stands at the fixed anchor, so from the anchor on the moving body the usual
 * "run next to this block" lookup finds nothing: the server notes the chain's entity id and syncs
 * it, and both sides look the run up by that.
 */
public class CableChainAnchorBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, INetworkJack, IHaveGoggleInformation {
    private DeviceSpliceHost deviceHubs;
    private final JackSupport jack = new JackSupport(this, false);
    private int chainEntity = -1;

    public CableChainAnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, CableChainAnchorBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
    }

    /** Same fill as a 4" raceway, on the knockout and on the chain. */
    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        if(hub != CableChainAnchorBlock.CHAIN_HUB)
            return deviceHubs().hubRun(hub);
        if(level != null && chainEntity >= 0 && level.getEntity(chainEntity) instanceof CableChainEntity chain && !chain.isRemoved())
            return chain;
        return level == null || level.isClientSide ? null : SpliceSupport.runAt(this, CableChainAnchorBlock.chainHubTerminal());
    }

    @Nullable
    public CableChainEntity chain() {
        return hubRun(CableChainAnchorBlock.CHAIN_HUB) instanceof CableChainEntity chain ? chain : null;
    }

    public Vec3 postPosition() {
        return IElectric.getTerminalPos(level, worldPosition, CableChainAnchorBlock.chainHubTerminal());
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        var run = SpliceSupport.runAt(this, CableChainAnchorBlock.chainHubTerminal());
        int id = run == null ? -1 : run.getId();
        if(id != chainEntity) {
            chainEntity = id;
            sendData();
        }
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
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket)
            tag.putInt("ChainEntity", chainEntity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket && tag.contains("ChainEntity"))
            chainEntity = tag.getInt("ChainEntity");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.cable_chain.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        var chain = chain();
        if(chain == null)
            Lang.builder().translate("gui.cable_chain.free").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.cable_chain.chained", String.format("%.0f", chain.travelLimit())).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
