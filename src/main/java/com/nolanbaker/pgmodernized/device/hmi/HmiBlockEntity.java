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
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * One panel of a screen. The screen's origin panel holds the layout, the controls cabinet it
 * found over any member's Cat6, and the last value of every widget's tag, refreshed a few times
 * a second and sent to the client when it changes; the other panels only know their origin and
 * forward to it. Presses come in from a block face or the screen and go to the cabinet as tag
 * writes.
 */
public class HmiBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    public static final int PRESS = 0, MINUS = 1, PLUS = 2;
    /** Grid cells across one panel. */
    public static final int CELLS = 8;
    private static final int PULSE_TICKS = 10, SEARCH_LIMIT = 256;

    private final JackSupport jack = new JackSupport(this, true);
    // The screen this panel is part of.
    private BlockPos origin;
    private int width = 1, height = 1;
    // Only meaningful on the origin.
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
        origin = pos;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- the screen ----

    public BlockPos origin() {
        return origin;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isOrigin() {
        return origin.equals(worldPosition);
    }

    /** The panel that holds this screen's state: this one, or the origin, or null while the origin is missing. */
    @Nullable
    public HmiBlockEntity head() {
        if(isOrigin())
            return this;
        return level != null && level.getBlockEntity(origin) instanceof HmiBlockEntity be && be.isOrigin() ? be : null;
    }

    /** Where this panel sits in its screen: {columns from the left, rows from the bottom}. */
    public int[] offset() {
        return HmiGroups.offsetOf(HmiBlock.facing(getBlockState()), origin, worldPosition);
    }

    /** Server side, from the group logic. The origin's layout grows or shrinks with the screen. */
    void setGroup(BlockPos origin, int width, int height) {
        boolean wasOrigin = isOrigin();
        this.origin = origin;
        this.width = width;
        this.height = height;
        if(isOrigin()) {
            if(layout.resize(width * CELLS, height * CELLS))
                ++revision;
            layoutTag = layout.save();
        } else if(wasOrigin) {
            // Layout handed over (or lost) by the group logic; nothing to keep here.
            layout = new HmiLayout();
            layoutTag = layout.save();
            values = new float[0];
            cabinet = null;
        }
        Arrays.fill(pulses, 0);
        dirty = true;
        syncTimer = 0;
        setChanged();
    }

    /**
     * Takes the widgets of a screen that merged into this one, moved to where that screen now
     * sits inside the merged rectangle; this panel is the merged screen's origin.
     */
    void adoptWidgets(HmiBlockEntity absorbedOrigin, HmiGroups.Rect absorbed, HmiGroups.Rect merged, Direction facing) {
        var o = HmiGroups.offsetOf(facing, merged.origin(), absorbed.origin());
        int colCells = o[0] * CELLS;
        int rowCells = (merged.height() - o[1] - absorbed.height()) * CELLS;
        // My own widgets keep their place from the top-left of my old rectangle, which may now sit
        // higher up: rows count from the top, so they move down by the rows gained above them.
        var mine = HmiGroups.offsetOf(facing, merged.origin(), origin);
        int myRowCells = (merged.height() - mine[1] - height) * CELLS;
        int myColCells = mine[0] * CELLS;
        layout.resize(merged.width() * CELLS, merged.height() * CELLS);
        if(myRowCells != 0 || myColCells != 0) {
            for(var w : layout.widgets) {
                w.col += myColCells;
                w.row += myRowCells;
            }
        }
        for(var w : absorbedOrigin.layout.widgets) {
            var moved = w.copy();
            moved.col += colCells;
            moved.row += rowCells;
            if(layout.free(moved, null) && layout.widgets.size() < HmiLayout.MAX_WIDGETS)
                layout.widgets.add(moved);
        }
        layout.clampAll();
        layoutTag = layout.save();
        ++revision;
        dirty = true;
    }

    // ---- what the screens read (always from the origin) ----

    public HmiLayout layout() {
        var head = head();
        return head == null || head == this ? layout : head.layout;
    }

    /** Counts up whenever the layout changes, so screens know to rebuild. */
    public int revision() {
        var head = head();
        return head == null || head == this ? revision : head.revision;
    }

    public float[] values() {
        var head = head();
        return head == null || head == this ? values : head.values;
    }

    public float value(int index) {
        var v = values();
        return index >= 0 && index < v.length ? v[index] : Float.NaN;
    }

    @Nullable
    public BlockPos cabinetPos() {
        var head = head();
        return head == null || head == this ? cabinet : head.cabinet;
    }

    public boolean connected() {
        return cabinetPos() != null;
    }

    @Nullable
    private ControlsCabinetBlockEntity cabinet() {
        return cabinet != null && level != null && level.isLoaded(cabinet) && level.getBlockEntity(cabinet) instanceof ControlsCabinetBlockEntity c ? c : null;
    }

    // ---- server side ----

    /** A new layout from the editor; lands on the origin. */
    public void setLayout(CompoundTag tag) {
        var head = head();
        if(head != null && head != this) {
            head.setLayout(tag);
            return;
        }
        layout = HmiLayout.load(tag);
        layout.resize(width * CELLS, height * CELLS);
        layoutTag = layout.save();
        ++revision;
        values = new float[0];
        Arrays.fill(pulses, 0);
        dirty = true;
        syncTimer = 0;
        setChanged();
    }

    /** A press on a widget: a button writes its tag, a setpoint steps it. Lands on the origin. */
    public void press(int index, int action, @Nullable Player player) {
        var head = head();
        if(head != null && head != this) {
            head.press(index, action, player);
            return;
        }
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

    /** The jacks of every panel in this screen. */
    private List<JackSupport> memberJacks() {
        var jacks = new ArrayList<JackSupport>();
        if(level == null)
            return jacks;
        var right = HmiGroups.right(HmiBlock.facing(getBlockState()));
        for(int dy = 0; dy < height; ++dy)
            for(int dx = 0; dx < width; ++dx)
                if(level.getBlockEntity(origin.relative(right, dx).above(dy)) instanceof HmiBlockEntity be)
                    jacks.add(be.jack);
        return jacks;
    }

    /** The nearest controls cabinet reachable over any member's cables, or null. */
    @Nullable
    private BlockPos findCabinet() {
        if(level == null)
            return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<JackSupport>();
        for(var j : memberJacks()) {
            queue.add(j);
            visited.add(j.pos());
        }
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
        if(!isOrigin()) {
            if(dirty && --syncTimer <= 0) {
                dirty = false;
                syncTimer = 2;
                sendData();
            }
            return;
        }
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

    /** The origin draws the whole screen, which may reach blocks to the viewer's right and up. */
    @Override
    public AABB getRenderBoundingBox() {
        if(!isOrigin())
            return new AABB(worldPosition);
        var right = HmiGroups.right(HmiBlock.facing(getBlockState()));
        var far = worldPosition.relative(right, width - 1).above(height - 1);
        return new AABB(worldPosition).minmax(new AABB(far));
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
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        if(isOrigin())
            tag.put("Layout", layoutTag.copy());
        if(clientPacket && isOrigin()) {
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
        origin = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : worldPosition;
        width = Math.max(1, tag.getInt("Width"));
        height = Math.max(1, tag.getInt("Height"));
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
        Lang.builder().translate("gui.hmi.size", width, height).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        var at = cabinetPos();
        if(at == null)
            Lang.builder().translate("gui.hmi.unconnected").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.hmi.connected", at.getX(), at.getY(), at.getZ()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.hmi.widgets", layout().widgets.size()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
