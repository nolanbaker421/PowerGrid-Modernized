package com.nolanbaker.pgmodernized.device.busduct;

import com.google.common.collect.ImmutableList;
import com.nolanbaker.pgmodernized.util.Rotation4ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/** The trip rating of a plug-in breaker: a wrench-only value box on top of the unit, 10 to 800 A in 10 A steps. */
public class PlugInRatingBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<PlugInRatingBehaviour> TYPE = new BehaviourType<>();
    public static final int MIN = 10, MAX = 800, STEP = 10;

    public PlugInRatingBehaviour(BusDuctPlugInBreakerBlockEntity unit) {
        super(Lang.builder().translate("gui.breaker_panel.rating").component(), unit,
                new Rotation4ValueBox(new Vec3(8, BusDuctPlugInBlock.BODY.maxY + 0.25, 8), 0.3f));
        between(MIN, MAX);
        requiresWrench();
        withFormatter(v -> v + " A");
    }

    public static int snap(int rating) {
        int bounded = Math.max(MIN, Math.min(MAX, rating));
        return Math.round(bounded / (float) STEP) * STEP;
    }

    /** Shows a rating without calling back. */
    public void mirror(int rating) {
        value = rating;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public String getClipboardKey() {
        return "PlugInRating";
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        int settings = (MAX - MIN) / STEP + 1;
        return new ValueSettingsBoard(label, settings - 1, 10,
                ImmutableList.of(Lang.builder().translate("gui.breaker_panel.rating").component()),
                new ValueSettingsFormatter(s -> Component.literal((MIN + s.value() * STEP) + " A")));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        int rating = MIN + valueSetting.value() * STEP;
        if(rating != value)
            playFeedbackSound(this);
        setValue(rating);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, (snap(value) - MIN) / STEP);
    }
}
