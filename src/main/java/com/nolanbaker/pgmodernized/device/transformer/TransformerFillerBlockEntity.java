package com.nolanbaker.pgmodernized.device.transformer;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.List;

/**
 * The electrical face of a filler cell: Power Grid asks the block entity at a position first, and
 * this one answers with its base's terminals moved into this cell and its base's circuit. A wire
 * clicked on a bushing in the cell above a tank therefore lands on the tank. Goggles looking at a
 * filler read the base as well.
 */
public class TransformerFillerBlockEntity extends BlockEntity implements IElectric, IHaveGoggleInformation {
    public TransformerFillerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private BlockPos basePos() {
        return TransformerFillerBlock.basePos(worldPosition, getBlockState());
    }

    @Nullable
    private TransformerBlock base() {
        if(level == null)
            return null;
        return level.getBlockState(basePos()).getBlock() instanceof TransformerBlock block ? block : null;
    }

    @Nullable
    private TransformerBlockEntity baseEntity() {
        return level != null && level.getBlockEntity(basePos()) instanceof TransformerBlockEntity be ? be : null;
    }

    @Override
    public int terminalCount() {
        var base = base();
        return base == null ? 0 : base.terminalCount();
    }

    @Override
    public @Nullable ITerminalPlacement terminal(BlockState state, int index) {
        var base = base();
        if(base == null || level == null)
            return null;
        var basePos = basePos();
        var placement = base.terminal(level.getBlockState(basePos), index);
        if(!(placement instanceof TerminalBoundingBox box))
            return placement;
        var offset = basePos.subtract(worldPosition);
        return box.offset(offset.getX(), offset.getY(), offset.getZ());
    }

    @Override
    public @Nullable ElectricBehaviour getBehaviour(Level world, BlockPos pos, BlockState state) {
        var base = base();
        if(base == null)
            return null;
        var basePos = basePos();
        return base.getBehaviour(world, basePos, world.getBlockState(basePos));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        var base = base();
        return base != null && base.accepts(wireStack);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var base = baseEntity();
        return base != null && base.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }
}
