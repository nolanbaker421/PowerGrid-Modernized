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
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

/**
 * A mirror of one of the head panel's rating boxes on an extension block, for a space that sits
 * in this section. Create only shows and works value boxes of the block under the crosshair, so
 * the extension carries its own; every read and write goes to the head's behaviour for that space.
 */
public class ExtensionRatingBehaviour extends ScrollValueBehaviour {
    /** Rows times columns of the biggest panel section. */
    public static final int PER_SECTION = 12;
    private static final BehaviourType<?>[] TYPES = new BehaviourType<?>[PER_SECTION];

    static {
        for(int i = 0; i < TYPES.length; ++i)
            TYPES[i] = new BehaviourType<ExtensionRatingBehaviour>();
    }

    private final PanelExtensionBlockEntity extension;
    private final int local;

    public ExtensionRatingBehaviour(PanelExtensionBlockEntity extension, int local) {
        super(Lang.builder().translate("gui.breaker_panel.rating").component(), extension, new ExtBox(extension, local));
        this.extension = extension;
        this.local = local;
        between(1, 800);
        requiresWrench();
        onlyActiveWhen(() -> target() != null);
        withFormatter(v -> v + " A");
    }

    /** The head's behaviour for this space, when the space exists and holds a breaker. */
    @Nullable
    private BreakerRatingBehaviour target() {
        var head = extension.head();
        if(head == null)
            return null;
        int slot = extension.globalSlot(local);
        if(slot < 0 || slot >= head.slotCount() || !head.breaker(slot).isBreaker())
            return null;
        return head.ratingBehaviour(slot);
    }

    @Override
    public void tick() {
        super.tick();
        var target = target();
        if(target != null && target.getValue() != value)
            value = target.getValue();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPES[local];
    }

    @Override
    public int netId() {
        return local + 2;
    }

    @Override
    public String getClipboardKey() {
        return "ExtensionRating" + (local + 1);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        var target = target();
        if(target != null)
            return target.createBoard(player, hitResult);
        return new ValueSettingsBoard(label, 1, 1, ImmutableList.of(label), new ValueSettingsFormatter(s -> Component.literal("-")));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        var target = target();
        if(target != null)
            target.setValueSettings(player, valueSetting, ctrlDown);
    }

    @Override
    public ValueSettings getValueSettings() {
        var target = target();
        return target != null ? target.getValueSettings() : new ValueSettings(0, 0);
    }

    /** The box on the face of the breaker in that space, seen from this block. */
    public static class ExtBox extends ValueBoxTransform {
        private final PanelExtensionBlockEntity extension;
        private final int local;

        public ExtBox(PanelExtensionBlockEntity extension, int local) {
            this.extension = extension;
            this.local = local;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if(!(state.getBlock() instanceof PanelExtensionBlock))
                return null;
            var head = extension.head();
            int slot = extension.globalSlot(local);
            if(head == null || slot < 0 || slot >= head.slotCount())
                return null;
            var breaker = head.breaker(slot);
            if(!breaker.isBreaker())
                return null;
            var center = PanelLayout.breakerCenter(head.spec(), slot, breaker.rows()).add(0, 16 * extension.section(), -0.75);
            return PanelLayout.fromNorthFrame(center, PanelExtensionBlock.facing(state)).scale(1 / 16.0);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            if(!(state.getBlock() instanceof PanelExtensionBlock))
                return;
            float yRot = AngleHelper.horizontalAngle(PanelExtensionBlock.facing(state)) + 180;
            ms.mulPose(Axis.YP.rotationDegrees(yRot));
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            if(!(state.getBlock() instanceof PanelExtensionBlock))
                return false;
            var head = extension.head();
            int slot = extension.globalSlot(local);
            if(head == null || slot < 0 || slot >= head.slotCount() || !head.breaker(slot).isBreaker())
                return false;
            var facing = PanelExtensionBlock.facing(state);
            int hit = PanelLayout.slotAt(head.spec(), facing, facing, localHit.add(0, -extension.section(), 0), head.slotCount());
            return hit != PanelLayout.NO_SLOT && head.headSlot(hit) == slot;
        }

        @Override
        public float getScale() {
            return 0.3f;
        }
    }
}
