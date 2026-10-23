package com.nolanbaker.pgmodernized.device.gear;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.bus.IBusJoin;
import com.nolanbaker.pgmodernized.bus.IBusLinkHost;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import com.nolanbaker.pgmodernized.device.breaker.BusBarItem;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nolanbaker.pgmodernized.device.gear.GearLayout.*;

/**
 * One cell of custom switchgear: nine slots, each holding a component that offers conductors on
 * some of its four sides. Two slots connect where both offer the same conductor across their
 * shared edge, so a layout is wired by placing things next to each other, like real draw-out
 * gear. Ports on the cell's edges are terminals that hidden bus bars join to the matching ports
 * of the next cell facing the same way (and a bus duct standing on the cell joins the middle
 * port of its top edge), so a lineup of any shape is one bus. Lug blocks are the splice points
 * for the knockouts and take hanging wire; everything else is reached through them.
 * <p>
 * Circuit: every connected edge between two slots is one node per conductor; a bar strings its
 * live ports together, a lug block ties its ports to its lugs, and a switched component puts a
 * switch per phase between its line side and its load side. Breakers trip on the panel's curve,
 * fuses blow on it and are then scrap, switches follow their handle, contactors the cell's
 * redstone signal, a starter all three of those at once, and a metering unit passes everything
 * through a shunt per phase. Bars carry 2000 A and burn out past it, like the hidden bus bars.
 */
public class GearCellBlockEntity extends ElectricBlockEntity implements ISpliceHost, IBusJoin, IBusLinkHost, IHaveGoggleInformation {
    private static final float BUS_R = 0.00005f;
    /** A metering unit's shunt per phase and its voltage sense to the neutral, ohms. */
    private static final float SHUNT = 0.001f, SENSE = 1_000_000f;

    /** A metering unit's wires, made in buildCircuit. */
    private static final class Meter {
        final ElectricWire[] shunts = new ElectricWire[3], senses = new ElectricWire[3];
        final AcReadings.Filter[] readings = {new AcReadings.Filter(), new AcReadings.Filter(), new AcReadings.Filter()};
    }

    private GearSlot[] slots;
    private SwitchedWire[][] poles;
    private ElectricWire[][] bars;
    private Meter[] meters;
    /** Slots whose bars burned out since the last packet, for the client's sparks. */
    private int burned;
    private GearRatingBehaviour[] ratings;
    private SpliceSupport splices;
    private List<SplicePoint> points;
    private boolean powered;
    private boolean needsRebuild;

    public GearCellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureInit();
        setLazyTickRate(10);
    }

    private void ensureInit() {
        if(slots == null) {
            slots = new GearSlot[SLOTS];
            for(int i = 0; i < SLOTS; ++i)
                slots[i] = new GearSlot();
            poles = new SwitchedWire[SLOTS][];
            bars = new ElectricWire[SLOTS][];
            meters = new Meter[SLOTS];
        }
    }

    public GearSlot slot(int slot) {
        ensureInit();
        return slots[slot];
    }

    public Direction facing() {
        var state = getBlockState();
        return state.getBlock() instanceof GearCellBlock ? GearCellBlock.facing(state) : Direction.NORTH;
    }

    public boolean powered() {
        return powered;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ensureInit();
        ratings = new GearRatingBehaviour[SLOTS];
        for(int slot = 0; slot < SLOTS; ++slot) {
            final int s = slot;
            var behaviour = new GearRatingBehaviour(this, slot);
            behaviour.withCallback(value -> onRating(s, value));
            ratings[slot] = behaviour;
            behaviours.add(behaviour);
        }
        super.addBehaviours(behaviours);
    }

    private void onRating(int slot, int value) {
        var breaker = slots[slot].breaker;
        if(!breaker.isBreaker())
            return;
        int snapped = breaker.frame().clamp(value);
        if(snapped != value)
            ratings[slot].mirror(snapped);
        if(snapped == breaker.rating())
            return;
        breaker.setRating(snapped);
        notifyUpdate();
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureInit();
        builder.setTerminalCount(TERMINAL_COUNT);
        splices().buildCircuit(builder);
        var shared = new HashMap<Integer, IElectricNode>();
        for(int s = 0; s < SLOTS; ++s) {
            poles[s] = null;
            bars[s] = null;
            meters[s] = null;
            var slot = slots[s];
            var kind = slot.kind();
            if(kind == null)
                continue;
            if(kind.bar()) {
                var wires = new ArrayList<ElectricWire>();
                for(int k = 0; k < CONDUCTORS; ++k) {
                    if((kind.conductors() & (1 << k)) == 0)
                        continue;
                    var ports = new ArrayList<IElectricNode>();
                    for(var side : GearSide.values()) {
                        if(slot.carries(side, k) && partnerCarries(s, side, k))
                            ports.add(node(builder, shared, s, side, k));
                    }
                    for(int i = 1; i < ports.size(); ++i)
                        wires.add(builder.connect(BUS_R, ports.get(i - 1), ports.get(i)));
                }
                bars[s] = wires.toArray(new ElectricWire[0]);
            } else if(kind == GearKind.METER) {
                // The neutral straight through; each phase through a shunt, with a sense branch to the neutral for its voltage.
                var meter = new Meter();
                meters[s] = meter;
                var neutral = node(builder, shared, s, slot.lineSide(), 3);
                builder.connect(BUS_R, neutral, node(builder, shared, s, slot.loadSide(), 3));
                for(int k = 0; k < 3; ++k) {
                    var in = node(builder, shared, s, slot.lineSide(), k);
                    var out = node(builder, shared, s, slot.loadSide(), k);
                    meter.senses[k] = ForkHooks.get().phaseSense(builder, SENSE, in, neutral);
                    meter.shunts[k] = ForkHooks.get().shunt(builder, SHUNT, meter.senses[k], in, out);
                }
            } else if(kind == GearKind.LUGS) {
                for(int k = 0; k < CONDUCTORS; ++k) {
                    var lug = builder.terminalNode(lug(s, k));
                    for(var side : GearSide.values()) {
                        if(slot.carries(side, k) && partnerCarries(s, side, k))
                            builder.connect(BUS_R, lug, node(builder, shared, s, side, k));
                    }
                }
            } else {
                poles[s] = new SwitchedWire[3];
                for(int k = 0; k < 3; ++k) {
                    poles[s][k] = builder.connectSwitch(resistance("pole"),
                            node(builder, shared, s, slot.lineSide(), k), node(builder, shared, s, slot.loadSide(), k), slot.closed());
                }
            }
        }
    }

    /** Whether the far side of a slot's port offers the conductor: the slot beside it, or on the cell's edge anything a link may bring. */
    private boolean partnerCarries(int slot, GearSide side, int k) {
        int n = neighbour(slot, side);
        return n < 0 || slots[n].carries(side.opposite(), k);
    }

    /** The node of a slot's port: a terminal on the cell's edge, else the node shared with the slot beside it. */
    private IElectricNode node(CircuitBuilder builder, Map<Integer, IElectricNode> shared, int slot, GearSide side, int k) {
        int n = neighbour(slot, side);
        if(n < 0)
            return builder.terminalNode(edge(side, edgeIndexOf(slot, side), k));
        int owner = side == GearSide.RIGHT || side == GearSide.DOWN ? slot : n;
        var ownerSide = side == GearSide.RIGHT || side == GearSide.DOWN ? side : side.opposite();
        int key = (owner * 4 + ownerSide.ordinal()) * CONDUCTORS + k;
        return shared.computeIfAbsent(key, ignored -> builder.addInternalNode());
    }

    private void applyWireStates() {
        for(int s = 0; s < SLOTS; ++s) {
            if(poles[s] == null)
                continue;
            for(var pole : poles[s])
                if(pole != null)
                    pole.setState(slots[s].closed());
        }
    }

    /** Components went in or out: the wiring is laid again around them. */
    private void rebuild() {
        points = null;
        if(level == null || level.isClientSide) {
            needsRebuild = level == null;
            return;
        }
        if(electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        applyWireStates();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if(needsRebuild && level != null && !level.isClientSide) {
            needsRebuild = false;
            rebuild();
        }
    }

    private float poleCurrent(int s) {
        return maxCurrent(poles[s]);
    }

    private static float maxCurrent(ElectricWire[] wires) {
        float current = 0;
        for(var wire : wires) {
            if(wire == null || !wire.isConverged())
                continue;
            float amps = (float) Math.abs(wire.current());
            if(Float.isFinite(amps))
                current = Math.max(current, amps);
        }
        return current;
    }

    private boolean converged(int s) {
        return converged(poles[s]);
    }

    private static boolean converged(ElectricWire[] wires) {
        for(var wire : wires)
            if(wire != null && wire.isConverged())
                return true;
        return false;
    }

    private void open(int s) {
        for(var pole : poles[s])
            if(pole != null)
                pole.setState(false);
    }

    @Override
    public void electricalTick() {
        boolean changed = false, burnt = false;
        for(int s = 0; s < SLOTS; ++s) {
            var slot = slots[s];
            var kind = slot.kind();
            if(kind == null)
                continue;
            if(kind.bar()) {
                if(bars[s] == null || !converged(bars[s]))
                    continue;
                if(slot.tickBar(maxCurrent(bars[s]))) {
                    burn(s);
                    burnt = true;
                }
                continue;
            }
            if(kind == GearKind.METER) {
                var meter = meters[s];
                if(meter == null)
                    continue;
                for(int k = 0; k < 3; ++k) {
                    var shunt = meter.shunts[k];
                    var sense = meter.senses[k];
                    if(shunt == null || sense == null || !shunt.isConverged())
                        continue;
                    var reading = meter.readings[k];
                    reading.sample(sense, shunt, ForkHooks.get().drainRealPower(shunt));
                    slot.setMeter(k, (float) reading.rmsVoltage(), (float) reading.rmsCurrent(), (float) reading.realPower());
                }
                continue;
            }
            if(poles[s] == null)
                continue;
            switch(kind) {
                case BREAKER -> {
                    var breaker = slot.breaker;
                    if(!breaker.isBreaker())
                        continue;
                    if(breaker.state() != BreakerState.ON) {
                        breaker.tickOpen();
                        continue;
                    }
                    if(!converged(s))
                        continue;
                    if(breaker.tickClosed(poleCurrent(s))) {
                        open(s);
                        if(level != null && !level.isClientSide)
                            ModdedSoundEvents.BREAKER_OFF.playOnServer(level, worldPosition);
                        changed = true;
                    }
                }
                case FUSE -> {
                    if(!converged(s))
                        continue;
                    if(slot.tickFuse(poleCurrent(s))) {
                        open(s);
                        if(level != null && !level.isClientSide)
                            ModdedSoundEvents.FUSE_POPS.playOnServer(level, worldPosition);
                        changed = true;
                    }
                }
                case STARTER -> {
                    if(!slot.closed()) {
                        slot.coolStarter();
                        continue;
                    }
                    if(!converged(s))
                        continue;
                    int trip = slot.tickStarter(poleCurrent(s));
                    if(trip != 0) {
                        open(s);
                        if(level != null && !level.isClientSide)
                            (trip == 1 ? ModdedSoundEvents.BREAKER_OFF : ModdedSoundEvents.CONTACTOR_OFF).playOnServer(level, worldPosition);
                        changed = true;
                    }
                }
                default -> {
                    if(converged(s))
                        slot.setCurrent(slot.closed() ? poleCurrent(s) : 0);
                }
            }
        }
        if(burnt) {
            if(level != null && !level.isClientSide)
                ModdedSoundEvents.WIRE_BURNED.playOnServer(level, worldPosition);
            rebuild();
        }
        if(changed || burnt)
            notifyUpdate();
    }

    /** A bar past its rating is gone: the slot empties and the client gets sparks for it. */
    private void burn(int s) {
        slots[s].clear();
        burned |= 1 << s;
    }

    /** Client side: sparks at the slots whose bars just burned. */
    private void sparks(int mask) {
        if(level == null || mask == 0)
            return;
        var facing = facing();
        for(int s = 0; s < SLOTS; ++s) {
            if((mask & (1 << s)) == 0)
                continue;
            var local = PanelLayout.fromNorthFrame(slotCenter(s), facing).scale(1 / 16.0);
            var pos = local.add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, facing, 4);
        }
    }

    // ---- redstone ----

    /** The cell's redstone signal is the coil of every contactor in it. */
    public void updateRedstone() {
        if(level == null || level.isClientSide)
            return;
        boolean now = level.hasNeighborSignal(worldPosition);
        if(now == powered)
            return;
        powered = now;
        boolean any = false;
        for(var slot : slots) {
            if(slot.is(GearKind.CONTACTOR) || slot.is(GearKind.STARTER)) {
                slot.setCoil(now);
                any = true;
            }
        }
        applyWireStates();
        if(any)
            (now ? ModdedSoundEvents.CONTACTOR_ON : ModdedSoundEvents.CONTACTOR_OFF).playOnServer(level, worldPosition);
        notifyUpdate();
    }

    // ---- links ----

    /** Whether the port at index {@code i} along that edge offers conductor {@code k}. */
    public boolean edgeCarries(GearSide side, int i, int k) {
        return slots[slotOnEdge(side, i)].carries(side, k);
    }

    /** Whether a port's partner offers the conductor, looking into the next cell on the cell's edges; for the renderer. */
    public boolean partnerCarriesAcross(int slot, GearSide side, int k) {
        int n = neighbour(slot, side);
        if(n >= 0)
            return slots[n].carries(side.opposite(), k);
        if(level == null)
            return false;
        int i = edgeIndexOf(slot, side);
        var other = worldPosition.relative(side.world(facing()));
        if(level.getBlockEntity(other) instanceof GearCellBlockEntity cell && cell.facing() == facing())
            return cell.edgeCarries(side.opposite(), i, k);
        return side.vertical() && i == 1 && level.getBlockEntity(other) instanceof IBusJoin join && !(join instanceof GearCellBlockEntity)
                && join.busJoin(side.world(facing()).getOpposite(), k) >= 0;
    }

    private void linkNeighbours() {
        var facing = facing();
        for(var side : GearSide.values()) {
            var other = worldPosition.relative(side.world(facing));
            if(!(level.getBlockEntity(other) instanceof GearCellBlockEntity cell) || cell.facing() != facing)
                continue;
            if(worldPosition.asLong() > other.asLong())
                continue;
            for(int i = 0; i < COLS; ++i) {
                for(int k = 0; k < CONDUCTORS; ++k) {
                    if(edgeCarries(side, i, k) && cell.edgeCarries(side.opposite(), i, k))
                        BusLinks.ensure(level, worldPosition, edge(side, i, k), other, edge(side.opposite(), i, k));
                }
            }
        }
    }

    @Override
    public boolean busLinkValid(int terminal, BlockPos other, int otherTerminal) {
        if(level == null || !isEdge(terminal))
            return false;
        var side = edgeSide(terminal);
        int i = edgeIndex(terminal), k = edgeConductor(terminal);
        var direction = BusLinks.between(worldPosition, other);
        if(direction == null)
            return false;
        if(direction == side.world(facing()) && level.getBlockEntity(other) instanceof GearCellBlockEntity cell && cell.facing() == facing())
            return otherTerminal == edge(side.opposite(), i, k) && edgeCarries(side, i, k) && cell.edgeCarries(side.opposite(), i, k);
        return BusLinks.joinValid(level, worldPosition, terminal, other, otherTerminal);
    }

    /** A bus duct standing on the cell, or under it, joins the middle port of that edge. */
    @Override
    public int busJoin(Direction side, int k) {
        var edge = side == Direction.UP ? GearSide.UP : side == Direction.DOWN ? GearSide.DOWN : null;
        if(edge == null || k < 0 || k >= CONDUCTORS)
            return -1;
        return edgeCarries(edge, 1, k) ? edge(edge, 1, k) : -1;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        updateRedstone();
        linkNeighbours();
        for(var slot : slots) {
            if(slot.is(GearKind.BREAKER) ? slot.breaker.currentChanged() : slot.currentChanged()) {
                sendData();
                break;
            }
        }
    }

    // ---- interaction, server side ----

    private void message(Player player, Component text) {
        if(level != null && !level.isClientSide)
            player.displayClientMessage(text, true);
    }

    /** "Slot 4", with the label in brackets when there is one. */
    public Component name(int s) {
        var name = Lang.builder().translate("gui.gear_cell.slot", s + 1);
        var slot = slots[s];
        if(!slot.label().isEmpty())
            name.text(" (" + slot.label() + ")");
        return name.component();
    }

    /** Whether the item is something a slot takes at all. */
    public static boolean isComponent(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof BreakerItem || item instanceof BusBarItem || item instanceof GearComponentItem;
    }

    public boolean install(int s, ItemStack stack, Player player) {
        var slot = slots[s];
        if(slot.installed()) {
            message(player, Lang.builder().translate("message.breaker_panel.space_taken").style(ChatFormatting.RED).component());
            return false;
        }
        var item = stack.getItem();
        if(item instanceof BreakerItem breaker) {
            if(breaker.isBlank()) {
                message(player, Lang.builder().translate("message.gear_cell.no_blank").style(ChatFormatting.RED).component());
                return false;
            }
            if(breaker.poles() != 3) {
                message(player, Lang.builder().translate("message.breaker_panel.main_poles", 3).style(ChatFormatting.RED).component());
                return false;
            }
            if(level == null || level.isClientSide)
                return true;
            slot.installBreaker(breaker);
            ratings[s].follow(slot.breaker);
        } else if(item instanceof BusBarItem) {
            if(level == null || level.isClientSide)
                return true;
            slot.install(GearKind.BUS, 0);
        } else if(item instanceof GearComponentItem component) {
            if(level == null || level.isClientSide)
                return true;
            slot.install(component.kind(), component.rating());
            if(component.kind() == GearKind.CONTACTOR || component.kind() == GearKind.STARTER)
                slot.setCoil(powered);
            if(component.kind() == GearKind.STARTER)
                ratings[s].follow(slot.breaker);
        } else {
            return false;
        }
        if(!player.isCreative())
            stack.shrink(1);
        ModdedSoundEvents.FUSE_INSTALL.playOnServer(level, worldPosition);
        message(player, Lang.builder().translate("message.gear_cell.installed", slot.kind().displayName(), name(s)).style(ChatFormatting.GRAY).component());
        rebuild();
        notifyUpdate();
        return true;
    }

    public boolean pull(int s, Player player) {
        var slot = slots[s];
        if(!slot.installed())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(slot.locked()) {
            slot.setLocked(false);
            give(player, ModItems.BREAKER_LOCK.asStack());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            message(player, Lang.builder().translate("message.breaker_panel.unlocked", name(s)).style(ChatFormatting.GRAY).component());
            notifyUpdate();
            return true;
        }
        var stack = slot.item();
        var kind = slot.kind();
        slot.clear();
        ratings[s].follow(slot.breaker);
        if(!stack.isEmpty())
            give(player, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        message(player, Lang.builder().translate("message.gear_cell.removed", kind.displayName(), name(s)).style(ChatFormatting.GRAY).component());
        dropSplicesOf(s);
        rebuild();
        notifyUpdate();
        return true;
    }

    /** A lug block leaving takes the splices on its lugs with it. */
    private void dropSplicesOf(int s) {
        var list = new ArrayList<>(splices().list());
        for(var splice : list) {
            if(isLug(splice[0]) && lugSlot(splice[0]) == s || isLug(splice[1]) && lugSlot(splice[1]) == s)
                splices().toggle(splice[0], splice[1]);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if(!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    /** Throws a handle; false when the slot has nothing to throw. */
    public boolean toggle(int s, Player player) {
        var slot = slots[s];
        var kind = slot.kind();
        if(kind == null)
            return false;
        switch(kind) {
            case BREAKER, STARTER -> {
                if(level == null || level.isClientSide)
                    return true;
                if(kind == GearKind.STARTER && slot.overload()) {
                    slot.resetOverload();
                    applyWireStates();
                    level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.3f);
                    message(player, Lang.builder().translate("message.gear_cell.overload_reset", name(s)).style(ChatFormatting.GRAY).component());
                    notifyUpdate();
                    return true;
                }
                if(slot.breaker.locked()) {
                    message(player, Lang.builder().translate("message.breaker_panel.locked", name(s)).style(ChatFormatting.RED).component());
                    level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                    return true;
                }
                var next = slot.breaker.state() == BreakerState.OFF ? BreakerState.ON : BreakerState.OFF;
                slot.breaker.setState(next);
                applyWireStates();
                (next == BreakerState.ON ? ModdedSoundEvents.BREAKER_ON : ModdedSoundEvents.BREAKER_OFF).playOnServer(level, worldPosition);
                message(player, Lang.builder().translate("message.breaker_panel.switched", name(s)).add(stateText(next)).component());
                notifyUpdate();
                return true;
            }
            case SWITCH -> {
                if(level == null || level.isClientSide)
                    return true;
                if(slot.locked()) {
                    message(player, Lang.builder().translate("message.disconnect.locked").style(ChatFormatting.RED).component());
                    return true;
                }
                slot.setOn(!slot.isOn());
                applyWireStates();
                level.playSound(null, worldPosition, slot.isOn() ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, slot.isOn() ? 0.7f : 0.6f);
                notifyUpdate();
                return true;
            }
            case CONTACTOR -> {
                message(player, Lang.builder().translate("message.gear_cell.contactor_redstone").style(ChatFormatting.GRAY).component());
                return true;
            }
            case FUSE -> {
                message(player, Lang.builder().translate(slot.blown() ? "message.gear_cell.fuse_blown" : "message.gear_cell.fuse_ok", slot.fuseRating()).style(slot.blown() ? ChatFormatting.RED : ChatFormatting.GRAY).component());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean lock(int s, Player player, ItemStack stack) {
        var slot = slots[s];
        if(slot.kind() == null || (!slot.kind().usesBreaker() && !slot.is(GearKind.SWITCH))) {
            message(player, Lang.builder().translate("message.breaker_panel.nothing_to_lock").style(ChatFormatting.RED).component());
            return false;
        }
        if(slot.locked()) {
            message(player, Lang.builder().translate("message.breaker_panel.already_locked").style(ChatFormatting.RED).component());
            return false;
        }
        if(slot.is(GearKind.SWITCH) && slot.isOn()) {
            message(player, Lang.builder().translate("message.disconnect.lock_open").style(ChatFormatting.RED).component());
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        slot.setLocked(true);
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
        message(player, Lang.builder().translate("message.breaker_panel.locked_on", name(s)).add(
                slot.kind().usesBreaker() ? stateText(slot.breaker.state()) : Lang.builder().translate("gui.disconnect.off").style(ChatFormatting.RED).component()).component());
        notifyUpdate();
        return true;
    }

    public boolean label(int s, Player player, ItemStack tag) {
        if(!slots[s].installed())
            return false;
        if(level == null || level.isClientSide)
            return true;
        String text = tag.has(DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        slots[s].setLabel(text.length() > 32 ? text.substring(0, 32) : text);
        if(!player.isCreative())
            tag.shrink(1);
        points = null;
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        message(player, slots[s].label().isEmpty()
                ? Lang.builder().translate("message.breaker_panel.label_cleared", name(s)).style(ChatFormatting.GRAY).component()
                : Lang.builder().translate("message.breaker_panel.labelled", name(s), slots[s].label()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** A wrench turns a component a quarter turn clockwise; false for bars and empty slots. */
    public boolean rotate(int s, Player player) {
        var slot = slots[s];
        if(slot.kind() == null || !slot.kind().rotatable())
            return false;
        if(level == null || level.isClientSide)
            return true;
        slot.rotate();
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        rebuild();
        notifyUpdate();
        return true;
    }

    public static Component stateText(BreakerState state) {
        var style = switch(state) {
            case ON -> ChatFormatting.GREEN;
            case OFF -> ChatFormatting.GRAY;
            case TRIPPED -> ChatFormatting.RED;
        };
        return Lang.builder().translate("gui.breaker_panel." + state.key()).style(style).component();
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeAll(tag, clientPacket);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeAll(tag, false);
    }

    private void writeAll(CompoundTag tag, boolean clientPacket) {
        ensureInit();
        splices().write(tag);
        var list = new ListTag();
        for(var slot : slots)
            list.add(slot.write(clientPacket));
        tag.put("Slots", list);
        tag.putBoolean("Powered", powered);
        if(clientPacket && burned != 0) {
            tag.putInt("Burned", burned);
            burned = 0;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ensureInit();
        super.read(tag, registries, clientPacket);
        var list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for(int i = 0; i < SLOTS; ++i) {
            if(i < list.size())
                slots[i].read(list.getCompound(i), clientPacket);
            else
                slots[i].clear();
        }
        powered = tag.getBoolean("Powered");
        if(ratings != null) {
            for(int i = 0; i < SLOTS; ++i)
                ratings[i].follow(slots[i].breaker);
        }
        splices().read(tag);
        points = null;
        if(clientPacket) {
            applyWireStates();
            if(tag.contains("Burned"))
                sparks(tag.getInt("Burned"));
        } else {
            rebuild();
        }
    }

    // ---- computers ----

    /** on, off, tripped, overload, closed, open, intact, blown, ok, or empty for an empty slot. */
    public String stateName(int s) {
        var slot = slots[s];
        var kind = slot.kind();
        if(kind == null)
            return "";
        return switch(kind) {
            case BREAKER -> slot.breaker.state().key();
            case STARTER -> slot.overload() ? "overload" : slot.breaker.state().key();
            case FUSE -> slot.blown() ? "blown" : "intact";
            case SWITCH, CONTACTOR -> slot.closed() ? "closed" : "open";
            default -> "ok";
        };
    }

    /** The hottest pole's current through a switched component, or the hottest phase of a metering unit. */
    public float currentOf(int s) {
        var slot = slots[s];
        return slot.is(GearKind.BREAKER) ? slot.breaker.current() : slot.current();
    }

    /** The rating of a breaker, starter or fuse set; 0 for anything else. */
    public int ratingOf(int s) {
        var slot = slots[s];
        var kind = slot.kind();
        if(kind == null)
            return 0;
        return switch(kind) {
            case BREAKER, STARTER -> slot.breaker.rating();
            case FUSE -> slot.fuseRating();
            default -> 0;
        };
    }

    /** One slot as a table: kind, label, state, closed, current, rating, and a metering unit's readings per phase. */
    public Map<String, Object> describe(int s) {
        var slot = slots[s];
        var kind = slot.kind();
        var table = new LinkedHashMap<String, Object>();
        if(kind == null)
            return table;
        table.put("kind", kind.id());
        table.put("label", slot.label());
        table.put("state", stateName(s));
        table.put("closed", slot.closed());
        table.put("current", (double) currentOf(s));
        table.put("rating", (double) ratingOf(s));
        if(kind == GearKind.STARTER)
            table.put("coil", slot.coil());
        if(kind == GearKind.METER) {
            var volts = new LinkedHashMap<Integer, Double>();
            var amps = new LinkedHashMap<Integer, Double>();
            var watts = new LinkedHashMap<Integer, Double>();
            for(int k = 0; k < 3; ++k) {
                volts.put(k + 1, (double) slot.volts(k));
                amps.put(k + 1, (double) slot.amps(k));
                watts.put(k + 1, (double) slot.watts(k));
            }
            table.put("voltage", volts);
            table.put("phaseCurrent", amps);
            table.put("power", watts);
        }
        return table;
    }

    // ---- code ----

    /** A lug block whose ground lug nothing bonds: no ground bar meets it on any side it takes. The inspector looks for this. */
    public boolean hasUngroundedLugs() {
        for(int s = 0; s < SLOTS; ++s) {
            var slot = slots[s];
            if(!slot.is(GearKind.LUGS))
                continue;
            boolean bonded = false;
            for(var side : GearSide.values()) {
                if(slot.carries(side, GROUND) && partnerCarriesAcross(s, side, GROUND)) {
                    bonded = true;
                    break;
                }
            }
            if(!bonded)
                return true;
        }
        return false;
    }

    @Override
    public void destroy() {
        super.destroy();
        if(!(level instanceof ServerLevel))
            return;
        var center = worldPosition.getCenter();
        for(var slot : slots) {
            if(!slot.installed())
                continue;
            var stack = slot.item();
            if(!stack.isEmpty())
                Containers.dropItemStack(level, center.x, center.y, center.z, stack);
            if(slot.locked())
                Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.BREAKER_LOCK.asStack());
            slot.clear();
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.gear_cell.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        boolean any = false;
        for(int s = 0; s < SLOTS; ++s) {
            var slot = slots[s];
            var kind = slot.kind();
            if(kind == null)
                continue;
            any = true;
            var line = Lang.builder().add(Lang.builder().add(name(s)).style(ChatFormatting.GRAY)).text(": ")
                    .add(Lang.builder().add(kind.displayName()).style(ChatFormatting.WHITE));
            switch(kind) {
                case BREAKER -> {
                    var breaker = slot.breaker;
                    line.text(" ").add(Lang.builder().text(breaker.rating() + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE))
                            .text("  ").add(Lang.builder().add(stateText(breaker.state())));
                    if(breaker.state() == BreakerState.ON)
                        line.text("  ").add(Lang.builder().text(String.format("%.1f ", breaker.current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
                    if(breaker.locked())
                        line.text("  ").add(Lang.builder().translate("gui.breaker_panel.locked").style(ChatFormatting.RED));
                }
                case FUSE -> {
                    line.text(" ").add(Lang.builder().text(slot.fuseRating() + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE)).text("  ")
                            .add(Lang.builder().translate(slot.blown() ? "gui.gear_cell.blown" : "gui.gear_cell.intact").style(slot.blown() ? ChatFormatting.RED : ChatFormatting.GREEN));
                    if(!slot.blown())
                        line.text("  ").add(Lang.builder().text(String.format("%.1f ", slot.current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
                }
                case SWITCH, CONTACTOR -> {
                    boolean closed = slot.closed();
                    line.text("  ").add(Lang.builder().translate(closed ? "gui.gear_cell.closed" : "gui.gear_cell.open").style(closed ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if(closed)
                        line.text("  ").add(Lang.builder().text(String.format("%.1f ", slot.current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
                    if(slot.locked())
                        line.text("  ").add(Lang.builder().translate("gui.breaker_panel.locked").style(ChatFormatting.RED));
                }
                case STARTER -> {
                    var breaker = slot.breaker;
                    line.text(" ").add(Lang.builder().text(breaker.rating() + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE))
                            .text("  ").add(Lang.builder().add(stateText(breaker.state())));
                    if(slot.overload())
                        line.text("  ").add(Lang.builder().translate("gui.gear_cell.overload").style(ChatFormatting.RED));
                    else if(breaker.state() == BreakerState.ON)
                        line.text("  ").add(Lang.builder().translate(slot.coil() ? "gui.gear_cell.closed" : "gui.gear_cell.open").style(slot.coil() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    if(slot.closed())
                        line.text("  ").add(Lang.builder().text(String.format("%.1f ", slot.current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
                    if(breaker.locked())
                        line.text("  ").add(Lang.builder().translate("gui.breaker_panel.locked").style(ChatFormatting.RED));
                }
                default -> {}
            }
            line.forGoggles(tooltip, 1);
            if(kind == GearKind.METER) {
                for(int k = 0; k < 3; ++k) {
                    Lang.builder().add(Lang.builder().add(conductorName(k)))
                            .text(String.format("  %.1f V  %.2f A  %.0f W", slot.volts(k), slot.amps(k), slot.watts(k))).style(ChatFormatting.WHITE)
                            .forGoggles(tooltip, 2);
                }
            }
        }
        if(!any)
            Lang.builder().translate("gui.gear_cell.empty").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    // ---- splice host ----

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    /** Whether hanging wire or a splice may land on that lug terminal: only on an installed lug block. */
    public boolean lugLive(int terminal) {
        return isLug(terminal) && slots[lugSlot(terminal)].is(GearKind.LUGS);
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            for(int s = 0; s < SLOTS; ++s) {
                if(!slots[s].is(GearKind.LUGS))
                    continue;
                for(int k = 0; k < CONDUCTORS; ++k) {
                    var name = Lang.builder().add(lugName(s, k));
                    if(!slots[s].label().isEmpty())
                        name.text(" (" + slots[s].label() + ")");
                    points.add(new SplicePoint(lug(s, k), name.component(), conductorRgb(k)));
                }
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return lugLive(terminal);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public int hubCount() {
        return HUB_COUNT;
    }

    @Override
    public Component hubName(int hub) {
        return GearLayout.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return GearLayout.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return GearLayout.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return GearLayout.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return conductorHub(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return GearLayout.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= HUB_COUNT ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }
}
