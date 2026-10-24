package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * One jack, a port number and an output level. The controls cabinet finds every port on its Cat6
 * and writes the level through the tag {@code RS<number>}; the level stays until it is written
 * again, so a program that stops leaves the signal where it was.
 */
public class RedstonePortBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    public static final int MAX_NUMBER = 99;

    private final JackSupport jack = new JackSupport(this, true);
    private int number = 1;
    private int output;
    private int input;
    @Nullable
    private BlockPos cabinet;
    private int findTimer;

    public RedstonePortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public int number() {
        return number;
    }

    /** The redstone level put out, 0..15. */
    public int output() {
        return output;
    }

    /** The strongest redstone signal reaching the port from a neighbour, 0..15, read as tag RI<n>. */
    public int input() {
        return input;
    }

    /** Server side: sets the level, clamped to 0..15, and wakes the neighbours when it changes. */
    public void setOutput(int level) {
        level = Math.max(0, Math.min(15, level));
        if(level == output)
            return;
        output = level;
        if(this.level != null) {
            var state = getBlockState();
            if(state.getBlock() instanceof RedstonePortBlock && state.getValue(RedstonePortBlock.LIT) != (output > 0))
                this.level.setBlock(worldPosition, state.setValue(RedstonePortBlock.LIT, output > 0), Block.UPDATE_ALL);
            RedstonePortBlock.notifyOutput(this.level, worldPosition, getBlockState());
        }
        setChanged();
        sendData();
    }

    /** Server side: counts the port number up or down, wrapping at 1 and {@link #MAX_NUMBER}. */
    public void renumber(Player player, int step) {
        number = ((number - 1 + step + MAX_NUMBER) % MAX_NUMBER) + 1;
        player.displayClientMessage(Lang.builder().translate("message.redstone_port.number", number).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide)
            return;
        // What the neighbours put on the port. A port that also puts out a level reads its own signal back through
        // anything it powers, so an input port is best left at output 0.
        int now = level.getBestNeighborSignal(worldPosition);
        if(now != input) {
            input = now;
            setChanged();
        }
        if(++findTimer >= 40) {
            findTimer = 0;
            var found = DriveLink.nearest(level, jack, ControlsCabinetBlockEntity.class, worldPosition);
            if(found == null ? cabinet != null : !found.equals(cabinet)) {
                cabinet = found;
                setChanged();
                sendData();
            }
        }
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return RedstonePortBlock.jackPosition(getBlockState(), getBlockPos());
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Number", number);
        tag.putInt("Level", output);
        if(cabinet != null)
            tag.putLong("Cabinet", cabinet.asLong());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        number = Math.max(1, Math.min(MAX_NUMBER, tag.getInt("Number")));
        output = Math.max(0, Math.min(15, tag.getInt("Level")));
        cabinet = tag.contains("Cabinet") ? BlockPos.of(tag.getLong("Cabinet")) : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.redstone_port.title", number).style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.redstone_port.level", output).style(output > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.redstone_port.input", input).style(input > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        if(cabinet == null)
            Lang.builder().translate("gui.redstone_port.unconnected").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.redstone_port.connected", cabinet.getX(), cabinet.getY(), cabinet.getZ()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.redstone_port.tag", number).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
