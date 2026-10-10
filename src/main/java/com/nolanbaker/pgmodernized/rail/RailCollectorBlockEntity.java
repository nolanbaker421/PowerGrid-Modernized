package com.nolanbaker.pgmodernized.rail;

import org.patryk3211.powergrid.compat.sable.SableUtils;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.phys.AABB;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
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
    private long madeAt;
    /** Where the arm points and how far, for the renderer; null when retracted. */
    @Nullable
    private Direction arm;
    private int reach = 1;

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

    @Nullable
    public Direction arm() {
        return arm;
    }

    public int reach() {
        return reach;
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
        // The arm swings to the first rail block within reach: the front first, then straight away
        // from the mounting face, then the other four directions.
        var centre = Vec3.atCenterOf(worldPosition);
        var front = IElectric.getTerminalPos(level, worldPosition, RailCollectorBlock.SHOE).subtract(centre);
        var tried = new ArrayList<Direction>();
        tried.add(Direction.getNearest(front.x, front.y, front.z));
        if(getBlockState().hasProperty(BlockStateProperties.FACING))
            tried.add(getBlockState().getValue(BlockStateProperties.FACING).getOpposite());
        for(var other : Direction.values())
            tried.add(other);
        BlockPos railPos = null;
        Direction foundArm = null;
        int foundReach = 1;
        search:
        for(var direction : tried) {
            for(int k = 1; k <= RailCollectorBlock.MAX_REACH; ++k) {
                var point = SableCompanion.INSTANCE.projectOutOfSubLevel(level, centre.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(k)));
                var sample = BlockPos.containing(point);
                if(!level.isLoaded(sample))
                    break;
                var rail = railAt(point);
                if(rail != null) {
                    railPos = rail;
                    foundArm = direction;
                    foundReach = k;
                    break search;
                }
                if(!level.getBlockState(sample).getCollisionShape(level, sample).isEmpty())
                    break;   // something solid in the way
            }
        }
        if(foundArm != arm || foundReach != reach) {
            arm = foundArm;
            reach = foundReach;
            setChanged();
            sendData();
        }
        BlockPos head = null;
        if(railPos != null) {
            // The walk is only redone when the shoes are on a different block than last time.
            head = railPos.equals(lastRail) ? feed : ConductorRailBlock.findFeed(level, railPos);
            lastRail = railPos;
        } else {
            lastRail = null;
        }
        if(head != null && !level.isLoaded(head))
            head = feed;
        boolean alive = pickupsAlive();
        if(Objects.equals(head, feed) && (feed == null || alive))
            return;
        if(feed != null && Objects.equals(head, feed) && !alive && level.getGameTime() - madeAt < 60)
            return;   // just made: give the entities a moment to turn up in the lookup
        PowerGridModernized.LOGGER.debug("Rail collector at {}: {} (feed {} -> {}, pickups alive {})", worldPosition,
                head == null ? "leaving the rail" : "connecting", feed, head, alive);
        dropPickups();
        feed = head;
        if(feed != null)
            makePickups(feed);
        setChanged();
        sendData();
    }

    /**
     * The rail block at a world point: a block of the world, or a block of another body that is
     * there (a trolley riding a bridge that is itself a body, with the rail on the bridge). Our own
     * body is skipped. The position returned is where the block really is, so for a rail on a body
     * it is that body's own coordinates, which is what the feed walk and the pickups work in.
     */
    @Nullable
    private BlockPos railAt(Vec3 point) {
        var worldPos = BlockPos.containing(point);
        if(ConductorRailBlock.isRail(level.getBlockState(worldPos)))
            return worldPos;
        var mine = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        for(var other : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(new AABB(worldPos)))) {
            if(mine != null && other.getUniqueId().equals(mine.getUniqueId()))
                continue;
            var plot = BlockPos.containing(other.logicalPose().transformPositionInverse(point));
            if(level.isLoaded(plot) && ConductorRailBlock.isRail(level.getBlockState(plot)))
                return plot;
        }
        return null;
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
        madeAt = level.getGameTime();
        // Enough wire for the whole run and then some: the contact slides, it never snaps.
        double distance = SableUtils.projectedDistance(level, Vec3.atCenterOf(worldPosition), Vec3.atCenterOf(feedPos));
        float length = (float) (Double.isFinite(distance) ? distance * 2 + 2 * ConductorRailBlock.SEARCH : 4 * ConductorRailBlock.SEARCH);
        for(int terminal = RailCollectorBlock.L1; terminal <= RailCollectorBlock.N; ++terminal) {
            var wire = RailPickupEntity.create(level, worldPosition, new BlockWireEndpoint(worldPosition, terminal), new BlockWireEndpoint(feedPos, terminal), length);
            if(server.tryAddFreshEntityWithPassengers(wire))
                pickups.add(wire.getUUID());
        }
        var cat6 = RailCat6Entity.create(level, worldPosition, new JackEndpoint(worldPosition, LINK_PORT),
                new JackEndpoint(feedPos, RailFeedBlockEntity.LINK_PORT), length);
        if(server.tryAddFreshEntityWithPassengers(cat6))
            link = cat6.getUUID();
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.6f);
    }

    private void dropPickups() {
        if(level instanceof ServerLevel server) {
            for(var id : pickups) {
                Entity entity = server.getEntity(id);
                if(entity instanceof RailPickupEntity pickup)
                    pickup.release();
                else if(entity != null)
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
        // The pickups go first, before the circuit behind them is torn down.
        if(level != null && !level.isClientSide)
            dropPickups();
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }


    /** Port 0 is the player's; port 1 is the internal one the hidden link uses, so the jack stays free. */
    public static final int LINK_PORT = 1;

    @Override
    public int portCount() {
        return 2;
    }

    @Override
    public int portAt(Vec3 localHit) {
        return 0;
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
        if(arm != null)
            tag.putInt("Arm", arm.get3DDataValue());
        tag.putInt("Reach", reach);
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
        arm = tag.contains("Arm") ? Direction.from3DDataValue(tag.getInt("Arm")) : null;
        reach = Math.max(1, tag.getInt("Reach"));
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
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
