package com.nolanbaker.pgmodernized.device.meter;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlock.*;

/** Digital voltmeter: measures the potential between its two probes through a 100 MΩ input. No range limit. */
public class LineVoltmeterBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, INetworkJack, IDeviceSpliceHost {
    private DeviceSpliceHost deviceHubs;

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LineVoltmeterBlock.LAYOUT);
        return deviceHubs;
    }

    private ElectricWire input;
    private float voltage;
    private float syncedVoltage;

    private final JackSupport jack = new JackSupport(this, false);

    public LineVoltmeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        input = builder.connect(resistance("input"), builder.terminalNode(PROBE_POSITIVE), builder.terminalNode(PROBE_NEGATIVE));
    }

    @Override
    public void electricalTick() {
        if(input == null)
            return;
        float value = (float) input.potentialDifference();
        voltage = Float.isFinite(value) ? value : 0;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
        if(level != null && !level.isClientSide && Math.abs(voltage - syncedVoltage) > Math.max(0.05f, Math.abs(syncedVoltage) * 0.002f))
            sendData();
    }

    /** Signed voltage (V) of the positive probe relative to the negative probe. */
    public float getVoltage() {
        return voltage;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket)
            voltage = tag.getFloat("Voltage");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket) {
            tag.putFloat("Voltage", voltage);
            syncedVoltage = voltage;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.line_voltmeter.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        String text = Math.abs(voltage) >= 1000 ? String.format("%.2f k", voltage / 1000) : String.format("%.2f ", voltage);
        Lang.builder().text(text).add(Unit.VOLTAGE.get()).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
