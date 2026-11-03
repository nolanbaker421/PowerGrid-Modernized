package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One block's worth of a ladder's rails: the ladder is picked and hit through these, so a click
 * only lands on the ladder where the ladder actually is, not anywhere in the box round a leaning
 * one. Never pickable while someone is on the ladder, so the climber's clicks reach the gear
 * behind it.
 */
public class LadderPart extends PartEntity<LadderEntity> {
    private AABB box;
    private boolean active;

    public LadderPart(LadderEntity parent) {
        super(parent);
    }

    void set(AABB box, boolean active) {
        this.box = box;
        this.active = active;
        setPos(box.getCenter());
    }

    @Override
    protected AABB makeBoundingBox() {
        return box == null ? super.makeBoundingBox() : box;
    }

    @Override
    public boolean isPickable() {
        return active && getParent().getPassengers().isEmpty();
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        return getParent().climbOn(player, position().add(hit));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return getParent().hurt(source, amount);
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
