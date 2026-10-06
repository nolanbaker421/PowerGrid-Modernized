package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.DIAL_STEP;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.PULSE_TICKS;

/**
 * Four door cells away from the cabinet. The station keeps its own devices, their states and
 * their wiring (a module channel index each, slot * 8 + channel); the cabinet it found over the
 * Cat6 reads its inputs and dials every tick and the station reads the cabinet's outputs for its
 * lights and displays. A station number, set in its screen, names its cells to the PLC as
 * B1.1 to B1.4.
 */
public class StationBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    public static final int CELLS = 4;

    private final JackSupport jack = new JackSupport(this, true);
    private final PanelDevice[] panel = new PanelDevice[CELLS];
    private final int[] wire = new int[CELLS];
    private final int[] color = new int[CELLS];
    private final boolean[] latched = new boolean[CELLS];
    private final int[] selector = new int[CELLS];
    private final int[] pulse = new int[CELLS];
    private final int[] display = new int[CELLS];
    private final int[] dial = new int[CELLS];
    private int station = 1;
    @Nullable
    private BlockPos cabinet;
    private boolean dirty;
    private int syncTimer, findTimer;

    public StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        java.util.Arrays.fill(wire, -1);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- what the screen and the cabinet read ----

    public int station() {
        return station;
    }

    @Nullable
    public PanelDevice device(int cell) {
        return cell >= 0 && cell < CELLS ? panel[cell] : null;
    }

    public int wireOf(int cell) {
        return cell >= 0 && cell < CELLS ? wire[cell] : -1;
    }

    public int colorOf(int cell) {
        return cell >= 0 && cell < CELLS ? color[cell] : 0;
    }

    @Nullable
    public BlockPos cabinetPos() {
        return cabinet;
    }

    @Nullable
    public ControlsCabinetBlockEntity cabinet() {
        return cabinet != null && level != null && level.isLoaded(cabinet) && level.getBlockEntity(cabinet) instanceof ControlsCabinetBlockEntity c ? c : null;
    }

    private boolean powered() {
        var c = cabinet();
        return c != null && c.isPowered();
    }

    /** What a device is doing: 0 or 1 for buttons and lights, the position for a selector, the value for a display, percent for a dial. */
    public int deviceState(int cell) {
        var device = device(cell);
        if(device == null)
            return 0;
        return switch(device) {
            case E_STOP, TOGGLE -> latched[cell] ? 1 : 0;
            case MOMENTARY -> pulse[cell] > 0 ? 1 : 0;
            case SELECTOR -> selector[cell];
            case LED -> {
                var c = cabinet();
                yield c != null && c.isPowered() && wire[cell] >= 0 && c.output(wire[cell] / CHANNELS, wire[cell] % CHANNELS) ? 1 : 0;
            }
            case DISPLAY -> display[cell];
            case DIAL -> dial[cell];
        };
    }

    /** Whether any input device here closes the given channel index. */
    public boolean drives(int channel) {
        for(int cell = 0; cell < CELLS; ++cell) {
            var device = panel[cell];
            if(device == null || !device.isInput() || wire[cell] < 0)
                continue;
            boolean closes = switch(device) {
                case E_STOP, TOGGLE -> wire[cell] == channel && latched[cell];
                case MOMENTARY -> wire[cell] == channel && pulse[cell] > 0;
                case SELECTOR -> (selector[cell] == 0 && wire[cell] == channel) || (selector[cell] == 2 && wire[cell] + 1 == channel && (wire[cell] % CHANNELS) < CHANNELS - 1);
                default -> false;
            };
            if(closes)
                return true;
        }
        return false;
    }

    /** A dial wired to that channel index, 0 to 100, or -1 when none. */
    public int dialFor(int target) {
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] == PanelDevice.DIAL && wire[cell] == target)
                return dial[cell];
        return -1;
    }

    /** Whether any E-stop here is pressed. */
    public boolean isEStopped() {
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] == PanelDevice.E_STOP && latched[cell])
                return true;
        return false;
    }

    // ---- server side ----

    public void operate(int cell, Player player) {
        var device = device(cell);
        if(device == null || level == null)
            return;
        switch(device) {
            case E_STOP -> {
                latched[cell] = !latched[cell];
                level.playSound(null, worldPosition, latched[cell] ? SoundEvents.ANVIL_LAND : SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, latched[cell] ? 1.6f : 0.8f);
            }
            case TOGGLE -> {
                latched[cell] = !latched[cell];
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, latched[cell] ? 0.7f : 0.6f);
            }
            case MOMENTARY -> {
                pulse[cell] = PULSE_TICKS;
                level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.2f);
            }
            case SELECTOR -> {
                selector[cell] = (selector[cell] + 1) % 3;
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.9f);
            }
            case DIAL -> {
                dial[cell] = dial[cell] >= 100 ? 0 : Math.min(100, dial[cell] + DIAL_STEP);
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25f, 1.2f + dial[cell] / 200f);
            }
            default -> {
                return;
            }
        }
        dirty = true;
        syncTimer = 2;
    }

    public boolean installDevice(int cell, PanelDevice device, Player player) {
        if(cell < 0 || cell >= CELLS || panel[cell] != null) {
            player.displayClientMessage(Lang.builder().translate("message.controls.cell_used").style(ChatFormatting.RED).component(), true);
            return false;
        }
        panel[cell] = device;
        color[cell] = 0;
        latched[cell] = false;
        selector[cell] = 1;
        pulse[cell] = 0;
        display[cell] = 0;
        dial[cell] = 0;
        var c = cabinet();
        wire[cell] = c == null ? -1 : c.firstFreeTarget(device);
        dirty = true;
        syncTimer = 2;
        setChanged();
        return true;
    }

    public void removeDevice(int cell, @Nullable Player player) {
        if(cell < 0 || cell >= CELLS || panel[cell] == null || level == null)
            return;
        drop(ModItems.PANEL_DEVICES.get(panel[cell]).asStack());
        panel[cell] = null;
        wire[cell] = -1;
        latched[cell] = false;
        pulse[cell] = 0;
        dirty = true;
        syncTimer = 2;
        setChanged();
    }

    /** From the screen: wire a cell to a channel index of the cabinet's modules, or -1. */
    public void setWire(int cell, int target) {
        var device = device(cell);
        var c = cabinet();
        if(device == null || (target >= 0 && (c == null || !c.canWireDevice(device, target))))
            return;
        wire[cell] = target;
        dirty = true;
        syncTimer = 2;
        setChanged();
    }

    public void setColor(int cell, int index) {
        if(cell < 0 || cell >= CELLS)
            return;
        color[cell] = Math.floorMod(index, ControlsCabinetBlockEntity.LED_COLORS.length);
        dirty = true;
        syncTimer = 2;
        setChanged();
    }

    public void setStation(int number) {
        station = Math.max(1, Math.min(99, number));
        dirty = true;
        syncTimer = 2;
        setChanged();
    }

    /** Everything fitted drops when the block breaks. */
    public void dropAll() {
        for(int cell = 0; cell < CELLS; ++cell)
            if(panel[cell] != null)
                drop(ModItems.PANEL_DEVICES.get(panel[cell]).asStack());
    }

    private void drop(ItemStack stack) {
        if(level == null)
            return;
        var at = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(StationBlock.facing(getBlockState()).getNormal()).scale(0.5));
        Containers.dropItemStack(level, at.x, at.y, at.z, stack);
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide)
            return;
        for(int cell = 0; cell < CELLS; ++cell) {
            if(pulse[cell] > 0 && --pulse[cell] == 0)
                dirty = true;
        }
        if(++findTimer >= 40) {
            findTimer = 0;
            var found = DriveLink.nearest(level, jack, ControlsCabinetBlockEntity.class, worldPosition);
            if(found == null ? cabinet != null : !found.equals(cabinet)) {
                cabinet = found;
                dirty = true;
            }
        }
        // Displays wired to an analog output follow it.
        var c = cabinet();
        for(int cell = 0; cell < CELLS; ++cell) {
            if(panel[cell] != PanelDevice.DISPLAY || wire[cell] < 0)
                continue;
            int shown = c == null || !c.isPowered() ? 0 : Math.max(-999, Math.min(9999, Math.round(c.analogOut(wire[cell] / CHANNELS, wire[cell] % CHANNELS))));
            if(display[cell] != shown) {
                display[cell] = shown;
                dirty = true;
            }
        }
        if(dirty && --syncTimer <= 0) {
            dirty = false;
            syncTimer = 2;
            sendData();
        }
    }

    // ---- two ports, one node ----

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public int portCount() {
        return 2;
    }

    @Override
    public int portAt(Vec3 localHit) {
        return StationBlock.portAt(getBlockState(), localHit);
    }

    @Override
    public Vec3 jackPosition(int port) {
        return StationBlock.jackPosition(getBlockState(), getBlockPos(), port);
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

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        var panelTag = new int[CELLS];
        for(int i = 0; i < CELLS; ++i)
            panelTag[i] = panel[i] == null ? -1 : panel[i].ordinal();
        tag.putIntArray("Panel", panelTag);
        tag.putIntArray("Wire", wire);
        tag.putIntArray("Color", color);
        var latchedTag = new byte[CELLS];
        for(int i = 0; i < CELLS; ++i)
            latchedTag[i] = (byte) (latched[i] ? 1 : 0);
        tag.putByteArray("Latched", latchedTag);
        tag.putIntArray("Selector", selector);
        tag.putIntArray("Display", display);
        tag.putIntArray("Dial", dial);
        tag.putInt("Station", station);
        if(clientPacket) {
            tag.putIntArray("Pulse", pulse);
            if(cabinet != null)
                tag.putLong("Cabinet", cabinet.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        var panelTag = tag.getIntArray("Panel");
        for(int i = 0; i < CELLS; ++i)
            panel[i] = i < panelTag.length ? PanelDevice.fromOrdinal(panelTag[i]) : null;
        fill(wire, tag.getIntArray("Wire"), -1);
        fill(color, tag.getIntArray("Color"), 0);
        var latchedTag = tag.getByteArray("Latched");
        for(int i = 0; i < CELLS; ++i)
            latched[i] = i < latchedTag.length && latchedTag[i] != 0;
        fill(selector, tag.getIntArray("Selector"), 1);
        fill(display, tag.getIntArray("Display"), 0);
        fill(dial, tag.getIntArray("Dial"), 0);
        station = Math.max(1, tag.getInt("Station"));
        if(clientPacket) {
            fill(pulse, tag.getIntArray("Pulse"), 0);
            cabinet = tag.contains("Cabinet") ? BlockPos.of(tag.getLong("Cabinet")) : null;
        }
    }

    private static void fill(int[] values, int[] in, int fallback) {
        for(int i = 0; i < values.length; ++i)
            values[i] = i < in.length ? in[i] : fallback;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.station.title", station).style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(cabinet == null)
            Lang.builder().translate("gui.station.unconnected").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.station.connected", cabinet.getX(), cabinet.getY(), cabinet.getZ()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        int fitted = 0;
        for(var d : panel)
            if(d != null)
                ++fitted;
        Lang.builder().translate("gui.station.devices", fitted).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
