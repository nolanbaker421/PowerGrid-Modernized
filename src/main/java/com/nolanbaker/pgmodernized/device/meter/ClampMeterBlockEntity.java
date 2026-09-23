package com.nolanbaker.pgmodernized.device.meter;

import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import com.nolanbaker.pgmodernized.util.WireGeometry;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

/**
 * Clamp meter: every few ticks it looks for wire entities whose path runs through the block's
 * interior ("the jaw") and reports the current flowing in the first one it finds. It is not part
 * of any circuit, so it loads nothing and can be placed around an existing line.
 */
public class ClampMeterBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private static final int SCAN_INTERVAL = 5;
    private static final double JAW_INSET = 1.0 / 16.0;
    private static final double SEARCH_RADIUS = 64;

    private float current;
    private int wireCount;
    private final AcReadings.Smoother smoother = new AcReadings.Smoother(SCAN_INTERVAL * 0.05);
    private float syncedCurrent;
    private int scanCountdown;

    public ClampMeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(0);
    }

    /** Region a wire must pass through to be measured. */
    public AABB getJaw() {
        return new AABB(worldPosition).deflate(JAW_INSET);
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null || level.isClientSide)
            return;
        if(--scanCountdown > 0)
            return;
        scanCountdown = SCAN_INTERVAL;

        var jaw = getJaw();
        // Entity lookups only scan sections near the query box, but a wire entity is anchored at one point of a
        // run that can be tens of blocks long. Search a wide area and let passesThrough() do the precise test.
        var wires = level.getEntitiesOfClass(BaseWireEntity.class, jaw.inflate(SEARCH_RADIUS),
                wire -> wire.isAlive() && WireGeometry.passesThrough(wire, jaw));
        wireCount = wires.size();
        // Magnitude from the wires' RMS (heatingCurrent is the RMS where the wire keeps one); the
        // sign is kept only when the instantaneous sum agrees with it, which on DC it does exactly.
        double instantaneous = 0, rms = 0;
        for(var wire : wires) {
            instantaneous += AcReadings.finite(wire.current());
            rms += AcReadings.finite(wire.heatingCurrent());
        }
        current = (float) smoother.update(instantaneous, rms);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide && Math.abs(current - syncedCurrent) > 0.005f)
            sendData();
    }

    /** Signed current (A) in the clamped wire(s); sign follows the wire's own orientation. Zero when nothing is clamped. */
    public float getCurrent() {
        return current;
    }

    public boolean isClamped() {
        return wireCount > 0;
    }

    public int getWireCount() {
        return wireCount;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if(clientPacket) {
            current = tag.getFloat("Current");
            wireCount = tag.getInt("Wires");
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if(clientPacket) {
            tag.putFloat("Current", current);
            tag.putInt("Wires", wireCount);
            syncedCurrent = current;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.clamp_meter.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(wireCount == 0) {
            Lang.builder().translate("gui.clamp_meter.no_wire").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        } else {
            Lang.builder()
                    .text(String.format("%.3f ", current))
                    .add(Unit.CURRENT.get())
                    .style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
            if(wireCount > 1)
                Lang.builder().translate("gui.clamp_meter.multiple", wireCount).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        return true;
    }
}
