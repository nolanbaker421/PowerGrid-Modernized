package com.nolanbaker.pgmodernized.chain;

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
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.UUID;

/**
 * One conductor inside a cable chain: an invisible, untouchable Power Grid hanging wire from a
 * terminal of one anchor to the same terminal of the other, so it rides a Sable body exactly as the
 * chain does. It carries the chain item as its wire type and goes away when its chain does.
 */
public class ChainConductorEntity extends HangingWireEntity {
    private static final int CHECK_INTERVAL = 20, LOAD_GRACE = 100;

    @Nullable
    private UUID chain;
    private int checkTimer = -LOAD_GRACE;

    public ChainConductorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ChainConductorEntity create(Level level, UUID chain, BlockWireEndpoint a, BlockWireEndpoint b) {
        var entity = new ChainConductorEntity(ModEntities.CHAIN_CONDUCTOR.get(), level);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("Id", BuiltInRegistries.ITEM.getKey(ModItems.CABLE_CHAIN.get()).toString());
        item.putInt("Count", 1);
        tag.put("Item", item);
        tag.put("Endpoint1", a.serialize());
        tag.put("Endpoint2", b.serialize());
        tag.putInt("Color", 0x404040);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
        tag.putFloat("PlacedLength", 1f);
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

    /** Nothing to pick up: the links belong to the chain. */
    @Override
    public void dropWire() {}

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
