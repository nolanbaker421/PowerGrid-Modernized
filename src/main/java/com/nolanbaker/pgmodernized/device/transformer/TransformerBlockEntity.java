package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

/**
 * Ideal couplings from the primary to each secondary leg, at the ratio set on the value box, with
 * a small winding resistance in series. Every leg carries a tiny shunt for its current and a
 * high-resistance sense branch to the neutral for its voltage, which the goggles show.
 */
public class TransformerBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost {
    public static final float SHUNT = 0.001f;
    public static final float SENSE = 1_000_000f;

    // No initialisers: buildCircuit and addBehaviours run from the superclass constructor.
    private TransformerKind kind;
    private TransformerMount mount;
    private TransformerBlock block;
    private DeviceSpliceHost deviceHubs;
    private TransformerRatioBehaviour ratio;
    private TransformerCoupling[] couplings;
    private ElectricWire[] shunts;
    private ElectricWire[] senses;
    private float[] amps;
    private float[] volts;
    private float[] syncedAmps;

    public TransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureInit();
        setLazyTickRate(10);
    }

    private void ensureInit() {
        if(kind != null)
            return;
        var state = getBlockState();
        if(state != null && state.getBlock() instanceof TransformerBlock b) {
            block = b;
            kind = b.kind();
            mount = b.mount();
        } else {
            kind = TransformerKind.SPLIT_PHASE;
            mount = TransformerMount.DRY;
        }
        int legs = kind.legs().length;
        couplings = new TransformerCoupling[legs];
        shunts = new ElectricWire[legs];
        senses = new ElectricWire[legs];
        amps = new float[legs];
        volts = new float[legs];
        syncedAmps = new float[legs];
    }

    public TransformerKind kind() {
        ensureInit();
        return kind;
    }

    public TransformerMount mount() {
        ensureInit();
        return mount;
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null) {
            ensureInit();
            var layout = block != null ? block.layout() : new DeviceHubs.Layout(kind.pointCount(), 0, new int[0]);
            deviceHubs = new DeviceSpliceHost(this, layout);
        }
        return deviceHubs;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ensureInit();
        // Before the electric behaviour, whose construction builds the circuit at this ratio.
        ratio = new TransformerRatioBehaviour(this, new TransformerRatioBehaviour.FrontBox(TransformerGeometry.valueBox(mount, kind)));
        ratio.withCallback(i -> applyRatio());
        behaviours.add(ratio);
        super.addBehaviours(behaviours);
    }

    // ---- circuit ----

    public int ratioIndex() {
        return ratio == null ? TransformerRatio.UNITY : TransformerRatio.clamp(ratio.getValue());
    }

    /** Secondary volts per primary volt for one leg's coupling. */
    private float legRatio(int leg) {
        return TransformerRatio.value(ratioIndex()) * kind.legRatio(leg);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureInit();
        deviceHubs().buildCircuit(builder);
        float winding = resistance("winding");
        var neutral = builder.terminalNode(kind.neutralTerminal());
        var legs = kind.legs();
        for(int i = 0; i < legs.length; ++i) {
            var pair = kind.primaryPair(i);
            var p1 = builder.terminalNode(kind.primaryTerminal(pair[0]));
            var p2 = builder.terminalNode(kind.primaryTerminal(pair[1]));
            var out = builder.terminalNode(kind.secondaryTerminal(legs[i]));
            var mid = builder.addInternalNode();
            couplings[i] = kind.reversed(i)
                    ? builder.couple(legRatio(i), winding, p1, p2, neutral, mid)
                    : builder.couple(legRatio(i), winding, p1, p2, mid, neutral);
            shunts[i] = builder.connect(SHUNT, mid, out);
            senses[i] = builder.connect(SENSE, out, neutral);
        }
    }

    /** Re-tunes the couplings in place; the ratio is the only thing the value box changes. */
    public void applyRatio() {
        if(couplings == null)
            return;
        for(int i = 0; i < couplings.length; ++i) {
            if(couplings[i] != null)
                couplings[i].setRatio(legRatio(i));
        }
        setChanged();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    /** Signed current out of a leg, amperes. */
    protected float readCurrent(ElectricWire shunt) {
        float value = (float) shunt.current();
        return Float.isFinite(value) ? value : 0;
    }

    /** Voltage of a leg against the neutral. */
    protected float readVoltage(ElectricWire sense) {
        float value = (float) sense.potentialDifference();
        return Float.isFinite(value) ? value : 0;
    }

    @Override
    public void electricalTick() {
        if(shunts == null)
            return;
        for(int i = 0; i < shunts.length; ++i) {
            if(shunts[i] == null || senses[i] == null || !shunts[i].isConverged())
                continue;
            amps[i] = readCurrent(shunts[i]);
            volts[i] = readVoltage(senses[i]);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        for(int i = 0; i < amps.length; ++i) {
            if(Math.abs(amps[i] - syncedAmps[i]) > Math.max(0.01f, Math.abs(syncedAmps[i]) * 0.02f)) {
                sendData();
                return;
            }
        }
    }

    // ---- readings ----

    public float current(int leg) {
        return amps[leg];
    }

    public float voltage(int leg) {
        return volts[leg];
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket) {
            var list = new net.minecraft.nbt.ListTag();
            for(int i = 0; i < amps.length; ++i) {
                list.add(net.minecraft.nbt.FloatTag.valueOf(volts[i]));
                list.add(net.minecraft.nbt.FloatTag.valueOf(amps[i]));
                syncedAmps[i] = amps[i];
            }
            tag.put("Legs", list);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket && tag.contains("Legs")) {
            var list = tag.getList("Legs", net.minecraft.nbt.Tag.TAG_FLOAT);
            for(int i = 0; i < amps.length && i * 2 + 1 < list.size(); ++i) {
                volts[i] = list.getFloat(i * 2);
                amps[i] = list.getFloat(i * 2 + 1);
            }
        }
        // The value box read its setting with the other behaviours; the couplings were built before it.
        applyRatio();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().add(getBlockState().getBlock().getName()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.transformer.ratio_line", TransformerRatio.describe(ratioIndex())).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        var legs = kind.legs();
        for(int i = 0; i < legs.length; ++i) {
            Lang.builder()
                    .add(Component.translatable("powergrid." + kind.secondaryKey(legs[i])).copy().withStyle(ChatFormatting.GRAY))
                    .text(": ")
                    .add(Lang.builder().text(String.format("%.1f ", volts[i])).add(Unit.VOLTAGE.get()).style(ChatFormatting.GOLD))
                    .text("  ")
                    .add(Lang.builder().text(String.format("%.2f ", amps[i])).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 1);
        }
        if(mount.hasHubs())
            deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
