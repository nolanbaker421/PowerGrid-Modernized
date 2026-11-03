package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.pole.RecloserBlock.*;

/**
 * The recloser's workings. Each pole is a contact whose current heats a breaker element against
 * the pickup setting; a trip opens all three. A tripped recloser closes again after three
 * seconds, a shot, as many times as it is set for; the fault still there after the last shot
 * locks it out until someone resets it. Five seconds of holding clean forgets the shots. Opened
 * by hand it stays open until closed by hand.
 */
public class RecloserBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    public static final int RECLOSE_TICKS = 60, RESET_TICKS = 100, MAX_SHOTS = 4, DEFAULT_PICKUP = 100, DEFAULT_SHOTS = 3;

    private SwitchedWire[] contacts;
    private AcReadings.Filter[] readings;
    private float[] amps, heat;
    private boolean open, lockout, manual;
    private int pickup = DEFAULT_PICKUP, shots = DEFAULT_SHOTS, shotsUsed, recloseTimer, holdTimer, syncTimer;
    private RecloserPickupBehaviour pickupBox;

    public RecloserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    private void ensureArrays() {
        if(amps == null) {
            amps = new float[POLES];
            heat = new float[POLES];
            contacts = new SwitchedWire[POLES];
            readings = new AcReadings.Filter[POLES];
            for(int k = 0; k < POLES; ++k)
                readings[k] = new AcReadings.Filter();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        pickupBox = new RecloserPickupBehaviour(this);
        pickupBox.setValue(pickup);
        pickupBox.withCallback(value -> {
            pickup = Math.max(10, Math.min(800, value));
            setChanged();
        });
        behaviours.add(pickupBox);
        super.addBehaviours(behaviours);
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureArrays();
        builder.setTerminalCount(TERMINAL_COUNT);
        for(int k = 0; k < POLES; ++k)
            contacts[k] = builder.connectSwitch(resistance("contact"), builder.terminalNode(LINE_BASE + k), builder.terminalNode(LOAD_BASE + k), !open);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private void applyContacts() {
        if(contacts == null)
            return;
        for(var contact : contacts)
            if(contact != null)
                contact.setState(!open);
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || contacts == null)
            return;
        for(int k = 0; k < POLES; ++k) {
            if(contacts[k] != null && contacts[k].isConverged())
                readings[k].sample(contacts[k]);
            amps[k] = open ? 0 : (float) AcReadings.finite(readings[k].rmsCurrent());
        }
    }

    @Override
    public void tick() {
        ensureArrays();
        super.tick();
        if(level == null || level.isClientSide)
            return;
        boolean changed = false;
        if(!open) {
            boolean trip = false;
            for(int k = 0; k < POLES; ++k) {
                heat[k] = BreakerTripCurve.step(heat[k], amps[k], pickup);
                if(BreakerTripCurve.trips(heat[k], amps[k], pickup))
                    trip = true;
            }
            if(trip) {
                open = true;
                holdTimer = 0;
                for(int k = 0; k < POLES; ++k)
                    heat[k] = 0;
                if(shotsUsed < shots) {
                    ++shotsUsed;
                    recloseTimer = RECLOSE_TICKS;
                } else {
                    lockout = true;
                }
                applyContacts();
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 1.6f);
                changed = true;
            } else if(++holdTimer >= RESET_TICKS && shotsUsed > 0) {
                shotsUsed = 0;
                changed = true;
            }
        } else if(recloseTimer > 0 && !lockout && !manual) {
            if(--recloseTimer == 0) {
                open = false;
                holdTimer = 0;
                applyContacts();
                level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
                changed = true;
            }
        }
        if(changed)
            syncState();
        else if(++syncTimer >= 20) {
            syncTimer = 0;
            setChanged();
            sendData();
        }
    }

    private void syncState() {
        if(level == null)
            return;
        var state = getBlockState();
        if(state.getBlock() instanceof RecloserBlock)
            level.setBlock(worldPosition, state.setValue(OPEN, open).setValue(LOCKOUT, lockout), Block.UPDATE_ALL);
        setChanged();
        sendData();
    }

    // ---- hands ----

    /** Server side: a closed recloser is tripped and held open; an open or locked-out one is reset and closed. */
    public void manual(Player player) {
        if(level == null || level.isClientSide)
            return;
        if(!open) {
            open = true;
            manual = true;
            recloseTimer = 0;
            applyContacts();
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 1.6f);
            player.displayClientMessage(Lang.translate("message.recloser.opened").style(ChatFormatting.GRAY).component(), true);
        } else {
            open = false;
            manual = false;
            lockout = false;
            shotsUsed = 0;
            recloseTimer = 0;
            holdTimer = 0;
            for(int k = 0; k < POLES; ++k)
                heat[k] = 0;
            applyContacts();
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
            player.displayClientMessage(Lang.translate("message.recloser.reset").style(ChatFormatting.GRAY).component(), true);
        }
        syncState();
    }

    /** Server side: the shots before lockout, one to four, round and round. */
    public void cycleShots(Player player) {
        if(level == null || level.isClientSide)
            return;
        shots = shots % MAX_SHOTS + 1;
        player.displayClientMessage(Lang.builder().translate("message.recloser.shots", shots).style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ensureArrays();
        Lang.builder().translate("gui.recloser.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        String state = lockout ? "gui.recloser.lockout" : open && manual ? "gui.recloser.open_manual" : open ? "gui.recloser.reclosing" : "gui.recloser.closed";
        var style = lockout ? ChatFormatting.RED : open ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        Lang.builder().translate(state, (recloseTimer + 19) / 20).style(style).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.recloser.settings", pickup, shotsUsed, shots).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if(!open) {
            var line = Lang.builder();
            for(int k = 0; k < POLES; ++k)
                line.text(k == 0 ? "" : "  ").add(Lang.builder().text(String.format("L%d %.1f A", k + 1, amps[k])).style(ChatFormatting.GOLD));
            line.forGoggles(tooltip, 1);
        }
        return true;
    }

    // ---- nbt ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ensureArrays();
        tag.putBoolean("Open", open);
        tag.putBoolean("Lockout", lockout);
        tag.putBoolean("Manual", manual);
        tag.putInt("Pickup", pickup);
        tag.putInt("Shots", shots);
        tag.putInt("ShotsUsed", shotsUsed);
        tag.putInt("Reclose", recloseTimer);
        if(clientPacket) {
            var list = new int[POLES];
            for(int k = 0; k < POLES; ++k)
                list[k] = Math.round(amps[k] * 10);
            tag.putIntArray("Amps", list);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ensureArrays();
        open = tag.getBoolean("Open");
        lockout = tag.getBoolean("Lockout");
        manual = tag.getBoolean("Manual");
        if(tag.contains("Pickup"))
            pickup = tag.getInt("Pickup");
        if(tag.contains("Shots"))
            shots = tag.getInt("Shots");
        shotsUsed = tag.getInt("ShotsUsed");
        recloseTimer = tag.getInt("Reclose");
        var list = tag.getIntArray("Amps");
        for(int k = 0; k < POLES && k < list.length; ++k)
            amps[k] = list[k] / 10f;
        if(pickupBox != null)
            pickupBox.setValue(pickup);
        applyContacts();
    }
}
