package com.nolanbaker.pgmodernized.device.meter;

import com.nolanbaker.pgmodernized.util.AcReadings;
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
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlock.*;

/** Digital ammeter: reports the current through its shunt, signed from the IN terminal to the OUT terminal. */
public class LineAmmeterBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, INetworkJack, IDeviceSpliceHost {
    private DeviceSpliceHost deviceHubs;

    @Override
    public DeviceSpliceHost deviceHubs() {
        // Created lazily: buildCircuit runs from the superclass constructor, before field initialisers.
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LineAmmeterBlock.LAYOUT);
        return deviceHubs;
    }

    private ElectricWire shunt;
    private final AcReadings.Filter reading = new AcReadings.Filter();
    private float current;
    private float syncedCurrent;

    private final JackSupport jack = new JackSupport(this, false);

    public LineAmmeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
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
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        shunt = builder.connect(resistance("shunt"), builder.terminalNode(TERMINAL_IN), builder.terminalNode(TERMINAL_OUT));
    }

    @Override
    public void tick() {
        applyPower(shunt);
        super.tick();
        jack.tick();
    }

    @Override
    public void electricalTick() {
        if(shunt == null)
            return;
        reading.sample(shunt);
        float value = shunt.isConverged() ? (float) reading.signedRmsCurrent() : 0;
        current = Float.isFinite(value) ? value : 0;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        deviceHubs().lazyTick();
        if(level != null && !level.isClientSide && Math.abs(current - syncedCurrent) > Math.max(0.005f, Math.abs(syncedCurrent) * 0.002f))
            sendData();
    }

    /** Signed current (A); positive when flowing from the IN terminal to the OUT terminal. */
    public float getCurrent() {
        return current;
    }

    public float getPower() {
        return current * current * resistance("shunt");
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket)
            current = tag.getFloat("Current");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket) {
            tag.putFloat("Current", current);
            syncedCurrent = current;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.line_ammeter.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().text(String.format("%.3f ", current)).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
