package com.nolanbaker.pgmodernized.device.transformer;

import com.google.common.collect.ImmutableList;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One winding's tap changer: a value box on the front of the base block, nine positions from ten
 * percent under to ten percent over the nameplate voltage. There is no on-load tap changer:
 * changing a tap while that winding is live arcs and shocks whoever did it.
 */
public class TransformerTapBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<TransformerTapBehaviour> HV_TYPE = new BehaviourType<>();
    public static final BehaviourType<TransformerTapBehaviour> LV_TYPE = new BehaviourType<>();

    private final TransformerBlockEntity transformer;
    private final boolean highSide;

    public TransformerTapBehaviour(TransformerBlockEntity transformer, boolean highSide) {
        super(Lang.builder().translate(highSide ? "gui.transformer.hv_tap" : "gui.transformer.lv_tap").component(), transformer, new TapBox(highSide));
        this.transformer = transformer;
        this.highSide = highSide;
        between(-TransformerSpec.TAP_RANGE, TransformerSpec.TAP_RANGE);
        withFormatter(this::describe);
    }

    private String describe(int tap) {
        var spec = transformer.spec();
        float volts = highSide ? spec.hvAt(tap) : spec.lvAt(tap);
        return String.format("%+d (%s)", tap, TransformerSpec.volts(volts));
    }

    /** The tap, -4..4. */
    public int tap() {
        return Math.max(-TransformerSpec.TAP_RANGE, Math.min(TransformerSpec.TAP_RANGE, value));
    }

    @Override
    public BehaviourType<?> getType() {
        return highSide ? HV_TYPE : LV_TYPE;
    }

    @Override
    public int netId() {
        return highSide ? 1 : 2;
    }

    @Override
    public String getClipboardKey() {
        return highSide ? "HvTap" : "LvTap";
    }

    private String key() {
        return highSide ? "HvTap" : "LvTap";
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt(key(), value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if(nbt.contains(key()))
            value = nbt.getInt(key());
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, 2 * TransformerSpec.TAP_RANGE, 2,
                ImmutableList.of(Lang.builder().translate(highSide ? "gui.transformer.hv_tap" : "gui.transformer.lv_tap").component()),
                new ValueSettingsFormatter(settings -> Component.literal(describe(settings.value() - TransformerSpec.TAP_RANGE))));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        int tap = valueSetting.value() - TransformerSpec.TAP_RANGE;
        if(tap == value)
            return;
        // No on-load tap changer: moving a live winding's tap draws an arc.
        float live = transformer.liveVolts(highSide);
        if(live >= ShockDamage.SAFE_VOLTS && !player.level().isClientSide) {
            ShockDamage.shockVolts(player, live);
            player.displayClientMessage(Lang.builder().translate("message.transformer.tap_arc").style(ChatFormatting.RED).component(), true);
        }
        playFeedbackSound(this);
        setValue(tap);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, tap() + TransformerSpec.TAP_RANGE);
    }

    /** On the front of the base cell, high side on the viewer's left, low side on the right. */
    public static class TapBox extends CenteredSideValueBoxTransform {
        private final boolean highSide;

        public TapBox(boolean highSide) {
            super((state, dir) -> dir == TransformerBlock.facing(state));
            this.highSide = highSide;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(highSide ? 12 / 16.0 : 4 / 16.0, 9 / 16.0, 1);
        }

        @Override
        public Vec3 getLocalOffset(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, BlockState state) {
            if(!(state.getBlock() instanceof TransformerBlock block))
                return super.getLocalOffset(level, pos, state);
            var south = TransformerGeometry.tapBox(block.spec().size(), TransformerBlock.hung(state), highSide).scale(1 / 16.0);
            var location = net.createmod.catnip.math.VecHelper.rotateCentered(south, net.createmod.catnip.math.AngleHelper.horizontalAngle(getSide()), net.minecraft.core.Direction.Axis.Y);
            return location;
        }

        @Override
        protected boolean isSideActive(BlockState state, net.minecraft.core.Direction direction) {
            return state.getBlock() instanceof TransformerBlock && direction == TransformerBlock.facing(state);
        }

        @Override
        public float getScale() {
            return 0.35f;
        }
    }
}
