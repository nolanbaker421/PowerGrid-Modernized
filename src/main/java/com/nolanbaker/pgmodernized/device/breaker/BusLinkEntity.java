package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.JunctionWireEndpoint;

import java.util.List;

/**
 * A hidden bus bar between the couplers of two adjacent switchgear sections: a Power Grid block
 * wire of the bus bar item, one block long, that cannot be clicked or cut and goes away by itself
 * when either section does or turns away.
 */
public class BusLinkEntity extends BlockWireEntity {
    private static final int CHECK_INTERVAL = 20;
    private int checkTimer;

    public BusLinkEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static BusLinkEntity create(Level level, BlockWireEndpoint a, BlockWireEndpoint b) {
        var entity = new BusLinkEntity(ModEntities.BUS_LINK.get(), level);
        entity.setItem(ModItems.BUS_BAR.get(), 1);
        var start = a.getExactPosition(level);
        var end = b.getExactPosition(level);
        entity.setPosRaw(start.x, start.y, start.z);
        entity.segments.addAll(segment(start, end));
        entity.bakeBoundingBoxes();
        entity.setEndpoint1(a);
        entity.setEndpoint2(b);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    private static List<Point> segment(Vec3 from, Vec3 to) {
        float dx = (float) (to.x - from.x), dz = (float) (to.z - from.z);
        return Math.abs(dx) > Math.abs(dz) ? List.of(Point.x(dx)) : List.of(Point.z(dz));
    }

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || isRemoved())
            return;
        if(++checkTimer >= CHECK_INTERVAL) {
            checkTimer = 0;
            if(!(getEndpoint1() instanceof BlockWireEndpoint a) || !(getEndpoint2() instanceof BlockWireEndpoint b)
                    || !SwitchgearBlockEntity.linked(level(), a.getPos(), b.getPos()))
                kill();
        }
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
    public JunctionWireEndpoint split(int segmentIndex, int segmentPoint) {
        return null;
    }

    @Override
    public BlockWireEntity flip() {
        return this;
    }

    /** Nothing to pick up: the bus bar belongs to the sections. */
    @Override
    public void dropWire() {}
}
