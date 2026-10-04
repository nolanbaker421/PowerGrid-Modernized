package com.nolanbaker.pgmodernized.ac.source;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The two boxes on the load bank's door: the load it should draw, in kilowatts, and the
 * line-to-neutral voltage that load is rated at. Together they fix the resistance per phase.
 */
public class LoadBankBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<LoadBankBehaviour> KW_TYPE = new BehaviourType<>();
    public static final BehaviourType<LoadBankBehaviour> VOLTS_TYPE = new BehaviourType<>();
    /** Load steps, in kilowatts for all three phases together. */
    public static final double[] KILOWATTS = {0.5, 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000};

    private final boolean load;

    public LoadBankBehaviour(SmartBlockEntity be, boolean load) {
        super(Lang.builder().translate(load ? "gui.load_bank.load" : "gui.load_bank.rated").component(), be, new AcSourceBehaviour.FrontBox(load, FeInverterBlock.DOOR));
        this.load = load;
        if(load) {
            between(0, KILOWATTS.length - 1);
            setValue(3);
            withFormatter(i -> format(kilowattsOf(i)) + " kW");
        } else {
            between(0, AcSourceBehaviour.VOLTS.length - 1);
            setValue(0);
            withFormatter(i -> AcSourceBehaviour.voltsOf(i) + " V L-N");
        }
    }

    public static double kilowattsOf(int index) {
        return KILOWATTS[Math.max(0, Math.min(KILOWATTS.length - 1, index))];
    }

    private static String format(double kw) {
        return kw < 1 ? String.format("%.1f", kw) : String.format("%.0f", kw);
    }

    @Override
    public BehaviourType<?> getType() {
        return load ? KW_TYPE : VOLTS_TYPE;
    }

    @Override
    public int netId() {
        return load ? 1 : 2;
    }

    @Override
    public String getClipboardKey() {
        return key();
    }

    private String key() {
        return load ? "LoadKw" : "LoadVolts";
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
        if(load) {
            return new ValueSettingsBoard(label, KILOWATTS.length - 1, 1, ImmutableList.of(label),
                    new ValueSettingsFormatter(settings -> Component.literal(format(kilowattsOf(settings.value())) + " kW")));
        }
        return new ValueSettingsBoard(label, AcSourceBehaviour.VOLTS.length - 1, 1, ImmutableList.of(label),
                new ValueSettingsFormatter(settings -> Component.literal(AcSourceBehaviour.voltsOf(settings.value()) + " V L-N")));
    }
}
