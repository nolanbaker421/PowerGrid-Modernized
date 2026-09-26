package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.conduit.ConduitSwitchBlock.*;

/**
 * One hub, two poles and a contact between them. Whenever the set of wires pulled through the
 * run changes, the first two are spliced to the poles automatically, so there is nothing to edit.
 */
public class ConduitSwitchBlockEntity extends ElectricBlockEntity implements ISpliceHost, IHaveGoggleInformation {
    private SpliceSupport splices;
    private List<SplicePoint> points;
    private SwitchedWire contact;
    private boolean on;
    private int landedKey = -1;

    public ConduitSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        contact = builder.connectSwitch(resistance("contact"), builder.terminalNode(TERMINAL_POLE_A), builder.terminalNode(TERMINAL_POLE_B), on);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    public boolean isOn() {
        return on;
    }

    /** Server side. */
    public void toggle(Player player) {
        on = !on;
        if(contact != null)
            contact.setState(on);
        if(level != null) {
            var state = getBlockState();
            if(state.getBlock() instanceof ConduitSwitchBlock)
                level.setBlock(worldPosition, state.setValue(ON, on), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 0.6f : 0.5f);
        }
        setChanged();
        sendData();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        splices().prune();
        var run = hubRun(0);
        int key = 0;
        if(run != null) {
            for(var conductor : run.conductors())
                key |= 1 << conductor.slot();
        }
        if(key != landedKey) {
            landedKey = key;
            if(key != 0)
                splices().land(0);
        }
    }

    // ---- splice host ----

    @Override
    public List<SplicePoint> points() {
        if(points == null) {
            points = List.of(
                    new SplicePoint(TERMINAL_POLE_A, Lang.builder().translate("conduit_switch.line").style(ChatFormatting.RED).component(), IDecoratedTerminal.RED),
                    new SplicePoint(TERMINAL_POLE_B, Lang.builder().translate("conduit_switch.load").style(ChatFormatting.BLUE).component(), IDecoratedTerminal.BLUE));
        }
        return points;
    }

    @Override
    public boolean isPoint(int terminal) {
        return terminal == TERMINAL_POLE_A || terminal == TERMINAL_POLE_B;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public int hubCount() {
        return 1;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("conduit_socket.hub").style(ChatFormatting.AQUA).component();
    }

    @Override
    public int hubTerminal(int hub) {
        return TERMINAL_HUB;
    }

    @Override
    public int hubAt(int terminal) {
        return terminal == TERMINAL_HUB ? 0 : -1;
    }

    @Override
    public int conductorTerminal(int hub, int conductor) {
        return CONDUCTOR_BASE + conductor;
    }

    @Override
    public int hubOf(int terminal) {
        return terminal >= CONDUCTOR_BASE && terminal < TERMINAL_COUNT ? 0 : -1;
    }

    @Override
    public int conductorOf(int terminal) {
        return terminal - CONDUCTOR_BASE;
    }

    @Override
    public @Nullable ConduitRunEntity hubRun(int hub) {
        return hub == 0 ? SpliceSupport.runAt(this, TERMINAL_HUB) : null;
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        splices().write(tag);
        tag.putBoolean("On", on);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        splices().write(tag);
        tag.putBoolean("On", on);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        splices().read(tag);
        on = tag.getBoolean("On");
        if(contact != null)
            contact.setState(on);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.conduit_switch.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(on ? "gui.conduit_switch.on" : "gui.conduit_switch.off").style(on ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 1);
        splices().addGoggleLines(tooltip);
        return true;
    }
}
