package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A chaseway's memory and wiring: the block it went through, for the model and the drop, which
 * of the 27 slots hold a sleeve, and for each sleeve twelve straight-through joins, slot k of
 * the run on one end to slot k of the run on the other.
 */
public class ChasewayBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IHaveGoggleInformation {
    /** Model data: the mimicked block, and the sleeves as a bit per slot. */
    public static final ModelProperty<BlockState> MATERIAL = new ModelProperty<>();
    public static final ModelProperty<Integer> SLEEVES = new ModelProperty<>();

    // No initialisers: ElectricBlockEntity's constructor calls buildCircuit before they would run.
    private DeviceSpliceHost deviceHubs;
    @Nullable
    private BlockState material;
    private int sleeves;

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

    // ---- material ----

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

    // ---- sleeves ----

    public boolean hasSleeve(int slot) {
        return slot >= 0 && slot < ChasewayBlock.SLOT_COUNT && (sleeves & (1 << slot)) != 0;
    }

    public int sleeveBits() {
        return sleeves;
    }

    public int sleeveCount() {
        return Integer.bitCount(sleeves);
    }

    /** Server side: adds a sleeve and wires it through. */
    public void addSleeve(int slot) {
        if(hasSleeve(slot))
            return;
        sleeves |= 1 << slot;
        if(level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        setChanged();
        if(level != null && !level.isClientSide)
            sendData();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    /** A plate is a knockout only while its sleeve is there. */
    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return hasSleeve(ChasewayBlock.slotOfHub(hub)) ? ConduitSize.TWO : null;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("gui.chaseway.end", ChasewayBlock.slotOfHub(hub) + 1, hub % 2 + 1).style(ChatFormatting.AQUA).component();
    }

    /** Every landing on one end of a sleeve is joined to the same-numbered landing on its other end. */
    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var layout = ChasewayBlock.LAYOUT;
        for(int slot = 0; slot < ChasewayBlock.SLOT_COUNT; ++slot) {
            if(!hasSleeve(slot))
                continue;
            var axis = ChasewayBlock.axisOf(slot);
            int a = ChasewayBlock.hub(slot, Direction.get(Direction.AxisDirection.POSITIVE, axis));
            int b = ChasewayBlock.hub(slot, Direction.get(Direction.AxisDirection.NEGATIVE, axis));
            for(int k = 0; k < DeviceHubs.PER_HUB; ++k)
                builder.connect(SpliceSupport.SPLICE_RESISTANCE, builder.terminalNode(layout.conductorTerminal(a, k)), builder.terminalNode(layout.conductorTerminal(b, k)));
        }
    }

    /** The circuit built in the constructor knew no sleeves; build it again once the tag is in. */
    @Override
    public void initialize() {
        super.initialize();
        if(level != null && !level.isClientSide && electricBehaviour != null && sleeves != 0)
            electricBehaviour.rebuildCircuit(false);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            deviceHubs().lazyTick();
    }

    @Override
    public ModelData getModelData() {
        var builder = ModelData.builder().with(SLEEVES, sleeves);
        if(material != null)
            builder.with(MATERIAL, material);
        return builder.build();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var through = Lang.builder().translate("gui.chaseway.through", sleeveCount()).text(" ");
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
        tag.putInt("Sleeves", sleeves);
        if(material != null)
            tag.put("Material", NbtUtils.writeBlockState(material));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        BlockState wasMaterial = material;
        int wasSleeves = sleeves;
        sleeves = tag.getInt("Sleeves");
        material = null;
        if(tag.contains("Material")) {
            var read = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Material"));
            if(!read.isAir())
                material = read;
        }
        if(clientPacket && level != null && level.isClientSide && (material != wasMaterial || sleeves != wasSleeves)) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
