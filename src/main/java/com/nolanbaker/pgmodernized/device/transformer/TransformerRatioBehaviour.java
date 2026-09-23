package com.nolanbaker.pgmodernized.device.transformer;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/** Create value box on the front of the transformer that scrolls through {@link TransformerRatio}'s presets. */
public class TransformerRatioBehaviour extends ScrollValueBehaviour {
    public TransformerRatioBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(Lang.builder().translate("gui.transformer.ratio").component(), be, slot);
        between(0, TransformerRatio.COUNT - 1);
        setValue(TransformerRatio.UNITY);
        withFormatter(TransformerRatio::describe);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, max, 1,
                ImmutableList.of(Lang.builder().translate("gui.transformer.ratio").component()),
                new ValueSettingsFormatter(settings -> Component.literal(TransformerRatio.describe(settings.value()))));
    }

    @Override
    public String getClipboardKey() {
        return "TransformerRatio";
    }

    /** Value box on the face the transformer's front looks at, at a fixed spot on that face. */
    public static class FrontBox extends CenteredSideValueBoxTransform {
        private final Vec3 location;

        /** @param px position on the south face in pixels, as Create's "south location" */
        public FrontBox(Vec3 px) {
            super((state, dir) -> dir == TransformerBlock.facing(state));
            this.location = px.scale(1 / 16.0);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return location;
        }
    }
}
