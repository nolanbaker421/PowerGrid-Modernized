package com.nolanbaker.pgmodernized.chain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One clickable piece of a cable chain. The chain itself keeps a tiny hitbox at its fixed post,
 * because one box over the whole run between two anchors catches every look and click in the
 * area (Jade reported the chain while looking at the sky). These parts sit along the drawn links
 * instead, like the Ender Dragon's, and hand every click and hit to the chain.
 */
public class CableChainPart extends PartEntity<CableChainEntity> {
    private EntityDimensions size;

    public CableChainPart(CableChainEntity parent, float size) {
        super(parent);
        this.size = EntityDimensions.scalable(size, size);
        refreshDimensions();
    }

    /** Centre the part on a point of the chain. */
    void centreAt(Vec3 point, float newSize) {
        if(Math.abs(newSize - size.width()) > 1e-3) {
            size = EntityDimensions.scalable(newSize, newSize);
            refreshDimensions();
        }
        setPos(point.x, point.y - size.height() / 2, point.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return !getParent().isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
