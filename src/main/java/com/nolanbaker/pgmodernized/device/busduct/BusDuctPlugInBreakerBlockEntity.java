package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import com.nolanbaker.pgmodernized.device.gear.GearCellBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

/**
 * The plug-in unit with a breaker for a handle: its three contacts open by themselves on the
 * panel's trip curve at the wrench-set rating, and the handle then sits tripped until a bare
 * hand resets it to off.
 */
public class BusDuctPlugInBreakerBlockEntity extends BusDuctPlugInBlockEntity {
    public static final int DEFAULT_RATING = 200;

    private int rating = DEFAULT_RATING;
    private float heat, current, syncedCurrent;
    private boolean tripped;
    private boolean sparks;
    private PlugInRatingBehaviour ratingBox;

    public BusDuctPlugInBreakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ratingBox = new PlugInRatingBehaviour(this);
        ratingBox.withCallback(this::onRating);
        ratingBox.mirror(rating);
        behaviours.add(ratingBox);
        super.addBehaviours(behaviours);
    }

    private void onRating(int value) {
        int snapped = PlugInRatingBehaviour.snap(value);
        if(snapped != value)
            ratingBox.mirror(snapped);
        if(snapped == rating)
            return;
        rating = snapped;
        heat = 0;
        notifyUpdate();
    }

    public int rating() {
        return rating;
    }

    public boolean isTripped() {
        return tripped;
    }

    public BreakerState state() {
        return tripped ? BreakerState.TRIPPED : on ? BreakerState.ON : BreakerState.OFF;
    }

    @Override
    public void electricalTick() {
        if(!on) {
            current = 0;
            heat = BreakerTripCurve.cool(heat);
            return;
        }
        if(contacts == null)
            return;
        boolean converged = false;
        for(var contact : contacts)
            converged |= contact != null && contact.isConverged();
        if(!converged)
            return;
        current = current();
        heat = BreakerTripCurve.step(heat, current, rating);
        if(BreakerTripCurve.trips(heat, current, rating))
            trip();
    }

    private void trip() {
        on = false;
        tripped = true;
        heat = 0;
        current = 0;
        sparks = true;
        setContacts();
        if(level != null && !level.isClientSide)
            ModdedSoundEvents.BREAKER_OFF.playOnServer(level, worldPosition);
        syncState();
    }

    @Override
    protected void syncState() {
        if(level == null)
            return;
        var state = getBlockState();
        if(state.getBlock() instanceof BusDuctPlugInBreakerBlock)
            level.setBlock(worldPosition, state.setValue(BusDuctPlugInBlock.ON, on).setValue(BusDuctPlugInBlock.LOCKED, locked).setValue(BusDuctPlugInBreakerBlock.TRIPPED, tripped), Block.UPDATE_ALL);
        setChanged();
        sendData();
    }

    /** Server side: a tripped handle goes to off first, like a breaker's; otherwise it throws unless locked out. */
    @Override
    public void toggle(Player player) {
        if(locked) {
            message(player, Lang.builder().translate("message.disconnect.locked").style(ChatFormatting.RED).component());
            return;
        }
        if(tripped) {
            tripped = false;
            on = false;
            heat = 0;
            if(level != null)
                level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.6f);
            syncState();
            return;
        }
        on = !on;
        heat = 0;
        setContacts();
        if(level != null)
            (on ? ModdedSoundEvents.BREAKER_ON : ModdedSoundEvents.BREAKER_OFF).playOnServer(level, worldPosition);
        syncState();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        if(Math.abs(current - syncedCurrent) > Math.max(0.05f, Math.abs(syncedCurrent) * 0.02f))
            sendData();
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Rating", rating);
        tag.putBoolean("Tripped", tripped);
        if(clientPacket) {
            tag.putFloat("Current", current);
            syncedCurrent = current;
            if(sparks) {
                tag.putBoolean("Sparks", true);
                sparks = false;
            }
        } else {
            tag.putFloat("Heat", heat);
        }
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Rating", rating);
        tag.putBoolean("Tripped", tripped);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rating = tag.contains("Rating") ? PlugInRatingBehaviour.snap(tag.getInt("Rating")) : DEFAULT_RATING;
        tripped = tag.getBoolean("Tripped");
        if(ratingBox != null)
            ratingBox.mirror(rating);
        if(clientPacket) {
            current = tag.getFloat("Current");
            if(tag.getBoolean("Sparks") && level != null) {
                var pos = worldPosition.getCenter();
                SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, ductFacing().getOpposite(), 3);
            }
        } else {
            heat = tag.getFloat("Heat");
        }
    }

    private net.minecraft.core.Direction ductFacing() {
        var state = getBlockState();
        return state.getBlock() instanceof BusDuctPlugInBlock ? BusDuctPlugInBlock.facing(state) : net.minecraft.core.Direction.DOWN;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.bus_duct_plug_in_breaker.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        var line = Lang.builder().text(rating + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE)
                .text("  ").add(Lang.builder().add(GearCellBlockEntity.stateText(state())));
        if(on)
            line.text("  ").add(Lang.builder().text(String.format("%.1f ", current)).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
        line.forGoggles(tooltip, 1);
        if(locked)
            Lang.builder().translate("gui.disconnect.locked").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
