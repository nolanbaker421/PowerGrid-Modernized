package com.nolanbaker.pgmodernized.device.analogio;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlock.*;

public class AnalogIOBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, INetworkJack, IDeviceSpliceHost {
    private DeviceSpliceHost deviceHubs;

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, AnalogIOBlock.LAYOUT);
        return deviceHubs;
    }

    public static final float MAX_VOLTAGE = 24.0f;
    public static final float CURRENT_LIMIT = 0.5f;
    private static final float LIMIT_RECOVERY_PER_TICK = 0.5f;

    // Allocated lazily: SmartBlockEntity's constructor calls buildCircuit() before field initializers run.
    private float[] setpoints, applied, limitCap, inputs, syncedInputs;
    private ProvidedVoltageSourceCoupling[] sources;
    private ElectricWire[] inputWires;

    private final JackSupport jack = new JackSupport(this, false);

    public AnalogIOBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureAllocated();
        setLazyTickRate(10);
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return IElectric.getTerminalPos(level, worldPosition, JACK);
    }

    @Override
    public int jackTerminalIndex() {
        return JACK;
    }

    private void ensureAllocated() {
        if(setpoints != null)
            return;
        setpoints = new float[CHANNELS];
        applied = new float[CHANNELS];
        limitCap = new float[CHANNELS];
        inputs = new float[CHANNELS];
        syncedInputs = new float[CHANNELS];
        sources = new ProvidedVoltageSourceCoupling[CHANNELS];
        inputWires = new ElectricWire[CHANNELS];
        java.util.Arrays.fill(limitCap, MAX_VOLTAGE);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureAllocated();
        deviceHubs().buildCircuit(builder);
        var common = builder.terminalNode(COMMON_TERMINAL);
        float outputResistance = resistance("output");
        float inputResistance = resistance("input");
        for(int i = 0; i < CHANNELS; ++i) {
            final int channel = i;
            sources[i] = new ProvidedVoltageSourceCoupling(builder.terminalNode(OUTPUT_TERMINAL + i), common, outputResistance);
            sources[i].setVoltageProvider(() -> applied[channel]);
            builder.add(sources[i]);
            inputWires[i] = builder.connect(inputResistance, builder.terminalNode(INPUT_TERMINAL + i), common);
        }
    }

    @Override
    public void electricalTick() {
        for(int i = 0; i < CHANNELS; ++i) {
            var source = sources[i];
            if(source != null) {
                float current = Math.abs((float) source.getCurrent());
                if(current > CURRENT_LIMIT && Math.abs(applied[i]) > 0.001f) {
                    limitCap[i] = Math.abs(applied[i]) * CURRENT_LIMIT / current * 0.95f;
                } else {
                    limitCap[i] = Math.min(MAX_VOLTAGE, limitCap[i] + LIMIT_RECOVERY_PER_TICK);
                }
                applied[i] = Mth.clamp(setpoints[i], -limitCap[i], limitCap[i]);
            }
            var wire = inputWires[i];
            if(wire != null) {
                float value = (float) wire.potentialDifference();
                inputs[i] = Float.isFinite(value) ? value : 0;
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
        if(level == null || level.isClientSide)
            return;
        for(int i = 0; i < CHANNELS; ++i) {
            if(Math.abs(inputs[i] - syncedInputs[i]) > 0.01f) {
                sendData();
                return;
            }
        }
    }

    public void setOutput(int channel, float voltage) {
        if(!Float.isFinite(voltage))
            voltage = 0;
        voltage = Mth.clamp(voltage, -MAX_VOLTAGE, MAX_VOLTAGE);
        if(setpoints[channel] == voltage)
            return;
        setpoints[channel] = voltage;
        setChanged();
        sendData();
    }

    public float getOutput(int channel) {
        return setpoints[channel];
    }

    public float getAppliedOutput(int channel) {
        return applied[channel];
    }

    public float getOutputCurrent(int channel) {
        var source = sources[channel];
        return source == null ? 0 : (float) -source.getCurrent();
    }

    public float getInput(int channel) {
        return inputs[channel];
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        for(int i = 0; i < CHANNELS; ++i) {
            setpoints[i] = tag.getFloat("Out" + i);
            if(clientPacket) {
                inputs[i] = tag.getFloat("In" + i);
                applied[i] = tag.getFloat("Applied" + i);
            } else {
                applied[i] = setpoints[i];
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        for(int i = 0; i < CHANNELS; ++i) {
            tag.putFloat("Out" + i, setpoints[i]);
            if(clientPacket) {
                tag.putFloat("In" + i, inputs[i]);
                tag.putFloat("Applied" + i, applied[i]);
                syncedInputs[i] = inputs[i];
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.analog_io.outputs").style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(int i = 0; i < CHANNELS; ++i)
            channelLine(tooltip, i, applied[i], ChatFormatting.RED);
        Lang.builder().translate("gui.analog_io.inputs").style(ChatFormatting.GRAY).forGoggles(tooltip);
        for(int i = 0; i < CHANNELS; ++i)
            channelLine(tooltip, i, inputs[i], ChatFormatting.DARK_GREEN);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }

    private static void channelLine(List<Component> tooltip, int channel, float value, ChatFormatting color) {
        Lang.builder()
                .text(String.format("%d: %s%.2f ", channel + 1, value >= 0 ? " " : "", value))
                .add(Unit.VOLTAGE.get())
                .style(color)
                .forGoggles(tooltip, 1);
    }
}
