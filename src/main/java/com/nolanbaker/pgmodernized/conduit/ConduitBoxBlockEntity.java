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

    private SpliceSupport splices;
    private List<SplicePoint> points;
    /** The filament of the bulb in a light socket cover, made in buildCircuit; null under any other cover or with no bulb. */
    private SwitchedWire lamp;
    private float lampWatts;
    /** The bulb in the socket: any of Power Grid's. Its state sets the filament's resistance and the power level. */
    @Nullable
    private LightBulbState bulb;
    private int powerLevel;
    /** Bulb data read before the filament wire existed; applied when the circuit is next built. */
    @Nullable
    private CompoundTag pendingBulb;

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
        if(level != null && !level.isClientSide)
            splices().prune();
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
        if(before.isLight() != after.isLight() && level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
    }

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = new ArrayList<>();
            var cover = cover();
            if(cover.isDevice()) {
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
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
        writeBulb(tag);
    }

    private void writeBulb(CompoundTag tag) {
        if(bulb != null) {
            var b = new CompoundTag();
            bulb.write(b);
            tag.put("Bulb", b);
        }
        tag.putInt("PowerLevel", powerLevel);
        tag.putBoolean("BulbKept", true);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // Boxes saved before 0.53.4 faced out of their mount; the facing points into it now.
        if(!clientPacket && !tag.getBoolean(MOUNT_FACING_KEY))
            flipFacing = true;
        splices().read(tag);
        powerLevel = tag.getInt("PowerLevel");
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
        if(cover().isLight() && bulb != null)
            Lang.builder().add(bulb.toStack().getHoverName().copy()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(cover().isLight()) {
            boolean lit = cover() == ConduitCover.LIGHT_ON;
            Lang.builder().translate(lit ? "gui.conduit_box.lamp_lit" : "gui.conduit_box.lamp_dark", String.format("%.0f", lampWatts)).style(lit ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
