package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The visible cable chain between two anchors. A Power Grid hanging wire underneath, which is what
 * makes it follow a Sable body: every tick the base class projects both ends back into world space.
 * It carries no current itself; its four {@link ChainConductorEntity}s and its {@link ChainCat6Entity}
 * do, and they live and die with it. The chain has a fixed length: pull the anchors further apart
 * than it can reach and it snaps, dropping its links.
 */
public class CableChainEntity extends HangingWireEntity {
    /** Radius of the bend, metres. */
    public static final double BEND_RADIUS = 0.4;
    private static final int CHECK_INTERVAL = 5;

    private float chainLength;
    private final List<UUID> conductors = new ArrayList<>();
    @Nullable
    private UUID link;
    private int checkTimer;

    public CableChainEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static CableChainEntity create(Level level, BlockPos fixed, BlockPos moving, ItemStack chain, float chainLength) {
        var entity = new CableChainEntity(ModEntities.CABLE_CHAIN.get(), level);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("Id", BuiltInRegistries.ITEM.getKey(chain.getItem()).toString());
        item.putInt("Count", chain.getCount());
        tag.put("Item", item);
        tag.put("Endpoint1", new BlockWireEndpoint(fixed, CableChainAnchorBlock.MOUNT).serialize());
        tag.put("Endpoint2", new BlockWireEndpoint(moving, CableChainAnchorBlock.MOUNT).serialize());
        tag.putInt("Color", 0x404040);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
        tag.putFloat("PlacedLength", chainLength);
        tag.putFloat("ChainLength", chainLength);
        entity.readAdditionalSaveData(tag);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    public void setChildren(List<UUID> conductors, @Nullable UUID link) {
        this.conductors.clear();
        this.conductors.addAll(conductors);
        this.link = link;
    }

    public float chainLength() {
        return chainLength;
    }

    /** How far apart the anchors may be before the chain is straight. */
    public double travelLimit() {
        return chainLength - Math.PI * BEND_RADIUS;
    }

    /** The two ends in world space, as the base class last projected them; the first is the fixed end. */
    @Nullable
    public Vec3 fixedEnd() {
        return terminalPos1;
    }

    @Nullable
    public Vec3 movingEnd() {
        return terminalPos2;
    }

    // ---- no current of its own ----

    @Override
    public void makeWire() {}

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    // ---- the fixed length ----

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || isRemoved())
            return;
        if(++checkTimer < CHECK_INTERVAL)
            return;
        checkTimer = 0;
        var a = terminalPos1;
        var b = terminalPos2;
        if(a == null || b == null)
            return;
        if(a.distanceTo(b) > travelLimit() + 0.5) {
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.7f);
            kill();
        }
    }

    @Override
    public AABB calculateClientBoundingBox() {
        var a = terminalPos1;
        var b = terminalPos2;
        if(a == null || b == null)
            return super.calculateClientBoundingBox();
        return new AABB(a, b).inflate(chainLength / 2 + 1);
    }

    @Override
    public void endpointRemoved(IWireEndpoint endpoint) {
        kill();
    }

    /** The conductors and the link go with the chain; the anchors forget it. */
    @Override
    public void remove(RemovalReason reason) {
        if(!level().isClientSide && reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.UNLOADED_WITH_PLAYER
                && level() instanceof ServerLevel server) {
            for(var id : conductors) {
                Entity child = server.getEntity(id);
                if(child != null)
                    child.discard();
            }
            if(link != null) {
                Entity child = server.getEntity(link);
                if(child != null)
                    child.discard();
            }
            forgetAt(getEndpoint1());
            forgetAt(getEndpoint2());
        }
        super.remove(reason);
    }

    private void forgetAt(@Nullable IWireEndpoint endpoint) {
        if(endpoint instanceof BlockWireEndpoint block && level().isLoaded(block.getPos())
                && level().getBlockEntity(block.getPos()) instanceof CableChainAnchorBlockEntity anchor && getUUID().equals(anchor.chain()))
            anchor.setChain(null);
    }

    // ---- save and sync ----

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ChainLength", chainLength);
        var list = new ListTag();
        for(var id : conductors)
            list.add(NbtUtils.createUUID(id));
        tag.put("Conductors", list);
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
        if(tag.contains("Conductors")) {
            conductors.clear();
            for(Tag id : tag.getList("Conductors", Tag.TAG_INT_ARRAY))
                conductors.add(NbtUtils.loadUUID(id));
        }
        link = tag.hasUUID("Link") ? tag.getUUID("Link") : null;
    }
}
