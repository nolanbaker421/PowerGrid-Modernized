package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
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
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Circuit: line point -> main breaker -> bus -> one breaker per branch space -> circuit point.
 * Every breaker is a {@link SwitchedWire}; an empty or blanked space keeps its wire open. Breakers
 * open on overcurrent following {@link BreakerTripCurve} and stay tripped until someone resets them.
 * A locked breaker cannot be flipped or pulled but still trips. Wiring arrives through conduit
 * knockouts and is spliced to the line, neutral and circuit points in the splice editor.
 */
public class BreakerPanelBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, ISpliceHost {
    public static final int TERMINAL_LINE = 0;
    public static final int TERMINAL_NEUTRAL = 1;
    public static final int TERMINAL_BRANCH_FIRST = 2;

    /** Slot index of the main breaker in the interaction and lookup methods. */
    public static final int MAIN = PanelLayout.MAIN;

    // Create's SmartBlockEntity constructor builds the circuit before this class's constructor body
    // and field initialisers run, so everything buildCircuit() needs is created lazily from the block state.
    private PanelSpec spec;
    private Breaker main;
    private Breaker[] branches;
    @Nullable
    private SwitchedWire mainWire;
    private SwitchedWire[] branchWires;
    private SpliceSupport splices;
    private List<SplicePoint> points;

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
        main = new Breaker();
        branches = new Breaker[spec.slots()];
        for(int i = 0; i < branches.length; ++i)
            branches[i] = new Breaker();
        branchWires = new SwitchedWire[spec.slots()];
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

    /** @param slot {@link #MAIN} or a branch index */
    public Breaker breaker(int slot) {
        ensureInit();
        return slot == MAIN ? main : branches[slot];
    }

    @Nullable
    private SwitchedWire wire(int slot) {
        return slot == MAIN ? mainWire : branchWires[slot];
    }

    // ------------------------------------------------------------------ circuit

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureInit();
        builder.setTerminalCount(PanelLayout.terminalCount(spec));
        splices().buildCircuit(builder);
        var bus = builder.addInternalNode();
        mainWire = builder.connectSwitch(resistance("main"), builder.terminalNode(TERMINAL_LINE), bus, main.closed());
        for(int i = 0; i < branchWires.length; ++i) {
            branchWires[i] = builder.connectSwitch(resistance("branch"), bus,
                    builder.terminalNode(TERMINAL_BRANCH_FIRST + i), branches[i].closed());
        }
    }

    private void applyWireStates() {
        if(mainWire != null)
            mainWire.setState(main.closed());
        for(int i = 0; i < branchWires.length; ++i) {
            if(branchWires[i] != null)
                branchWires[i].setState(branches[i].closed());
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        tickBreaker(MAIN);
        for(int i = 0; i < branches.length; ++i)
            tickBreaker(i);
    }

    private void tickBreaker(int slot) {
        var breaker = breaker(slot);
        var wire = wire(slot);
        if(wire == null || !breaker.isBreaker())
            return;
        if(breaker.state != BreakerState.ON) {
            breaker.current = 0;
            breaker.heat = BreakerTripCurve.cool(breaker.heat);
            return;
        }
        if(!wire.isConverged())
            return;
        float current = (float) Math.abs(wire.current());
        if(!Float.isFinite(current))
            current = 0;
        breaker.current = current;
        breaker.heat = BreakerTripCurve.step(breaker.heat, current, breaker.rating);
        if(BreakerTripCurve.trips(breaker.heat, current, breaker.rating))
            trip(slot);
    }

    private void trip(int slot) {
        var breaker = breaker(slot);
        breaker.state = BreakerState.TRIPPED;
        breaker.heat = 0;
        breaker.current = 0;
        var wire = wire(slot);
        if(wire != null)
            wire.setState(false);
        if(level != null && !level.isClientSide)
            ModdedSoundEvents.BREAKER_OFF.playOnServer(level, worldPosition);
        notifyUpdate();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
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

    /** Plug a breaker or a blank into an empty space. Consumes one item unless the player is in creative mode. */
    public boolean install(int slot, BreakerItem item, Player player, ItemStack stack) {
        var breaker = breaker(slot);
        if(breaker.installed()) {
            message(player, Lang.builder().translate("message.breaker_panel.space_taken").style(ChatFormatting.RED).component());
            return false;
        }
        if(!item.isBlank() && !spec.accepts(item.rating())) {
            message(player, Lang.builder().translate("message.breaker_panel.too_large", item.rating(), spec.rating()).style(ChatFormatting.RED).component());
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        breaker.rating = item.rating();
        breaker.blank = item.isBlank();
        breaker.state = BreakerState.OFF;
        breaker.heat = 0;
        breaker.current = 0;
        applyWireStates();
        if(!player.isCreative())
            stack.shrink(1);
        ModdedSoundEvents.FUSE_INSTALL.playOnServer(level, worldPosition);
        if(item.isBlank())
            message(player, Lang.builder().translate("message.breaker_panel.blank_installed", slotName(slot)).style(ChatFormatting.GRAY).component());
        else
            message(player, Lang.builder().translate("message.breaker_panel.installed", slotName(slot), item.rating()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** Shift-click: take the lock off a locked breaker, otherwise pull the breaker or blank. */
    public boolean pull(int slot, Player player) {
        var breaker = breaker(slot);
        if(!breaker.installed())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked) {
            breaker.locked = false;
            give(player, ModItems.BREAKER_LOCK.asStack());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            message(player, Lang.builder().translate("message.breaker_panel.unlocked", slotName(slot)).style(ChatFormatting.GRAY).component());
            notifyUpdate();
            return true;
        }
        var stack = breaker.blank ? ModItems.breaker(BreakerItem.BLANK) : ModItems.breaker(breaker.rating);
        breaker.clear();
        applyWireStates();
        give(player, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        message(player, Lang.builder().translate("message.breaker_panel.removed", slotName(slot)).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        if(!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    /** Flip a handle: ON -> OFF, OFF -> ON, TRIPPED -> OFF (a tripped breaker resets before it closes again). */
    public boolean toggle(int slot, Player player) {
        var breaker = breaker(slot);
        if(!breaker.isBreaker())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked) {
            message(player, Lang.builder().translate("message.breaker_panel.locked", slotName(slot)).style(ChatFormatting.RED).component());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            return true;
        }
        var next = breaker.state == BreakerState.OFF ? BreakerState.ON : BreakerState.OFF;
        setState(slot, next);
        message(player, Lang.builder().translate("message.breaker_panel.switched", slotName(slot))
                .add(stateText(next)).component());
        return true;
    }

    /** Hang a lock on an installed breaker, freezing its handle. */
    public boolean lock(int slot, Player player, ItemStack stack) {
        var breaker = breaker(slot);
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
        message(player, Lang.builder().translate("message.breaker_panel.locked_on", slotName(slot)).add(stateText(breaker.state)).component());
        notifyUpdate();
        return true;
    }

    /** A renamed name tag labels the space; a plain one clears the label. The tag is used up. */
    public boolean label(int slot, Player player, ItemStack tag) {
        var breaker = breaker(slot);
        if(level == null || level.isClientSide)
            return true;
        String text = tag.has(DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        breaker.label = text.length() > 32 ? text.substring(0, 32) : text;
        if(!player.isCreative())
            tag.shrink(1);
        points = null;
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        if(breaker.label.isEmpty())
            message(player, Lang.builder().translate("message.breaker_panel.label_cleared", slotName(slot)).style(ChatFormatting.GRAY).component());
        else
            message(player, Lang.builder().translate("message.breaker_panel.labelled", slotName(slot), breaker.label).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    /** Server-side state change with sound. */
    public void setState(int slot, BreakerState state) {
        var breaker = breaker(slot);
        if(!breaker.isBreaker() || breaker.state == state)
            return;
        breaker.state = state;
        breaker.heat = 0;
        breaker.current = 0;
        applyWireStates();
        if(level != null && !level.isClientSide) {
            if(slot == MAIN) {
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

    /** "Main" or "Circuit n", with the label in brackets when there is one. */
    public Component slotName(int slot) {
        var breaker = breaker(slot);
        var name = slot == MAIN
                ? Lang.builder().translate("gui.breaker_panel.main")
                : Lang.builder().translate("breaker_panel.branch", slot + 1);
        if(!breaker.label.isEmpty())
            name.text(" (" + breaker.label + ")");
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
        var prevMain = main.state;
        var prevBranches = new BreakerState[branches.length];
        for(int i = 0; i < branches.length; ++i)
            prevBranches[i] = branches[i].state;

        if(tag.contains("Main"))
            main.read(tag.getCompound("Main"), clientPacket);
        var list = tag.getList("Branches", Tag.TAG_COMPOUND);
        for(int i = 0; i < branches.length && i < list.size(); ++i)
            branches[i].read(list.getCompound(i), clientPacket);
        // Reading splices may rebuild the circuit, which recreates the switch wires; set their states after.
        splices().read(tag);
        applyWireStates();
        points = null;

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
        var local = PanelLayout.fromNorthFrame(PanelLayout.breakerCenter(spec, slot), facing).scale(1 / 16.0);
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
        }
    }

    private void dropBreaker(Breaker breaker) {
        if(!breaker.installed())
            return;
        var center = worldPosition.getCenter();
        Containers.dropItemStack(level, center.x, center.y, center.z, breaker.blank ? ModItems.breaker(BreakerItem.BLANK) : ModItems.breaker(breaker.rating));
        if(breaker.locked)
            Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.BREAKER_LOCK.asStack());
        breaker.clear();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        var requirement = ItemRequirement.NONE;
        for(int slot = MAIN; slot < branches.length; ++slot) {
            var breaker = breaker(slot);
            if(!breaker.installed())
                continue;
            var stack = breaker.blank ? ModItems.breaker(BreakerItem.BLANK) : ModItems.breaker(breaker.rating);
            requirement = requirement.union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, stack));
        }
        return requirement;
    }

    // ------------------------------------------------------------------ goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.breaker_panel.title", spec.rating()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        goggleLine(tooltip, MAIN);
        for(int i = 0; i < branches.length; ++i)
            goggleLine(tooltip, i);
        splices().addGoggleLines(tooltip);
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
            line.add(Lang.builder().text(breaker.rating + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE))
                    .text("  ")
                    .add(Lang.builder().add(stateText(breaker.state)));
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
            points.add(new SplicePoint(TERMINAL_LINE, Lang.builder().translate("breaker_panel.line").style(ChatFormatting.RED).component(), IDecoratedTerminal.RED));
            points.add(new SplicePoint(TERMINAL_NEUTRAL, Lang.builder().translate("breaker_panel.neutral").style(ChatFormatting.BLUE).component(), IDecoratedTerminal.BLUE));
            for(int i = 0; i < spec.slots(); ++i)
                points.add(new SplicePoint(TERMINAL_BRANCH_FIRST + i, Lang.builder().add(slotName(i)).style(ChatFormatting.WHITE).component(), 0xC8C8C8));
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return PanelLayout.isPoint(spec(), terminal);
    }

    @Override
    public int hubCount() {
        return PanelLayout.HUB_COUNT;
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
        return hub < 0 || hub >= PanelLayout.HUB_COUNT ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ------------------------------------------------------------------ breaker record

    /** One space in the panel: empty, a blank filler, or a breaker with a rating. */
    public static final class Breaker {
        private int rating;
        private boolean blank;
        private boolean locked;
        private String label = "";
        private BreakerState state = BreakerState.OFF;
        private float heat;
        private float current;
        private float syncedCurrent;

        /** Something occupies the space, breaker or blank. */
        public boolean installed() {
            return rating > 0 || blank;
        }

        /** A real breaker sits here. */
        public boolean isBreaker() {
            return rating > 0 && !blank;
        }

        public boolean isBlank() {
            return blank;
        }

        public boolean closed() {
            return isBreaker() && state == BreakerState.ON;
        }

        public int rating() {
            return rating;
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

        /** Magnitude of the current through the breaker, amperes; zero when open. */
        public float current() {
            return current;
        }

        void clear() {
            rating = 0;
            blank = false;
            locked = false;
            state = BreakerState.OFF;
            heat = 0;
            current = 0;
        }

        boolean currentChanged() {
            return Math.abs(current - syncedCurrent) > Math.max(0.05f, Math.abs(syncedCurrent) * 0.02f);
        }

        CompoundTag write(boolean clientPacket) {
            var tag = new CompoundTag();
            tag.putInt("Rating", rating);
            tag.putBoolean("Blank", blank);
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

        void read(CompoundTag tag, boolean clientPacket) {
            rating = tag.getInt("Rating");
            blank = tag.getBoolean("Blank");
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
