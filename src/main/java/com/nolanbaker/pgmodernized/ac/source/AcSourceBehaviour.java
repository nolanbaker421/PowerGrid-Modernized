package com.nolanbaker.pgmodernized.ac.source;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.function.BooleanSupplier;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The two boxes on top of the creative source: the voltage one steps through the nameplate
 * voltages of the mod, the frequency one runs in whole hertz. Two behaviours on one block need
 * two types and net ids.
 */
public class AcSourceBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<AcSourceBehaviour> VOLTS_TYPE = new BehaviourType<>();
    public static final BehaviourType<AcSourceBehaviour> HERTZ_TYPE = new BehaviourType<>();
    /** Line-to-neutral choices, in volts. */
    public static final int[] VOLTS = {120, 208, 240, 277, 480, 600, 1000, 3500, 8000, 10000, 35000, 100000};
    public static final int MAX_HZ = 120;

    private final boolean voltage;
    private final BooleanSupplier lineToLine;

    public AcSourceBehaviour(SmartBlockEntity be, boolean voltage) {
        this(be, voltage, CreativeAcSourceBlock.TOP, () -> false);
    }

    /** @param top the lid height in 16ths the boxes sit on; @param lineToLine whether the voltage figure is line-to-line. */
    public AcSourceBehaviour(SmartBlockEntity be, boolean voltage, double top, BooleanSupplier lineToLine) {
        this(be, voltage, new TopBox(voltage, top), lineToLine);
    }

    /** @param box where the box sits on the block. */
    public AcSourceBehaviour(SmartBlockEntity be, boolean voltage, CenteredSideValueBoxTransform box, BooleanSupplier lineToLine) {
        super(Lang.builder().translate(voltage ? "gui.creative_ac_source.voltage" : "gui.creative_ac_source.frequency").component(), be, box);
        this.voltage = voltage;
        this.lineToLine = lineToLine;
        if(voltage) {
            between(0, VOLTS.length - 1);
            setValue(0);
            withFormatter(i -> voltsOf(i) + (lineToLine.getAsBoolean() ? " V L-L" : " V L-N"));
        } else {
            between(1, MAX_HZ);
            setValue(10);
            withFormatter(hz -> hz + " Hz");
        }
    }

    public static int voltsOf(int index) {
        return VOLTS[Math.max(0, Math.min(VOLTS.length - 1, index))];
    }

    @Override
    public BehaviourType<?> getType() {
        return voltage ? VOLTS_TYPE : HERTZ_TYPE;
    }

    @Override
    public int netId() {
        return voltage ? 1 : 2;
    }

    @Override
    public String getClipboardKey() {
        return voltage ? "SourceVolts" : "SourceHertz";
    }

    private String key() {
        return voltage ? "SourceVolts" : "SourceHertz";
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
        if(voltage) {
            return new ValueSettingsBoard(label, VOLTS.length - 1, 1, ImmutableList.of(label),
                    new ValueSettingsFormatter(settings -> Component.literal(voltsOf(settings.value()) + (lineToLine.getAsBoolean() ? " V L-L" : " V L-N"))));
        }
        return new ValueSettingsBoard(label, MAX_HZ, 10, ImmutableList.of(label),
                new ValueSettingsFormatter(settings -> Component.literal(settings.value() + " Hz")));
    }

    /** On the door of a wall cabinet whose door is {@code door} 16ths in from the block edge: voltage on the viewer's left, frequency on the right. */
    public static class FrontBox extends CenteredSideValueBoxTransform {
        private final boolean voltage;
        private final double door;

        public FrontBox(boolean voltage, double door) {
            super((state, dir) -> state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir == state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            this.voltage = voltage;
            this.door = door;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3((voltage ? 11.5 : 4.5) / 16.0, 10.5 / 16.0, door / 16.0);
        }

        @Override
        public float getScale() {
            return 0.4f;
        }
    }

    /** On the top face: voltage on the viewer's left, frequency on the right. */
    public static class TopBox extends CenteredSideValueBoxTransform {
        private final boolean voltage;
        private final double top;

        public TopBox(boolean voltage) {
            this(voltage, CreativeAcSourceBlock.TOP);
        }

        public TopBox(boolean voltage, double top) {
            super((state, dir) -> dir == Direction.UP);
            this.voltage = voltage;
            this.top = top;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3((voltage ? 11.5 : 4.5) / 16.0, 8 / 16.0, (top + 0.25) / 16.0);
        }

        @Override
        public float getScale() {
            return 0.4f;
        }
    }
}
