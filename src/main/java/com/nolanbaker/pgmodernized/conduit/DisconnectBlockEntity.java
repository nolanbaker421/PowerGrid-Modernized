package com.nolanbaker.pgmodernized.conduit;

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
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.conduit.DisconnectBlock.*;

/** Three contacts thrown together by one handle, four knockouts spliced to the lugs in the editor. */
public class DisconnectBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    private List<SplicePoint> points;
    /** Made in buildCircuit, which Power Grid calls from the super constructor before field initialisers run. */
    private SwitchedWire[] contacts;
    private boolean on, locked;

    public DisconnectBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        builder.setTerminalCount(TERMINAL_COUNT);
        splices().buildCircuit(builder);
        contacts = new SwitchedWire[POLES];
        for(int k = 0; k < POLES; ++k)
            contacts[k] = builder.connectSwitch(resistance("contact"), builder.terminalNode(LINE_BASE + k), builder.terminalNode(LOAD_BASE + k), on);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private void setContacts() {
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

    private void message(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    private void syncState() {
        if(level == null)
            return;
        var state = getBlockState();
        if(state.getBlock() instanceof DisconnectBlock)
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

    /** A lockout hasp goes on an open switch only; the item is used up until the lock comes off. */
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

    /** Server side: takes the lock off and hands it back. */
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

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            splices().prune();
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            var list = new ArrayList<SplicePoint>();
            for(int k = 0; k < POLES; ++k)
                list.add(new SplicePoint(LINE_BASE + k, lineName(k), IDecoratedTerminal.RED));
            for(int k = 0; k < POLES; ++k)
                list.add(new SplicePoint(LOAD_BASE + k, loadName(k), IDecoratedTerminal.BLUE));
            points = List.copyOf(list);
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return isLug(terminal);
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.TWO;
    }

    @Override
    public int hubCount() {
        return HUBS;
    }

    @Override
    public Component hubName(int hub) {
        return DisconnectBlock.hubName(hub);
    }

    @Override
    public int hubTerminal(int hub) {
        return HUB_BASE + hub;
    }

    @Override
    public int hubAt(int terminal) {
        return isHub(terminal) ? terminal - HUB_BASE : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return CONDUCTOR_BASE + hub * ConductorColors.COUNT + conductor;
    }

    @Override
    public int hubOf(int terminal) {
        return terminal >= CONDUCTOR_BASE && terminal < TERMINAL_COUNT ? (terminal - CONDUCTOR_BASE) / ConductorColors.COUNT : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return (terminal - CONDUCTOR_BASE) % ConductorColors.COUNT;
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= HUBS ? null : SpliceSupport.runAt(this, hubTerminal(hub));
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        splices().write(tag);
        tag.putBoolean("On", on);
        tag.putBoolean("Locked", locked);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
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
        Lang.builder().translate("gui.disconnect.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(on ? "gui.disconnect.on" : "gui.disconnect.off").style(on ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        if(locked)
            Lang.builder().translate("gui.disconnect.locked").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
