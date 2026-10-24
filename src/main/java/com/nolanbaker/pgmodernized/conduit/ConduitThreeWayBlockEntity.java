package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.conduit.ConduitThreeWayBlock.*;

/**
 * The 3-way switch's circuit: the common closes to traveller A when the toggle is down and to
 * traveller B when it is up, never both. Everything else, the run, the landing and the splice
 * editor, is the switch's with three points and the 3-way terminal numbering.
 */
public class ConduitThreeWayBlockEntity extends ConduitSwitchBlockEntity {
    private SwitchedWire toA, toB;
    private List<SplicePoint> threePoints;

    public ConduitThreeWayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(TERMINAL_COUNT_3);
        splices().buildCircuit(builder);
        float r = resistance("contact");
        toA = builder.connectSwitch(r, builder.terminalNode(TERMINAL_COMMON), builder.terminalNode(TERMINAL_TRAVELLER_A), !on);
        toB = builder.connectSwitch(r, builder.terminalNode(TERMINAL_COMMON), builder.terminalNode(TERMINAL_TRAVELLER_B), on);
    }

    @Override
    protected void applyContacts() {
        if(toA != null)
            toA.setState(!on);
        if(toB != null)
            toB.setState(on);
    }

    @Override
    public List<SplicePoint> points() {
        if(threePoints == null) {
            threePoints = List.of(
                    new SplicePoint(TERMINAL_COMMON, ConductorColors.hotLabel("conduit_switch.common", 0), ConductorColors.hotRgb(0)),
                    new SplicePoint(TERMINAL_TRAVELLER_A, ConductorColors.hotLabel("conduit_switch.traveller_a", 1), ConductorColors.hotRgb(1)),
                    new SplicePoint(TERMINAL_TRAVELLER_B, ConductorColors.hotLabel("conduit_switch.traveller_b", 2), ConductorColors.hotRgb(2)));
        }
        return threePoints;
    }

    @Override
    public boolean isPoint(int terminal) {
        return terminal == TERMINAL_COMMON || terminal == TERMINAL_TRAVELLER_A || terminal == TERMINAL_TRAVELLER_B;
    }

    @Override
    public int hubTerminal(int hub) {
        return hub == 0 ? TERMINAL_HUB_3 : TERMINAL_HUB2_3;
    }

    @Override
    public int hubAt(int terminal) {
        return terminal == TERMINAL_HUB_3 ? 0 : terminal == TERMINAL_HUB2_3 ? 1 : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return (hub == 0 ? CONDUCTOR_BASE_3 : CONDUCTOR2_BASE_3) + conductor;
    }

    @Override
    public int hubOf(int terminal) {
        if(terminal >= CONDUCTOR_BASE_3 && terminal < TERMINAL_HUB2_3)
            return 0;
        return terminal >= CONDUCTOR2_BASE_3 && terminal < TERMINAL_COUNT_3 ? 1 : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return terminal >= CONDUCTOR2_BASE_3 ? terminal - CONDUCTOR2_BASE_3 : terminal - CONDUCTOR_BASE_3;
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub == 0 ? SpliceSupport.runAt(this, TERMINAL_HUB_3) : hub == 1 ? SpliceSupport.runAt(this, TERMINAL_HUB2_3) : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.conduit_switch.title_3way").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(on ? "gui.conduit_switch.to_b" : "gui.conduit_switch.to_a").style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
