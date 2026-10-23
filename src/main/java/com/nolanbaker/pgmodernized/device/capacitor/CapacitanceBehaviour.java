package com.nolanbaker.pgmodernized.device.capacitor;

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

/** The capacitance of each of a bank's three capacitors, on its door: 10 to 5000 µF in 10 µF steps. */
public class CapacitanceBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<CapacitanceBehaviour> TYPE = new BehaviourType<>();
    public static final int STEP_UF = 10, STEPS = 500, DEFAULT = 100;

    public CapacitanceBehaviour(SmartBlockEntity be) {
        super(Lang.builder().translate("gui.capacitor_bank.capacitance").component(), be, new DoorValueBox(9.5, 9.5, CapacitorBankBlock.DOOR, 0.4f));
        between(1, STEPS);
        setValue(DEFAULT);
        withFormatter(i -> i * STEP_UF + " µF");
    }

    /** Farads for a setting. */
    public static double farads(int setting) {
        return Math.max(1, Math.min(STEPS, setting)) * STEP_UF * 1e-6;
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
        return "Capacitance";
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("Capacitance", value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if(nbt.contains("Capacitance"))
            value = nbt.getInt("Capacitance");
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, STEPS - 1, 10, ImmutableList.of(label),
                new ValueSettingsFormatter(settings -> Component.literal((settings.value() + 1) * STEP_UF + " µF")));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        int setting = valueSetting.value() + 1;
        if(setting != value)
            playFeedbackSound(this);
        setValue(setting);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, Math.max(1, value) - 1);
    }
}
