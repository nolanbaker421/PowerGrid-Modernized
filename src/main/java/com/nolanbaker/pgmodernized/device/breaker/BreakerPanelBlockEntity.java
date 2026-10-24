package com.nolanbaker.pgmodernized.device.breaker;

import net.minecraft.world.level.block.Block;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import net.minecraft.world.phys.AABB;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.List;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import net.minecraft.network.chat.Style;

/**
 * Circuit: one line point per lug -> one pole of the main breaker each -> one bus per lug -> one
 * breaker pole per branch space -> circuit point. Every pole is a {@link SwitchedWire}; an empty or
 * blanked space keeps its wire open.
 * <p>
 * A breaker is a frame (1-50, 51-200, 201-400 or 401-800 A) whose trip rating is set with a wrench
 * once it is in. Bigger frames take more rows of the column per pole, and a two- or three-pole
 * breaker takes that many poles' worth of rows under one handle: pole k lands on the lug after the
 * head row's, so its poles switch and trip together across the lugs. The rows a pole takes beyond
 * its first are dead: no circuit point, wire held open. Installing or pulling a breaker rebuilds
 * the circuit so each pole's wire hangs off the right bus.
 * <p>
 * Breakers open on overcurrent following {@link BreakerTripCurve} and stay tripped until someone
 * resets them. A locked breaker cannot be flipped or pulled but still trips. Wiring arrives through
 * conduit knockouts and is spliced to the line, neutral and circuit points in the splice editor.
 */
public class BreakerPanelBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, ISpliceHost {
    /** Slot index of the main breaker in the interaction and lookup methods. */
    public static final int MAIN = PanelLayout.MAIN;
    /** Ground current at which a GFCI breaker trips. */
    public static final int GFCI_TRIP_MILLIAMPS = 50;

    // Create's SmartBlockEntity constructor builds the circuit before this class's constructor body
    // and field initialisers run, so everything buildCircuit() needs is created lazily from the block state.
    private PanelSpec spec;
    private Breaker main;
    private Breaker[] branches;
    private SwitchedWire[] mainWires;
    private SwitchedWire[] branchWires;
    /** The main bonding jumper, ground to neutral: whatever comes home on the ground flows here. */
    private ElectricWire bond;
    private float groundAmps;
    private BreakerRatingBehaviour[] ratings;
    private SpliceSupport splices;
    private List<SplicePoint> points;
    private boolean needsRebuild;
    /** The head plus the extensions below it, from the block state. */
    private int sections = 1;

    public BreakerPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureInit();
        setLazyTickRate(10);
    }

    private void ensureInit() {
        if(spec != null)
            return;
        var state = getBlockState();
        spec = state != null && state.getBlock() instanceof BreakerPanelBlock block ? block.spec() : PanelSpec.A200;
        sections = state != null ? BreakerPanelBlock.sectionsOf(state) : 1;
        main = new Breaker();
        // Room for every extension the panel could get; spaces past slotCount() stay empty.
        branches = new Breaker[PanelLayout.maxSlots(spec)];
        for(int i = 0; i < branches.length; ++i)
            branches[i] = new Breaker();
        mainWires = new SwitchedWire[spec.lugs()];
        branchWires = new SwitchedWire[branches.length];
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ensureInit();
        ratings = new BreakerRatingBehaviour[1 + branches.length];
        for(int slot = MAIN; slot < branches.length; ++slot) {
            final int s = slot;
            var behaviour = new BreakerRatingBehaviour(this, slot);
            behaviour.withCallback(value -> onRating(s, value));
            ratings[slot + 1] = behaviour;
            behaviours.add(behaviour);
        }
        super.addBehaviours(behaviours);
    }

    private BreakerRatingBehaviour rating(int slot) {
        return ratings[slot + 1];
    }

    /** The rating box of a space, for the extension blocks' mirrors. */
    public BreakerRatingBehaviour ratingBehaviour(int slot) {
        return ratings[slot + 1];
    }

    /** The head and its extensions, from the block state as it stands; the cached count only tracks changes. */
    public int sections() {
        ensureInit();
        var state = getBlockState();
        return state != null && state.getBlock() instanceof BreakerPanelBlock ? BreakerPanelBlock.sectionsOf(state) : sections;
    }

    /**
     * The extensions actually standing under the head. The state's count is set when an extension is
     * placed or taken; this puts it right if the two ever disagree, as was seen after a reload.
     */
    private void healSections() {
        if(level == null || level.isClientSide)
            return;
        var state = getBlockState();
        if(!(state.getBlock() instanceof BreakerPanelBlock))
            return;
        int found = 1;
        while(found <= PanelLayout.MAX_EXT && level.getBlockState(worldPosition.below(found)).getBlock() instanceof PanelExtensionBlock)
            ++found;
        if(found != BreakerPanelBlock.sectionsOf(state))
            level.setBlock(worldPosition, state.setValue(BreakerPanelBlock.SECTIONS, found), Block.UPDATE_ALL);
    }

    /** Branch spaces the panel has with its present extensions. */
    public int slotCount() {
        ensureInit();
        return spec.slots() * sections;
    }

    /** Whether any of the head's bottom knockouts carries a run, which an extension would cover. */
    public boolean bottomHubsUsed() {
        for(int h = 4; h < PanelLayout.EDGE_HUBS; ++h) {
            if(SpliceSupport.runAt(this, PanelLayout.hubTerminal(spec(), h)) != null)
                return true;
        }
        return false;
    }

    /** An extension came or went: the spaces beyond the new end are emptied and their breakers dropped. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if(spec == null)
            return;
        int now = BreakerPanelBlock.sectionsOf(state);
        if(now == sections)
            return;
        sections = now;
        trimTo(slotCount());
        points = null;
        if(level != null && !level.isClientSide) {
            rebuild();
            notifyUpdate();
        }
    }

    private void trimTo(int count) {
        for(int slot = 0; slot < branches.length; ++slot) {
            var breaker = branches[slot];
            int head = breaker.isCovered() ? breaker.head : slot;
            if(head < 0 || head >= branches.length || !branches[head].installed())
                continue;
            int last = head + 2 * (branches[head].rows() - 1);
            if(last < count)
                continue;
            var stack = branches[head].item();
            boolean locked = branches[head].locked;
            uninstall(head);
            if(level instanceof ServerLevel) {
                var center = worldPosition.getCenter();
                Containers.dropItemStack(level, center.x, center.y, center.z, stack);
                if(locked)
                    Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.BREAKER_LOCK.asStack());
            }
        }
    }

    /** The breakers of the extensions are drawn by this block entity, a block or two below it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY() - (sections - 1), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
    }

    private void onRating(int slot, int value) {
        var breaker = breaker(slot);
        if(!breaker.isBreaker())
            return;
        int rating = breaker.frame.clamp(value);
        if(rating != value)
            rating(slot).mirror(rating);
        if(rating == breaker.rating)
            return;
        breaker.rating = rating;
        breaker.heat = 0;
        notifyUpdate();
    }

    public PanelSpec spec() {
        ensureInit();
        return spec;
    }

    public Breaker main() {
        ensureInit();
        return main;
    }

    public Breaker branch(int slot) {
        ensureInit();
        return branches[slot];
    }

    /** The record in that space itself; a space covered by a bigger breaker only points at its head. */
    public Breaker breaker(int slot) {
        ensureInit();
        return slot == MAIN ? main : branches[slot];
    }

    /** The space whose breaker owns a slot: itself, or the head of the breaker covering it. */
    public int headSlot(int slot) {
        ensureInit();
        if(slot == MAIN)
            return MAIN;
        var breaker = branches[slot];
        return breaker.head >= 0 ? breaker.head : slot;
    }

    /** The breaker that owns a slot. */
    public Breaker effective(int slot) {
        return breaker(headSlot(slot));
    }

    /** The lug a branch space's wire hangs off: its pole's lug for a pole row, its row's lug otherwise. */
    private int lugFor(int slot) {
        var breaker = branches[slot];
        if(breaker.head >= 0 && breaker.pole >= 0)
            return (spec.leg(breaker.head) + breaker.pole) % spec.lugs();
        return spec.leg(slot);
    }

    /** A branch space's wire is closed when its breaker is on and this row carries a pole. */
    private boolean wireClosed(int slot) {
        var breaker = branches[slot];
        if(breaker.head >= 0)
            return breaker.pole >= 0 && branches[breaker.head].closed();
        return breaker.closed();
    }

    /** The pole wires of the breaker heading a slot: one per lug for the main, one per pole for a branch. */
    private SwitchedWire[] wiresOf(int head) {
        if(head == MAIN)
            return mainWires;
        var breaker = branches[head];
        var wires = new SwitchedWire[breaker.poles];
        int rows = breaker.frame == null ? 1 : breaker.frame.rows();
        for(int p = 0; p < breaker.poles; ++p) {
            int slot = head + 2 * p * rows;
            wires[p] = slot < branchWires.length ? branchWires[slot] : null;
        }
        return wires;
    }

    // ------------------------------------------------------------------ circuit

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureInit();
        builder.setTerminalCount(PanelLayout.terminalCount(spec));
        splices().buildCircuit(builder);
        var buses = new FloatingNode[spec.lugs()];
        for(int lug = 0; lug < spec.lugs(); ++lug) {
            buses[lug] = builder.addInternalNode();
            mainWires[lug] = builder.connectSwitch(resistance("main"), builder.terminalNode(spec.lineTerminal(lug)), buses[lug], main.closed());
        }
        for(int slot = 0; slot < branchWires.length; ++slot) {
            branchWires[slot] = builder.connectSwitch(resistance("branch"), buses[lugFor(slot)],
                    builder.terminalNode(PanelLayout.branchTerminal(spec, slot)), wireClosed(slot));
        }
        bond = builder.connect(resistance("bond"), builder.terminalNode(spec.neutralTerminal()), builder.terminalNode(PanelLayout.groundTerminal(spec)));
    }

    private void applyWireStates() {
        for(var wire : mainWires) {
            if(wire != null)
                wire.setState(main.closed());
        }
        for(int slot = 0; slot < branchWires.length; ++slot) {
            if(branchWires[slot] != null)
                branchWires[slot].setState(wireClosed(slot));
        }
    }

    /** Multi-pole breakers hang their poles off other lugs than their rows', so the wiring is rebuilt around them. */
    private void rebuild() {
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
        if(needsRebuild && level != null && !level.isClientSide) {
            needsRebuild = false;
            rebuild();
        }
        super.tick();
    }

    @Override
    public void electricalTick() {
        tickBreaker(MAIN);
        for(int i = 0; i < branches.length; ++i) {
            if(!branches[i].isCovered())
                tickBreaker(i);
        }
        tickGround();
    }

    /** Current on the bonding jumper is a ground fault somewhere downstream: every GFCI breaker that is on trips. */
    private void tickGround() {
        if(bond == null || !bond.isConverged())
            return;
        float amps = (float) Math.abs(bond.current());
        groundAmps = Float.isFinite(amps) ? amps : 0;
        if(groundAmps * 1000 < GFCI_TRIP_MILLIAMPS)
            return;
        for(int i = 0; i < branches.length; ++i) {
            var breaker = branches[i];
            if(breaker.gfci && breaker.isBreaker() && breaker.state == BreakerState.ON)
                trip(i);
        }
    }

    /** Amperes coming home on the ground. */
    public float groundCurrent() {
        return groundAmps;
    }

    /** Whether anything is wired to the ground point: a splice in the editor or a hanging wire. The inspector looks for this. */
    public boolean groundBonded() {
        int ground = PanelLayout.groundTerminal(spec());
        for(var splice : splices().list()) {
            if(splice[0] == ground || splice[1] == ground)
                return true;
        }
        var behaviour = getElectricBehaviour();
        if(behaviour == null)
            return false;
        for(var entry : behaviour.getConnections().entrySet()) {
            if(entry.getKey().getTerminal() == ground && !entry.getValue().isEmpty())
                return true;
        }
        return false;
    }

    /**
     * Magnitude of the current through one pole, amperes. The fork's settled RMS: an instantaneous
     * sample of an alternating current lands anywhere in the cycle, and one tick's RMS covers a
     * fraction of a cycle at Create's frequencies.
     */
    protected float readCurrent(SwitchedWire wire) {
        float current = (float) ForkHooks.get().lastRmsCurrent(wire);
        return Float.isFinite(current) ? current : 0;
    }

    private void tickBreaker(int head) {
        var breaker = breaker(head);
        if(!breaker.isBreaker())
            return;
        if(breaker.state != BreakerState.ON) {
            breaker.current = 0;
            breaker.heat = BreakerTripCurve.cool(breaker.heat);
            return;
        }
        // Common trip: the hottest pole decides for all of them.
        float current = 0;
        boolean converged = false;
        for(var wire : wiresOf(head)) {
            if(wire == null || !wire.isConverged())
                continue;
            converged = true;
            current = Math.max(current, readCurrent(wire));
        }
        if(!converged)
            return;
        breaker.current = current;
        breaker.heat = BreakerTripCurve.step(breaker.heat, current, breaker.rating);
        if(BreakerTripCurve.trips(breaker.heat, current, breaker.rating))
            trip(head);
    }

    private void trip(int head) {
        var breaker = breaker(head);
        breaker.state = BreakerState.TRIPPED;
        breaker.heat = 0;
        breaker.current = 0;
        for(var wire : wiresOf(head)) {
            if(wire != null)
                wire.setState(false);
        }
        if(level != null && !level.isClientSide)
            ModdedSoundEvents.BREAKER_OFF.playOnServer(level, worldPosition);
        notifyUpdate();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        healSections();
        splices().prune();
        if(main.currentChanged()) {
            sendData();
            return;
        }
        for(var branch : branches) {
            if(branch.currentChanged()) {
                sendData();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    /** Rows that item takes from a head space: poles times the frame's rows; a blank or the main takes one. */
    private int rowsFor(int slot, BreakerItem item) {
        if(item.isBlank() || slot == MAIN)
            return 1;
        return item.poles() * item.frame().rows();
    }

    /** Why that item cannot go into that space, or null when it can. */
    @Nullable
    public Component installProblem(int slot, BreakerItem item) {
        var breaker = breaker(slot);
        if(breaker.installed())
            return Lang.builder().translate("message.breaker_panel.space_taken").style(ChatFormatting.RED).component();
        if(item.isBlank())
            return null;
        if(!spec.accepts(item.rating()))
            return Lang.builder().translate("message.breaker_panel.too_large", item.rating(), spec.rating()).style(ChatFormatting.RED).component();
        if(slot == MAIN) {
            if(item.poles() != spec.lugs())
                return Lang.builder().translate("message.breaker_panel.main_poles", spec.lugs()).style(ChatFormatting.RED).component();
            return null;
        }
        if(!spec.acceptsPoles(item.poles()))
            return Lang.builder().translate("message.breaker_panel.too_many_poles", item.poles(), spec.lugs()).style(ChatFormatting.RED).component();
        int rows = rowsFor(slot, item);
        if(!spec.fits(slot, rows, slotCount()))
            return Lang.builder().translate("message.breaker_panel.no_room", rows).style(ChatFormatting.RED).component();
        for(int i = 1; i < rows; ++i) {
            if(branches[slot + 2 * i].installed())
                return Lang.builder().translate("message.breaker_panel.no_room", rows).style(ChatFormatting.RED).component();
        }
        return null;
    }

    public boolean canInstall(int slot, BreakerItem item) {
        return installProblem(slot, item) == null;
    }

    /** Plug a breaker or a blank into an empty space. Consumes one item unless the player is in creative mode. */
    /** Installs a breaker with no player involved, as a Ponder scene does. */
    public void installForScene(int slot, BreakerItem item) {
        var breaker = breaker(slot);
        breaker.frame = item.frame();
        breaker.rating = item.rating();
        breaker.blank = item.isBlank();
        breaker.gfci = item.gfci();
        breaker.poles = item.isBlank() ? 1 : slot == MAIN ? spec.lugs() : item.poles();
        breaker.state = BreakerState.OFF;
        breaker.heat = 0;
        breaker.current = 0;
        if(slot != MAIN && !item.isBlank()) {
            int frameRows = item.frame().rows();
            for(int i = 1; i < rowsFor(slot, item); ++i)
                branches[slot + 2 * i].coverBy(slot, i % frameRows == 0 ? i / frameRows : -1);
        }
        rating(slot).follow(breaker);
        rebuild();
        points = null;
        notifyUpdate();
    }

    /** Fits or removes the cover with no player involved, as a Ponder scene does. */
    public void setCover(boolean fitted) {
        cover = fitted;
        coverOpen = false;
        notifyUpdate();
    }

    public boolean install(int slot, BreakerItem item, Player player, ItemStack stack) {
        var problem = installProblem(slot, item);
        if(problem != null) {
            message(player, problem);
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        var breaker = breaker(slot);
        breaker.frame = item.frame();
        breaker.rating = item.rating();
        breaker.blank = item.isBlank();
        breaker.gfci = item.gfci();
        breaker.poles = item.isBlank() ? 1 : slot == MAIN ? spec.lugs() : item.poles();
        breaker.state = BreakerState.OFF;
        breaker.heat = 0;
        breaker.current = 0;
        if(slot != MAIN && !item.isBlank()) {
            int frameRows = item.frame().rows();
            for(int i = 1; i < rowsFor(slot, item); ++i)
                branches[slot + 2 * i].coverBy(slot, i % frameRows == 0 ? i / frameRows : -1);
        }
        rating(slot).follow(breaker);
        rebuild();
        if(!player.isCreative())
            stack.shrink(1);
        ModdedSoundEvents.FUSE_INSTALL.playOnServer(level, worldPosition);
        points = null;
        if(item.isBlank())
            message(player, Lang.builder().translate("message.breaker_panel.blank_installed", slotName(slot)).style(ChatFormatting.GRAY).component());
        else
            message(player, Lang.builder().translate("message.breaker_panel.installed", slotName(slot), item.rating()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** Shift-click: take the lock off a locked breaker, otherwise pull the breaker or blank. */
    public boolean pull(int slot, Player player) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        if(!breaker.installed())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked) {
            breaker.locked = false;
            give(player, ModItems.BREAKER_LOCK.asStack());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            message(player, Lang.builder().translate("message.breaker_panel.unlocked", slotName(head)).style(ChatFormatting.GRAY).component());
            notifyUpdate();
            return true;
        }
        var stack = breaker.item();
        var name = slotName(head);
        uninstall(head);
        give(player, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        message(player, Lang.builder().translate("message.breaker_panel.removed", name).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** Clears a head space and every space its breaker covered. */
    private void uninstall(int head) {
        var breaker = breaker(head);
        if(head != MAIN) {
            for(int i = 1; i < breaker.rows(); ++i) {
                int slot = head + 2 * i;
                if(slot < branches.length && branches[slot].head == head)
                    branches[slot].clear();
            }
        }
        breaker.clear();
        rating(head).follow(breaker);
        points = null;
        rebuild();
    }

    private static void give(Player player, ItemStack stack) {
        if(!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    /** Flip a handle: ON -> OFF, OFF -> ON, TRIPPED -> OFF (a tripped breaker resets before it closes again). */
    public boolean toggle(int slot, Player player) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        if(!breaker.isBreaker())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked) {
            message(player, Lang.builder().translate("message.breaker_panel.locked", slotName(head)).style(ChatFormatting.RED).component());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            return true;
        }
        var next = breaker.state == BreakerState.OFF ? BreakerState.ON : BreakerState.OFF;
        setState(head, next);
        message(player, Lang.builder().translate("message.breaker_panel.switched", slotName(head))
                .add(stateText(next)).component());
        return true;
    }

    /** Hang a lock on an installed breaker, freezing its handle. */
    public boolean lock(int slot, Player player, ItemStack stack) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        if(!breaker.isBreaker()) {
            message(player, Lang.builder().translate("message.breaker_panel.nothing_to_lock").style(ChatFormatting.RED).component());
            return false;
        }
        if(breaker.locked) {
            message(player, Lang.builder().translate("message.breaker_panel.already_locked").style(ChatFormatting.RED).component());
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        breaker.locked = true;
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
        message(player, Lang.builder().translate("message.breaker_panel.locked_on", slotName(head)).add(stateText(breaker.state)).component());
        notifyUpdate();
        return true;
    }

    /** A renamed name tag labels the space; a plain one clears the label. The tag is used up. */
    public boolean label(int slot, Player player, ItemStack tag) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        if(level == null || level.isClientSide)
            return true;
        String text = tag.has(DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        breaker.label = text.length() > 32 ? text.substring(0, 32) : text;
        if(!player.isCreative())
            tag.shrink(1);
        points = null;
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        if(breaker.label.isEmpty())
            message(player, Lang.builder().translate("message.breaker_panel.label_cleared", slotName(head)).style(ChatFormatting.GRAY).component());
        else
            message(player, Lang.builder().translate("message.breaker_panel.labelled", slotName(head), breaker.label).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** Server-side state change with sound. */
    public void setState(int slot, BreakerState state) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        if(!breaker.isBreaker() || breaker.state == state)
            return;
        breaker.state = state;
        breaker.heat = 0;
        breaker.current = 0;
        applyWireStates();
        if(level != null && !level.isClientSide) {
            if(head == MAIN) {
                (state == BreakerState.ON ? ModdedSoundEvents.BREAKER_ON : ModdedSoundEvents.BREAKER_OFF).playOnServer(level, worldPosition);
            } else {
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, state == BreakerState.ON ? 0.7f : 0.6f);
            }
        }
        notifyUpdate();
    }

    private void message(Player player, Component text) {
        if(level != null && !level.isClientSide)
            player.displayClientMessage(text, true);
    }

    /** "Main" or "Circuit n" (a multi-pole breaker lists the space of each pole, "Circuit 1/3"), with the label in brackets when there is one. */
    public Component slotName(int slot) {
        int head = headSlot(slot);
        var breaker = breaker(head);
        var numbers = new StringBuilder();
        int frameRows = breaker.frame == null ? 1 : breaker.frame.rows();
        for(int p = 0; p < Math.max(1, breaker.poles); ++p) {
            if(p > 0)
                numbers.append('/');
            numbers.append(head + 2 * p * frameRows + 1);
        }
        var name = head == MAIN
                ? Lang.builder().translate("gui.breaker_panel.main")
                : Lang.builder().translate("breaker_panel.branch", numbers.toString());
        if(!breaker.label.isEmpty())
            name.text(" (" + breaker.label + ")");
        return name.component();
    }

    /** The circuit conductor of one space: its own number, with the owning breaker's label. */
    public Component pointName(int slot) {
        var name = Lang.builder().translate("breaker_panel.branch", slot + 1);
        var label = effective(slot).label;
        if(!label.isEmpty())
            name.text(" (" + label + ")");
        return name.component();
    }

    private static Component stateText(BreakerState state) {
        var style = switch(state) {
            case ON -> ChatFormatting.GREEN;
            case OFF -> ChatFormatting.GRAY;
            case TRIPPED -> ChatFormatting.RED;
        };
        return Lang.builder().translate("gui.breaker_panel." + state.key()).style(style).component();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeBreakers(tag, clientPacket);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeBreakers(tag, false);
    }

    private void writeBreakers(CompoundTag tag, boolean clientPacket) {
        ensureInit();
        splices().write(tag);
        tag.putBoolean("Cover", cover);
        tag.putBoolean("CoverOpen", coverOpen);
        tag.put("Main", main.write(clientPacket));
        var list = new ListTag();
        for(var branch : branches)
            list.add(branch.write(clientPacket));
        tag.put("Branches", list);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ensureInit();
        super.read(tag, registries, clientPacket);
        cover = tag.getBoolean("Cover");
        coverOpen = tag.getBoolean("CoverOpen");
        var prevMain = main.state;
        var prevBranches = new BreakerState[branches.length];
        for(int i = 0; i < branches.length; ++i)
            prevBranches[i] = branches[i].state;

        if(tag.contains("Main"))
            main.read(tag.getCompound("Main"), clientPacket);
        var list = tag.getList("Branches", Tag.TAG_COMPOUND);
        for(int i = 0; i < branches.length && i < list.size(); ++i)
            branches[i].read(list.getCompound(i), clientPacket);
        if(ratings != null) {
            for(int slot = MAIN; slot < branches.length; ++slot)
                rating(slot).follow(breaker(slot));
        }
        // Reading splices may rebuild the circuit, which recreates the switch wires; set their states after.
        splices().read(tag);
        applyWireStates();
        points = null;
        // Multi-lug mains installed before 0.9.0 were stored as one pole; they are the panel's pole count.
        if(main.installed() && !main.blank && main.poles < spec.lugs())
            main.poles = spec.lugs();
        if(!clientPacket) {
            // Loaded from disk: multi-pole wiring depends on what is installed, so rebuild once ticking.
            for(var branch : branches) {
                if(branch.isCovered()) {
                    needsRebuild = true;
                    break;
                }
            }
        }

        if(clientPacket && level != null) {
            if(prevMain == BreakerState.ON && main.state == BreakerState.TRIPPED)
                tripEffect(MAIN);
            for(int i = 0; i < branches.length; ++i) {
                if(prevBranches[i] == BreakerState.ON && branches[i].state == BreakerState.TRIPPED)
                    tripEffect(i);
            }
        }
    }

    private void tripEffect(int slot) {
        var facing = BreakerPanelBlock.facing(getBlockState());
        var local = PanelLayout.fromNorthFrame(PanelLayout.breakerCenter(spec, slot, breaker(slot).rows()), facing).scale(1 / 16.0);
        var pos = local.add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, facing, 3);
    }

    @Override
    public void destroy() {
        super.destroy();
        if(level instanceof ServerLevel) {
            dropBreaker(main);
            for(var branch : branches)
                dropBreaker(branch);
            if(cover) {
                var center = worldPosition.getCenter();
                Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.PANEL_COVER.asStack());
                cover = false;
            }
        }
    }

    // ---- the cover ----

    private boolean cover, coverOpen;

    public boolean hasCover() {
        return cover;
    }

    public boolean coverOpen() {
        return cover && coverOpen;
    }

    /** A closed door: the breakers are out of reach and out of sight. */
    public boolean coverClosed() {
        return cover && !coverOpen;
    }

    public boolean fitCover(Player player, ItemStack stack) {
        if(cover) {
            if(level != null && !level.isClientSide)
                player.displayClientMessage(Lang.builder().translate("message.breaker_panel.has_cover").style(ChatFormatting.RED).component(), true);
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        cover = true;
        coverOpen = false;
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(Lang.builder().translate("message.breaker_panel.cover_fitted").style(ChatFormatting.GRAY).component(), true);
        notifyUpdate();
        return true;
    }

    public void removeCover(Player player) {
        if(!cover || level == null || level.isClientSide)
            return;
        cover = false;
        coverOpen = false;
        var plate = ModItems.PANEL_COVER.asStack();
        if(!player.getInventory().add(plate))
            player.drop(plate, false);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(Lang.builder().translate("message.breaker_panel.cover_removed").style(ChatFormatting.GRAY).component(), true);
        notifyUpdate();
    }

    public void setCoverOpen(boolean open) {
        if(!cover || coverOpen == open || level == null || level.isClientSide)
            return;
        coverOpen = open;
        level.playSound(null, worldPosition, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.3f);
        notifyUpdate();
    }

    private void dropBreaker(Breaker breaker) {
        if(!breaker.installed() || breaker.isCovered())
            return;
        var center = worldPosition.getCenter();
        Containers.dropItemStack(level, center.x, center.y, center.z, breaker.item());
        if(breaker.locked)
            Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.BREAKER_LOCK.asStack());
        breaker.clear();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        var requirement = ItemRequirement.NONE;
        if(cover)
            requirement = requirement.union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, ModItems.PANEL_COVER.asStack()));
        for(int slot = MAIN; slot < branches.length; ++slot) {
            var breaker = breaker(slot);
            if(!breaker.installed() || breaker.isCovered())
                continue;
            requirement = requirement.union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, breaker.item()));
        }
        return requirement;
    }

    // ------------------------------------------------------------------ goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate(spec.titleKey(), spec.rating()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(cover)
            Lang.builder().translate(coverOpen ? "gui.breaker_panel.cover_open" : "gui.breaker_panel.cover_closed").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        goggleLine(tooltip, MAIN);
        for(int i = 0; i < slotCount(); ++i) {
            if(!branches[i].isCovered())
                goggleLine(tooltip, i);
        }
        if(groundAmps >= 0.005f)
            Lang.builder().translate("gui.breaker_panel.ground_current", String.format("%.0f", groundAmps * 1000)).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    private void goggleLine(List<Component> tooltip, int slot) {
        var breaker = breaker(slot);
        var line = Lang.builder().add(Lang.builder().add(slotName(slot)).style(ChatFormatting.GRAY)).text(": ");
        if(!breaker.installed()) {
            line.add(Lang.builder().translate("gui.breaker_panel.empty").style(ChatFormatting.DARK_GRAY));
        } else if(breaker.blank) {
            line.add(Lang.builder().translate("gui.breaker_panel.blank").style(ChatFormatting.DARK_GRAY));
        } else {
            line.add(Lang.builder().text(breaker.rating + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE));
            int poles = slot == MAIN ? spec.lugs() : breaker.poles;
            if(poles > 1)
                line.text(" ").add(Lang.builder().translate("gui.breaker_panel.poles", poles).style(ChatFormatting.WHITE));
            if(breaker.gfci)
                line.text(" ").add(Lang.builder().translate("gui.breaker_panel.gfci").style(ChatFormatting.AQUA));
            line.text("  ").add(Lang.builder().add(stateText(breaker.state)));
            if(breaker.state == BreakerState.ON)
                line.text("  ").add(Lang.builder().text(String.format("%.1f ", breaker.current)).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
            if(breaker.locked)
                line.text("  ").add(Lang.builder().translate("gui.breaker_panel.locked").style(ChatFormatting.RED));
        }
        line.forGoggles(tooltip, 1);
    }

    // ------------------------------------------------------------------ splice host (conduit knockouts)

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public List<SplicePoint> points() {
        ensureInit();
        if(points == null) {
            points = new ArrayList<>();
            for(int lug = 0; lug < spec.lugs(); ++lug)
                points.add(new SplicePoint(spec.lineTerminal(lug), PanelLayout.lineName(spec, lug), PanelLayout.lineRgb(spec, lug)));
            points.add(new SplicePoint(spec.neutralTerminal(), ConductorColors.neutralLabel("breaker_panel.neutral"), ConductorColors.neutralRgb()));
            points.add(new SplicePoint(PanelLayout.groundTerminal(spec), ConductorColors.groundLabel("breaker_panel.ground"), ConductorColors.groundRgb()));
            for(int i = 0; i < slotCount(); ++i) {
                if(branches[i].isDeadRow())
                    continue;
                // The space's wire hangs off its lug, a pole's lug on a pole row, so it wears that line's colour.
                int lug = lugFor(i);
                var name = pointName(i).copy().withStyle(Style.EMPTY.withColor(ConductorColors.hotTextRgb(lug)));
                points.add(new SplicePoint(PanelLayout.branchTerminal(spec, i), name, ConductorColors.hotRgb(lug)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        if(!PanelLayout.isPoint(spec(), terminal))
            return false;
        int slot = PanelLayout.branchSlot(spec, terminal);
        return slot < 0 || (slot < slotCount() && !branches[slot].isDeadRow());
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.TWO;
    }

    @Override
    public int hubCount() {
        return PanelLayout.HUB_COUNT + PanelLayout.EXT_HUBS * (sections() - 1);
    }

    @Override
    public Component hubName(int hub) {
        return PanelLayout.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return PanelLayout.hubTerminal(spec(), hub);
    }

    @Override
    public int hubAt(int terminal) {
        return PanelLayout.hubAt(spec(), terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return PanelLayout.conductorTerminal(spec(), hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return PanelLayout.conductorHub(spec(), terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return PanelLayout.conductorOf(spec(), terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        if(hub < 0 || hub >= hubCount() || (sections() > 1 && PanelLayout.isHeadBottomHub(hub)))
            return null;
        return SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ------------------------------------------------------------------ breaker record

    /**
     * One space in the panel: empty, a blank filler, a breaker with a frame and a trip rating, or a
     * space covered by a bigger breaker headed in another space (carrying one of its poles, or dead).
     */
    public static final class Breaker {
        @Nullable
        private BreakerFrame frame;
        private int rating;
        private int poles = 1;
        private int head = -1;
        private int pole = -1;
        private boolean blank;
        private boolean gfci;
        private boolean locked;
        private String label = "";
        private BreakerState state = BreakerState.OFF;
        private float heat;
        private float current;
        private float syncedCurrent;

        /** Something occupies the space: a breaker, a blank, or part of another breaker. */
        public boolean installed() {
            return frame != null || blank || head >= 0;
        }

        /** A real breaker is headed here. */
        public boolean isBreaker() {
            return frame != null && !blank && head < 0;
        }

        public boolean isBlank() {
            return blank;
        }

        /** Trips on ground-fault current too. */
        public boolean isGfci() {
            return gfci;
        }

        /** Taken by the breaker in {@link #head()}. */
        public boolean isCovered() {
            return head >= 0;
        }

        /** Taken by a bigger breaker's extra row: no pole, no circuit point. */
        public boolean isDeadRow() {
            return head >= 0 && pole < 0;
        }

        public boolean closed() {
            return isBreaker() && state == BreakerState.ON;
        }

        @Nullable
        public BreakerFrame frame() {
            return frame;
        }

        /** Trip rating in amperes. */
        public int rating() {
            return rating;
        }

        public int poles() {
            return poles;
        }

        /** Rows of the column this breaker takes from its head: poles times the frame's rows. */
        public int rows() {
            return frame == null ? 1 : poles * frame.rows();
        }

        /** Head space of the breaker covering this space, or -1. */
        public int head() {
            return head;
        }

        /** Pole index this covered row carries, or -1 for a dead row. */
        public int pole() {
            return pole;
        }

        public boolean locked() {
            return locked;
        }

        public String label() {
            return label;
        }

        public BreakerState state() {
            return state;
        }

        /** Magnitude of the current through the breaker (its hottest pole), amperes; zero when open. */
        public float current() {
            return current;
        }

        /** The item that put this breaker or blank here. */
        public ItemStack item() {
            if(blank || frame == null)
                return ModItems.blank();
            return gfci ? ModItems.BREAKER_GFCI.asStack() : ModItems.breaker(frame, poles);
        }

        public void clear() {
            frame = null;
            rating = 0;
            poles = 1;
            head = -1;
            pole = -1;
            blank = false;
            gfci = false;
            locked = false;
            state = BreakerState.OFF;
            heat = 0;
            current = 0;
        }

        void coverBy(int headSlot, int poleIndex) {
            clear();
            head = headSlot;
            pole = poleIndex;
        }

        // Shared with the switchgear section and the switchgear cells, which keep records of their own.

        public void installFrom(BreakerItem item, int poleCount) {
            clear();
            frame = item.frame();
            rating = item.rating();
            blank = item.isBlank();
            gfci = item.gfci();
            poles = blank ? 1 : poleCount;
        }

        /** A breaker of that frame built into something, at the top of its range. */
        public void installFrame(BreakerFrame frame, int poleCount) {
            clear();
            this.frame = frame;
            rating = frame.max();
            poles = poleCount;
        }

        public void setRating(int value) {
            rating = value;
            heat = 0;
        }

        public void setState(BreakerState value) {
            state = value;
            heat = 0;
            current = 0;
        }

        public void setLocked(boolean value) {
            locked = value;
        }

        public void setLabel(String value) {
            label = value;
        }

        /** Open or tripped: nothing flows and the thermal element cools. */
        public void tickOpen() {
            current = 0;
            heat = BreakerTripCurve.cool(heat);
        }

        /** Closed and carrying this current; true when it trips on this tick. */
        public boolean tickClosed(float amps) {
            current = amps;
            heat = BreakerTripCurve.step(heat, amps, rating);
            if(!BreakerTripCurve.trips(heat, amps, rating))
                return false;
            state = BreakerState.TRIPPED;
            heat = 0;
            current = 0;
            return true;
        }

        public boolean currentChanged() {
            return Math.abs(current - syncedCurrent) > Math.max(0.05f, Math.abs(syncedCurrent) * 0.02f);
        }

        public CompoundTag write(boolean clientPacket) {
            var tag = new CompoundTag();
            if(frame != null)
                tag.putByte("Frame", (byte) frame.ordinal());
            tag.putInt("Rating", rating);
            if(poles > 1)
                tag.putInt("Poles", poles);
            if(head >= 0) {
                tag.putInt("Head", head);
                tag.putInt("Pole", pole);
            }
            tag.putBoolean("Blank", blank);
            if(gfci)
                tag.putBoolean("Gfci", true);
            tag.putBoolean("Locked", locked);
            if(!label.isEmpty())
                tag.putString("Label", label);
            tag.putByte("State", (byte) state.ordinal());
            if(clientPacket) {
                tag.putFloat("Current", current);
                syncedCurrent = current;
            } else {
                tag.putFloat("Heat", heat);
            }
            return tag;
        }

        public void read(CompoundTag tag, boolean clientPacket) {
            rating = tag.getInt("Rating");
            // Panels saved before frames existed only had a rating.
            frame = tag.contains("Frame") ? BreakerFrame.byOrdinal(tag.getByte("Frame")) : rating > 0 ? BreakerFrame.forRating(rating) : null;
            if(frame != null)
                rating = frame.clamp(rating);
            poles = tag.contains("Poles") ? Math.max(1, tag.getInt("Poles")) : 1;
            head = tag.contains("Head") ? tag.getInt("Head") : -1;
            pole = head >= 0 ? (tag.contains("Pole") ? tag.getInt("Pole") : 1) : -1;
            blank = tag.getBoolean("Blank");
            if(blank)
                frame = null;
            gfci = tag.getBoolean("Gfci");
            locked = tag.getBoolean("Locked");
            label = tag.getString("Label");
            state = BreakerState.fromOrdinal(tag.getByte("State"));
            if(clientPacket)
                current = tag.getFloat("Current");
            else
                heat = tag.getFloat("Heat");
        }
    }
}
