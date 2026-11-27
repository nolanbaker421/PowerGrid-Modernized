package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * A chaseway's memory and wiring: the block it went through, for the model and the drop, which
 * of the 27 slots hold a sleeve, and for each piece of sleeve (one sleeve, or several that cross)
 * twelve joins, slot k of the run on one end to slot k of the run on every other. It also lays
 * the nipples that join it to the next chased block and to any fitting against a sleeve end.
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

    /**
     * The sleeves a sleeve is one piece with: itself, every sleeve crossing it inside the block, and
     * every sleeve crossing those. A lone sleeve is a pass-through; crossing ones an elbow or a tee.
     */
    public List<Integer> joinedSlots(int slot) {
        var group = new ArrayList<Integer>();
        if(!hasSleeve(slot))
            return group;
        group.add(slot);
        for(int i = 0; i < group.size(); ++i) {
            for(int s = 0; s < ChasewayBlock.SLOT_COUNT; ++s)
                if(hasSleeve(s) && !group.contains(s) && ChasewayBlock.cross(group.get(i), s))
                    group.add(s);
        }
        return group;
    }

    /** Every sleeve end a wire arriving at {@code hub} goes on to: the other ends of its own sleeve and of the sleeves joined to it. */
    public List<Integer> joinedHubs(int hub) {
        var hubs = new ArrayList<Integer>();
        for(int slot : joinedSlots(ChasewayBlock.slotOfHub(hub))) {
            for(int end = 0; end < 2; ++end) {
                int h = slot * 2 + end;
                if(h != hub)
                    hubs.add(h);
            }
        }
        return hubs;
    }

    /**
     * Every landing on one end of a sleeve is joined to the same-numbered landing on every other
     * end of its piece: the far end of a lone sleeve, every end of an elbow or tee.
     */
    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        var layout = ChasewayBlock.LAYOUT;
        var done = new boolean[ChasewayBlock.SLOT_COUNT];
        for(int slot = 0; slot < ChasewayBlock.SLOT_COUNT; ++slot) {
            if(!hasSleeve(slot) || done[slot])
                continue;
            var ends = new ArrayList<Integer>();
            for(int s : joinedSlots(slot)) {
                done[s] = true;
                ends.add(s * 2);
                ends.add(s * 2 + 1);
            }
            for(int i = 1; i < ends.size(); ++i)
                for(int k = 0; k < DeviceHubs.PER_HUB; ++k)
                    builder.connect(SpliceSupport.SPLICE_RESISTANCE, builder.terminalNode(layout.conductorTerminal(ends.get(i - 1), k)), builder.terminalNode(layout.conductorTerminal(ends.get(i), k)));
        }
    }

    // ---- links laid on their own ----

    /**
     * Every free sleeve end looks across its face: a sleeve in the next block ending at the same
     * spot gets a nipple, so a row of chased blocks is one chase; any other fitting (a gang box, a
     * fixture, a panel, anything with knockouts) with a knockout against that face near the sleeve
     * gets one to that knockout, so a sleeve ending behind a box feeds it with nothing on the wall.
     * Between two chaseways the one on the positive side lays it, so there is only ever one.
     */
    private void linkNeighbours(ServerLevel server) {
        for(int slot = 0; slot < ChasewayBlock.SLOT_COUNT; ++slot) {
            if(!hasSleeve(slot))
                continue;
            for(int end = 0; end < 2; ++end) {
                int hub = slot * 2 + end;
                if(deviceHubs().hubRun(hub) != null)
                    continue;
                var face = ChasewayBlock.faceOfHub(hub);
                var there = worldPosition.relative(face);
                if(!server.isLoaded(there))
                    continue;
                var be = server.getBlockEntity(there);
                if(be instanceof ChasewayBlockEntity other) {
                    if(face.getAxisDirection() != Direction.AxisDirection.POSITIVE || !other.hasSleeve(slot))
                        continue;
                    int theirs = ChasewayBlock.hub(slot, face.getOpposite());
                    if(other.deviceHubs().hubRun(theirs) == null)
                        lay(server, hub, there, ChasewayBlock.LAYOUT.hubTerminal(theirs));
                } else if(be instanceof ISpliceHost host && server.getBlockState(there).getBlock() instanceof IElectric) {
                    int best = knockoutBehind(server, host, there, face.getOpposite(), slot);
                    if(best >= 0)
                        lay(server, hub, there, host.hubTerminal(best));
                }
            }
        }
    }

    /**
     * The fitting's free knockout nearest the sleeve's centre on the fitting's {@code side} (the
     * face against this block), within four pixels of that face and six of the sleeve, or -1.
     */
    private static int knockoutBehind(ServerLevel server, ISpliceHost host, BlockPos pos, Direction side, int slot) {
        var centre = ChasewayBlock.centre(slot).scale(1 / 16.0);
        var axis = side.getAxis();
        int best = -1;
        double bestDistance = 6 / 16.0;
        for(int h = 0; h < host.hubCount(); ++h) {
            if(host.maxConduit(h) == null || host.hubRun(h) != null)
                continue;
            Vec3 at;
            try {
                at = IElectric.getTerminalPos(server, pos, host.hubTerminal(h)).subtract(pos.getX(), pos.getY(), pos.getZ());
            } catch(RuntimeException e) {
                continue;
            }
            double depth = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? at.get(axis) : 1 - at.get(axis);
            if(depth > 4 / 16.0)
                continue;
            double d2 = 0;
            for(var other : Direction.Axis.values()) {
                if(other != axis) {
                    double d = at.get(other) - centre.get(other);
                    d2 += d * d;
                }
            }
            double distance = Math.sqrt(d2);
            if(distance < bestDistance) {
                bestDistance = distance;
                best = h;
            }
        }
        return best;
    }

    private void lay(ServerLevel server, int hub, BlockPos there, int theirTerminal) {
        var mine = new BlockWireEndpoint(worldPosition, ChasewayBlock.LAYOUT.hubTerminal(hub));
        var theirs = new BlockWireEndpoint(there, theirTerminal);
        var nipple = ConduitRunEntity.createNipple(server, mine, theirs);
        if(nipple != null)
            server.tryAddFreshEntityWithPassengers(nipple);
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
        if(level instanceof ServerLevel server) {
            deviceHubs().lazyTick();
            if(sleeves != 0)
                linkNeighbours(server);
        }
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
