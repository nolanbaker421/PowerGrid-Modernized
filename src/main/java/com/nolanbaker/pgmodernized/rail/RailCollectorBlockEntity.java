package com.nolanbaker.pgmodernized.rail;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Every half second the collector projects its shoes into world space and looks at the block
 * there. A rail block of a fed run means contact: four hidden pickup wires run from its studs to
 * the feed's studs, and a hidden Cat6 from its jack to the feed's. They are Power Grid hanging
 * wires, so they follow the body between checks. Leaving the rail, or crossing onto a run with
 * another feed, drops them; the next contact makes new ones.
 */
public class RailCollectorBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, INetworkJack, IHaveGoggleInformation {
    private DeviceSpliceHost deviceHubs;
    private final JackSupport jack = new JackSupport(this, false);

    @Nullable
    private BlockPos feed;
    @Nullable
    private BlockPos lastRail;
    private final List<UUID> pickups = new ArrayList<>();
    @Nullable
    private UUID link;

    public RailCollectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, RailCollectorBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Nullable
    public BlockPos feed() {
        return feed;
    }

    /** Whether this collector made the given pickup; the pickups ask every second. */
    public boolean owns(UUID id) {
        return pickups.contains(id) || id.equals(link);
    }

    // ---- contact ----

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        var shoe = SableCompanion.INSTANCE.projectOutOfSubLevel(level, IElectric.getTerminalPos(level, worldPosition, RailCollectorBlock.SHOE));
        var railPos = BlockPos.containing(shoe);
        BlockPos head = null;
        if(level.isLoaded(railPos) && ConductorRailBlock.isRail(level.getBlockState(railPos))) {
            // The walk is only redone when the shoes are on a different block than last time.
            head = railPos.equals(lastRail) ? feed : ConductorRailBlock.findFeed(level, railPos);
            lastRail = railPos;
        } else {
            lastRail = null;
        }
        if(head != null && !level.isLoaded(head))
            head = feed;
        if(Objects.equals(head, feed) && (feed == null || pickupsAlive()))
            return;
        dropPickups();
        feed = head;
        if(feed != null)
            makePickups(feed);
        setChanged();
        sendData();
    }

    private boolean pickupsAlive() {
        if(!(level instanceof ServerLevel server) || pickups.isEmpty())
            return false;
        for(var id : pickups) {
            Entity entity = server.getEntity(id);
            if(entity == null || !entity.isAlive())
                return false;
        }
        return true;
    }

    private void makePickups(BlockPos feedPos) {
        if(!(level instanceof ServerLevel server) || !(level.getBlockEntity(feedPos) instanceof RailFeedBlockEntity feedBe))
            return;
        for(int terminal = RailCollectorBlock.L1; terminal <= RailCollectorBlock.N; ++terminal) {
            var wire = RailPickupEntity.create(level, worldPosition, new BlockWireEndpoint(worldPosition, terminal), new BlockWireEndpoint(feedPos, terminal));
            if(server.tryAddFreshEntityWithPassengers(wire))
                pickups.add(wire.getUUID());
        }
        if(!jack.isPortUsed(0) && !feedBe.networkJack().isPortUsed(0)) {
            var cat6 = RailCat6Entity.create(level, worldPosition, new JackEndpoint(worldPosition, 0), new JackEndpoint(feedPos, 0));
            if(server.tryAddFreshEntityWithPassengers(cat6))
                link = cat6.getUUID();
        }
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.6f);
    }

    private void dropPickups() {
        if(level instanceof ServerLevel server) {
            for(var id : pickups) {
                Entity entity = server.getEntity(id);
                if(entity != null)
                    entity.discard();
            }
            if(link != null) {
                Entity entity = server.getEntity(link);
                if(entity != null)
                    entity.discard();
            }
        }
        pickups.clear();
        link = null;
    }

    // ---- jack and lifecycle ----

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
        if(level != null && !level.isClientSide)
            dropPickups();
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
        return IElectric.getTerminalPos(level, worldPosition, RailCollectorBlock.JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return RailCollectorBlock.JACK;
    }

    // ---- save ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(feed != null)
            tag.put("Feed", NbtUtils.writeBlockPos(feed));
        if(!clientPacket) {
            var list = new ListTag();
            for(var id : pickups)
                list.add(NbtUtils.createUUID(id));
            tag.put("Pickups", list);
            if(link != null)
                tag.putUUID("Link", link);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        feed = NbtUtils.readBlockPos(tag, "Feed").orElse(null);
        if(!clientPacket) {
            pickups.clear();
            for(Tag id : tag.getList("Pickups", Tag.TAG_INT_ARRAY))
                pickups.add(NbtUtils.loadUUID(id));
            link = tag.hasUUID("Link") ? tag.getUUID("Link") : null;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.rail_collector.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(feed == null)
            Lang.builder().translate("gui.rail_collector.off_rail").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.rail_collector.on_rail", feed.getX(), feed.getY(), feed.getZ()).style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
