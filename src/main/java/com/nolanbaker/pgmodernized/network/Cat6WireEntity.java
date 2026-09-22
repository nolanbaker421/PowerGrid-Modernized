package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/**
 * A hanging Power Grid wire that carries computer network traffic instead of current.
 * Rendering, sag, placement length, pick-up and cutting all come from {@link HangingWireEntity};
 * only the "make wire" step is replaced by {@link Cat6LinkState}.
 */
public class Cat6WireEntity extends HangingWireEntity implements ICat6Cable {
    public static final int DEFAULT_COLOR = 0x2F6FD6;

    private final Cat6LinkState link = new Cat6LinkState(this);

    public Cat6WireEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public static Cat6WireEntity create(Level world, JackEndpoint endpoint1, JackEndpoint endpoint2, ItemStack item) {
        var entity = new Cat6WireEntity(ModEntities.CAT6_CABLE.get(), world);
        // Feed the entity the same NBT a saved wire would have. That runs Power Grid's own load path,
        // which resolves endpoints, terminal positions and the catenary without touching its
        // package-private fields.
        var tag = new CompoundTag();
        var itemTag = new CompoundTag();
        itemTag.putString("Id", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        itemTag.putInt("Count", item.getCount());
        tag.put("Item", itemTag);
        tag.put("Endpoint1", endpoint1.serialize());
        tag.put("Endpoint2", endpoint2.serialize());
        tag.putInt("Color", DEFAULT_COLOR);
        tag.putFloat("Temperature", ThermalBehaviour.STANDARD_TEMPERATURE);
        entity.readAdditionalSaveData(tag);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    @Override
    public void setEndpoint1(IWireEndpoint endpoint) {
        super.setEndpoint1(JackEndpoint.from(endpoint));
    }

    @Override
    public void setEndpoint2(IWireEndpoint endpoint) {
        super.setEndpoint2(JackEndpoint.from(endpoint));
    }

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    @Override
    public void makeWire() {
        link.makeWire();
    }

    @Override
    public void dropWire() {
        link.dropWire();
    }

    @Override
    public void unloaded() {
        dropWire();
    }

    @Override
    public void tick() {
        super.tick();
        link.tick();
    }

    // --- ICat6Cable ---

    @Override
    public BaseWireEntity asWireEntity() {
        return this;
    }

    @Override
    public void jackLost(BlockPos jackPos) {
        if(!isRemoved() && !level().isClientSide)
            kill(); // a hanging cable needs both ends
    }
}
