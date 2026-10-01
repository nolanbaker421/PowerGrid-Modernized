package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.network.Cat6WireEntity;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;

import java.util.UUID;

/** The Cat6 pair inside a cable chain: a hidden network cable between the two anchors' jacks, gone when the chain is. */
public class ChainCat6Entity extends Cat6WireEntity {
    private static final int CHECK_INTERVAL = 20, LOAD_GRACE = 100;

    @Nullable
    private UUID chain;
    private int checkTimer = -LOAD_GRACE;

    public ChainCat6Entity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ChainCat6Entity create(Level level, UUID chain, JackEndpoint a, JackEndpoint b, float length) {
        var entity = new ChainCat6Entity(ModEntities.CHAIN_CAT6.get(), level);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("Id", BuiltInRegistries.ITEM.getKey(ModItems.CAT6_CABLE.get()).toString());
        item.putInt("Count", 1);
        tag.put("Item", item);
        tag.put("Endpoint1", a.serialize());
        tag.put("Endpoint2", b.serialize());
        tag.putInt("Color", DEFAULT_COLOR);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
        tag.putFloat("PlacedLength", length);
        tag.putUUID("Chain", chain);
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
        if(chain == null || !(level() instanceof ServerLevel server) || !(server.getEntity(chain) instanceof CableChainEntity parent) || !parent.isAlive())
            discard();
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


    /**
     * A hanging wire kills itself when its ends are further apart than the wire it was placed with.
     * This one is a sliding contact: it is given plenty of length to start with and never snaps.
     */
    @Override
    public void updateCurveParams() {
        super.updateCurveParams();
        if(curveParams != null)
            curveParams.valid = true;
    }

    /** Never hands a cable back; the chain owns it. */
    @Override
    public void kill() {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(chain != null)
            tag.putUUID("Chain", chain);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chain = tag.hasUUID("Chain") ? tag.getUUID("Chain") : chain;
    }
}
