package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.nolanbaker.pgmodernized.util.ShockDamage;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.pole.CutoutBlock.*;

/**
 * The cutout's poles. Each carries a link of some amps, or none, in a tube that is closed or
 * hanging open; a closed tube with a good link conducts through a contact. The link heats on the
 * breaker curve against its rating and blows: the tube drops. An open gap, blown or thrown, that
 * sees more voltage than its class holds off flashes over: an arc conducts across it, sparks,
 * and the fault stays on until the line is killed upstream or the holder is swapped for the
 * right class.
 */
public class CutoutBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private int[] link;
    private boolean[] open, blown, arcing;
    private float[] heat, amps, volts;
    private SwitchedWire[] contacts, arcs;
    private ElectricWire[] senses;
    private AcReadings.Filter[] currentReading, gapReading;
    private int sparkTimer, syncTimer;
    private boolean[] syncedBlown;

    public CutoutBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    /** No initialisers: buildCircuit runs from the superclass constructor, before field initialisers would. */
    private void ensureArrays() {
        if(link == null) {
            link = new int[MAX_POLES];
            open = new boolean[MAX_POLES];
            blown = new boolean[MAX_POLES];
            arcing = new boolean[MAX_POLES];
            heat = new float[MAX_POLES];
            amps = new float[MAX_POLES];
            volts = new float[MAX_POLES];
            syncedBlown = new boolean[MAX_POLES];
            contacts = new SwitchedWire[MAX_POLES];
            arcs = new SwitchedWire[MAX_POLES];
            senses = new ElectricWire[MAX_POLES];
            currentReading = new AcReadings.Filter[MAX_POLES];
            gapReading = new AcReadings.Filter[MAX_POLES];
            for(int k = 0; k < MAX_POLES; ++k) {
                currentReading[k] = new AcReadings.Filter();
                gapReading[k] = new AcReadings.Filter();
            }
        }
    }

    public CutoutClass cutoutClass() {
        return getBlockState().getBlock() instanceof CutoutBlock block ? block.cutoutClass() : CutoutClass.KV15;
    }

    public int poles() {
        var state = getBlockState();
        return state.hasProperty(POLES) ? state.getValue(POLES) : 1;
    }

    // ---- circuit ----

    private boolean conducting(int k) {
        return link[k] > 0 && !open[k] && !blown[k];
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureArrays();
        builder.setTerminalCount(TERMINAL_COUNT);
        for(int k = 0; k < MAX_POLES; ++k) {
            var line = builder.terminalNode(LINE_BASE + k);
            var load = builder.terminalNode(LOAD_BASE + k);
            contacts[k] = builder.connectSwitch(resistance("contact"), line, load, conducting(k));
            arcs[k] = builder.connectSwitch(resistance("arc"), line, load, arcing[k]);
            senses[k] = builder.connect(resistance("sense"), line, load);
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private void applyContacts() {
        if(contacts == null)
            return;
        for(int k = 0; k < MAX_POLES; ++k) {
            if(contacts[k] != null)
                contacts[k].setState(conducting(k));
            if(arcs[k] != null)
                arcs[k].setState(arcing[k]);
        }
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || contacts == null)
            return;
        for(int k = 0; k < poles(); ++k) {
            var carrying = arcing[k] ? arcs[k] : contacts[k];
            if(carrying != null && carrying.isConverged())
                currentReading[k].sample(carrying);
            if(senses[k] != null && senses[k].isConverged())
                gapReading[k].sample(senses[k]);
            amps[k] = conducting(k) || arcing[k] ? (float) AcReadings.finite(currentReading[k].rmsCurrent()) : 0;
            volts[k] = (float) AcReadings.finite(gapReading[k].rmsVoltage());
        }
    }

    @Override
    public void tick() {
        ensureArrays();
        super.tick();
        if(level == null)
            return;
        if(level.isClientSide) {
            clientSparks();
            return;
        }
        boolean changed = false;
        var cls = cutoutClass();
        for(int k = 0; k < poles(); ++k) {
            if(conducting(k)) {
                heat[k] = BreakerTripCurve.step(heat[k], amps[k], link[k]);
                if(BreakerTripCurve.trips(heat[k], amps[k], link[k])) {
                    blow(k);
                    changed = true;
                }
            } else {
                heat[k] = BreakerTripCurve.cool(heat[k]);
            }
            // An open gap over the class's withstand arcs across; it goes out only once the voltage is gone.
            boolean gap = open[k] || blown[k] || link[k] == 0;
            boolean shouldArc = gap && volts[k] > cls.withstandVolts();
            if(arcing[k] && !gap)
                shouldArc = false;
            if(arcing[k] && gap && volts[k] < cls.withstandVolts() * 0.25f)
                shouldArc = false;
            if(shouldArc != arcing[k]) {
                arcing[k] = shouldArc;
                if(arcs[k] != null)
                    arcs[k].setState(shouldArc);
                if(shouldArc)
                    level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6f, 1.6f);
                changed = true;
            }
        }
        if(++syncTimer >= 20 || changed) {
            syncTimer = 0;
            setChanged();
            sendData();
        }
    }

    private void blow(int k) {
        blown[k] = true;
        heat[k] = 0;
        if(contacts[k] != null)
            contacts[k].setState(false);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
    }

    /** Client side: sparks off an arcing gap every few ticks, and once when a link has just blown. */
    private void clientSparks() {
        boolean any = false;
        for(int k = 0; k < poles(); ++k) {
            if(blown[k] && !syncedBlown[k])
                sparks(k, 6);
            syncedBlown[k] = blown[k];
            any |= arcing[k];
        }
        if(any && ++sparkTimer >= 8) {
            sparkTimer = 0;
            for(int k = 0; k < poles(); ++k)
                if(arcing[k])
                    sparks(k, 3);
        }
    }

    private void sparks(int k, int count) {
        var facing = CutoutBlock.facing(getBlockState());
        double x = centres(poles())[k];
        var local = PanelLayout.fromNorthFrame(new Vec3(x, cutoutClass().top() * 0.6, 7), facing).scale(1 / 16.0);
        var pos = local.add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, facing, count);
    }

    // ---- the lineman's work ----

    public boolean hasLink(int k) {
        ensureArrays();
        return k >= 0 && k < MAX_POLES && link[k] > 0;
    }

    public int link(int k) {
        ensureArrays();
        return k >= 0 && k < MAX_POLES ? link[k] : 0;
    }

    public boolean isOpen(int k) {
        ensureArrays();
        return k >= 0 && k < MAX_POLES && (open[k] || blown[k] || link[k] == 0);
    }

    public boolean isBlown(int k) {
        ensureArrays();
        return k >= 0 && k < MAX_POLES && blown[k];
    }

    public boolean isArcing(int k) {
        ensureArrays();
        return k >= 0 && k < MAX_POLES && arcing[k];
    }

    private void message(Player player, String key, ChatFormatting style, Object... args) {
        player.displayClientMessage(Lang.builder().translate(key, args).style(style).component(), true);
    }

    /** Server side: a link from the stack goes into the pole's tube, which is left hanging open to be closed. */
    public void loadLink(int k, ItemStack stack, Player player) {
        ensureArrays();
        int rating = FuseLinkItem.ratingOf(stack);
        if(level == null || level.isClientSide || rating <= 0 || k < 0 || k >= poles())
            return;
        if(blown[k]) {
            message(player, "message.cutout.blown_first", ChatFormatting.RED);
            return;
        }
        if(link[k] > 0) {
            message(player, "message.cutout.has_link", ChatFormatting.RED, link[k]);
            return;
        }
        link[k] = rating;
        open[k] = true;
        heat[k] = 0;
        if(!player.isCreative())
            stack.shrink(1);
        applyContacts();
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.3f);
        message(player, "message.cutout.loaded", ChatFormatting.GRAY, rating, k + 1);
        setChanged();
        sendData();
    }

    /** Server side: the link comes out, good ones into the hand, blown ones as nothing. */
    public void pullLink(int k, Player player) {
        ensureArrays();
        if(level == null || level.isClientSide || k < 0 || k >= MAX_POLES || link[k] == 0)
            return;
        int rating = link[k];
        boolean wasBlown = blown[k];
        link[k] = 0;
        blown[k] = false;
        open[k] = true;
        heat[k] = 0;
        applyContacts();
        if(!wasBlown) {
            var stack = ModItems.fuseLink(rating);
            if(!player.getInventory().add(stack))
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
            message(player, "message.cutout.pulled", ChatFormatting.GRAY, rating, k + 1);
        } else {
            message(player, "message.cutout.scrap", ChatFormatting.GRAY, rating, k + 1);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.3f);
        setChanged();
        sendData();
    }

    /**
     * Server side: the tube closes or swings open. By hand on a gap with voltage across it, the
     * hand is on the live parts: a shock. On the stick there is none.
     */
    public void toggle(int k, Player player, boolean byHand) {
        ensureArrays();
        if(level == null || level.isClientSide || k < 0 || k >= poles())
            return;
        if(link[k] == 0) {
            message(player, "message.cutout.no_link", ChatFormatting.RED);
            return;
        }
        if(blown[k]) {
            message(player, "message.cutout.blown", ChatFormatting.RED, k + 1);
            return;
        }
        if(byHand && volts[k] > ShockDamage.SAFE_VOLTS)
            ShockDamage.shockVolts(player, volts[k]);
        open[k] = !open[k];
        applyContacts();
        level.playSound(null, worldPosition, open[k] ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.2f);
        message(player, open[k] ? "message.cutout.opened" : "message.cutout.closed", ChatFormatting.GRAY, k + 1);
        setChanged();
        sendData();
    }

    /** The stick's one click: a blown link comes out, a link in the other hand goes in, else the tube is thrown. */
    public void stick(int k, Player player, ItemStack offhand) {
        ensureArrays();
        if(k < 0 || k >= poles())
            return;
        if(blown[k])
            pullLink(k, player);
        else if(link[k] == 0 && FuseLinkItem.ratingOf(offhand) > 0)
            loadLink(k, offhand, player);
        else
            toggle(k, player, false);
    }

    /** A pole taken off the holder: its record clears. */
    public void poleRemoved(int k) {
        ensureArrays();
        if(k < 0 || k >= MAX_POLES)
            return;
        link[k] = 0;
        open[k] = false;
        blown[k] = false;
        arcing[k] = false;
        heat[k] = 0;
        applyContacts();
        setChanged();
        sendData();
    }

    /** The holder is going: every good link falls out. */
    public void dropLinks() {
        ensureArrays();
        if(level == null || level.isClientSide)
            return;
        for(int k = 0; k < MAX_POLES; ++k) {
            if(link[k] > 0 && !blown[k])
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, ModItems.fuseLink(link[k]));
            link[k] = 0;
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ensureArrays();
        var cls = cutoutClass();
        Lang.builder().translate("gui.cutout.title", cls.label()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(int k = 0; k < poles(); ++k) {
            String state = arcing[k] ? "gui.cutout.flashover" : blown[k] ? "gui.cutout.blown" : link[k] == 0 ? "gui.cutout.empty" : open[k] ? "gui.cutout.open" : "gui.cutout.closed";
            var style = arcing[k] ? ChatFormatting.RED : blown[k] ? ChatFormatting.GOLD : link[k] == 0 ? ChatFormatting.DARK_GRAY : open[k] ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            var line = Lang.builder().translate("gui.cutout.pole", k + 1).style(ChatFormatting.GRAY);
            if(link[k] > 0)
                line.text(" ").add(Lang.builder().translate("gui.cutout.link", link[k]).style(ChatFormatting.WHITE));
            line.text(" ").add(Lang.builder().translate(state).style(style));
            if(conducting(k) || arcing[k])
                line.text(" ").add(Lang.builder().text(String.format("%.1f A", amps[k])).style(ChatFormatting.GOLD));
            else if(volts[k] > 1)
                line.text(" ").add(Lang.builder().text(String.format("%.0f V", volts[k])).style(ChatFormatting.AQUA));
            line.forGoggles(tooltip, 1);
        }
        return true;
    }

    // ---- nbt ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ensureArrays();
        tag.putIntArray("Links", link.clone());
        int bits = 0;
        for(int k = 0; k < MAX_POLES; ++k)
            bits |= (open[k] ? 1 : 0) << k | (blown[k] ? 1 : 0) << (k + 4) | (arcing[k] ? 1 : 0) << (k + 8);
        tag.putInt("Bits", bits);
        if(!clientPacket) {
            var heats = new int[MAX_POLES];
            for(int k = 0; k < MAX_POLES; ++k)
                heats[k] = Math.round(heat[k] * 10);
            tag.putIntArray("Heat", heats);
        } else {
            var readings = new int[MAX_POLES * 2];
            for(int k = 0; k < MAX_POLES; ++k) {
                readings[k] = Math.round(amps[k] * 10);
                readings[MAX_POLES + k] = Math.round(volts[k]);
            }
            tag.putIntArray("Readings", readings);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ensureArrays();
        var links = tag.getIntArray("Links");
        int bits = tag.getInt("Bits");
        for(int k = 0; k < MAX_POLES; ++k) {
            link[k] = k < links.length ? links[k] : 0;
            open[k] = (bits >> k & 1) != 0;
            blown[k] = (bits >> (k + 4) & 1) != 0;
            arcing[k] = (bits >> (k + 8) & 1) != 0;
        }
        var heats = tag.getIntArray("Heat");
        for(int k = 0; k < MAX_POLES && k < heats.length; ++k)
            heat[k] = heats[k] / 10f;
        var readings = tag.getIntArray("Readings");
        for(int k = 0; k < MAX_POLES && MAX_POLES + k < readings.length; ++k) {
            amps[k] = readings[k] / 10f;
            volts[k] = readings[MAX_POLES + k];
        }
        applyContacts();
    }
}
