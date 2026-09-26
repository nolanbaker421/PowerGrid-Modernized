package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * <p>
 * Its colour is its slot's by default, but can be set to any of the twelve from the splice editor,
 * so a neutral can be white whatever its slot.
 */
public class ConductorEntity extends BlockWireEntity {
    private static final int CHECK_INTERVAL = 20;
    /** Ticks after loading before the run is looked for: entities of a chunk arrive after its blocks. */
    private static final int LOAD_GRACE = 100;
    private static final EntityDataAccessor<Integer> COLOR_INDEX = SynchedEntityData.defineId(ConductorEntity.class, EntityDataSerializers.INT);

    private UUID runId = new UUID(0, 0);
    private BlockPos runPos = BlockPos.ZERO;
    private int slot;
    private int checkTimer = -LOAD_GRACE;

    public ConductorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ConductorEntity create(Level level, ConduitRunEntity run, int slot, ItemStack wire,
                                         BlockWireEndpoint a, BlockWireEndpoint b, Vec3 start, List<Point> segments) {
        var entity = new ConductorEntity(ModEntities.CONDUCTOR.get(), level);
        entity.runId = run.getUUID();
        entity.runPos = run.blockPosition();
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR_INDEX, -1);
    }

    public UUID runId() {
        return runId;
    }

    /** Position in the conduit, 0-based. */
    public int slot() {
        return slot;
    }

    /** Colour index into {@link ConductorColors}: the slot's unless one was chosen. Works on both sides. */
    public int colorIndex() {
        int chosen = entityData.get(COLOR_INDEX);
        return chosen >= 0 ? Math.floorMod(chosen, ConductorColors.COUNT) : Math.floorMod(slot, ConductorColors.COUNT);
    }

    public boolean hasChosenColor() {
        return entityData.get(COLOR_INDEX) >= 0;
    }

    /** Server side. A negative index goes back to the slot's colour. */
    public void setColorIndex(int index) {
        entityData.set(COLOR_INDEX, index < 0 ? -1 : Math.floorMod(index, ConductorColors.COUNT));
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

    /**
     * Goes away when its run is gone. A run whose chunk is not loaded, or that has not been added
     * to the level yet, is not gone: entities arrive after the blocks of their chunk, and a run
     * can sit in the next chunk over.
     */
    private void validate() {
        if(!(level() instanceof ServerLevel server))
            return;
        if(!(server.getEntity(runId) instanceof ConduitRunEntity run) || run.isRemoved()) {
            if(server.isLoaded(runPos) && server.getEntity(runId) == null)
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
        nbt.put("RunPos", NbtUtils.writeBlockPos(runPos));
        nbt.putInt("Slot", slot);
        nbt.putInt("ChosenColor", entityData.get(COLOR_INDEX));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if(nbt.hasUUID("Run"))
            runId = nbt.getUUID("Run");
        runPos = NbtUtils.readBlockPos(nbt, "RunPos").orElse(blockPosition());
        slot = nbt.getInt("Slot");
        entityData.set(COLOR_INDEX, nbt.contains("ChosenColor") ? nbt.getInt("ChosenColor") : -1);
        checkTimer = -LOAD_GRACE;
    }
}
