package com.nolanbaker.pgmodernized.rail;

import com.nolanbaker.pgmodernized.network.Cat6WireEntity;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
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

/** The rail's data channel for one collector: a hidden Cat6 from its jack to the feed's, owned by the collector. */
public class RailCat6Entity extends Cat6WireEntity {
    private static final int CHECK_INTERVAL = 20, LOAD_GRACE = 100;

    private BlockPos collector = BlockPos.ZERO;
    private int checkTimer = -LOAD_GRACE;

    public RailCat6Entity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static RailCat6Entity create(Level level, BlockPos collector, JackEndpoint a, JackEndpoint b) {
        var entity = new RailCat6Entity(ModEntities.RAIL_CAT6.get(), level);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("Id", BuiltInRegistries.ITEM.getKey(ModItems.CAT6_CABLE.get()).toString());
        item.putInt("Count", 1);
        tag.put("Item", item);
        tag.put("Endpoint1", a.serialize());
        tag.put("Endpoint2", b.serialize());
        tag.putInt("Color", DEFAULT_COLOR);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
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

    /** Never hands a cable back. */
    @Override
    public void kill() {
        discard();
    }

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
