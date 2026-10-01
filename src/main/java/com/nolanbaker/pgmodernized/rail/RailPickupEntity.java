package com.nolanbaker.pgmodernized.rail;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/**
 * One shoe on one bar: an invisible, untouchable Power Grid hanging wire from a collector stud to
 * the matching feed stud. Rides the body like any hanging wire. Lives while its collector still
 * claims it, and is replaced when the collector finds a different run.
 */
public class RailPickupEntity extends HangingWireEntity {
    private static final int CHECK_INTERVAL = 20, LOAD_GRACE = 100;

    private BlockPos collector = BlockPos.ZERO;
    private int checkTimer = -LOAD_GRACE;

    public RailPickupEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static RailPickupEntity create(Level level, BlockPos collector, BlockWireEndpoint shoe, BlockWireEndpoint bar) {
        var entity = new RailPickupEntity(ModEntities.RAIL_PICKUP.get(), level);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("Id", BuiltInRegistries.ITEM.getKey(ModItems.RAIL_SHOE.get()).toString());
        item.putInt("Count", 1);
        tag.put("Item", item);
        tag.put("Endpoint1", shoe.serialize());
        tag.put("Endpoint2", bar.serialize());
        tag.putInt("Color", 0xE07020);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
        tag.putFloat("PlacedLength", 1f);
        tag.put("Collector", NbtUtils.writeBlockPos(collector));
        entity.readAdditionalSaveData(tag);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || isRemoved())
            return;
        if(++checkTimer < CHECK_INTERVAL)
            return;
        checkTimer = 0;
        if(!level().isLoaded(collector))
            return;
        if(!(level().getBlockEntity(collector) instanceof RailCollectorBlockEntity owner) || !owner.owns(getUUID()))
            discard();
    }

    @Override
    public void endpointRemoved(IWireEndpoint endpoint) {
        kill();
    }

    // ---- hidden from players ----

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public @Nullable Vec3 raycast(Vec3 min, Vec3 max) {
        return null;
    }

    @Override
    public void dropWire() {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Collector", NbtUtils.writeBlockPos(collector));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        collector = NbtUtils.readBlockPos(tag, "Collector").orElse(collector);
    }
}
