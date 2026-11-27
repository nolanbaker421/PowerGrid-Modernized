package com.nolanbaker.pgmodernized.device.fixture;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.device.light.IGlowOwner;
import com.nolanbaker.pgmodernized.device.light.StripLightBlockEntity;
import com.nolanbaker.pgmodernized.device.light.StripLightGlowBlock;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.nbt.NbtUtils;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.light.bulb.IFixtureEntity;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.light.bulb.LightBulbState;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.fixture.CustomFixtureBlock.*;

/**
 * A placed designed fixture. Its sockets all take the same lamp: one bulb item per socket goes
 * in, and one filament per socket is built across L and N, so a four-socket chandelier draws four
 * bulbs' worth while the bulb's own physics see one socket's share. The block lights up at the
 * bulb's level and the glow-material parts of the design light with it.
 */
public class CustomFixtureBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IFixtureEntity, IHaveGoggleInformation, IGlowOwner {
    // No initialisers here: ElectricBlockEntity's constructor calls buildCircuit before they would
    // run, so anything set there would be wiped, and anything read there would be null.
    private DeviceSpliceHost deviceHubs;
    @Nullable
    private FixtureDesign design;
    @Nullable
    private SwitchedWire[] filaments;
    @Nullable
    private LightBulbState bulb;
    @Nullable
    private CompoundTag pendingBulb;
    private int powerLevel;
    private float watts, syncedWatts;
    @Nullable
    private VoxelShape shape;
    private Direction shapeFacing;
    /** The glow this fixture keeps on the floor below its lamps while lit, or null. */
    @Nullable
    private BlockPos glowPos;

    public CustomFixtureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    // ---- the design ----

    public FixtureDesign design() {
        if(design == null)
            design = new FixtureDesign();
        return design;
    }

    private SwitchedWire[] filaments() {
        return filaments == null ? new SwitchedWire[0] : filaments;
    }

    public void setDesign(FixtureDesign design) {
        this.design = design.copy();
        shape = null;
        if(level != null && !level.isClientSide) {
            if(bulb != null && sockets() == 0)
                dropBulbs();
            if(electricBehaviour != null)
                electricBehaviour.rebuildCircuit(false);
            setChanged();
            sendData();
        }
    }

    /** The fixture as the item that places it again, design on board. */
    public ItemStack toItem() {
        var stack = new ItemStack(ModItems.CUSTOM_FIXTURE.get());
        design().putOn(stack);
        return stack;
    }

    /** The design's parts inside the anchor block, turned to the facing; cached until either changes. */
    public VoxelShape shape(Direction facing) {
        if(shape == null || shapeFacing != facing) {
            shape = CustomFixtureBlock.rotate(design().anchorShape(), facing);
            shapeFacing = facing;
        }
        return shape;
    }

    /** The render box: the whole three-block design cube round the anchor. */
    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }

    public int sockets() {
        return design().socketCount();
    }

    // ---- the knockout ----

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return hub >= 0 && hub < HUB_COUNT ? ConduitSize.HALF : null;
    }

    @Override
    public boolean hubTakes(int hub, ItemStack stack) {
        return com.nolanbaker.pgmodernized.device.hvac.LineSetItem.kindOf(stack) == null;
    }

    @Override
    public int landings(int hub) {
        return ConductorColors.COUNT;
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        int n = Math.max(1, sockets());
        var built = new SwitchedWire[n];
        for(int i = 0; i < n; ++i)
            built[i] = builder.connectSwitch(bulb != null ? bulb.resistance() : 1e6f, builder.terminalNode(L), builder.terminalNode(N), bulb != null);
        filaments = built;
        if(pendingBulb != null && bulb != null) {
            bulb.read(pendingBulb);
            pendingBulb = null;
        }
    }

    /** One socket's filament: the bulb's physics see one socket's share of the power. */
    @Override
    public SwitchedWire getFilament() {
        var f = filaments();
        return f.length > 0 ? f[0] : null;
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
        var filaments = filaments();
        if(filaments.length == 0 || bulb == null || level == null || level.isClientSide || !filaments[0].isConverged())
            return;
        double w = 0;
        for(var f : filaments)
            w += Math.abs(f.power());
        watts = Float.isFinite((float) w) ? (float) w : 0;
        int before = powerLevel;
        bulb.tick();
        if(powerLevel != before) {
            sendData();
            updateLight();
        }
    }

    private void updateLight() {
        if(level == null || level.isClientSide)
            return;
        var state = getBlockState();
        if(!(state.getBlock() instanceof CustomFixtureBlock))
            return;
        int wanted = lightLevel();
        if(state.getValue(LIGHT) != wanted)
            level.setBlock(worldPosition, state.setValue(LIGHT, wanted), Block.UPDATE_ALL);
        projectDown();
    }

    // ---- the glow on the floor ----

    /**
     * The block the lowest lamp holder (socket or text) sits in, straight under the anchor; a
     * pendant's bulb hangs a block below the anchor, so its light should start there.
     */
    private BlockPos lampBlock() {
        int lowest = FixturePart.ANCHOR_MIN;
        for(var p : design().parts)
            if(p.kind == FixturePart.Kind.SOCKET || p.kind == FixturePart.Kind.TEXT)
                lowest = Math.min(lowest, p.y);
        return worldPosition.below(Math.floorDiv(FixturePart.ANCHOR_MIN - lowest + 15, 16));
    }

    /**
     * Like the strip light: a lit fixture keeps a glow block on the first floor below its lamps,
     * so the room is lit from the floor up and not just from the ceiling down. Server side.
     */
    private void projectDown() {
        if(level == null || level.isClientSide)
            return;
        int wanted = lightLevel();
        BlockPos target = null;
        if(wanted > 0) {
            var from = lampBlock();
            for(int i = 0; i <= StripLightBlockEntity.PROJECTION; ++i) {
                var p = from.below(i);
                if(!level.isLoaded(p))
                    break;
                var s = level.getBlockState(p);
                if(p.equals(worldPosition) || s.is(ModBlocks.STRIP_LIGHT_GLOW.get()) || s.isAir())
                    continue;
                if(i > 0 && !p.above().equals(worldPosition))
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
                level.setBlock(target, s.setValue(StripLightGlowBlock.LEVEL, wanted), Block.UPDATE_ALL);
        } else if(s.isAir()) {
            level.setBlock(target, ModBlocks.STRIP_LIGHT_GLOW.getDefaultState().setValue(StripLightGlowBlock.LEVEL, wanted), Block.UPDATE_ALL);
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

    @Override
    public boolean ownsGlow(BlockPos pos) {
        return pos.equals(glowPos) && lit();
    }

    // ---- bulbs: one per socket, all the same ----

    /** Server side: fills every socket from the stack, or says how many it wants. */
    public boolean fitBulbs(Player player, ItemStack stack) {
        if(!(stack.getItem() instanceof ILightBulb kind) || level == null || level.isClientSide)
            return false;
        int n = sockets();
        if(n == 0) {
            player.displayClientMessage(Lang.translate("message.fixture.no_sockets").style(ChatFormatting.RED).component(), true);
            return false;
        }
        if(!player.isCreative() && stack.getCount() < n) {
            player.displayClientMessage(Lang.builder().translate("message.fixture.needs_bulbs", n).style(ChatFormatting.RED).component(), true);
            return false;
        }
        if(bulb != null)
            dropBulbs();
        bulb = kind.createState(this);
        if(!player.isCreative())
            stack.shrink(n);
        bulbChanged();
        return true;
    }

    /** Server side: empties the sockets into the player's hands. */
    public boolean takeBulbs(Player player) {
        if(bulb == null || level == null || level.isClientSide)
            return false;
        var stack = bulb.toStack();
        stack.setCount(Math.max(1, sockets()));
        bulb = null;
        if(!player.getInventory().add(stack))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        bulbChanged();
        return true;
    }

    public void dropBulbs() {
        if(bulb == null || level == null || level.isClientSide)
            return;
        var stack = bulb.toStack();
        stack.setCount(Math.max(1, sockets()));
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        bulb = null;
    }

    private void bulbChanged() {
        powerLevel = 0;
        watts = 0;
        if(level != null && !level.isClientSide && electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        updateLight();
        setChanged();
        sendData();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        projectDown();
        if(Math.abs(watts - syncedWatts) > 0.5f) {
            syncedWatts = watts;
            sendData();
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var design = design();
        Lang.builder().add(Component.literal(design.name.isEmpty() ? "Fixture" : design.name)).style(ChatFormatting.GRAY).forGoggles(tooltip);
        int n = sockets();
        if(n == 0)
            Lang.builder().translate("gui.fixture.no_sockets").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        else if(bulb == null)
            Lang.builder().translate("gui.fixture.no_bulbs", n).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        else
            Lang.builder().text(n + "x ").add(bulb.toStack().getHoverName().copy()).style(ChatFormatting.WHITE).text(": ")
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
        tag.put("Design", design().write());
        tag.putInt("Power", powerLevel);
        tag.putFloat("Watts", watts);
        if(glowPos != null)
            tag.put("Glow", NbtUtils.writeBlockPos(glowPos));
        if(bulb != null) {
            var b = new CompoundTag();
            bulb.write(b);
            tag.put("Bulb", b);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(tag.contains("Design")) {
            design = FixtureDesign.read(tag.getCompound("Design"));
            shape = null;
        }
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
                if(filaments().length == 0 && electricBehaviour != null)
                    electricBehaviour.rebuildCircuit(false);
                if(filaments().length > 0)
                    bulb.read(b);
                else
                    pendingBulb = b.copy();
            }
        } else {
            bulb = null;
        }
    }
}
