package com.nolanbaker.pgmodernized.device.drive;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/** Frequency setpoint value box on top of the drive, in whole hertz; a computer can set finer values. */
public class DriveFrequencyBehaviour extends ScrollValueBehaviour {
    public DriveFrequencyBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(Lang.builder().translate("gui.three_phase_drive.frequency").component(), be, slot);
        between(0, ThreePhaseDriveBlockEntity.MAX_HZ);
        setValue(0);
        withFormatter(DriveFrequencyBehaviour::describe);
    }

    private static String describe(int hz) {
        return hz + " Hz";
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, max, 5,
                ImmutableList.of(Lang.builder().translate("gui.three_phase_drive.frequency").component()),
                new ValueSettingsFormatter(settings -> Component.literal(describe(settings.value()))));
    }

    @Override
    public String getClipboardKey() {
        return "DriveFrequency";
    }

    /** On the top face, on the drive's display. */
    public static class TopBox extends CenteredSideValueBoxTransform {
        public TopBox() {
            super((state, dir) -> dir == Direction.UP);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8 / 16.0, 8 / 16.0, 9.25 / 16.0);
        }
    }
}
