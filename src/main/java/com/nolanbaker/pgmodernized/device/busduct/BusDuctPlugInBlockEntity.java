package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.bus.BusLinks;
import com.nolanbaker.pgmodernized.bus.IBusLinkHost;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.busduct.BusDuctPlugInBlock.*;

/**
 * Three contacts and a neutral through-path from the stabs to the lugs. Every second the unit
 * links its stabs to the bus of the duct it sits on, and a link dies with the duct or the unit.
 */
public class BusDuctPlugInBlockEntity extends ElectricBlockEntity implements ISpliceHost, IBusLinkHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    private List<SplicePoint> points;
    /** Made in buildCircuit, which Power Grid calls from the super constructor before field initialisers run. */
    protected SwitchedWire[] contacts;
    protected boolean on, locked;

    public BusDuctPlugInBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        builder.setTerminalCount(LAYOUT.terminalCount());
        splices().buildCircuit(builder);
        contacts = new SwitchedWire[POLES];
        for(int k = 0; k < POLES; ++k)
            contacts[k] = builder.connectSwitch(resistance("contact"), builder.terminalNode(STAB_BASE + k), builder.terminalNode(LOAD_BASE + k), on);
        builder.connect(resistance("contact"), builder.terminalNode(STAB_BASE + 3), builder.terminalNode(LOAD_BASE + 3));
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    protected void setContacts() {
        if(contacts == null)
            return;
        for(var contact : contacts)
            if(contact != null)
                contact.setState(on);
    }

    public boolean isOn() {
        return on;
    }

    public boolean isLocked() {
        return locked;
    }

    // ---- the duct ----

    public BlockPos ductPos() {
        var state = getBlockState();
        return state.getBlock() instanceof BusDuctPlugInBlock ? worldPosition.relative(BusDuctPlugInBlock.facing(state)) : worldPosition;
    }

    /** Whether the link from stab {@code stab} to conductor {@code ductTerminal} of the duct at {@code duct} is this unit's. */
    public boolean tapValid(BlockPos duct, int stab, int ductTerminal) {
        return duct.equals(ductPos()) && ductTerminal >= 0 && ductTerminal < BusDuctBlock.BUS_COUNT && stab == STAB_BASE + ductTerminal;
    }

    @Override
    public boolean busLinkValid(int terminal, BlockPos other, int otherTerminal) {
        return level != null && tapValid(other, terminal, otherTerminal) && level.getBlockEntity(other) instanceof BusDuctBlockEntity;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        var duct = ductPos();
        if(level.getBlockEntity(duct) instanceof BusDuctBlockEntity) {
            for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k)
                BusLinks.ensure(level, worldPosition, STAB_BASE + k, duct, k);
        }
    }

    // ---- the handle ----

    protected void message(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    protected void syncState() {
        if(level == null)
            return;
        var state = getBlockState();
        if(state.getBlock() instanceof BusDuctPlugInBlock)
            level.setBlock(worldPosition, state.setValue(ON, on).setValue(LOCKED, locked), Block.UPDATE_ALL);
        setChanged();
        sendData();
    }

    /** Server side: throws the handle unless it is locked out. */
    public void toggle(Player player) {
        if(locked) {
            message(player, Lang.builder().translate("message.disconnect.locked").style(ChatFormatting.RED).component());
            return;
        }
        on = !on;
        setContacts();
        if(level != null)
            level.playSound(null, worldPosition, on ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, on ? 0.7f : 0.6f);
        syncState();
    }

    public boolean lock(Player player, ItemStack stack) {
        if(locked) {
            message(player, Lang.builder().translate("message.disconnect.already_locked").style(ChatFormatting.RED).component());
            return false;
        }
        if(on) {
            message(player, Lang.builder().translate("message.disconnect.lock_open").style(ChatFormatting.RED).component());
            return false;
        }
        if(level == null || level.isClientSide)
            return true;
        locked = true;
        if(!player.isCreative())
            stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.0f);
        message(player, Lang.builder().translate("message.disconnect.locked_on").style(ChatFormatting.GRAY).component());
        syncState();
        return true;
    }

    public void unlock(Player player) {
        if(!locked || level == null)
            return;
        locked = false;
        var hasp = ModItems.BREAKER_LOCK.asStack();
        if(!player.isCreative() && !player.getInventory().add(hasp))
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, hasp);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
        message(player, Lang.builder().translate("message.disconnect.unlocked").style(ChatFormatting.GRAY).component());
        syncState();
    }

    /** The hottest pole, amperes. */
    public float current() {
        float current = 0;
        if(contacts != null) {
            for(var contact : contacts) {
                if(contact == null || !contact.isConverged())
                    continue;
                float amps = (float) Math.abs(contact.current());
                if(Float.isFinite(amps))
                    current = Math.max(current, amps);
            }
        }
        return current;
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            var list = new ArrayList<SplicePoint>();
            for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k)
                list.add(new SplicePoint(LOAD_BASE + k, loadName(k), BusDuctBlock.busRgb(k)));
            points = List.copyOf(list);
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return LAYOUT.isPoint(terminal);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.TWO;
    }

    @Override
    public int hubCount() {
        return LAYOUT.hubCount();
    }

    @Override
    public Component hubName(int hub) {
        return DeviceHubs.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return LAYOUT.hubTerminal(hub);
    }

    @Override
    public int hubAt(int terminal) {
        return LAYOUT.hubAt(terminal);
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return LAYOUT.conductorTerminal(hub, conductor);
    }

    @Override
    public int hubOf(int terminal) {
        return LAYOUT.hubOf(terminal);
    }

    @Override
    public int conductorOf(int terminal) {
        return LAYOUT.conductorOf(terminal);
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= LAYOUT.hubCount() ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeAll(tag);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeAll(tag);
    }

    private void writeAll(CompoundTag tag) {
        splices().write(tag);
        tag.putBoolean("On", on);
        tag.putBoolean("Locked", locked);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        splices().read(tag);
        on = tag.getBoolean("On");
        locked = tag.getBoolean("Locked");
        setContacts();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.bus_duct_plug_in.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        var line = Lang.builder().translate(on ? "gui.disconnect.on" : "gui.disconnect.off").style(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        if(on)
            line.text("  ").add(Lang.builder().text(String.format("%.1f ", current())).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD));
        line.forGoggles(tooltip, 1);
        if(locked)
            Lang.builder().translate("gui.disconnect.locked").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
