package com.nolanbaker.pgmodernized.conduit.fire;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.*;

/**
 * The panel's insides: a load across line and neutral that tells it whether it is fed, a 24 V
 * source on each loop, and the three states. The initiating loop reads its current: a detector's
 * supervisory resistor draws a milliampere or so, a detector in alarm draws twenty or more, and a
 * loop with nothing on it draws none. The notification loop idles at two volts, enough to see a
 * horn strobe's load without waking it, and goes to twenty-four in alarm, or twelve once silenced.
 */
public class FireAlarmPanelBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IHaveGoggleInformation {
    public enum State { NORMAL, ALARM, TROUBLE }

    public static final float LOOP_VOLTS = 24, SILENCED_VOLTS = 12, SUPERVISORY_VOLTS = 2;
    public static final float POWERED_VOLTS = 90, ALARM_AMPS = 0.010f, SUPERVISED_AMPS = 0.0005f;

    private DeviceSpliceHost deviceHubs;
    private ElectricWire feed;
    private ProvidedVoltageSourceCoupling idc, nac;
    private AcReadings.Filter feedReading;
    private float feedVolts, idcVolts, nacVolts, idcAmps, nacAmps;
    private boolean powered, alarm, silenced, idcOpen, nacOpen;
    private State state = State.NORMAL;
    private State syncedState = State.NORMAL;
    private boolean syncedPowered, syncedSilenced;
    private float syncedIdc, syncedNac;

    public FireAlarmPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.ONE;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        feed = builder.connect(resistance("feed"), builder.terminalNode(TERMINAL_LINE), builder.terminalNode(TERMINAL_NEUTRAL));
        idc = new ProvidedVoltageSourceCoupling(builder.terminalNode(TERMINAL_IDC_PLUS), builder.terminalNode(TERMINAL_IDC_MINUS), resistance("loop"));
        idc.setVoltageProvider(() -> idcVolts);
        builder.add(idc);
        nac = new ProvidedVoltageSourceCoupling(builder.terminalNode(TERMINAL_NAC_PLUS), builder.terminalNode(TERMINAL_NAC_MINUS), resistance("loop"));
        nac.setVoltageProvider(() -> nacVolts);
        builder.add(nac);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    // ---- the panel's logic ----

    public boolean inAlarm() {
        return alarm;
    }

    public State state() {
        return state;
    }

    public boolean powered() {
        return powered;
    }

    public boolean silenced() {
        return silenced;
    }

    /** For Ponder: a client-side panel shown in a state, with its lights. */
    public void demo(State shown, boolean silencedShown) {
        powered = true;
        state = shown;
        alarm = shown == State.ALARM;
        silenced = silencedShown;
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || feed == null)
            return;
        if(feedReading == null)
            feedReading = new AcReadings.Filter();
        if(feed.isConverged())
            feedReading.sample(feed);
        feedVolts = (float) AcReadings.finite(feedReading.rmsVoltage());
        powered = feedVolts >= POWERED_VOLTS;
        idcAmps = powered && idc != null ? (float) AcReadings.finite(Math.abs(idc.getCurrent())) : 0;
        nacAmps = powered && nac != null ? (float) AcReadings.finite(Math.abs(nac.getCurrent())) : 0;
        boolean wasAlarm = alarm;
        if(!powered) {
            idcVolts = 0;
            nacVolts = 0;
            idcOpen = false;
            nacOpen = false;
            state = State.TROUBLE;
        } else {
            if(idcAmps >= ALARM_AMPS)
                alarm = true;
            idcOpen = idcAmps < SUPERVISED_AMPS;
            nacOpen = !alarm && nacAmps < SUPERVISED_AMPS;
            state = alarm ? State.ALARM : idcOpen || nacOpen ? State.TROUBLE : State.NORMAL;
            idcVolts = LOOP_VOLTS;
            nacVolts = alarm ? (silenced ? SILENCED_VOLTS : LOOP_VOLTS) : SUPERVISORY_VOLTS;
        }
        if(alarm != wasAlarm)
            alarmChanged();
    }

    private void alarmChanged() {
        setChanged();
        sendData();
        if(level != null)
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    /** Silence: the horns stop, the strobes keep going, the alarm stands. Server side. */
    public void silence(Player player) {
        if(level == null || level.isClientSide)
            return;
        if(!alarm) {
            message(player, "message.fire_alarm.nothing_to_silence", ChatFormatting.GRAY);
            return;
        }
        silenced = true;
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.2f);
        message(player, "message.fire_alarm.silenced", ChatFormatting.YELLOW);
        setChanged();
        sendData();
    }

    /** Reset: the alarm clears if no detector is still holding the loop. Server side. */
    public void reset(Player player) {
        if(level == null || level.isClientSide)
            return;
        if(!alarm) {
            message(player, "message.fire_alarm.nothing_to_reset", ChatFormatting.GRAY);
            return;
        }
        if(idcAmps >= ALARM_AMPS) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.5f, 0.8f);
            message(player, "message.fire_alarm.reset_refused", ChatFormatting.RED);
            return;
        }
        alarm = false;
        silenced = false;
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.2f);
        message(player, "message.fire_alarm.reset", ChatFormatting.GREEN);
        alarmChanged();
    }

    private static void message(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(state != syncedState || powered != syncedPowered || silenced != syncedSilenced
                || Math.abs(idcAmps - syncedIdc) > 0.001f || Math.abs(nacAmps - syncedNac) > 0.005f) {
            syncedState = state;
            syncedPowered = powered;
            syncedSilenced = silenced;
            syncedIdc = idcAmps;
            syncedNac = nacAmps;
            sendData();
        }
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("Silenced", silenced);
        tag.putInt("State", state.ordinal());
        tag.putBoolean("Powered", powered);
        tag.putFloat("FeedVolts", feedVolts);
        tag.putFloat("IdcAmps", idcAmps);
        tag.putFloat("NacAmps", nacAmps);
        tag.putBoolean("IdcOpen", idcOpen);
        tag.putBoolean("NacOpen", nacOpen);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        deviceHubs().write(tag, registries, false);
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("Silenced", silenced);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        alarm = tag.getBoolean("Alarm");
        silenced = tag.getBoolean("Silenced");
        if(clientPacket) {
            int s = tag.getInt("State");
            state = s >= 0 && s < State.values().length ? State.values()[s] : State.NORMAL;
            powered = tag.getBoolean("Powered");
            feedVolts = tag.getFloat("FeedVolts");
            idcAmps = tag.getFloat("IdcAmps");
            nacAmps = tag.getFloat("NacAmps");
            idcOpen = tag.getBoolean("IdcOpen");
            nacOpen = tag.getBoolean("NacOpen");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.fire_alarm.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(powered)
            Lang.builder().translate("gui.fire_alarm.powered", String.format("%.0f", feedVolts)).style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.fire_alarm.unpowered").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        var stateStyle = switch(state) {
            case NORMAL -> ChatFormatting.GREEN;
            case ALARM -> ChatFormatting.RED;
            case TROUBLE -> ChatFormatting.YELLOW;
        };
        Lang.builder().translate("gui.fire_alarm.state_" + state.name().toLowerCase()).style(stateStyle).forGoggles(tooltip, 1);
        if(silenced)
            Lang.builder().translate("gui.fire_alarm.silenced").style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
        if(powered) {
            Lang.builder().translate(idcOpen ? "gui.fire_alarm.idc_open" : "gui.fire_alarm.idc", String.format("%.1f", idcAmps * 1000))
                    .style(idcOpen ? ChatFormatting.YELLOW : ChatFormatting.WHITE).forGoggles(tooltip, 1);
            Lang.builder().translate(nacOpen ? "gui.fire_alarm.nac_open" : "gui.fire_alarm.nac", String.format("%.1f", nacAmps * 1000))
                    .style(nacOpen ? ChatFormatting.YELLOW : ChatFormatting.WHITE).forGoggles(tooltip, 1);
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
