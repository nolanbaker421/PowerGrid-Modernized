package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.compat.sable.SableUtils;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/**
 * A hanging coax between two coax ports. Rendering, sag, placement length, pick-up and cutting come
 * from Power Grid's hanging wire; it carries no current and joins no network of its own. It only
 * registers itself with the blocks at its two ends, so a radio can follow its run to an antenna.
 */
public class CoaxWireEntity extends HangingWireEntity {
    public static final int DEFAULT_COLOR = 0x1C1C1C;
    private static final int RELINK_INTERVAL = 20;

    @Nullable
    private CoaxSupport linkedA, linkedB;
    private int relinkTimer;

    public CoaxWireEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public static CoaxWireEntity create(Level world, CoaxEndpoint endpoint1, CoaxEndpoint endpoint2, ItemStack item) {
        var entity = new CoaxWireEntity(ModEntities.COAX_CABLE.get(), world);
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
        super.setEndpoint1(CoaxEndpoint.from(endpoint));
    }

    @Override
    public void setEndpoint2(IWireEndpoint endpoint) {
        super.setEndpoint2(CoaxEndpoint.from(endpoint));
    }

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    /** Length of the span, measured where its two ends really are. */
    public double length() {
        var a = getEndpoint1();
        var b = getEndpoint2();
        if(a == null || b == null)
            return 0;
        return SableUtils.projectedDistance(level(), a.getExactPosition(level()), b.getExactPosition(level()));
    }

    /** Whether one end sits in that port of the block at that position. */
    public boolean connectsTo(BlockPos pos, int port) {
        return endOn(getEndpoint1(), pos, port) || endOn(getEndpoint2(), pos, port);
    }

    private static boolean endOn(@Nullable IWireEndpoint endpoint, BlockPos pos, int port) {
        return endpoint instanceof CoaxEndpoint coax && coax.getPos().equals(pos) && (port < 0 || coax.getPort() == port);
    }

    /** The far end of the span from the given block, or null when neither end is there. */
    @Nullable
    public CoaxEndpoint otherEnd(BlockPos pos) {
        if(endOn(getEndpoint1(), pos, -1))
            return (CoaxEndpoint) getEndpoint2();
        if(endOn(getEndpoint2(), pos, -1))
            return (CoaxEndpoint) getEndpoint1();
        return null;
    }

    @Override
    public void makeWire() {
        dropWire();
        if(level().isClientSide)
            return;
        var a = supportAt(getEndpoint1());
        var b = supportAt(getEndpoint2());
        if(a == null || b == null)
            return;
        a.add(this);
        b.add(this);
        linkedA = a;
        linkedB = b;
    }

    @Override
    public void dropWire() {
        if(linkedA != null)
            linkedA.remove(this);
        if(linkedB != null)
            linkedB.remove(this);
        linkedA = null;
        linkedB = null;
    }

    @Override
    public void unloaded() {
        dropWire();
    }

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || isRemoved())
            return;
        if(++relinkTimer < RELINK_INTERVAL)
            return;
        relinkTimer = 0;
        if(lost(getEndpoint1()) || lost(getEndpoint2()))
            return;
        var a = supportAt(getEndpoint1());
        var b = supportAt(getEndpoint2());
        if(a == null || b == null)
            return;
        if(a != linkedA || b != linkedB)
            makeWire();
    }

    /** A port block that vanished while its chunk is loaded takes the span with it. */
    private boolean lost(@Nullable IWireEndpoint endpoint) {
        if(!(endpoint instanceof CoaxEndpoint coax))
            return false;
        if(!level().isLoaded(coax.getPos()) || coax.portBlock(level()) != null)
            return false;
        portLost(coax.getPos());
        return true;
    }

    public void portLost(BlockPos pos) {
        if(!isRemoved() && !level().isClientSide)
            kill();
    }

    @Nullable
    private CoaxSupport supportAt(@Nullable IWireEndpoint endpoint) {
        if(!(endpoint instanceof CoaxEndpoint coax))
            return null;
        var block = coax.portBlock(level());
        return block == null ? null : block.coaxSupport();
    }
}
