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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.nolanbaker.pgmodernized.device.hvac.AirHandlerBlock.*;

/**
 * The air handler's workings. Fed on L1 and L2, its transformer puts 24 V across R and C; a
 * thermostat closing R to Y, W or G energises a relay coil here, and the coil's current is the
 * call. Y runs the blower and, through the condenser wired to Y and C, the compressor; W runs the
 * blower and the heat strip; G the blower alone.
 * <p>
 * It also keeps the room: a temperature that starts at the biome's, drifts back toward it, and
 * is pulled down while the compressor cools and up while the strip heats. Thermostats within a
 * dozen blocks read that temperature, so the system cycles the way a real one does instead of
 * running for ever against a biome that never changes.
 */
public class AirHandlerBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, ISpliceReadings, IHaveGoggleInformation {
    public static final float POWERED_VOLTS = 90, CALL_AMPS = 0.05f, CONTROL_VOLTS = 24;
    /** The room the handler conditions: how far a thermostat may stand from it. */
    public static final double ROOM_RANGE = 12;
    /** What the coil cools toward and the strip heats toward, and how fast the room follows, per tick. */
    public static final float COOL_TARGET_F = 55, HEAT_TARGET_F = 95, CONDITION_RATE = 0.0015f, DRIFT_RATE = 0.0002f;

    private static final Map<Level, Set<BlockPos>> HANDLERS = Collections.synchronizedMap(new WeakHashMap<>());

    private DeviceSpliceHost deviceHubs;
    private SwitchedWire blower, heat;
    private ElectricWire coilY, coilW, coilG;
    private ProvidedVoltageSourceCoupling control;
    private AcReadings.Filter feedReading, yReading, wReading, gReading;
    private float feedVolts;
    private boolean powered, callCool, callHeat, callFan, blowing, heating;
    private float roomF = Float.NaN;
    private int syncTimer;
    private boolean registered;

    public AirHandlerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    // ---- the hubs: three knockouts, two line ports ----

    @Override
    public @Nullable ConduitSize maxConduit(int hub) {
        return switch(hub) {
            case HUB_POWER, HUB_SUCTION -> ConduitSize.THREE_QUARTER;
            case HUB_THERMOSTAT, HUB_CONDENSER, HUB_LIQUID -> ConduitSize.HALF;
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
        return hub < HUB_LIQUID;
    }

    @Override
    public int landings(int hub) {
        return hubShown(hub) ? com.nolanbaker.pgmodernized.conduit.ConductorColors.COUNT : 0;
    }

    @Override
    public Component hubName(int hub) {
        return Lang.builder().translate("hvac.hub." + switch(hub) {
            case HUB_POWER -> "power";
            case HUB_THERMOSTAT -> "thermostat";
            case HUB_CONDENSER -> "condenser";
            case HUB_LIQUID -> "liquid";
            default -> "suction";
        }).component();
    }

    /** The condenser both lines reach, on its matching ports, or null. Works on both sides. */
    @Nullable
    public CondenserBlockEntity condenser() {
        var liquid = farEnd(HUB_LIQUID, CondenserBlock.HUB_LIQUID);
        var suction = farEnd(HUB_SUCTION, CondenserBlock.HUB_SUCTION);
        return liquid != null && liquid == suction ? liquid : null;
    }

    @Nullable
    private CondenserBlockEntity farEnd(int hub, int theirHub) {
        var run = hubRun(hub);
        if(run == null || level == null)
            return null;
        for(var endpoint : new Object[] {run.getEndpoint1(), run.getEndpoint2()}) {
            if(!(endpoint instanceof BlockWireEndpoint end) || end.getPos().equals(worldPosition))
                continue;
            if(level.getBlockEntity(end.getPos()) instanceof CondenserBlockEntity condenser && condenser.hubAt(end.getTerminal()) == theirHub)
                return condenser;
        }
        return null;
    }

    // ---- the room ----

    /** The nearest air handler within {@link #ROOM_RANGE} of a point, or null. Works on both sides. */
    @Nullable
    public static AirHandlerBlockEntity nearest(Level level, BlockPos pos) {
        var set = HANDLERS.get(level);
        if(set == null)
            return null;
        AirHandlerBlockEntity best = null;
        double bestDistance = ROOM_RANGE * ROOM_RANGE;
        BlockPos[] positions;
        synchronized(set) {
            positions = set.toArray(new BlockPos[0]);
        }
        for(var at : positions) {
            double d = at.distSqr(pos);
            if(d <= bestDistance && level.getBlockEntity(at) instanceof AirHandlerBlockEntity handler) {
                best = handler;
                bestDistance = d;
            }
        }
        return best;
    }

    private void register() {
        if(level == null || registered)
            return;
        HANDLERS.computeIfAbsent(level, l -> Collections.synchronizedSet(new HashSet<>())).add(worldPosition);
        registered = true;
    }

    /** Create's hook for both removal and unload: the handler leaves the room list. */
    @Override
    public void invalidate() {
        super.invalidate();
        if(level != null) {
            var set = HANDLERS.get(level);
            if(set != null)
                set.remove(worldPosition);
        }
        registered = false;
    }

    /** The room's temperature, Fahrenheit. Works on both sides. */
    public float roomF() {
        if(Float.isNaN(roomF))
            roomF = level == null ? 70 : Climate.outdoorF(level, worldPosition);
        return roomF;
    }

    public float outdoorF() {
        return level == null ? 70 : Climate.outdoorF(level, worldPosition);
    }

    private void roomTick() {
        float outdoor = outdoorF();
        float room = roomF();
        var condenser = condenser();
        boolean cooling = blowing && callCool && condenser != null && condenser.cooling();
        if(cooling)
            room += (COOL_TARGET_F - room) * CONDITION_RATE;
        else if(heating)
            room += (HEAT_TARGET_F - room) * CONDITION_RATE;
        room += (outdoor - room) * DRIFT_RATE;
        roomF = room;
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        blower = builder.connectSwitch(resistance("blower"), builder.terminalNode(L1), builder.terminalNode(L2), blowing);
        heat = builder.connectSwitch(resistance("heat"), builder.terminalNode(L1), builder.terminalNode(L2), heating);
        control = new ProvidedVoltageSourceCoupling(builder.terminalNode(R), builder.terminalNode(C), resistance("transformer"));
        control.setVoltageProvider(() -> powered ? CONTROL_VOLTS : 0f);
        builder.add(control);
        coilY = builder.connect(resistance("coil"), builder.terminalNode(Y), builder.terminalNode(C));
        coilW = builder.connect(resistance("coil"), builder.terminalNode(W), builder.terminalNode(C));
        coilG = builder.connect(resistance("coil"), builder.terminalNode(G), builder.terminalNode(C));
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || blower == null)
            return;
        if(feedReading == null) {
            feedReading = new AcReadings.Filter();
            yReading = new AcReadings.Filter();
            wReading = new AcReadings.Filter();
            gReading = new AcReadings.Filter();
        }
        if(blower.isConverged())
            feedReading.sample(blower);
        if(coilY.isConverged())
            yReading.sample(coilY);
        if(coilW.isConverged())
            wReading.sample(coilW);
        if(coilG.isConverged())
            gReading.sample(coilG);
        feedVolts = (float) AcReadings.finite(feedReading.rmsVoltage());
        powered = feedVolts >= POWERED_VOLTS;
        callCool = powered && AcReadings.finite(yReading.rmsCurrent()) >= CALL_AMPS;
        callHeat = powered && AcReadings.finite(wReading.rmsCurrent()) >= CALL_AMPS;
        callFan = powered && AcReadings.finite(gReading.rmsCurrent()) >= CALL_AMPS;
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        register();
        boolean wantBlow = callCool || callHeat || callFan;
        boolean wantHeat = callHeat;
        if(wantBlow != blowing || wantHeat != heating) {
            blowing = wantBlow;
            heating = wantHeat;
            if(blower != null) {
                blower.setState(blowing);
                heat.setState(heating);
            }
            setChanged();
            sendData();
        }
        roomTick();
        if(++syncTimer >= 40) {
            syncTimer = 0;
            setChanged();
            sendData();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
    }

    public boolean blowing() {
        return blowing && powered;
    }

    public boolean heating() {
        return heating && powered;
    }

    // ---- readings ----

    @Override
    public List<Component> readings() {
        var lines = new ArrayList<Component>();
        var condenser = condenser();
        String state = !powered ? "hvac.handler.unpowered" : callCool ? (condenser != null && condenser.cooling() ? "hvac.handler.cooling" : "hvac.handler.calling_cool")
                : heating ? "hvac.handler.heating" : blowing ? "hvac.handler.fan" : "hvac.handler.idle";
        var style = !powered ? ChatFormatting.RED : blowing ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        lines.add(Lang.builder().translate(state).style(style).component());
        lines.add(Lang.builder().translate("hvac.handler.room", Climate.degrees(roomF()), Climate.degrees(outdoorF())).style(ChatFormatting.GRAY).component());
        lines.add(Lang.builder().translate(condenser != null ? "hvac.handler.condenser_ok" : "hvac.handler.condenser_missing").style(condenser != null ? ChatFormatting.GRAY : ChatFormatting.GOLD).component());
        return lines;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.hvac.handler").style(ChatFormatting.GRAY).forGoggles(tooltip);
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
        tag.putBoolean("Powered", powered);
        tag.putBoolean("CallCool", callCool);
        tag.putBoolean("CallHeat", callHeat);
        tag.putBoolean("Blowing", blowing);
        tag.putBoolean("Heating", heating);
        tag.putFloat("RoomF", roomF());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        powered = tag.getBoolean("Powered");
        callCool = tag.getBoolean("CallCool");
        callHeat = tag.getBoolean("CallHeat");
        blowing = tag.getBoolean("Blowing");
        heating = tag.getBoolean("Heating");
        if(tag.contains("RoomF"))
            roomF = tag.getFloat("RoomF");
        if(blower != null) {
            blower.setState(blowing);
            heat.setState(heating);
        }
    }
}
