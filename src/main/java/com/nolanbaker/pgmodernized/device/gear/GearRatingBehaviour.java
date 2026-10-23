package com.nolanbaker.pgmodernized.device.gear;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.breaker.BreakerFrame;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
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
 * The trip rating of a breaker in one slot of a switchgear cell: a wrench-only value box on the
 * breaker's face, ranged by its frame. One behaviour type and net id per slot, as the panel does.
 */
public class GearRatingBehaviour extends ScrollValueBehaviour {
    private static final BehaviourType<?>[] TYPES = new BehaviourType<?>[GearLayout.SLOTS];
    static {
        for(int i = 0; i < TYPES.length; ++i)
            TYPES[i] = new BehaviourType<GearRatingBehaviour>();
    }

    private final GearCellBlockEntity cell;
    private final int slot;

    public GearRatingBehaviour(GearCellBlockEntity cell, int slot) {
        super(Lang.builder().translate("gui.breaker_panel.rating").component(), cell, new SlotBox(cell, slot));
        this.cell = cell;
        this.slot = slot;
        between(1, 800);
        requiresWrench();
        onlyActiveWhen(() -> cell.slot(slot).breaker.isBreaker());
        withFormatter(v -> v + " A");
    }

    private BreakerFrame frame() {
        var frame = cell.slot(slot).breaker.frame();
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
        return TYPES[slot];
    }

    @Override
    public int netId() {
        return slot + 2;
    }

    @Override
    public String getClipboardKey() {
        return "GearRating" + (slot + 1);
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

    /** The box on the face of the breaker in that slot. */
    public static class SlotBox extends ValueBoxTransform {
        private final GearCellBlockEntity cell;
        private final int slot;

        public SlotBox(GearCellBlockEntity cell, int slot) {
            this.cell = cell;
            this.slot = slot;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if(!(state.getBlock() instanceof GearCellBlock) || !cell.slot(slot).breaker.isBreaker())
                return null;
            var center = GearLayout.slotCenter(slot).add(0, 0, -0.75);
            return PanelLayout.fromNorthFrame(center, GearCellBlock.facing(state)).scale(1 / 16.0);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            if(!(state.getBlock() instanceof GearCellBlock))
                return;
            float yRot = AngleHelper.horizontalAngle(GearCellBlock.facing(state)) + 180;
            ms.mulPose(Axis.YP.rotationDegrees(yRot));
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            if(!(state.getBlock() instanceof GearCellBlock) || !cell.slot(slot).breaker.isBreaker())
                return false;
            var facing = GearCellBlock.facing(state);
            return GearLayout.slotAt(facing, facing, localHit) == slot;
        }

        @Override
        public float getScale() {
            return 0.25f;
        }
    }
}
