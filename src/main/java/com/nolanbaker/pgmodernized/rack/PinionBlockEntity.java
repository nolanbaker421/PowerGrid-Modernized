package com.nolanbaker.pgmodernized.rack;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The pinion's kinetic side. The actual pushing of the body happens in the Sable subclass that
 * {@link SableHooks} swaps in when Create Aeronautics is present; this base keeps the numbers
 * the goggles show and the stress the cog draws.
 */
public class PinionBlockEntity extends KineticBlockEntity {
    public static final float STRESS = 8f;
    /** Replaced by {@link SableHooks} with the body-driving subclass. */
    public static BlockEntityFactory<PinionBlockEntity> FACTORY = PinionBlockEntity::new;

    /** Whether a rack faced the rim during the last physics step, and the body speed along it. */
    protected boolean engaged;
    protected float travel;
    private boolean lastEngaged;
    private float lastTravel;
    private int syncTimer;

    public PinionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Pitch radius in metres: the rim speed is the shaft speed times this. */
    public double pitchRadius() {
        return PgmConfig.PINION_PITCH_RADIUS.get();
    }

    /** Rim speed in metres per second for the current shaft speed. */
    public double rimSpeed() {
        return Math.abs(getSpeed()) * Math.PI * 2 / 60 * pitchRadius();
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        if(++syncTimer < 10)
            return;
        syncTimer = 0;
        if(engaged != lastEngaged || Math.abs(travel - lastTravel) > 0.01f) {
            lastEngaged = engaged;
            lastTravel = travel;
            sendData();
        }
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = STRESS;
        return STRESS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Engaged", engaged);
        tag.putFloat("Travel", travel);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        engaged = tag.getBoolean("Engaged");
        travel = tag.getFloat("Travel");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean any = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        LangBuilder line = Lang.builder().translate("gui.pinion.rim_speed", String.format("%.2f", rimSpeed()));
        line.style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(engaged)
            Lang.builder().translate("gui.pinion.engaged", String.format("%.2f", travel)).style(ChatFormatting.GREEN).forGoggles(tooltip);
        else
            Lang.builder().translate("gui.pinion.no_rack").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        return true;
    }
}
