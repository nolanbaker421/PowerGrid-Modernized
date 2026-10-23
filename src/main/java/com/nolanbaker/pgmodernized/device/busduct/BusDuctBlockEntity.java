package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.bus.IBusJoin;
import com.nolanbaker.pgmodernized.bus.IBusLinkHost;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.busduct.BusDuctBlock.BUS_COUNT;

/**
 * Four bus terminals and nothing between them: the conductors run along the duct and across to
 * the next block through hidden bus bars. Every second a segment links itself to whatever its axis
 * points at that can take a bus (the lower of two segments does the linking, so a pair makes one
 * bar per conductor, not two). Plug-ins link themselves to the segment they sit on.
 */
public class BusDuctBlockEntity extends ElectricBlockEntity implements IBusJoin, IBusLinkHost, IHaveGoggleInformation {
    public BusDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(BUS_COUNT);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    public Direction.Axis axis() {
        var state = getBlockState();
        return state.getBlock() instanceof BusDuctBlock ? BusDuctBlock.axis(state) : Direction.Axis.Y;
    }

    @Override
    public int busJoin(Direction side, int k) {
        return side.getAxis() == axis() && k >= 0 && k < BUS_COUNT ? k : -1;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        for(var side : Direction.values()) {
            if(side.getAxis() != axis())
                continue;
            var other = worldPosition.relative(side);
            if(level.getBlockEntity(other) instanceof BusDuctBlockEntity && worldPosition.asLong() > other.asLong())
                continue;
            BusLinks.join(level, worldPosition, this, side);
        }
    }

    @Override
    public boolean busLinkValid(int terminal, BlockPos other, int otherTerminal) {
        if(level == null)
            return false;
        var side = BusLinks.between(worldPosition, other);
        if(side == null)
            return false;
        if(side.getAxis() == axis())
            return BusLinks.joinValid(level, worldPosition, terminal, other, otherTerminal);
        return level.getBlockEntity(other) instanceof BusDuctPlugInBlockEntity plug && plug.tapValid(worldPosition, otherTerminal, terminal);
    }

    /** Volts on conductor k, or NaN when the duct is in no circuit. */
    public double voltage(int k) {
        var node = electricBehaviour == null ? null : electricBehaviour.getTerminal(k);
        return node == null ? Double.NaN : node.getVoltage();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.bus_duct.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        double neutral = voltage(3);
        for(int k = 0; k < 3; ++k) {
            double volts = voltage(k) - neutral;
            if(!Double.isFinite(volts))
                continue;
            Lang.builder().translate("gui.bus_duct.line", k + 1, String.format("%.0f", Math.abs(volts))).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        }
        return true;
    }
}
