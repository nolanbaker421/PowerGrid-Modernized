package com.nolanbaker.pgmodernized.device.pole;

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/** The recloser's pickup current: a wrench-only value box on its front, 10 to 800 A. */
public class RecloserPickupBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<RecloserPickupBehaviour> TYPE = new BehaviourType<>();

    public RecloserPickupBehaviour(RecloserBlockEntity recloser) {
        super(Lang.builder().translate("gui.recloser.pickup").component(), recloser, new FrontBox());
        between(10, 800);
        requiresWrench();
        withFormatter(v -> v + " A");
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public String getClipboardKey() {
        return "RecloserPickup";
    }

    /** High on the tank's front. */
    public static class FrontBox extends CenteredSideValueBoxTransform {
        public FrontBox() {
            super((state, dir) -> dir == RecloserBlock.facing(state));
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8, 11.5, 14).scale(1 / 16.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, net.minecraft.core.Direction direction) {
            return direction == RecloserBlock.facing(state);
        }
    }
}
