package com.nolanbaker.pgmodernized.device.hmi;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * The screen's state: its layout, the controls cabinet it found over the Cat6, and the last value
 * of every widget's tag, refreshed a few times a second and sent to the client when it changes.
 * Presses come in from the block face or the screen and go to the cabinet as tag writes.
 */
public class HmiBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    public static final int PRESS = 0, MINUS = 1, PLUS = 2;
    private static final int PULSE_TICKS = 10, SEARCH_LIMIT = 256;

    private final JackSupport jack = new JackSupport(this, true);
    private HmiLayout layout = new HmiLayout();
    private CompoundTag layoutTag = layout.save();
    private int revision;
    @Nullable
    private BlockPos cabinet;
    private float[] values = new float[0];
    private final int[] pulses = new int[HmiLayout.MAX_WIDGETS];
    private boolean dirty;
    private int syncTimer, findTimer, readTimer;

    public HmiBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- what the screens read ----

    public HmiLayout layout() {
        return layout;
    }

    /** Counts up whenever the layout changes, so screens know to rebuild. */
    public int revision() {
        return revision;
    }

    public float[] values() {
        return values;
    }

    public float value(int index) {
        return index >= 0 && index < values.length ? values[index] : Float.NaN;
    }

    @Nullable
    public BlockPos cabinetPos() {
        return cabinet;
    }

    public boolean connected() {
        return cabinet != null;
    }

    @Nullable
    private ControlsCabinetBlockEntity cabinet() {
        return cabinet != null && level != null && level.isLoaded(cabinet) && level.getBlockEntity(cabinet) instanceof ControlsCabinetBlockEntity c ? c : null;
    }

    // ---- server side ----

    /** A new layout from the editor. */
    public void setLayout(CompoundTag tag) {
        layout = HmiLayout.load(tag);
        layoutTag = layout.save();
        ++revision;
        values = new float[0];
        Arrays.fill(pulses, 0);
        dirty = true;
        syncTimer = 0;
        setChanged();
    }

    /** A press on a widget: a button writes its tag, a setpoint steps it. */
    public void press(int index, int action, @Nullable Player player) {
        if(index < 0 || index >= layout.widgets.size())
            return;
        var widget = layout.widgets.get(index);
        var c = cabinet();
        if(c == null) {
            if(player != null)
                player.displayClientMessage(Lang.builder().translate("message.hmi.no_cabinet").style(ChatFormatting.RED).component(), true);
            return;
        }
        if(widget.tag.isEmpty())
            return;
        boolean ok;
        switch(widget.kind) {
            case BUTTON -> {
                if(widget.toggle) {
                    double now = c.plcRead(widget.tag);
                    ok = c.plcWrite(widget.tag, Double.isNaN(now) || now == 0 ? 1 : 0);
                } else {
                    ok = c.plcWrite(widget.tag, 1);
                    pulses[index] = PULSE_TICKS;
                }
            }
            case SETPOINT -> {
                double now = c.plcRead(widget.tag);
                if(Double.isNaN(now))
                    now = widget.min;
                double next = action == MINUS ? now - widget.step : action == PLUS ? now + widget.step : now;
                next = Math.max(widget.min, Math.min(widget.max, next));
                ok = c.plcWrite(widget.tag, next);
            }
            default -> {
                return;
            }
        }
        if(!ok && player != null)
            player.displayClientMessage(Lang.builder().translate("message.hmi.bad_tag", widget.tag).style(ChatFormatting.RED).component(), true);
        readTimer = 99;   // pick the new value up next tick
    }

    /** The nearest controls cabinet reachable over the jack's cables, or null. */
    @Nullable
    private BlockPos findCabinet() {
        if(level == null)
            return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<JackSupport>();
        queue.add(jack);
        visited.add(jack.pos());
        while(!queue.isEmpty() && visited.size() < SEARCH_LIMIT) {
            var at = queue.poll();
            if(at.owner() instanceof ControlsCabinetBlockEntity c) {
                double d = c.getBlockPos().distSqr(worldPosition);
                if(d < bestDistance) {
                    bestDistance = d;
                    best = c.getBlockPos();
                }
            }
            for(var cable : List.copyOf(at.cables())) {
                var wire = cable.asWireEntity();
                for(IWireEndpoint endpoint : new IWireEndpoint[] {wire.getEndpoint1(), wire.getEndpoint2()}) {
                    if(!(endpoint instanceof JackEndpoint end) || visited.contains(end.getPos()))
                        continue;
                    var other = end.jack(level);
                    if(other == null)
                        continue;
                    visited.add(end.getPos());
                    queue.add(other);
                }
            }
        }
        return best;
    }

    private void refreshValues() {
        var c = cabinet();
        var fresh = new float[layout.widgets.size()];
        for(int i = 0; i < fresh.length; ++i) {
            var widget = layout.widgets.get(i);
            fresh[i] = c == null || !widget.kind.usesTag() || widget.tag.isEmpty() ? Float.NaN : (float) c.plcRead(widget.tag);
        }
        if(!Arrays.equals(fresh, values)) {
            values = fresh;
            dirty = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide)
            return;
        if(++findTimer >= 40) {
            findTimer = 0;
            var found = findCabinet();
            if(found == null ? cabinet != null : !found.equals(cabinet)) {
                cabinet = found;
                dirty = true;
            }
        }
        var c = cabinet();
        for(int i = 0; i < pulses.length; ++i) {
            if(pulses[i] > 0 && --pulses[i] == 0 && c != null && i < layout.widgets.size())
                c.plcWrite(layout.widgets.get(i).tag, 0);
        }
        if(++readTimer >= 3) {
            readTimer = 0;
            refreshValues();
        }
        if(dirty && --syncTimer <= 0) {
            dirty = false;
            syncTimer = 2;
            sendData();
        }
    }

    // ---- jack ----

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return HmiBlock.jackPosition(getBlockState(), getBlockPos());
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
        tag.put("Layout", layoutTag.copy());
        if(clientPacket) {
            var bits = new int[values.length];
            for(int i = 0; i < bits.length; ++i)
                bits[i] = Float.floatToIntBits(values[i]);
            tag.putIntArray("Values", bits);
            if(cabinet != null)
                tag.putLong("Cabinet", cabinet.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        var newLayout = tag.getCompound("Layout");
        if(!newLayout.equals(layoutTag)) {
            layoutTag = newLayout.copy();
            layout = HmiLayout.load(newLayout);
            ++revision;
        }
        if(clientPacket) {
            var bits = tag.getIntArray("Values");
            values = new float[bits.length];
            for(int i = 0; i < bits.length; ++i)
                values[i] = Float.intBitsToFloat(bits[i]);
            cabinet = tag.contains("Cabinet") ? BlockPos.of(tag.getLong("Cabinet")) : null;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.hmi.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(cabinet == null)
            Lang.builder().translate("gui.hmi.unconnected").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.hmi.connected", cabinet.getX(), cabinet.getY(), cabinet.getZ()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.hmi.widgets", layout.widgets.size()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
