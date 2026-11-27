package com.nolanbaker.pgmodernized.device.light;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.bus.IBusJoin;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.bus.IBusLinkHost;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.light.bulb.IFixtureEntity;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.light.bulb.LightBulbState;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.light.StripLightBlock.*;

/**
 * One section of strip light: a socket holding a Power Grid bulb across L and N, with the bulb's
 * own physics, and the factory light's trick for the light itself: nothing glows at the fixture;
 * a hidden glow block is kept on the first floor below it, at the bulb's power level, so a high
 * ceiling lights the room rather than the rafters. Sections in a row along the same axis join
 * L to L and N to N through hidden bus links.
 */
public class StripLightBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IFixtureEntity, IBusJoin, IBusLinkHost, IHaveGoggleInformation, IGlowOwner {
    /** How far down the floor is looked for. */
    public static final int PROJECTION = 32;

    private DeviceSpliceHost deviceHubs;
    private SwitchedWire lamp;
    @Nullable
    private LightBulbState bulb;
    @Nullable
    private CompoundTag pendingBulb;
    private int powerLevel;
    private float watts, syncedWatts;
    @Nullable
    private BlockPos glowPos;

    public StripLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    // ---- the knockout ----

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return hub == HUB_NORTH || hub == HUB_SOUTH ? ConduitSize.HALF : null;
    }

    @Override
    public boolean hubTakes(int hub, ItemStack stack) {
        return com.nolanbaker.pgmodernized.device.hvac.LineSetItem.kindOf(stack) == null;
    }

    @Override
    public int landings(int hub) {
        return ConductorColors.COUNT;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("strip_light.hub").component();
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        lamp = builder.connectSwitch(bulb != null ? bulb.resistance() : 1e6f, builder.terminalNode(L), builder.terminalNode(N), bulb != null);
        if(pendingBulb != null && bulb != null) {
            bulb.read(pendingBulb);
            pendingBulb = null;
        }
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

    @Nullable
    public LightBulbState bulb() {
        return bulb;
    }

    public boolean lit() {
        return powerLevel > 0 && bulb != null && !bulb.isBurned();
    }

    /**
     * Power Grid's bulb reports 0 (dark), 1 (warm, under-run) or 2 (full), not a light level; its
     * own fixtures light at 10 and 15 for the two, and so do ours.
     */
    public int lightLevel() {
        if(!lit())
            return 0;
        return powerLevel >= 2 ? 15 : 10;
    }

    @Override
    public void electricalTick() {
        if(lamp == null || bulb == null || level == null || level.isClientSide || !lamp.isConverged())
            return;
        double w = Math.abs(lamp.power());
        watts = Float.isFinite((float) w) ? (float) w : 0;
        int before = powerLevel;
        bulb.tick();
        if(powerLevel != before) {
            sendData();
            projectDown();
        }
    }

    // ---- the bulb ----

    /** Server side: puts a bulb in, handing back the one that was there. */
    public boolean fitBulb(Player player, ItemStack stack) {
        if(!(stack.getItem() instanceof ILightBulb kind) || level == null || level.isClientSide)
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

    /** The fixture broke: the bulb falls out. */
    public void dropBulb() {
        if(bulb == null || level == null || level.isClientSide)
            return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, bulb.toStack());
        bulb = null;
    }

    private void bulbChanged() {
        powerLevel = 0;
        watts = 0;
        if(level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        projectDown();
        setChanged();
        sendData();
    }

    // ---- the glow on the floor ----

    /** Keeps one glow block on the first floor below at the bulb's power level, and none when dark. */
    private void projectDown() {
        if(level == null || level.isClientSide)
            return;
        int wanted = lightLevel();
        BlockPos target = null;
        if(wanted > 0) {
            for(int i = 1; i <= PROJECTION; ++i) {
                var p = worldPosition.below(i);
                if(!level.isLoaded(p))
                    break;
                var s = level.getBlockState(p);
                if(s.is(ModBlocks.STRIP_LIGHT_GLOW.get()) || s.isAir())
                    continue;
                if(i > 1)
                    target = p.above();
                break;
            }
        }
        if(glowPos != null && !glowPos.equals(target))
            removeGlow();
        if(target == null)
            return;
        var s = level.getBlockState(target);
        if(s.is(ModBlocks.STRIP_LIGHT_GLOW.get())) {
            if(s.getValue(StripLightGlowBlock.LEVEL) != wanted)
                level.setBlock(target, s.setValue(StripLightGlowBlock.LEVEL, wanted), 3);
        } else if(s.isAir()) {
            level.setBlock(target, ModBlocks.STRIP_LIGHT_GLOW.getDefaultState().setValue(StripLightGlowBlock.LEVEL, wanted), 3);
        } else {
            return;
        }
        if(!target.equals(glowPos)) {
            glowPos = target;
            setChanged();
        }
    }

    public void removeGlow() {
        if(glowPos != null && level != null && !level.isClientSide && level.getBlockState(glowPos).is(ModBlocks.STRIP_LIGHT_GLOW.get()))
            level.removeBlock(glowPos, false);
        glowPos = null;
    }

    /** Whether the glow at that position is this section's: the glow asks before it stays. */
    @Override
    public boolean ownsGlow(BlockPos pos) {
        return pos.equals(glowPos) && lit();
    }

    // ---- sections join along the axis ----

    @Override
    public int busJoin(net.minecraft.core.Direction side, int k) {
        var state = getBlockState();
        if(!(state.getBlock() instanceof StripLightBlock) || side.getAxis() != facing(state).getAxis())
            return -1;
        return k == 0 ? L : k == 1 ? N : -1;
    }

    @Override
    public boolean busLinkValid(int terminal, BlockPos other, int otherTerminal) {
        return level != null && BusLinks.joinValid(level, worldPosition, terminal, other, otherTerminal);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        var state = getBlockState();
        if(state.getBlock() instanceof StripLightBlock) {
            var axis = facing(state);
            BusLinks.join(level, worldPosition, this, axis);
            BusLinks.join(level, worldPosition, this, axis.getOpposite());
        }
        deviceHubs().lazyTick();
        projectDown();
        if(Math.abs(watts - syncedWatts) > 0.5f) {
            syncedWatts = watts;
            sendData();
        }
    }

    @Override
    public void destroy() {
        dropBulb();
        removeGlow();
        super.destroy();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.strip_light.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(bulb == null)
            Lang.builder().translate("gui.conduit_box.no_bulb").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().add(bulb.toStack().getHoverName().copy()).style(ChatFormatting.WHITE).text(": ")
                    .add(Lang.builder().translate(lit() ? "gui.conduit_box.lamp_lit" : "gui.conduit_box.lamp_dark", String.format("%.0f", watts))
                            .style(lit() ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    // ---- nbt ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putInt("Power", powerLevel);
        tag.putFloat("Watts", watts);
        if(bulb != null) {
            var b = new CompoundTag();
            bulb.write(b);
            tag.put("Bulb", b);
        }
        if(glowPos != null)
            tag.put("Glow", NbtUtils.writeBlockPos(glowPos));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        powerLevel = tag.getInt("Power");
        watts = tag.getFloat("Watts");
        glowPos = NbtUtils.readBlockPos(tag, "Glow").orElse(null);
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
        } else {
            bulb = null;
        }
    }
}
