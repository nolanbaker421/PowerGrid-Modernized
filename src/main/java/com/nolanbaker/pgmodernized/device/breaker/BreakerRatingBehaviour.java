package com.nolanbaker.pgmodernized.device.breaker;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The trip rating of one breaker space, as a Create value box on the breaker's face that only
 * shows and reacts with a wrench in hand (a bare hand flips the handle). Its range follows the
 * frame of whatever breaker is installed; an empty, blank or covered space has no box.
 * <p>
 * Every space needs its own behaviour type and net id: Create keys behaviours by type and routes
 * a setting packet to the first behaviour with a matching id.
 */
public class BreakerRatingBehaviour extends ScrollValueBehaviour {
    /** One type per space: index 0 is the main, 1.. the branch spaces. */
    private static final BehaviourType<?>[] TYPES = new BehaviourType<?>[1 + 12];

    static {
        for(int i = 0; i < TYPES.length; ++i)
            TYPES[i] = new BehaviourType<BreakerRatingBehaviour>();
    }

    private final BreakerPanelBlockEntity panel;
    private final int slot;

    public BreakerRatingBehaviour(BreakerPanelBlockEntity panel, int slot) {
        super(Lang.builder().translate("gui.breaker_panel.rating").component(), panel, new SlotBox(panel, slot));
        this.panel = panel;
        this.slot = slot;
        between(1, 800);
        requiresWrench();
        onlyActiveWhen(() -> panel.breaker(slot).isBreaker());
        withFormatter(v -> v + " A");
    }

    private BreakerFrame frame() {
        var frame = panel.breaker(slot).frame();
        return frame == null ? BreakerFrame.F50 : frame;
    }

    /** Called when a breaker goes in or out: the range follows its frame. */
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

    /** Sets the value without the callback, for syncing from the breaker record. */
    public void mirror(int rating) {
        value = rating;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPES[slot + 1];
    }

    @Override
    public int netId() {
        return slot + 2;
    }

    @Override
    public String getClipboardKey() {
        return "BreakerRating" + (slot + 1);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        var frame = frame();
        int milestone = Math.max(1, frame.settings() / 10);
        return new ValueSettingsBoard(label, frame.settings() - 1, milestone,
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

    /** The value box on the face of the breaker in that space, facing the way the panel opens. */
    public static class SlotBox extends ValueBoxTransform {
        private final BreakerPanelBlockEntity panel;
        private final int slot;

        public SlotBox(BreakerPanelBlockEntity panel, int slot) {
            this.panel = panel;
            this.slot = slot;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if(!(state.getBlock() instanceof BreakerPanelBlock))
                return null;
            var breaker = panel.breaker(slot);
            if(!breaker.isBreaker())
                return null;
            var center = PanelLayout.breakerCenter(panel.spec(), slot, breaker.rows()).add(0, 0, -0.75);
            return PanelLayout.fromNorthFrame(center, BreakerPanelBlock.facing(state)).scale(1 / 16.0);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            if(!(state.getBlock() instanceof BreakerPanelBlock))
                return;
            float yRot = AngleHelper.horizontalAngle(BreakerPanelBlock.facing(state)) + 180;
            ms.mulPose(Axis.YP.rotationDegrees(yRot));
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            if(!(state.getBlock() instanceof BreakerPanelBlock) || !panel.breaker(slot).isBreaker())
                return false;
            var facing = BreakerPanelBlock.facing(state);
            int hit = PanelLayout.slotAt(panel.spec(), facing, facing, localHit);
            return hit != PanelLayout.NO_SLOT && panel.headSlot(hit) == slot;
        }

        @Override
        public float getScale() {
            return 0.3f;
        }
    }
}
