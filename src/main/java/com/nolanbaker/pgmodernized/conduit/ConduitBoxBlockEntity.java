package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Containers;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.light.bulb.LightBulbState;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.light.bulb.IFixtureEntity;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminals plus splices; which runs are attached is read from the wire entities on the hubs. A
 * light socket cover puts a lamp between cover terminals 0 and 1 that lights the block from ten
 * watts and goes dark under five.
 */
public class ConduitBoxBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation, IFixtureEntity {
    /** Written by every box saved since the facing turned to point into its mount; a save without it gets turned once. */
    private static final String MOUNT_FACING_KEY = "MountFacing";
    private boolean flipFacing;

    public static final float LIT_WATTS = 10, DARK_WATTS = 5;
    /** A strobe runs from this many volts on the notification loop, a horn from this many: silenced is twelve. */
    public static final float STROBE_VOLTS = 8, HORN_VOLTS = 16;
    /** Blocks each way a smoke detector looks for fire, and ticks it holds its alarm after the last smoke. */
    public static final int SMOKE_RANGE = 4, SMOKE_HOLD = 200;
    /** The horn pattern: three half-second pulses and a pause, eighty ticks round. */
    private static final int HORN_CYCLE = 80;

    private SpliceSupport splices;
    private List<SplicePoint> points;
    /** The filament of the bulb in a light socket cover, made in buildCircuit; null under any other cover or with no bulb. */
    private SwitchedWire lamp;
    private float lampWatts;
    /** The watts last sent to clients; goggles read the client copy. */
    private float syncedWatts;
    /** The bulb in the socket: any of Power Grid's. Its state sets the filament's resistance and the power level. */
    @Nullable
    private LightBulbState bulb;
    private int powerLevel;
    /** Bulb data read before the filament wire existed; applied when the circuit is next built. */
    @Nullable
    private CompoundTag pendingBulb;
    /** The alarm contact of a detector or pull station cover, closed in alarm; null under any other cover. */
    private SwitchedWire detector;
    private boolean smokeAlarm;
    private int smokeClearTicks;
    /** A pull station's handle, down until a wrench resets it. */
    private boolean pulled;
    /** The load of a horn strobe cover; null under any other cover. */
    private org.patryk3211.powergrid.electricity.sim.ElectricWire horn;
    private float hornVolts;
    private int hornTimer;

    public ConduitBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public SpliceSupport splices() {
        if(splices == null)
            splices = new SpliceSupport(this);
        return splices;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(ConduitBoxGeometry.TERMINAL_COUNT);
        splices().buildCircuit(builder);
        // A light socket always has its filament wire, open with no bulb in: the bulb's state loader and
        // ticker both reach for it, on the client as on the server.
        lamp = cover().isLight() ? builder.connectSwitch(bulb != null ? bulb.resistance() : resistance("lamp"), builder.terminalNode(0), builder.terminalNode(1), bulb != null) : null;
        if(lamp != null && bulb != null && pendingBulb != null) {
            bulb.read(pendingBulb);
            pendingBulb = null;
        }
        // A detector or a pull station: a supervisory resistor the panel sees it by, and a contact that closes in alarm.
        detector = null;
        if(cover().isInitiating()) {
            builder.connect(resistance("supervisory"), builder.terminalNode(0), builder.terminalNode(1));
            detector = builder.connectSwitch(resistance("detector"), builder.terminalNode(0), builder.terminalNode(1), contactClosed());
        }
        // A horn strobe: a load the panel supervises at two volts and drives at twenty-four.
        horn = cover().isHorn() ? builder.connect(resistance("horn"), builder.terminalNode(0), builder.terminalNode(1)) : null;
    }

    // ---- the smoke detector and the horn strobe ----

    public boolean smokeAlarm() {
        return smokeAlarm;
    }

    public boolean pulled() {
        return pulled;
    }

    /** Whether the cover's contact across the loop is closed: a tripped detector or a pulled station. */
    private boolean contactClosed() {
        return cover().isPull() ? pulled : smokeAlarm;
    }

    /** Server side: the pull station's handle comes down and the loop closes. */
    public boolean pull(Player player) {
        if(level == null || level.isClientSide || !cover().isPull())
            return false;
        if(pulled) {
            player.displayClientMessage(Lang.translate("message.conduit_box.pull_reset_wrench").style(ChatFormatting.GRAY).component(), true);
            return true;
        }
        pulled = true;
        if(detector != null)
            detector.setState(true);
        var state = getBlockState();
        level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, ConduitCover.PULL_PULLED), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.5f);
        player.displayClientMessage(Lang.translate("message.conduit_box.pulled").style(ChatFormatting.RED).component(), true);
        setChanged();
        sendData();
        return true;
    }

    /** Server side: a wrench resets a pulled station. */
    public boolean resetPull(Player player) {
        if(level == null || level.isClientSide || !cover().isPull() || !pulled)
            return false;
        pulled = false;
        if(detector != null)
            detector.setState(false);
        var state = getBlockState();
        level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, ConduitCover.PULL), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.7f);
        player.displayClientMessage(Lang.translate("message.conduit_box.pull_reset").style(ChatFormatting.GREEN).component(), true);
        setChanged();
        sendData();
        return true;
    }

    public float hornVolts() {
        return hornVolts;
    }

    /** Every second: fire within range trips the detector, four blocks and anything burning for smoke, the next block over for heat; it holds ten seconds past the last. */
    private void smokeTick() {
        boolean heat = cover().isHeat();
        boolean fire = smokeNearby(heat ? 1 : SMOKE_RANGE, !heat);
        if(fire)
            smokeClearTicks = SMOKE_HOLD;
        else if(smokeClearTicks > 0)
            smokeClearTicks -= 20;
        boolean next = fire || smokeClearTicks > 0;
        if(next == smokeAlarm)
            return;
        smokeAlarm = next;
        if(detector != null)
            detector.setState(smokeAlarm);
        var state = getBlockState();
        if(state.getBlock() instanceof ConduitBoxBlock && ConduitBoxBlock.cover(state).isDetector()) {
            var shown = heat ? (smokeAlarm ? ConduitCover.HEAT_ALARM : ConduitCover.HEAT) : (smokeAlarm ? ConduitCover.SMOKE_ALARM : ConduitCover.SMOKE);
            level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, shown), Block.UPDATE_ALL);
        }
        setChanged();
        sendData();
    }

    private boolean smokeNearby(int range, boolean burningThings) {
        var from = worldPosition.offset(-range, -range, -range);
        var to = worldPosition.offset(range, range, range);
        for(var pos : BlockPos.betweenClosed(from, to)) {
            var state = level.getBlockState(pos);
            if(state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(net.minecraft.world.level.block.Blocks.LAVA)
                    || (burningThings && state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)))
                return true;
        }
        if(!burningThings)
            return false;
        var box = new net.minecraft.world.phys.AABB(worldPosition).inflate(range);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, net.minecraft.world.entity.Entity::isOnFire).isEmpty();
    }

    /** Every tick: the loop's volts at the horn strobe set its strobe and its horn; the horn plays the three-pulse pattern. */
    private void hornTick() {
        if(horn == null || level == null || level.isClientSide)
            return;
        if(horn.isConverged()) {
            double v = Math.abs(horn.current()) * horn.getResistance();   // the volts across the load, from its current
            hornVolts = Float.isFinite((float) v) ? (float) v : 0;
        }
        boolean strobe = hornVolts >= STROBE_VOLTS, sound = hornVolts >= HORN_VOLTS;
        var state = getBlockState();
        if(state.getBlock() instanceof ConduitBoxBlock && ConduitBoxBlock.cover(state).isHorn()) {
            var next = strobe ? ConduitCover.HORN_ON : ConduitCover.HORN_OFF;
            if(next != ConduitBoxBlock.cover(state))
                level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, next), Block.UPDATE_ALL);
        }
        if(!sound) {
            hornTimer = 0;
            return;
        }
        int t = hornTimer++ % HORN_CYCLE;
        if(t < 60 && t % 20 < 10 && t % 4 == 0)
            level.playSound(null, worldPosition, com.nolanbaker.pgmodernized.registry.ModSounds.FIRE_ALARM_HORN.get(), SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    // ---- the bulb ----

    @Nullable
    public LightBulbState bulb() {
        return bulb;
    }

    @Override
    public SwitchedWire getFilament() {
        return lamp;
    }

    @Override
    public void setPowerLevel(int level) {
        powerLevel = level;
    }

    @Override
    public int getPowerLevel() {
        return powerLevel;
    }

    /** Server side: puts a bulb in, handing back the one that was there. */
    public boolean fitBulb(Player player, ItemStack stack) {
        if(!(stack.getItem() instanceof ILightBulb kind) || !cover().isLight() || level == null || level.isClientSide)
            return false;
        if(bulb != null)
            dropBulb();
        bulb = kind.createState(this);
        if(!player.isCreative())
            stack.shrink(1);
        bulbChanged();
        return true;
    }

    /** Server side: takes the bulb out and hands it to the player. */
    public boolean takeBulb(Player player) {
        if(bulb == null || level == null || level.isClientSide)
            return false;
        var stack = bulb.toStack();
        bulb = null;
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        bulbChanged();
        return true;
    }

    /** The cover came off, or the box broke: the bulb falls out. */
    public void dropBulb() {
        if(bulb == null || level == null || level.isClientSide)
            return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, bulb.toStack());
        bulb = null;
    }

    private void bulbChanged() {
        powerLevel = 0;
        lampWatts = 0;
        if(level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        setChanged();
        sendData();
    }

    private ConduitCover cover() {
        var state = getBlockState();
        return state.getBlock() instanceof ConduitBoxBlock ? ConduitBoxBlock.cover(state) : ConduitCover.OPEN;
    }

    public float lampWatts() {
        return lampWatts;
    }

    @Override
    public void electricalTick() {
        hornTick();
        if(lamp == null || bulb == null || level == null || level.isClientSide || !lamp.isConverged())
            return;
        double watts = Math.abs(lamp.power());
        lampWatts = Float.isFinite((float) watts) ? (float) watts : 0;
        // The bulb's own physics: filament resistance with temperature, the power level, burning out.
        int before = powerLevel;
        bulb.tick();
        if(powerLevel != before)
            sendData();
        var state = getBlockState();
        if(!(state.getBlock() instanceof ConduitBoxBlock))
            return;
        var cover = ConduitBoxBlock.cover(state);
        var next = powerLevel > 0 && !bulb.isBurned() ? ConduitCover.LIGHT_ON : ConduitCover.LIGHT_OFF;
        if(next != cover)
            level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.COVER, next), Block.UPDATE_ALL);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(flipFacing && level != null && !level.isClientSide) {
            flipFacing = false;
            var state = getBlockState();
            if(state.hasProperty(ConduitBoxBlock.FACING))
                level.setBlock(worldPosition, state.setValue(ConduitBoxBlock.FACING, state.getValue(ConduitBoxBlock.FACING).getOpposite()), net.minecraft.world.level.block.Block.UPDATE_ALL);
            setChanged();
        }
        if(level != null && !level.isClientSide) {
            splices().prune();
            if(Math.abs(lampWatts - syncedWatts) > 0.5f) {
                syncedWatts = lampWatts;
                sendData();
            }
            if(cover().isDetector())
                smokeTick();
        }
    }

    // ---- splice host ----

    private boolean coverTerminals() {
        return cover().hasTerminals();
    }

    /** Any hanging wire on a cover terminal, which a node plate cannot be taken off under. */
    public boolean hasCoverWires() {
        var behaviour = getElectricBehaviour();
        if(behaviour == null)
            return false;
        for(var entry : behaviour.getConnections().entrySet()) {
            if(ConduitBoxGeometry.isFront(entry.getKey().getTerminal()) && !entry.getValue().isEmpty())
                return true;
        }
        return false;
    }

    /** The cover changed: the points come and go with the plate, and a light socket's lamp with it. */
    @Override
    public void setBlockState(BlockState state) {
        var before = cover();
        super.setBlockState(state);
        var after = cover();
        if(before.terminals() != after.terminals()) {
            points = null;
            if(level != null && !level.isClientSide)
                splices().prune();
        }
        boolean rewire = before.isLight() != after.isLight() || before.isInitiating() != after.isInitiating() || before.isHorn() != after.isHorn();
        if(rewire && level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            var cover = cover();
            if(cover.isInitiating() || cover.isHorn()) {
                String device = cover.isHorn() ? "horn" : cover.isPull() ? "pull" : "detector";
                points.add(new SplicePoint(0, Lang.builder().translate("conduit_box." + device + "_plus").style(ChatFormatting.RED).component(), com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.LOOP_PLUS_RGB));
                points.add(new SplicePoint(1, Lang.builder().translate("conduit_box." + device + "_minus").style(ChatFormatting.DARK_GRAY).component(), com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.LOOP_MINUS_RGB));
            } else if(cover.isDevice()) {
                String device = cover.isLight() ? "lamp" : "receptacle";
                points.add(new SplicePoint(0, ConductorColors.hotLabel("conduit_box." + device + "_line", 0), ConductorColors.hotRgb(0)));
                points.add(new SplicePoint(1, ConductorColors.neutralLabel("conduit_box." + device + "_neutral"), ConductorColors.neutralRgb()));
            } else for(int k = 0; k < cover.terminals(); ++k) {
                var name = Lang.builder().translate("gui.conduit_box.cover").style(ChatFormatting.GRAY).text(" ").add(ConductorColors.name(k)).component();
                points.add(new SplicePoint(k, name, ConductorColors.rgb(k)));
            }
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return ConduitBoxGeometry.isFront(terminal) && cover().exposes(terminal);
    }

    @Override
    public int hubCount() {
        return ConduitBoxGeometry.HUB_COUNT;
    }

    @Override
    public Component hubName(int hub) {
        return ConduitBoxGeometry.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return ConduitBoxGeometry.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return ConduitBoxGeometry.hubOf(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return ConduitBoxGeometry.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return ConduitBoxGeometry.conductorHub(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return ConduitBoxGeometry.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= hubCount() ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(MOUNT_FACING_KEY, true);
        splices().write(tag);
        writeBulb(tag);
        tag.putFloat("LampWatts", lampWatts);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
        writeBulb(tag);
        tag.putFloat("LampWatts", lampWatts);
    }

    private void writeBulb(CompoundTag tag) {
        if(bulb != null) {
            var b = new CompoundTag();
            bulb.write(b);
            tag.put("Bulb", b);
        }
        tag.putInt("PowerLevel", powerLevel);
        tag.putBoolean("BulbKept", true);
        tag.putBoolean("SmokeAlarm", smokeAlarm);
        tag.putInt("SmokeClear", smokeClearTicks);
        tag.putBoolean("Pulled", pulled);
        tag.putFloat("HornVolts", hornVolts);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // Boxes saved before 0.53.4 faced out of their mount; the facing points into it now.
        if(!clientPacket && !tag.getBoolean(MOUNT_FACING_KEY))
            flipFacing = true;
        splices().read(tag);
        powerLevel = tag.getInt("PowerLevel");
        smokeAlarm = tag.getBoolean("SmokeAlarm");
        smokeClearTicks = tag.getInt("SmokeClear");
        pulled = tag.getBoolean("Pulled");
        hornVolts = tag.getFloat("HornVolts");
        if(detector != null)
            detector.setState(contactClosed());
        if(clientPacket)
            lampWatts = tag.getFloat("LampWatts");
        if(tag.contains("Bulb")) {
            var b = tag.getCompound("Bulb");
            var item = LightBulbState.getBulbItem(b);
            if(bulb == null || !bulb.isOf(item)) {
                bulb = item instanceof ILightBulb kind ? kind.createState(this) : null;
                if(!clientPacket && level != null && !level.isClientSide && electricBehaviour != null)
                    electricBehaviour.rebuildCircuit(false);
            }
            if(bulb != null) {
                if(lamp == null && electricBehaviour != null)
                    electricBehaviour.rebuildCircuit(false);
                if(lamp != null)
                    bulb.read(b);
                else
                    pendingBulb = b.copy();
            }
        } else if(!tag.getBoolean("BulbKept") && !clientPacket && cover().isLight()) {
            // A light socket from before bulbs were real: it had a lamp built in, so it keeps a standard bulb.
            bulb = ModdedItems.LIGHT_BULB.get().createState(this);
        } else if(!tag.contains("Bulb")) {
            bulb = null;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.conduit_box.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(cover().isLight() && bulb == null)
            Lang.builder().translate("gui.conduit_box.no_bulb").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        if(cover().isLight() && bulb != null) {
            boolean lit = cover() == ConduitCover.LIGHT_ON;
            Lang.builder().add(bulb.toStack().getHoverName().copy()).style(ChatFormatting.WHITE).text(": ")
                    .add(Lang.builder().translate(lit ? "gui.conduit_box.lamp_lit" : "gui.conduit_box.lamp_dark", String.format("%.0f", lampWatts))
                            .style(lit ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        }
        if(cover().isDetector())
            Lang.builder().translate(smokeAlarm ? "gui.conduit_box.smoke_alarm" : "gui.conduit_box.smoke_normal").style(smokeAlarm ? ChatFormatting.RED : ChatFormatting.GREEN).forGoggles(tooltip, 1);
        if(cover().isPull())
            Lang.builder().translate(pulled ? "gui.conduit_box.pull_pulled" : "gui.conduit_box.pull_set").style(pulled ? ChatFormatting.RED : ChatFormatting.GREEN).forGoggles(tooltip, 1);
        if(cover().isHorn()) {
            String key = hornVolts >= HORN_VOLTS ? "gui.conduit_box.horn_sounding" : hornVolts >= STROBE_VOLTS ? "gui.conduit_box.horn_strobe" : "gui.conduit_box.horn_quiet";
            Lang.builder().translate(key, String.format("%.0f", hornVolts)).style(hornVolts >= STROBE_VOLTS ? ChatFormatting.RED : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
