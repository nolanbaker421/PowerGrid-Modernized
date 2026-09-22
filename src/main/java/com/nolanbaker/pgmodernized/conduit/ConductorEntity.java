package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.JunctionWireEndpoint;

import java.util.List;
import java.util.UUID;

/**
 * A wire pulled through a conduit run: an invisible Power Grid block wire made of whatever wire item
 * the player used, following the run's path between the hidden conductor terminals of the two
 * fittings. It cannot be clicked, cut or picked up directly; it drops its wire when its run goes
 * away, and burns out like any Power Grid wire when overloaded, which frees its slot.
 */
public class ConductorEntity extends BlockWireEntity {
    private static final int CHECK_INTERVAL = 20;

    private UUID runId = new UUID(0, 0);
    private int slot;
    private int checkTimer;

    public ConductorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ConductorEntity create(Level level, ConduitRunEntity run, int slot, ItemStack wire,
                                         BlockWireEndpoint a, BlockWireEndpoint b, Vec3 start, List<Point> segments) {
        var entity = new ConductorEntity(ModEntities.CONDUCTOR.get(), level);
        entity.runId = run.getUUID();
        entity.slot = slot;
        entity.setItem(wire.getItem(), wire.getCount());
        entity.setPosRaw(start.x, start.y, start.z);
        entity.segments.addAll(segments);
        entity.bakeBoundingBoxes();
        entity.setEndpoint1(a);
        entity.setEndpoint2(b);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    public UUID runId() {
        return runId;
    }

    /** Position in the conduit, 0-based; also its colour. */
    public int slot() {
        return slot;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || isRemoved())
            return;
        if(++checkTimer >= CHECK_INTERVAL) {
            checkTimer = 0;
            validate();
        }
    }

    private void validate() {
        if(!(level() instanceof ServerLevel server) || !(server.getEntity(runId) instanceof ConduitRunEntity run) || run.isRemoved()) {
            kill();
            return;
        }
        if(!(getEndpoint1() instanceof BlockWireEndpoint a) || !(getEndpoint2() instanceof BlockWireEndpoint b)
                || !run.endsAt(a.getPos(), b.getPos())) {
            kill();
        }
    }

    @Override
    public void endpointRemoved(IWireEndpoint endpoint) {
        kill();
    }

    // ------------------------------------------------------------------ hidden from players

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

    // ------------------------------------------------------------------ nbt

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putUUID("Run", runId);
        nbt.putInt("Slot", slot);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if(nbt.hasUUID("Run"))
            runId = nbt.getUUID("Run");
        slot = nbt.getInt("Slot");
    }
}
