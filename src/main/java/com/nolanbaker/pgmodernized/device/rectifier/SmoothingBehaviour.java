package com.nolanbaker.pgmodernized.device.rectifier;

import com.google.common.collect.ImmutableList;
import com.nolanbaker.pgmodernized.util.DoorValueBox;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.utility.Lang;

/** The smoothing capacitor across a rectifier's output, on its door: 0 to 500 mF in 10 mF steps. */
public class SmoothingBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<SmoothingBehaviour> TYPE = new BehaviourType<>();
    public static final int STEP_MF = 10, STEPS = 50, DEFAULT = 5;

    public SmoothingBehaviour(SmartBlockEntity be) {
        super(Lang.builder().translate("gui.rectifier.smoothing").component(), be, new DoorValueBox(8, 7.5, RectifierBlock.DOOR, 0.4f));
        between(0, STEPS);
        setValue(DEFAULT);
        withFormatter(i -> i * STEP_MF + " mF");
    }

    /** Farads for a setting. */
    public static double farads(int setting) {
        return Math.max(0, Math.min(STEPS, setting)) * STEP_MF / 1000.0;
    }

    public double farads() {
        return farads(getValue());
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public String getClipboardKey() {
        return "Smoothing";
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("Smoothing", value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if(nbt.contains("Smoothing"))
            value = nbt.getInt("Smoothing");
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, STEPS, 5, ImmutableList.of(label),
                new ValueSettingsFormatter(settings -> Component.literal(settings.value() * STEP_MF + " mF")));
    }
}
