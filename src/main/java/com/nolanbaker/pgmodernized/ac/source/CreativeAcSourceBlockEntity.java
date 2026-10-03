package com.nolanbaker.pgmodernized.ac.source;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.ac.source.CreativeAcSourceBlock.*;

/**
 * Three ideal alternating sources from each line to the neutral, a milliohm each, retuned every
 * tick to the two value boxes. Nothing limits the current: it is a creative block.
 */
public class CreativeAcSourceBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    public static final float SOURCE_R = 0.001f;
    private static final double THIRD_TURN = 2 * Math.PI / 3;

    private AcSourceBehaviour voltageBox, frequencyBox;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ACVoltageSourceCoupling[] sources;
    private boolean splitPhase;
    private boolean lineToLine;
    private boolean appliedSplit;

    public CreativeAcSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        voltageBox = new AcSourceBehaviour(this, true, CreativeAcSourceBlock.TOP, () -> lineToLine);
        frequencyBox = new AcSourceBehaviour(this, false, CreativeAcSourceBlock.TOP, () -> false);
        voltageBox.withCallback(i -> setChanged());
        frequencyBox.withCallback(i -> setChanged());
        behaviours.add(voltageBox);
        behaviours.add(frequencyBox);
        super.addBehaviours(behaviours);
    }

    /** Phase of line k: a third of a turn apart, or half a turn apart with L3 on L1 for split-phase. */
    private double phaseOffset(int k) {
        if(splitPhase)
            return k == 1 ? -Math.PI : 0;
        return -THIRD_TURN * k;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        sources = new ACVoltageSourceCoupling[3];
        var neutral = builder.terminalNode(N);
        for(int k = 0; k < 3; ++k) {
            sources[k] = new ACVoltageSourceCoupling(builder.terminalNode(L1 + k), neutral, (double) SOURCE_R, 0, 0);
            sources[k].setPhaseOffset(phaseOffset(k));
            builder.add(sources[k]);
        }
        appliedSplit = splitPhase;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    /** The figure on the box: line-to-neutral, or line-to-line when so set. */
    public float volts() {
        return voltageBox == null ? 0 : AcSourceBehaviour.voltsOf(voltageBox.getValue());
    }

    public boolean isLineToLine() {
        return lineToLine;
    }

    /** What each line actually gets to the neutral: a third of a turn apart gives L-L / sqrt 3, half a turn gives L-L / 2. */
    public float lineToNeutralVolts() {
        float v = volts();
        if(!lineToLine)
            return v;
        return (float) (splitPhase ? v / 2 : v / Math.sqrt(3));
    }

    public float hertz() {
        return frequencyBox == null ? 0 : frequencyBox.getValue();
    }

    public boolean isSplitPhase() {
        return splitPhase;
    }

    @Override
    public void electricalTick() {
        if(sources == null)
            return;
        if(appliedSplit != splitPhase) {
            for(int k = 0; k < 3; ++k)
                sources[k].setPhaseOffset(phaseOffset(k));
            appliedSplit = splitPhase;
        }
        float volts = lineToNeutralVolts(), hz = hertz();
        for(var source : sources) {
            source.setFrequency(hz);
            source.setRmsVoltage(volts);
        }
    }

    /** Server side: three-phase L-N, three-phase L-L, split-phase L-N, split-phase L-L, round again. */
    public void toggleSplitPhase(Player player) {
        if(!lineToLine) {
            lineToLine = true;
        } else {
            lineToLine = false;
            splitPhase = !splitPhase;
        }
        if(level != null)
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, splitPhase ? 0.6f : 0.7f);
        player.displayClientMessage(Lang.builder().translate(splitPhase ? "gui.creative_ac_source.split" : "gui.creative_ac_source.three")
                .text(", ").add(Lang.builder().translate(lineToLine ? "gui.ac_source.ll" : "gui.ac_source.ln"))
                .style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Split", splitPhase);
        tag.putBoolean("LineToLine", lineToLine);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        splitPhase = tag.getBoolean("Split");
        lineToLine = tag.getBoolean("LineToLine");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.creative_ac_source.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.ac_source.setting", String.format("%.0f", volts()), lineToLine ? "L-L" : "L-N",
                String.format("%.0f", lineToNeutralVolts()), String.format("%.0f", hertz())).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate(splitPhase ? "gui.creative_ac_source.split" : "gui.creative_ac_source.three").style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        return true;
    }
}
