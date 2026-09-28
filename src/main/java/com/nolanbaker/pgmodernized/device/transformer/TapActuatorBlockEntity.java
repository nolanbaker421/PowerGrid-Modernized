package com.nolanbaker.pgmodernized.device.transformer;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Every full turn of the shaft is one attempt to move the transformer's HV tap one step towards its
 * target. The target lives on the transformer, set here on the value box or by a computer through
 * the transformer's jack. A tier 2 unit only moves dead: turning it live arcs and the actuator
 * stalls. A tier 3 unit's on-load changer moves live, one step at a time. Tier 1 units have no drive.
 */
public class TapActuatorBlockEntity extends KineticBlockEntity {
    /** Stress the gearbox draws, in Create units per rpm. */
    public static final float STRESS = 4f;
    private static final float TURNS_PER_STEP = 1f;

    private TargetBehaviour target;
    private float progress;
    private TransformerBlockEntity.TapStatus status = TransformerBlockEntity.TapStatus.IDLE;
    private int syncTimer;

    public TapActuatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        target = new TargetBehaviour(this);
        target.withCallback(value -> {
            var transformer = transformer();
            if(transformer != null)
                transformer.setHvTapTarget(value);
        });
        behaviours.add(target);
    }

    @Override
    public float calculateStressApplied() {
        return STRESS;
    }

    @Nullable
    public TransformerBlockEntity transformer() {
        return level == null ? null : TapActuatorBlock.transformer(level, worldPosition, getBlockState());
    }

    public TransformerBlockEntity.TapStatus status() {
        return status;
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        var transformer = transformer();
        if(transformer == null) {
            setStatus(TransformerBlockEntity.TapStatus.NO_DRIVE);
            return;
        }
        // The box on top mirrors the transformer's target, which a computer may have changed.
        if(target != null && target.getValue() != transformer.hvTapTarget())
            target.setValue(transformer.hvTapTarget());
        float rpm = Math.abs(getSpeed());
        if(rpm < 1) {
            setStatus(transformer.hvTap() == transformer.hvTapTarget() ? TransformerBlockEntity.TapStatus.AT_TARGET : TransformerBlockEntity.TapStatus.IDLE);
            return;
        }
        progress += rpm / 60f / 20f;
        if(progress < TURNS_PER_STEP)
            return;
        progress -= TURNS_PER_STEP;
        var result = transformer.stepTapTowardTarget();
        if(result == TransformerBlockEntity.TapStatus.LIVE && status != TransformerBlockEntity.TapStatus.LIVE)
            level.playSound(null, worldPosition, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.8f, 0.6f);
        if(result == TransformerBlockEntity.TapStatus.OK)
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.6f);
        setStatus(result);
    }

    private void setStatus(TransformerBlockEntity.TapStatus value) {
        if(value == status)
            return;
        status = value;
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByte("TapStatus", (byte) status.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        var values = TransformerBlockEntity.TapStatus.values();
        int i = tag.getByte("TapStatus");
        status = i >= 0 && i < values.length ? values[i] : TransformerBlockEntity.TapStatus.IDLE;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.tap_actuator.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        var transformer = transformer();
        if(transformer == null) {
            Lang.builder().translate("gui.tap_actuator.no_transformer").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            Lang.builder().translate("gui.tap_actuator.taps", String.format("%+d", transformer.hvTap()), String.format("%+d", transformer.hvTapTarget()), transformer.tier())
                    .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
            Lang.builder().translate("gui.tap_actuator.status." + status.key()).style(status.colour()).forGoggles(tooltip, 1);
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    /** The HV tap target, -4..4, on the top face. */
    public static class TargetBehaviour extends ScrollValueBehaviour {
        public TargetBehaviour(TapActuatorBlockEntity be) {
            super(Lang.builder().translate("gui.tap_actuator.target").component(), be, new TopBox());
            between(-TransformerSpec.TAP_RANGE, TransformerSpec.TAP_RANGE);
            withFormatter(v -> String.format("%+d", v));
        }

        @Override
        public String getClipboardKey() {
            return "TapTarget";
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 2 * TransformerSpec.TAP_RANGE, 2, ImmutableList.of(label),
                    new ValueSettingsFormatter(settings -> Component.literal(String.format("%+d", settings.value() - TransformerSpec.TAP_RANGE))));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
            int tap = valueSetting.value() - TransformerSpec.TAP_RANGE;
            if(tap != value)
                playFeedbackSound(this);
            setValue(tap);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, value + TransformerSpec.TAP_RANGE);
        }
    }

    public static class TopBox extends CenteredSideValueBoxTransform {
        public TopBox() {
            super((state, dir) -> dir == Direction.UP);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8 / 16.0, 8 / 16.0, 13.25 / 16.0);
        }
    }
}
