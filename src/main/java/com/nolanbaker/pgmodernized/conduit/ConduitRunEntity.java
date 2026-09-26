package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.JunctionWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;
import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * An empty conduit run laid along block surfaces from one fitting hub to another, the raceway
 * counterpart of Power Grid's block wire. Path finding, segment geometry, rendering and pick-up are
 * inherited; it carries no current itself. Once both ends sit on hubs, right-clicking it with a
 * wire item pulls one {@link ConductorEntity} of that wire through it, up to the size's capacity.
 */
public class ConduitRunEntity extends BlockWireEntity {
    public ConduitRunEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ConduitRunEntity create(Level level, IWireEndpoint endpoint1, ItemStack item, List<Point> segments) {
        var entity = new ConduitRunEntity(ModEntities.CONDUIT_RUN.get(), level);
        entity.setItem(item.getItem(), item.getCount());
        var pos = BlockTrace.alignPosition(endpoint1.getExactPosition(level));
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.segments.addAll(segments);
        entity.bakeBoundingBoxes();
        entity.setEndpoint1(endpoint1);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    public ConduitSize size() {
        return getItem() instanceof ConduitItem conduit ? conduit.size() : ConduitSize.HALF;
    }

    // ------------------------------------------------------------------ no current of its own

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    @Override
    public void makeWire() {}

    @Override
    public void dropWire() {}

    @Override
    public void unloaded() {}

    // ------------------------------------------------------------------ ends

    @Nullable
    private BlockWireEndpoint hub(@Nullable IWireEndpoint endpoint) {
        return endpoint instanceof BlockWireEndpoint block ? block : null;
    }

    /** Whether the run is closed with a hub on each end (the ends may still be unloaded). */
    public boolean isComplete() {
        return hub(getEndpoint1()) != null && hub(getEndpoint2()) != null;
    }

    /** Whether the run's two ends are on these two blocks, in either order. */
    public boolean endsAt(BlockPos a, BlockPos b) {
        var e1 = hub(getEndpoint1());
        var e2 = hub(getEndpoint2());
        if(e1 == null || e2 == null)
            return false;
        return (e1.getPos().equals(a) && e2.getPos().equals(b)) || (e1.getPos().equals(b) && e2.getPos().equals(a));
    }

    private Vec3 endPosition() {
        var pos = position();
        for(var segment : segments)
            pos = pos.add(segment.vector());
        return pos;
    }

    // ------------------------------------------------------------------ pulled wires

    /** Every wire currently pulled through this run. Works on both sides. */
    public List<ConductorEntity> conductors() {
        var list = new ArrayList<ConductorEntity>();
        for(var conductor : level().getEntitiesOfClass(ConductorEntity.class, getBoundingBox().inflate(2))) {
            if(!conductor.isRemoved() && conductor.runId().equals(getUUID()))
                list.add(conductor);
        }
        return list;
    }

    @Nullable
    public ConductorEntity conductor(int slot) {
        for(var conductor : conductors()) {
            if(conductor.slot() == slot)
                return conductor;
        }
        return null;
    }

    private int freeSlot() {
        var taken = new boolean[size().conductors()];
        for(var conductor : conductors()) {
            if(conductor.slot() >= 0 && conductor.slot() < taken.length)
                taken[conductor.slot()] = true;
        }
        for(int k = 0; k < taken.length; ++k) {
            if(!taken[k])
                return k;
        }
        return -1;
    }

    /** Pull one conductor of the held wire through the run. Server side. */
    public InteractionResult pull(Player player, ItemStack stack) {
        var level = level();
        var e1 = hub(getEndpoint1());
        var e2 = hub(getEndpoint2());
        if(e1 == null || e2 == null || !e1.isValid(level) || !e2.isValid(level))
            return fail(player, "message.conduit.close_first");
        if(!(level.getBlockEntity(e1.getPos()) instanceof ISpliceHost hostA) || !(level.getBlockEntity(e2.getPos()) instanceof ISpliceHost hostB))
            return fail(player, "message.connection_failed");
        int hubA = hostA.hubAt(e1.getTerminal());
        int hubB = hostB.hubAt(e2.getTerminal());
        if(hubA < 0 || hubB < 0)
            return fail(player, "message.connection_failed");

        var entry = WireRegistry.forItem(level, stack.getItem());
        if(entry == null || entry.cord() || !WireAcceptance.electrical(stack))
            return fail(player, "message.connection_incorrect_wire_type");
        int slot = freeSlot();
        if(slot < 0)
            return fail(player, "message.conduit.full");
        // Fill by the book: the conductors' area against the raceway's, 53/31/40% for 1/2/more.
        var pulled = conductors();
        double used = ConduitFill.used(level, pulled);
        double added = WireGauge.areaOf(level, stack.getItem());
        if(!ConduitFill.fits(size(), used, pulled.size(), added)) {
            player.displayClientMessage(Lang.builder().translate("message.conduit.fill", stack.getHoverName(), size().label(),
                    ConduitFill.percent(size(), used + added), ConduitFill.percentLimit(pulled.size() + 1)).style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        int required = Math.max(1, (int) Math.ceil(getTotalLength() * entry.itemsPerMeter()));
        if(!PlayerUtilities.hasEnoughItems(player, stack, required))
            return fail(player, "message.connection_missing_items");

        int terminalA = hostA.conductorTerminal(hubA, slot);
        int terminalB = hostB.conductorTerminal(hubB, slot);
        var start = IElectric.getTerminalPos(level, e1.getPos(), terminalA);
        var end = IElectric.getTerminalPos(level, e2.getPos(), terminalB);
        var path = new ArrayList<Point>();
        path.addAll(manhattan(start, position()));
        for(var segment : segments)
            path.add(new Point(segment.direction, segment.gridLength));
        path.addAll(manhattan(endPosition(), end));

        var entity = ConductorEntity.create(level, this, slot, stack.copyWithCount(required),
                new BlockWireEndpoint(e1.getPos(), terminalA), new BlockWireEndpoint(e2.getPos(), terminalB), start, path);
        if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity))
            return fail(player, "message.connection_failed");
        if(!player.isCreative())
            PlayerUtilities.removeItems(player, stack, required);
        player.displayClientMessage(Lang.builder().translate("message.conduit.pulled", slot + 1)
                .add(Lang.builder().text(" ").add(ConductorColors.name(slot))).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    /** One chat line per slot: colour, wire and current. */
    public List<Component> contents() {
        var lines = new ArrayList<Component>();
        var size = size();
        var pulled = conductors();
        double used = ConduitFill.used(level(), pulled);
        lines.add(Lang.builder().translate("gui.conduit.contents", size.label(), pulled.size(), size.conductors(),
                ConduitFill.percent(size, used), ConduitFill.percentLimit(Math.max(1, pulled.size()))).style(ChatFormatting.GRAY).component());
        for(int k = 0; k < size.conductors(); ++k) {
            ConductorEntity conductor = null;
            for(var c : pulled) {
                if(c.slot() == k)
                    conductor = c;
            }
            if(conductor == null) {
                lines.add(Lang.builder().translate("gui.conduit.slot_empty", k + 1).style(ChatFormatting.DARK_GRAY).component());
            } else {
                lines.add(Lang.builder().text("  " + (k + 1) + " ").add(ConductorColors.name(conductor.colorIndex())).text(": ")
                        .add(Lang.builder().add(conductor.getItem().getDescription()).style(ChatFormatting.WHITE))
                        .text(String.format(" (%s), %.1f A", WireGauge.of(level(), conductor.getItem()).label(), conductor.measuredCurrent())).style(ChatFormatting.GRAY).component());
            }
        }
        return lines;
    }

    private InteractionResult inspect(Player player) {
        for(var line : contents())
            player.displayClientMessage(line, false);
        return InteractionResult.SUCCESS;
    }

    /** Highest current in any pulled wire, for shocking the careless. */
    private float maxAmps() {
        float max = 0;
        for(var conductor : conductors())
            max = Math.max(max, Math.abs(conductor.measuredCurrent()));
        return max;
    }

    /** Pull the most recently pulled conductor back out, returning its wire. Server side. */
    public InteractionResult pullOut(Player player) {
        ConductorEntity last = null;
        for(var conductor : conductors()) {
            if(last == null || conductor.slot() > last.slot())
                last = conductor;
        }
        if(last == null)
            return fail(player, "message.conduit.empty");
        ShockDamage.shockAmps(player, Math.abs(last.measuredCurrent()));
        last.kill();
        player.displayClientMessage(Lang.builder().translate("message.conduit.pulled_out", last.slot() + 1).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult fail(Player player, String key) {
        player.displayClientMessage(Lang.translate(key).style(ChatFormatting.RED).component(), true);
        return InteractionResult.FAIL;
    }

    /** Axis-aligned segments from one point to the next; the conductor is invisible so the route only needs to be legal. */
    static List<Point> manhattan(Vec3 from, Vec3 to) {
        var points = new ArrayList<Point>(3);
        float dx = (float) (to.x - from.x), dy = (float) (to.y - from.y), dz = (float) (to.z - from.z);
        if(Math.abs(dx) >= 1 / 32f)
            points.add(Point.x(dx));
        if(Math.abs(dy) >= 1 / 32f)
            points.add(Point.y(dy));
        if(Math.abs(dz) >= 1 / 32f)
            points.add(Point.z(dz));
        return points;
    }

    /** Taking the conduit up drops the pulled wires too. */
    @Override
    public void kill() {
        if(!level().isClientSide) {
            for(var conductor : conductors())
                conductor.kill();
        }
        super.kill();
    }

    // ------------------------------------------------------------------ interaction

    /** Same as Power Grid's flip, but the replacement stays a conduit run. */
    @Override
    public BlockWireEntity flip() {
        var entity = new ConduitRunEntity(ModEntities.CONDUIT_RUN.get(), level());
        entity.setItem(getItem(), getWireCount());
        entity.setEndpoint1(getEndpoint2());
        entity.setEndpoint2(getEndpoint1());
        entity.getEntityData().set(TEMPERATURE, getTemperature());
        entity.setColor(getColor());
        var pos = position();
        for(var segment : segments) {
            pos = pos.add(segment.vector());
            if(segment.gridLength > 0)
                entity.segments.add(0, new Point(segment.direction.getOpposite(), segment.gridLength));
        }
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.bakeBoundingBoxes();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        discard();
        ((ServerLevel) level()).tryAddFreshEntityWithPassengers(entity);
        return entity;
    }

    /** Runs cannot be split into junctions; use a fitting. */
    @Override
    public JunctionWireEndpoint split(int segmentIndex, int segmentPoint) {
        return null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if(stack.getItem() instanceof ConduitItem)
            return client ? InteractionResult.SUCCESS : ConduitPlacement.attach(this, player, stack);
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            if(client)
                return InteractionResult.SUCCESS;
            if(!player.isShiftKeyDown())
                return pullOut(player);
            // Sneaking takes the whole run. Power Grid would cut out a segment and respawn
            // electrical wire; the run goes as one piece instead.
            ShockDamage.shockAmps(player, maxAmps());
            kill();
            return InteractionResult.SUCCESS;
        }
        if(IWire.isWire(level(), stack.getItem()))
            return client ? InteractionResult.SUCCESS : pull(player, stack);
        if(stack.isEmpty() || stack.getItem() instanceof MultimeterItem)
            return client ? InteractionResult.SUCCESS : inspect(player);
        return super.interact(player, hand);
    }
}
