package com.nolanbaker.pgmodernized.device.hvac;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceReadings;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.hvac.CondenserBlock.*;

/**
 * The condenser's workings. A contactor coil sits across Y and C; the air handler's 24 V on it
 * pulls the compressor and the fan in across L1 and L2, after the anti-short-cycle timer has
 * run and only while both refrigerant lines reach an air handler. The compressor draws locked-
 * rotor current for its first half second, five times its running current, which is what trips a
 * breaker sized too close. Running it cools whichever air handler is on the other end of the
 * lines.
 */
public class CondenserBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, ISpliceReadings, IHaveGoggleInformation {
    public static final float POWERED_VOLTS = 90, CALL_AMPS = 0.05f, INRUSH_FACTOR = 5;
    /** The compressor runs at least ten seconds once started and rests at least ten once stopped. */
    public static final int MIN_ON_TICKS = 200, MIN_OFF_TICKS = 200, INRUSH_TICKS = 10;

    private DeviceSpliceHost deviceHubs;
    private SwitchedWire compressor, fan;
    private ElectricWire coil;
    private AcReadings.Filter feedReading, coilReading, compressorReading;
    private float feedVolts, coilAmps, amps;
    private boolean powered, called, running, lines;
    private int onTimer, offTimer, inrushTimer, lineCheck, humTimer;
    private boolean syncedRunning, syncedPowered;
    private float syncedAmps;

    public CondenserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    // ---- the hubs: two knockouts, two line ports ----

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return switch(hub) {
            case HUB_POWER, HUB_SUCTION -> ConduitSize.THREE_QUARTER;
            case HUB_CONTROL, HUB_LIQUID -> ConduitSize.HALF;
            default -> null;
        };
    }

    @Override
    public boolean hubTakes(int hub, ItemStack stack) {
        var line = LineSetItem.kindOf(stack);
        return switch(hub) {
            case HUB_LIQUID -> line == LineKind.LIQUID;
            case HUB_SUCTION -> line == LineKind.SUCTION;
            default -> line == null;
        };
    }

    @Override
    public boolean hubShown(int hub) {
        return hub == HUB_POWER || hub == HUB_CONTROL;
    }

    @Override
    public int landings(int hub) {
        return hubShown(hub) ? com.nolanbaker.pgmodernized.conduit.ConductorColors.COUNT : 0;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("hvac.hub." + switch(hub) {
            case HUB_POWER -> "power";
            case HUB_CONTROL -> "control";
            case HUB_LIQUID -> "liquid";
            default -> "suction";
        }).component();
    }

    /** The air handler both lines reach, on its matching ports, or null. Works on both sides. */
    @Nullable
    public AirHandlerBlockEntity airHandler() {
        var liquid = farEnd(HUB_LIQUID, AirHandlerBlock.HUB_LIQUID);
        var suction = farEnd(HUB_SUCTION, AirHandlerBlock.HUB_SUCTION);
        return liquid != null && liquid == suction ? liquid : null;
    }

    @Nullable
    private AirHandlerBlockEntity farEnd(int hub, int theirHub) {
        var run = hubRun(hub);
        if(run == null || level == null)
            return null;
        for(var endpoint : new Object[] {run.getEndpoint1(), run.getEndpoint2()}) {
            if(!(endpoint instanceof BlockWireEndpoint end) || end.getPos().equals(worldPosition))
                continue;
            if(level.getBlockEntity(end.getPos()) instanceof AirHandlerBlockEntity handler && handler.hubAt(end.getTerminal()) == theirHub)
                return handler;
        }
        return null;
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        compressor = builder.connectSwitch(resistance("compressor"), builder.terminalNode(L1), builder.terminalNode(L2), running);
        fan = builder.connectSwitch(resistance("fan"), builder.terminalNode(L1), builder.terminalNode(L2), running);
        coil = builder.connect(resistance("coil"), builder.terminalNode(Y), builder.terminalNode(C));
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || compressor == null)
            return;
        if(feedReading == null) {
            feedReading = new AcReadings.Filter();
            coilReading = new AcReadings.Filter();
            compressorReading = new AcReadings.Filter();
        }
        if(fan.isConverged())
            feedReading.sample(fan);
        if(coil.isConverged())
            coilReading.sample(coil);
        if(compressor.isConverged())
            compressorReading.sample(compressor);
        feedVolts = (float) AcReadings.finite(feedReading.rmsVoltage());
        coilAmps = (float) AcReadings.finite(coilReading.rmsCurrent());
        amps = running ? (float) AcReadings.finite(compressorReading.rmsCurrent()) : 0;
        powered = feedVolts >= POWERED_VOLTS;
        called = coilAmps >= CALL_AMPS;
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        if(onTimer > 0)
            --onTimer;
        if(offTimer > 0)
            --offTimer;
        if(++lineCheck >= 20) {
            lineCheck = 0;
            lines = airHandler() != null;
        }
        if(inrushTimer > 0 && --inrushTimer == 0 && compressor != null)
            compressor.setResistance(resistance("compressor"));
        boolean wants = called && lines;
        if(wants && !running && offTimer == 0)
            start();
        else if(!wants && running && onTimer == 0)
            stop();
        if(cooling() && ++humTimer >= 60) {
            humTimer = 0;
            level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.25f, 0.5f);
        }
        float shownAmps = Math.round(amps * 2) / 2f;
        if(running != syncedRunning || powered != syncedPowered || shownAmps != syncedAmps) {
            syncedRunning = running;
            syncedPowered = powered;
            syncedAmps = shownAmps;
            setChanged();
            sendData();
        }
    }

    private void start() {
        running = true;
        onTimer = MIN_ON_TICKS;
        inrushTimer = INRUSH_TICKS;
        if(compressor != null) {
            compressor.setResistance(resistance("compressor") / INRUSH_FACTOR);
            compressor.setState(true);
        }
        if(fan != null)
            fan.setState(true);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.45f);
    }

    private void stop() {
        running = false;
        offTimer = MIN_OFF_TICKS;
        inrushTimer = 0;
        if(compressor != null) {
            compressor.setState(false);
            compressor.setResistance(resistance("compressor"));
        }
        if(fan != null)
            fan.setState(false);
        level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 0.45f);
    }

    // ---- state ----

    /** Whether the compressor is turning: pulled in and fed. Works on both sides. */
    public boolean cooling() {
        return running && powered;
    }

    public boolean running() {
        return running;
    }

    public float amps() {
        return amps;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
    }

    // ---- readings ----

    @Override
    public List<Component> readings() {
        var lines = new ArrayList<Component>();
        String state = !powered ? "hvac.condenser.unpowered" : running ? "hvac.condenser.running" : called ? "hvac.condenser.waiting" : "hvac.condenser.idle";
        var style = !powered ? ChatFormatting.RED : running ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        lines.add(Lang.builder().translate(state).style(style).component());
        lines.add(Lang.builder().translate("hvac.condenser.feed", String.format("%.0f", feedVolts), String.format("%.1f", amps)).style(ChatFormatting.GRAY).component());
        lines.add(Lang.builder().translate(this.lines ? "hvac.condenser.lines_ok" : "hvac.condenser.lines_missing").style(this.lines ? ChatFormatting.GRAY : ChatFormatting.GOLD).component());
        return lines;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.hvac.condenser").style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(var line : readings())
            Lang.builder().add(line).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }

    // ---- nbt ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putBoolean("Running", running);
        tag.putBoolean("Powered", powered);
        tag.putFloat("Amps", amps);
        tag.putBoolean("Lines", lines);
        tag.putInt("OnTimer", onTimer);
        tag.putInt("OffTimer", offTimer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        running = tag.getBoolean("Running");
        powered = tag.getBoolean("Powered");
        amps = tag.getFloat("Amps");
        lines = tag.getBoolean("Lines");
        onTimer = tag.getInt("OnTimer");
        offTimer = tag.getInt("OffTimer");
        if(compressor != null) {
            compressor.setState(running);
            fan.setState(running);
        }
    }
}
