package com.nolanbaker.pgmodernized.device.breaker;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.breaker.SwitchgearLayout.*;

/**
 * Circuit: the four bus points each tie to their couplers on both sides; three switched poles run
 * from bus L1, L2, L3 to the load points. Every ten ticks the section looks at the block on each
 * side and, on the viewer's right, spawns hidden bus bars to a neighbouring section facing the same
 * way (the neighbour on the left does the same towards this one), so a row of sections shares one
 * bus. The breaker is one of the panel's records, restricted to three poles.
 */
public class SwitchgearBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, ISpliceHost {
    private static final float COUPLER_R = 0.0001f;

    private BreakerPanelBlockEntity.Breaker breaker;
    private SwitchedWire[] poles;
    private SwitchgearRatingBehaviour rating;
    private SpliceSupport splices;
    private List<SplicePoint> points;

    public SwitchgearBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureInit();
        setLazyTickRate(10);
    }

    private void ensureInit() {
        if(breaker == null) {
            breaker = new BreakerPanelBlockEntity.Breaker();
            poles = new SwitchedWire[3];
        }
    }

    public BreakerPanelBlockEntity.Breaker breaker() {
        ensureInit();
        return breaker;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ensureInit();
        rating = new SwitchgearRatingBehaviour(this);
        rating.withCallback(this::onRating);
        behaviours.add(rating);
        super.addBehaviours(behaviours);
    }

    private void onRating(int value) {
        if(!breaker.isBreaker())
            return;
        int snapped = breaker.frame().clamp(value);
        if(snapped != value)
            rating.mirror(snapped);
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
        for(int k = 0; k < COUPLERS; ++k) {
            var bus = builder.terminalNode(BUS_L1 + k);
            builder.connect(COUPLER_R, bus, builder.terminalNode(COUPLER_LEFT + k));
            builder.connect(COUPLER_R, bus, builder.terminalNode(COUPLER_RIGHT + k));
        }
        for(int k = 0; k < 3; ++k)
            poles[k] = builder.connectSwitch(resistance("pole"), builder.terminalNode(BUS_L1 + k), builder.terminalNode(LOAD_L1 + k), breaker.closed());
    }

    private void applyWireStates() {
        for(var pole : poles) {
            if(pole != null)
                pole.setState(breaker.closed());
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    /** Magnitude of the current through one pole, amperes. */
    protected float readCurrent(SwitchedWire wire) {
        float current = (float) Math.abs(wire.current());
        return Float.isFinite(current) ? current : 0;
    }

    @Override
    public void electricalTick() {
        if(!breaker.isBreaker())
            return;
        if(breaker.state() != BreakerState.ON) {
            breaker.tickOpen();
            return;
        }
        float current = 0;
        boolean converged = false;
        for(var pole : poles) {
            if(pole == null || !pole.isConverged())
                continue;
            converged = true;
            current = Math.max(current, readCurrent(pole));
        }
        if(!converged)
            return;
        if(breaker.tickClosed(current)) {
            for(var pole : poles)
                pole.setState(false);
            if(level != null && !level.isClientSide)
                ModdedSoundEvents.BREAKER_OFF.playOnServer(level, worldPosition);
            notifyUpdate();
        }
    }

    // ---- lineup ----

    /** Two positions hold sections facing the same way, side by side across their fronts. */
    public static boolean linked(Level level, BlockPos a, BlockPos b) {
        var stateA = level.getBlockState(a);
        var stateB = level.getBlockState(b);
        if(!(stateA.getBlock() instanceof SwitchgearBlock) || !(stateB.getBlock() instanceof SwitchgearBlock))
            return false;
        var facing = SwitchgearBlock.facing(stateA);
        if(facing != SwitchgearBlock.facing(stateB))
            return false;
        return a.relative(facing.getClockWise()).equals(b) || a.relative(facing.getCounterClockWise()).equals(b);
    }

    /** The section on the viewer's right, if any: the one this section links towards. */
    @Nullable
    private SwitchgearBlockEntity rightNeighbour() {
        if(level == null)
            return null;
        var facing = SwitchgearBlock.facing(getBlockState());
        var pos = worldPosition.relative(facing.getCounterClockWise());
        return level.getBlockEntity(pos) instanceof SwitchgearBlockEntity other && linked(level, worldPosition, pos) ? other : null;
    }

    private void linkNeighbour() {
        if(!(level instanceof ServerLevel server))
            return;
        var other = rightNeighbour();
        if(other == null || electricBehaviour == null)
            return;
        var connections = electricBehaviour.getConnections();
        for(int k = 0; k < COUPLERS; ++k) {
            var mine = new BlockWireEndpoint(worldPosition, COUPLER_RIGHT + k);
            var existing = connections.get(mine);
            if(existing != null && existing.stream().anyMatch(wire -> wire.isAlive() && wire instanceof BusLinkEntity))
                continue;
            var theirs = new BlockWireEndpoint(other.worldPosition, COUPLER_LEFT + k);
            server.addFreshEntity(BusLinkEntity.create(level, mine, theirs));
        }
    }

    /** Sections in this lineup, counted along the row. */
    public int lineupSize() {
        if(level == null)
            return 1;
        var facing = SwitchgearBlock.facing(getBlockState());
        int count = 1;
        for(var dir : new net.minecraft.core.Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            var pos = worldPosition;
            while(true) {
                var next = pos.relative(dir);
                if(!linked(level, pos, next))
                    break;
                ++count;
                pos = next;
                if(count > 64)
                    break;
            }
        }
        return count;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        linkNeighbour();
        if(breaker.currentChanged())
            sendData();
    }

    // ---- interaction ----

    @Nullable
    public Component installProblem(BreakerItem item) {
        if(breaker.installed())
            return Lang.builder().translate("message.breaker_panel.space_taken").style(ChatFormatting.RED).component();
        if(item.isBlank())
            return null;
        if(item.poles() != 3)
            return Lang.builder().translate("message.breaker_panel.main_poles", 3).style(ChatFormatting.RED).component();
        return null;
    }

    public boolean canInstall(BreakerItem item) {
        return installProblem(item) == null;
    }

    public boolean install(BreakerItem item, Player player, ItemStack stack) {
        var problem = installProblem(item);
        if(problem != null) {
            message(player, problem);
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        breaker.installFrom(item, 3);
        rating.follow(breaker);
        applyWireStates();
        if(!player.isCreative())
            stack.shrink(1);
        ModdedSoundEvents.FUSE_INSTALL.playOnServer(level, worldPosition);
        message(player, item.isBlank()
                ? Lang.builder().translate("message.breaker_panel.blank_installed", name()).style(ChatFormatting.GRAY).component()
                : Lang.builder().translate("message.breaker_panel.installed", name(), item.rating()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    public boolean pull(Player player) {
        if(!breaker.installed())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked()) {
            breaker.setLocked(false);
            give(player, ModItems.BREAKER_LOCK.asStack());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            message(player, Lang.builder().translate("message.breaker_panel.unlocked", name()).style(ChatFormatting.GRAY).component());
            notifyUpdate();
            return true;
        }
        var stack = breaker.item();
        breaker.clear();
        rating.follow(breaker);
        applyWireStates();
        give(player, stack);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        message(player, Lang.builder().translate("message.breaker_panel.removed", name()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        if(!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    public boolean toggle(Player player) {
        if(!breaker.isBreaker())
            return false;
        if(level == null || level.isClientSide)
            return true;
        if(breaker.locked()) {
            message(player, Lang.builder().translate("message.breaker_panel.locked", name()).style(ChatFormatting.RED).component());
            level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            return true;
        }
        var next = breaker.state() == BreakerState.OFF ? BreakerState.ON : BreakerState.OFF;
        breaker.setState(next);
        applyWireStates();
        (next == BreakerState.ON ? ModdedSoundEvents.BREAKER_ON : ModdedSoundEvents.BREAKER_OFF).playOnServer(level, worldPosition);
        message(player, Lang.builder().translate("message.breaker_panel.switched", name()).add(stateText(next)).component());
        notifyUpdate();
        return true;
    }

    public boolean lock(Player player, ItemStack stack) {
        if(!breaker.isBreaker()) {
            message(player, Lang.builder().translate("message.breaker_panel.nothing_to_lock").style(ChatFormatting.RED).component());
            return false;
        }
        if(breaker.locked()) {
            message(player, Lang.builder().translate("message.breaker_panel.already_locked").style(ChatFormatting.RED).component());
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        breaker.setLocked(true);
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
        message(player, Lang.builder().translate("message.breaker_panel.locked_on", name()).add(stateText(breaker.state())).component());
        notifyUpdate();
        return true;
    }

    public boolean label(Player player, ItemStack tag) {
        if(level == null || level.isClientSide)
            return true;
        String text = tag.has(DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        breaker.setLabel(text.length() > 32 ? text.substring(0, 32) : text);
        if(!player.isCreative())
            tag.shrink(1);
        points = null;
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        message(player, breaker.label().isEmpty()
                ? Lang.builder().translate("message.breaker_panel.label_cleared", name()).style(ChatFormatting.GRAY).component()
                : Lang.builder().translate("message.breaker_panel.labelled", name(), breaker.label()).style(ChatFormatting.GRAY).component());
        notifyUpdate();
        return true;
    }

    private void message(Player player, Component text) {
        if(level != null && !level.isClientSide)
            player.displayClientMessage(text, true);
    }

    /** "Feeder", with the label in brackets when there is one. */
    public Component name() {
        var name = Lang.builder().translate("gui.switchgear.feeder");
        if(!breaker.label().isEmpty())
            name.text(" (" + breaker.label() + ")");
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
        tag.put("Breaker", breaker.write(clientPacket));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ensureInit();
        super.read(tag, registries, clientPacket);
        var previous = breaker.state();
        if(tag.contains("Breaker"))
            breaker.read(tag.getCompound("Breaker"), clientPacket);
        if(rating != null)
            rating.follow(breaker);
        splices().read(tag);
        applyWireStates();
        points = null;
        if(clientPacket && level != null && previous == BreakerState.ON && breaker.state() == BreakerState.TRIPPED) {
            var facing = SwitchgearBlock.facing(getBlockState());
            var box = SwitchgearLayout.BREAKER;
            var center = new net.minecraft.world.phys.Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.minZ);
            var local = PanelLayout.fromNorthFrame(center, facing).scale(1 / 16.0);
            var pos = local.add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, facing, 3);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if(level instanceof ServerLevel && breaker.installed()) {
            var center = worldPosition.getCenter();
            Containers.dropItemStack(level, center.x, center.y, center.z, breaker.item());
            if(breaker.locked())
                Containers.dropItemStack(level, center.x, center.y, center.z, ModItems.BREAKER_LOCK.asStack());
            breaker.clear();
        }
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        if(!breaker.installed())
            return ItemRequirement.NONE;
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, breaker.item());
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.switchgear.title", lineupSize()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        var line = Lang.builder().add(Lang.builder().add(name()).style(ChatFormatting.GRAY)).text(": ");
        if(!breaker.installed()) {
            line.add(Lang.builder().translate("gui.breaker_panel.empty").style(ChatFormatting.DARK_GRAY));
        } else if(breaker.isBlank()) {
            line.add(Lang.builder().translate("gui.breaker_panel.blank").style(ChatFormatting.DARK_GRAY));
        } else {
            line.add(Lang.builder().text(breaker.rating() + " ").add(Unit.CURRENT.get()).style(ChatFormatting.WHITE))
                    .text(" ").add(Lang.builder().translate("gui.breaker_panel.poles", 3).style(ChatFormatting.WHITE))
                    .text("  ").add(Lang.builder().add(stateText(breaker.state())));
            if(breaker.state() == BreakerState.ON)
                line.text("  ").add(Lang.builder().text(String.format("%.1f ", breaker.current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
            if(breaker.locked())
                line.text("  ").add(Lang.builder().translate("gui.breaker_panel.locked").style(ChatFormatting.RED));
        }
        line.forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip);
        return true;
    }

    // ---- splice host ----

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            for(int k = 0; k < 4; ++k)
                points.add(new SplicePoint(BUS_L1 + k, busName(k), phaseRgb(k)));
            for(int k = 0; k < 3; ++k) {
                var name = Lang.builder().add(loadName(k));
                if(!breaker.label().isEmpty())
                    name.text(" (" + breaker.label() + ")");
                points.add(new SplicePoint(LOAD_L1 + k, name.component(), phaseRgb(k)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return SwitchgearLayout.isPoint(terminal);
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
        return SwitchgearLayout.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return SwitchgearLayout.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return SwitchgearLayout.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return SwitchgearLayout.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return SwitchgearLayout.conductorHub(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return SwitchgearLayout.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= HUB_COUNT ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }
}
