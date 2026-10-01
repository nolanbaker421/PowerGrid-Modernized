package com.nolanbaker.pgmodernized.chain;

import net.neoforged.neoforge.entity.PartEntity;
import java.util.List;
import java.util.ArrayList;
import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.UUID;

/**
 * The cable chain: a 4" conduit run between the chain posts of two anchors that you pull wire
 * through like any other run. It stands at the fixed anchor; the other anchor may be on a Sable
 * body, and every few ticks the chain re-projects that end into world space and re-lays its path
 * toward it, so it can be clicked and is drawn where it hangs. It always carries a hidden Cat6 pair.
 * Pulled past its length it snaps: links and pulled wire drop, the anchors are free again.
 */
public class CableChainEntity extends ConduitRunEntity {
    /** Radius of the bend, metres. */
    public static final double BEND_RADIUS = 0.4;
    private static final int REFRESH_INTERVAL = 5;
    /** Spacing of the drawn links, and the least height the loop is drawn with. */
    public static final double LINK_PITCH = 0.3, MIN_RISE = 0.5;
    /** Clickable pieces spread along the links; the chain's own box is just its fixed post. */
    private static final int PART_COUNT = 64;

    private float chainLength;
    @Nullable
    private UUID link;
    private int timer;
    @Nullable
    private Vec3 fixedEnd, movingEnd;
    private final CableChainPart[] parts = new CableChainPart[PART_COUNT];
    private float laidLength = -1;

    public CableChainEntity(EntityType<?> type, Level level) {
        super(type, level);
        for(int i = 0; i < PART_COUNT; ++i)
            parts[i] = new CableChainPart(this, 0.5f);
    }

    // ---- hitboxes: a stub at the fixed post plus parts along the links ----

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for(int i = 0; i < parts.length; ++i)
            parts[i].setId(id + i + 1);
    }

    private AABB postBox() {
        var p = fixedEnd != null ? fixedEnd : position();
        return new AABB(p, p).inflate(0.4);
    }

    @Override
    protected AABB makeBoundingBox() {
        return postBox();
    }

    @Override
    public void bakeBoundingBoxes() {
        super.bakeBoundingBoxes();
        setBoundingBox(postBox());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        var box = getBoundingBox();
        return fixedEnd == null || movingEnd == null ? box : box.minmax(new AABB(fixedEnd, movingEnd).inflate(chainLength / 2 + 1));
    }

    /** Spread the parts evenly along the drawn links, sized so neighbours touch. */
    private void placeParts() {
        if(fixedEnd == null || movingEnd == null) {
            for(var part : parts)
                part.centreAt(position(), 0.5f);
            return;
        }
        var points = path(fixedEnd, movingEnd, chainLength);
        double step = Math.max(1, (points.size() - 1) / (double) (PART_COUNT - 1));
        float size = (float) Math.min(1.0, Math.max(0.4, step * LINK_PITCH * 1.2));
        for(int i = 0; i < PART_COUNT; ++i) {
            int index = (int) Math.min(points.size() - 1, Math.round(i * step));
            parts[i].centreAt(points.get(index), size);
        }
    }

    /** Sample points along the energy-chain path from the fixed end {@code a} to the moving end {@code b}. */
    public static List<Vec3> path(Vec3 a, Vec3 b, double chainLength) {
        var horizontal = new Vec3(b.x - a.x, 0, b.z - a.z);
        double d = horizontal.length();
        var u = d > 0.01 ? horizontal.scale(1 / d) : new Vec3(1, 0, 0);
        double rise = b.y - a.y;
        if(Math.abs(rise) < MIN_RISE)
            rise = MIN_RISE;
        double r = Math.abs(rise) / 2;
        double usable = Math.max(chainLength - Math.PI * r, 0);
        // Where the bend is: half way between the moving end and the fully-pulled-out position.
        double bend = Math.max(d, Math.min(usable, (usable + d) / 2));
        var points = new ArrayList<Vec3>();
        // Lower run: from the fixed end out to the bend.
        for(double s = 0; s < bend; s += LINK_PITCH)
            points.add(a.add(u.scale(s)));
        var start = a.add(u.scale(bend));
        var centre = start.add(0, rise / 2, 0);
        // The half circle, bulging outward past the bend.
        int arcSteps = Math.max(2, (int) Math.round(Math.PI * r / LINK_PITCH));
        for(int i = 0; i <= arcSteps; ++i) {
            double theta = -Math.PI / 2 + Math.PI * i / arcSteps;
            points.add(centre.add(u.scale(r * Math.cos(theta))).add(0, r * Math.sin(theta) * Math.signum(rise), 0));
        }
        // Upper run: back from the bend to the moving end.
        var top = start.add(0, rise, 0);
        var toEnd = b.subtract(top);
        double upper = toEnd.length();
        if(upper > 0.01) {
            var v = toEnd.scale(1 / upper);
            for(double s = LINK_PITCH; s <= upper; s += LINK_PITCH)
                points.add(top.add(v.scale(s)));
        }
        points.add(b);
        return points;
    }

    /** Between the chain posts of two anchors, {@code fixed} being the one that stays put. */
    public static CableChainEntity create(Level level, BlockWireEndpoint fixed, BlockWireEndpoint moving, ItemStack chain, float chainLength) {
        var start = project(level, IElectric.getTerminalPos(level, fixed.getPos(), fixed.getTerminal()));
        var end = project(level, IElectric.getTerminalPos(level, moving.getPos(), moving.getTerminal()));
        var entity = new CableChainEntity(ModEntities.CABLE_CHAIN.get(), level);
        entity.chainLength = chainLength;
        entity.setItem(chain.getItem(), chain.getCount());
        entity.setColor(0x404040);
        entity.setPosRaw(start.x, start.y, start.z);
        entity.layPath(start, end);
        entity.setEndpoint1(fixed);
        entity.setEndpoint2(moving);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    static Vec3 project(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
    }

    public void setLink(@Nullable UUID link) {
        this.link = link;
    }

    public float chainLength() {
        return chainLength;
    }

    /** How far apart the anchors may be before the chain is straight. */
    public double travelLimit() {
        return chainLength - Math.PI * BEND_RADIUS;
    }

    @Nullable
    public Vec3 fixedEnd() {
        return fixedEnd;
    }

    @Nullable
    public Vec3 movingEnd() {
        return movingEnd;
    }

    @Override
    public ConduitSize size() {
        return ConduitSize.FOUR;
    }

    /** A manhattan path from the fixed post toward the moving one: where the chain can be clicked. */
    private void layPath(Vec3 start, Vec3 end) {
        fixedEnd = start;
        movingEnd = end;
        laidLength = chainLength;
        var path = manhattan(start, end);
        if(path.isEmpty())
            path = java.util.List.of(BlockWireEntity.Point.x(1));
        segments.clear();
        segments.addAll(path);
        bakeBoundingBoxes();
        placeParts();
    }

    // ---- following the body ----

    @Override
    public void tick() {
        super.tick();
        if(isRemoved() || ++timer < REFRESH_INTERVAL)
            return;
        timer = 0;
        if(!(getEndpoint1() instanceof BlockWireEndpoint fixed) || !(getEndpoint2() instanceof BlockWireEndpoint moving))
            return;
        var level = level();
        if(!level.isLoaded(fixed.getPos()) || !level.isLoaded(moving.getPos()))
            return;
        var start = project(level, IElectric.getTerminalPos(level, fixed.getPos(), fixed.getTerminal()));
        var end = project(level, IElectric.getTerminalPos(level, moving.getPos(), moving.getTerminal()));
        if(fixedEnd == null || movingEnd == null || start.distanceToSqr(fixedEnd) > 0.01 || end.distanceToSqr(movingEnd) > 0.01
                || Math.abs(laidLength - chainLength) > 1e-3)
            layPath(start, end);
        if(!level.isClientSide && start.distanceTo(end) > travelLimit() + 0.5) {
            level.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.7f);
            kill();
        }
    }

    // ---- the Cat6 pair goes with it; conduit never tees into it ----

    @Override
    public void kill() {
        if(!level().isClientSide && link != null && level() instanceof ServerLevel server) {
            Entity cat6 = server.getEntity(link);
            if(cat6 != null)
                cat6.discard();
        }
        super.kill();
    }

    @Override
    public void endpointRemoved(IWireEndpoint endpoint) {
        kill();
    }

    @Override
    public BlockWireEntity flip() {
        return this;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof ConduitItem) {
            if(!level().isClientSide)
                player.displayClientMessage(Lang.builder().translate("message.cable_chain.no_tee").style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        if(hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof CableChainItem)
            return level().isClientSide ? InteractionResult.SUCCESS : extend(player, stack);
        return super.interact(player, hand);
    }

    /** More links on an existing chain: one per click, the whole stack when sneaking, up to the configured run. */
    private InteractionResult extend(Player player, ItemStack stack) {
        float longest = PgmConfig.CABLE_CHAIN_MAX_LENGTH.get() + CableChainPlacement.SLACK_ITEMS * CableChainItem.METERS_PER_ITEM;
        int room = (int) Math.floor((longest - chainLength) / CableChainItem.METERS_PER_ITEM);
        if(room <= 0) {
            player.displayClientMessage(Lang.builder().translate("message.cable_chain.longest", String.format("%.0f", longest)).style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        int added = Math.min(room, player.isShiftKeyDown() ? stack.getCount() : 1);
        chainLength += added * CableChainItem.METERS_PER_ITEM;
        incrementWireCount(added);
        if(!player.isCreative())
            stack.shrink(added);
        sendExtraData();
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
        player.displayClientMessage(Lang.builder().translate("message.cable_chain.extended", added, String.format("%.0f", travelLimit())).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    // ---- save and sync ----

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ChainLength", chainLength);
        if(link != null)
            tag.putUUID("Link", link);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readChain(tag);
    }

    @Override
    public void onEntityDataPacket(CompoundTag tag) {
        super.onEntityDataPacket(tag);
        readChain(tag);
    }

    private void readChain(CompoundTag tag) {
        if(tag.contains("ChainLength"))
            chainLength = tag.getFloat("ChainLength");
        if(tag.hasUUID("Link"))
            link = tag.getUUID("Link");
    }
}
