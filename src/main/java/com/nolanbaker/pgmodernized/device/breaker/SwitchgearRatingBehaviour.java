package com.nolanbaker.pgmodernized.device.breaker;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/** Trip rating of a switchgear section's breaker: a wrench-only value box on the door, ranged by the installed frame. */
public class SwitchgearRatingBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<SwitchgearRatingBehaviour> TYPE = new BehaviourType<>();

    private final SwitchgearBlockEntity section;

    public SwitchgearRatingBehaviour(SwitchgearBlockEntity section) {
        super(Lang.builder().translate("gui.breaker_panel.rating").component(), section, new DoorBox());
        this.section = section;
        between(1, 800);
        requiresWrench();
        onlyActiveWhen(() -> section.breaker().isBreaker());
        withFormatter(v -> v + " A");
    }

    private BreakerFrame frame() {
        var frame = section.breaker().frame();
        return frame == null ? BreakerFrame.F50 : frame;
    }

    public void follow(BreakerPanelBlockEntity.Breaker breaker) {
        var frame = breaker.frame();
        if(frame == null) {
            between(1, 800);
            value = 0;
            return;
        }
        between(frame.min(), frame.max());
        value = breaker.rating();
    }

    public void mirror(int rating) {
        value = rating;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public String getClipboardKey() {
        return "SwitchgearRating";
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        var frame = frame();
        return new ValueSettingsBoard(label, frame.settings() - 1, Math.max(1, frame.settings() / 10),
                ImmutableList.of(Lang.builder().translate("gui.breaker_panel.rating").component()),
                new ValueSettingsFormatter(settings -> Component.literal(frame.ratingOf(settings.value()) + " A")));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        int rating = frame().ratingOf(valueSetting.value());
        if(rating != value)
            playFeedbackSound(this);
        setValue(rating);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, frame().settingOf(value));
    }

    /** On the door, above the breaker. */
    public static class DoorBox extends CenteredSideValueBoxTransform {
        public DoorBox() {
            super((state, dir) -> dir == SwitchgearBlock.facing(state));
        }

        @Override
        protected Vec3 getSouthLocation() {
            return SwitchgearLayout.VALUE_BOX.scale(1 / 16.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, net.minecraft.core.Direction direction) {
            return direction == SwitchgearBlock.facing(state);
        }
    }
}
