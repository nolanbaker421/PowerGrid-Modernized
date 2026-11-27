package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A chaseway's memory and wiring: the block it went through, for the model and the drop, and
 * twelve straight-through joins, slot k of the run on one end to slot k of the run on the other.
 */
public class ChasewayBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IHaveGoggleInformation {
    /** The model data key the client model reads the mimicked block from. */
    public static final ModelProperty<BlockState> MATERIAL = new ModelProperty<>();

    // No initialisers: ElectricBlockEntity's constructor calls buildCircuit before they would run.
    private DeviceSpliceHost deviceHubs;
    @Nullable
    private BlockState material;

    public ChasewayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, ChasewayBlock.LAYOUT);
        return deviceHubs;
    }

    @Nullable
    public BlockState material() {
        return material;
    }

    public void setMaterial(BlockState state) {
        material = state;
        setChanged();
        if(level != null && !level.isClientSide)
            sendData();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return hub == ChasewayBlock.HUB_POSITIVE || hub == ChasewayBlock.HUB_NEGATIVE ? ConduitSize.TWO : null;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("gui.chaseway.end", hub + 1).style(ChatFormatting.AQUA).component();
    }

    /** Every landing on one end is joined to the same-numbered landing on the other. */
    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var layout = ChasewayBlock.LAYOUT;
        for(int k = 0; k < DeviceHubs.PER_HUB; ++k)
            builder.connect(SpliceSupport.SPLICE_RESISTANCE,
                    builder.terminalNode(layout.conductorTerminal(ChasewayBlock.HUB_POSITIVE, k)),
                    builder.terminalNode(layout.conductorTerminal(ChasewayBlock.HUB_NEGATIVE, k)));
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            deviceHubs().lazyTick();
    }

    @Override
    public ModelData getModelData() {
        return material == null ? ModelData.EMPTY : ModelData.builder().with(MATERIAL, material).build();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var through = Lang.builder().translate("gui.chaseway.through").text(" ");
        if(material != null)
            through.add(material.getBlock().getName().copy());
        through.style(ChatFormatting.GRAY).forGoggles(tooltip);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(material != null)
            tag.put("Material", NbtUtils.writeBlockState(material));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        BlockState was = material;
        material = null;
        if(tag.contains("Material")) {
            var read = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Material"));
            if(!read.isAir())
                material = read;
        }
        if(clientPacket && level != null && level.isClientSide && material != was) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
