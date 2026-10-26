package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.util.BodyRider;
import com.nolanbaker.pgmodernized.util.BodyRide;
import com.nolanbaker.pgmodernized.util.SubLevelStick;
import com.nolanbaker.pgmodernized.inspector.CopperScrap;
import net.minecraft.nbt.CompoundTag;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
public class ConduitRunEntity extends BlockWireEntity implements BodyRider {
    private final BodyRide ride = new BodyRide();

    private static final int NIPPLE_CHECK = 20;
    /** Galvanized steel; Power Grid's colourable wires would otherwise start out black. */
    public static final int DEFAULT_COLOR = 0xC9CDD2;   // silver, as EMT comes
    /** Laid by a pull box to the panel above or below it; never picked up, never dropped. */
    private boolean nipple;
    private int nippleTimer;
    /** Two knockouts within a metre: drawn as one straight piece between them, an offset nipple, whatever its legs say. */
    private boolean direct;
    public ConduitRunEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ConduitRunEntity create(Level level, IWireEndpoint endpoint1, ItemStack item, List<Point> segments) {
        var entity = new ConduitRunEntity(ModEntities.CONDUIT_RUN.get(), level);
        entity.setItem(item.getItem(), item.getCount());
        entity.setColor(DEFAULT_COLOR);
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

    /** A short 4" run between a pull box knockout and a panel knockout, laid by the box itself. */
    @Nullable
    public static ConduitRunEntity createNipple(Level level, BlockWireEndpoint a, BlockWireEndpoint b) {
        return createNipple(level, a, b, ConduitSize.HALF);
    }

    /** @param drawn the trade size the nipple is drawn as; it takes anything whatever it looks like */
    @Nullable
    public static ConduitRunEntity createNipple(Level level, BlockWireEndpoint a, BlockWireEndpoint b, ConduitSize drawn) {
        var start = IElectric.getTerminalPos(level, a.getPos(), a.getTerminal());
        var end = IElectric.getTerminalPos(level, b.getPos(), b.getTerminal());
        var path = manhattan(start, end);
        if(path.isEmpty())
            return null;
        var entity = new ConduitRunEntity(ModEntities.CONDUIT_RUN.get(), level);
        entity.nipple = true;
        entity.setItem(ModItems.CONDUIT.get(drawn).get(), 1);
        entity.setColor(DEFAULT_COLOR);
        entity.setPosRaw(start.x, start.y, start.z);
        entity.segments.addAll(path);
        entity.bakeBoundingBoxes();
        entity.setEndpoint1(a);
        entity.setEndpoint2(b);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    public boolean isNipple() {
        return nipple;
    }

    /** Drawn as one straight piece from knockout to knockout. Works on both sides. */
    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    /** A nipple takes anything (it is a 4" fitting) but is drawn as the thinnest tube so it hides nothing. */
    public ConduitSize size() {
        if(nipple)
            return ConduitSize.FOUR;
        return getItem() instanceof ConduitItem conduit ? conduit.size() : ConduitSize.HALF;
    }

    private static boolean isGutter(@Nullable net.minecraft.world.level.block.entity.BlockEntity be) {
        return be instanceof PullBoxBlockEntity || be instanceof com.nolanbaker.pgmodernized.conduit.trough.ITroughSection;
    }

    private static boolean isPanel(@Nullable net.minecraft.world.level.block.entity.BlockEntity be) {
        return be instanceof com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity
                || be instanceof com.nolanbaker.pgmodernized.device.breaker.PanelExtensionBlockEntity;
    }

    /** A nipple lives exactly as long as its gutter and panel stand next to each other. */
    @Override
    public void tick() {
        super.tick();
        ride.tick(this);
        if(!nipple || level().isClientSide || isRemoved() || ++nippleTimer < NIPPLE_CHECK)
            return;
        nippleTimer = 0;
        if(!(getEndpoint1() instanceof BlockWireEndpoint a) || !(getEndpoint2() instanceof BlockWireEndpoint b)) {
            kill();
            return;
        }
        if(!level().isLoaded(a.getPos()) || !level().isLoaded(b.getPos()))
            return;
        var beA = level().getBlockEntity(a.getPos());
        var beB = level().getBlockEntity(b.getPos());
        // A gutter (pull box or trough section) against a panel (head or extension), either way round.
        boolean pair = (isGutter(beA) && isPanel(beB)) || (isGutter(beB) && isPanel(beA));
        if(!pair || !a.getPos().closerThan(b.getPos(), 1.5))
            kill();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ride.save(nbt);
        nbt.putBoolean("Nipple", nipple);
        nbt.putBoolean("Direct", direct);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ride.load(nbt);
        nipple = nbt.getBoolean("Nipple");
        direct = nbt.getBoolean("Direct");
        if(getColor() == 0)
            setColor(DEFAULT_COLOR); // laid before runs had a colour
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

    /** Wires this run takes: its trade size's slots, and no more than either fitting has landings for. */
    public int capacity() {
        int cap = size().conductors();
        cap = Math.min(cap, landingsAt(getEndpoint1()));
        cap = Math.min(cap, landingsAt(getEndpoint2()));
        return Math.max(1, cap);
    }

    private int landingsAt(@Nullable IWireEndpoint endpoint) {
        if(!(endpoint instanceof BlockWireEndpoint end) || !(level().getBlockEntity(end.getPos()) instanceof ISpliceHost host))
            return Integer.MAX_VALUE;
        int hub = host.hubAt(end.getTerminal());
        return hub < 0 ? Integer.MAX_VALUE : host.landings(hub);
    }

    private int freeSlot() {
        var taken = new boolean[capacity()];
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

    /** The two fittings a closed run joins, or null while either end is missing, unloaded or not a splice host. */
    @Nullable
    private Ends ends() {
        var level = level();
        var e1 = hub(getEndpoint1());
        var e2 = hub(getEndpoint2());
        if(e1 == null || e2 == null || !e1.isValid(level) || !e2.isValid(level))
            return null;
        if(!(level.getBlockEntity(e1.getPos()) instanceof ISpliceHost hostA) || !(level.getBlockEntity(e2.getPos()) instanceof ISpliceHost hostB))
            return null;
        int hubA = hostA.hubAt(e1.getTerminal());
        int hubB = hostB.hubAt(e2.getTerminal());
        if(hubA < 0 || hubB < 0)
            return null;
        return new Ends(e1, e2, hostA, hostB, hubA, hubB);
    }

    private record Ends(BlockWireEndpoint e1, BlockWireEndpoint e2, ISpliceHost hostA, ISpliceHost hostB, int hubA, int hubB) {}

    /** A conductor of that wire in that slot, routed through the run between the two fittings' landings. Server side. */
    @Nullable
    private ConductorEntity spawnConductor(Ends ends, int slot, ItemStack wire) {
        var level = level();
        int terminalA = ends.hostA().conductorTerminal(ends.hubA(), slot);
        int terminalB = ends.hostB().conductorTerminal(ends.hubB(), slot);
        var start = IElectric.getTerminalPos(level, ends.e1().getPos(), terminalA);
        var end = IElectric.getTerminalPos(level, ends.e2().getPos(), terminalB);
        var entity = ConductorEntity.create(level, this, slot, wire,
                new BlockWireEndpoint(ends.e1().getPos(), terminalA), new BlockWireEndpoint(ends.e2().getPos(), terminalB), start, conductorPath(start, end));
        SubLevelStick.stick(entity);
        return ((ServerLevel) level).tryAddFreshEntityWithPassengers(entity) ? entity : null;
    }

    /** Pull one conductor of the held wire through the run. Server side. */
    public InteractionResult pull(Player player, ItemStack stack) {
        var level = level();
        var ends = ends();
        if(ends == null) {
            var e1 = hub(getEndpoint1());
            var e2 = hub(getEndpoint2());
            boolean open = e1 == null || e2 == null || !e1.isValid(level) || !e2.isValid(level);
            return fail(player, open ? "message.conduit.close_first" : "message.connection_failed");
        }

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

        if(spawnConductor(ends, slot, stack.copyWithCount(required)) == null)
            return fail(player, "message.connection_failed");
        if(!player.isCreative())
            PlayerUtilities.removeItems(player, stack, required);
        CopperScrap.roll(player, ends.e1().getPos());
        player.displayClientMessage(Lang.builder().translate("message.conduit.pulled", slot + 1)
                .add(Lang.builder().text(" ").add(ConductorColors.name(slot))).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Puts a wire a bender kept back into a re-laid run: its old slot where that is free, the same
     * colour and label, topped up from the player's pockets when the new run is longer and the
     * surplus handed back when it is shorter. Server side; false when it cannot go in.
     */
    public boolean pullStored(Player player, int slot, ItemStack wire, int colorIndex, String label) {
        var ends = ends();
        if(ends == null)
            return false;
        var entry = WireRegistry.forItem(level(), wire.getItem());
        if(entry == null)
            return false;
        if(slot < 0 || slot >= capacity() || conductor(slot) != null)
            slot = freeSlot();
        if(slot < 0)
            return false;
        int required = Math.max(1, (int) Math.ceil(getTotalLength() * entry.itemsPerMeter()));
        int have = wire.getCount();
        if(have < required && !player.isCreative()) {
            if(!PlayerUtilities.hasEnoughItems(player, wire.getItem(), required - have))
                return false;
            PlayerUtilities.removeItems(player, wire.getItem(), required - have);
        } else if(have > required) {
            player.getInventory().placeItemBackInInventory(wire.copyWithCount(have - required));
        }
        var entity = spawnConductor(ends, slot, wire.copyWithCount(required));
        if(entity == null)
            return false;
        entity.setColorIndex(colorIndex);
        entity.setLabel(label);
        return true;
    }

    /** A leg hit by a look ray, and where. */
    public record Hit(int index, Vec3 point) {}

    /** The leg under a look ray from {@code from} to {@code to}, nearest first, or null. Works on both sides. */
    @Nullable
    public Hit segmentAt(Vec3 from, Vec3 to) {
        Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        for(int i = 0; i < boundingBoxes.size() && i < segments.size(); ++i) {
            var clip = boundingBoxes.get(i).clip(from, to);
            if(clip.isEmpty())
                continue;
            double d = clip.get().distanceToSqr(from);
            if(d < bestDistance) {
                bestDistance = d;
                best = new Hit(i, clip.get());
            }
        }
        return best;
    }

    /**
     * New legs from the bender between the same two ends. Server side. The wires inside are not
     * touched here; the bender has them {@link ConductorEntity#follow} the run afterwards.
     */
    public void reshape(RunReshape.Result result) {
        direct = false;   // bent by hand: it follows its legs from now on
        segments.clear();
        segments.addAll(result.points());
        var shift = result.startShift();
        if(!shift.equals(Vec3i.ZERO)) {
            var p = position().add(shift.getX() / 16.0, shift.getY() / 16.0, shift.getZ() / 16.0);
            setPosRaw(p.x, p.y, p.z);
            setOldPosAndRot();
            reapplyPosition();
        }
        bakeBoundingBoxes();
        sendExtraData();
    }

    /** One chat line per slot: colour, wire and current. */
    public List<Component> contents() {
        var lines = new ArrayList<Component>();
        var size = size();
        var pulled = conductors();
        double used = ConduitFill.used(level(), pulled);
        int capacity = capacity();
        lines.add(Lang.builder().translate("gui.conduit.contents", size.label(), pulled.size(), capacity,
                ConduitFill.percent(size, used), ConduitFill.percentLimit(Math.max(1, pulled.size()))).style(ChatFormatting.GRAY).component());
        for(int k = 0; k < capacity; ++k) {
            ConductorEntity conductor = null;
            for(var c : pulled) {
                if(c.slot() == k)
                    conductor = c;
            }
            if(conductor == null) {
                lines.add(Lang.builder().translate("gui.conduit.slot_empty", k + 1).style(ChatFormatting.DARK_GRAY).component());
            } else {
                var line = Lang.builder().text("  " + (k + 1) + " ").add(ConductorColors.name(conductor.colorIndex())).text(": ")
                        .add(Lang.builder().add(conductor.getItem().getDescription()).style(ChatFormatting.WHITE))
                        .text(String.format(" (%s), %.1f A", WireGauge.of(level(), conductor.getItem()).label(), conductor.measuredCurrent()));
                // An empty builder cannot be added: the label piece only goes in when there is one.
                if(!conductor.label().isEmpty())
                    line.add(Lang.builder().text(" \"" + conductor.label() + "\"").style(ChatFormatting.YELLOW));
                lines.add(line.style(ChatFormatting.GRAY).component());
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
    float maxAmps() {
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

    /** The route a pulled wire takes from its first terminal to its second: into the run, along it, and out. */
    protected List<Point> conductorPath(Vec3 start, Vec3 end) {
        var path = new ArrayList<Point>();
        path.addAll(manhattan(start, position()));
        for(var segment : segments)
            path.add(new Point(segment.direction, segment.gridLength));
        path.addAll(manhattan(endPosition(), end));
        return path;
    }

    /** Axis-aligned segments from one point to the next; the conductor is invisible so the route only needs to be legal. */
    public static List<Point> manhattan(Vec3 from, Vec3 to) {
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

    /**
     * An endpoint block is gone. Power Grid's block wire would shorten itself and stay as a stub to
     * build on, which for a conduit run is a ghost hitbox at the surviving device; the run comes down.
     */
    @Override
    public void endpointRemoved(IWireEndpoint endpoint) {
        kill();
    }

    /** Taking the conduit up drops the pulled wires too. */
    @Override
    public void kill() {
        if(!level().isClientSide) {
            for(var conductor : conductors())
                conductor.kill();
        }
        if(nipple) {
            discard(); // nothing to hand back: the box laid it
            return;
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
        SubLevelStick.stick(entity);
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
        if(stack.getItem() instanceof ConduitBenderItem) {
            if(client)
                return InteractionResult.SUCCESS;
            if(player.isShiftKeyDown())
                return ConduitBenderItem.pickUp(this, player, stack);
            if(ConduitBenderItem.loaded(stack))
                return nipple ? fail(player, "message.conduit.no_tee") : ConduitPlacement.attach(this, player, stack);
            return ConduitBenderItem.nudge(this, player, stack);
        }
        if(stack.getItem() instanceof ConduitItem)
            return client ? InteractionResult.SUCCESS : nipple ? fail(player, "message.conduit.no_tee") : ConduitPlacement.attach(this, player, stack);
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            if(client)
                return InteractionResult.SUCCESS;
            if(!player.isShiftKeyDown())
                return pullOut(player);
            if(nipple)
                return fail(player, "message.conduit.nipple");
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

    @Override
    public BodyRide ride() {
        return ride;
    }

    @Override
    public void onEntityDataPacket(CompoundTag tag) {
        super.onEntityDataPacket(tag);
        ride.load(tag);
    }
}
