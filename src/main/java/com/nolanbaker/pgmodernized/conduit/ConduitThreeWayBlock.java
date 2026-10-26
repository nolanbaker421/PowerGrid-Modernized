package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * A 3-way switch: a common and two travellers. Two of them at the ends of a run of two travellers
 * light a hall from either door, the common of one on the line and the common of the other on the
 * load. The first three wires pulled through its run land on common, traveller A and traveller B,
 * so pull them in the same order at both ends. Same body, knockouts and rotation as the switch.
 */
public class ConduitThreeWayBlock extends ConduitSwitchBlock {
    public static final int TERMINAL_COMMON = 0, TERMINAL_TRAVELLER_A = 1, TERMINAL_TRAVELLER_B = 2, TERMINAL_HUB_3 = 3, CONDUCTOR_BASE_3 = 4;
    public static final int TERMINAL_HUB2_3 = CONDUCTOR_BASE_3 + ConductorColors.COUNT, CONDUCTOR2_BASE_3 = TERMINAL_HUB2_3 + 1;
    public static final int TERMINAL_COUNT_3 = CONDUCTOR2_BASE_3 + ConductorColors.COUNT;

    public ConduitThreeWayBlock(Properties properties) {
        super(properties);
        setTerminalCollection(com.nolanbaker.pgmodernized.util.Rotation4Frame.collection(this, threeWayTerminals(), Shapes.or(box(BODY), box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(HUB)), box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(HUB2)), box(PLATE))));
    }

    private static TerminalBoundingBox[] threeWayTerminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT_3];
        terminals[TERMINAL_COMMON] = terminal(ConductorColors.hotLabel("conduit_switch.common", 0), HIDDEN).withColor(ConductorColors.hotRgb(0));
        terminals[TERMINAL_TRAVELLER_A] = terminal(ConductorColors.hotLabel("conduit_switch.traveller_a", 1), HIDDEN).withColor(ConductorColors.hotRgb(1));
        terminals[TERMINAL_TRAVELLER_B] = terminal(ConductorColors.hotLabel("conduit_switch.traveller_b", 2), HIDDEN).withColor(ConductorColors.hotRgb(2));
        terminals[TERMINAL_HUB_3] = terminal(name("conduit_socket.hub", ChatFormatting.AQUA), HUB).withColor(ConduitSize.ONE.bandColor());
        for(int k = 0; k < ConductorColors.COUNT; ++k)
            terminals[CONDUCTOR_BASE_3 + k] = terminal(ConductorColors.name(k), HIDDEN).withColor(ConductorColors.rgb(k));
        terminals[TERMINAL_HUB2_3] = terminal(name("conduit_socket.hub2", ChatFormatting.AQUA), HUB2).withColor(ConduitSize.ONE.bandColor());
        for(int k = 0; k < ConductorColors.COUNT; ++k)
            terminals[CONDUCTOR2_BASE_3 + k] = terminal(ConductorColors.name(k), HIDDEN).withColor(ConductorColors.rgb(k));
        return terminals;
    }

    @Override
    protected int[] hubTerminals() {
        return new int[] {TERMINAL_HUB_3, TERMINAL_HUB2_3};
    }

    @Override
    public Class<ConduitSwitchBlockEntity> getBlockEntityClass() {
        return ConduitSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ConduitSwitchBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONDUIT_SWITCH_3WAY.get();
    }
}
