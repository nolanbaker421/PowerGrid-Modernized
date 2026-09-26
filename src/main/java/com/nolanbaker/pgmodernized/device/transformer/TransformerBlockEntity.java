package com.nolanbaker.pgmodernized.device.transformer;

import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
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
 * Ideal couplings from the primary to each secondary leg at the nameplate ratio moved by the two
 * taps, with a small winding resistance in series. Every leg carries a tiny shunt for its current
 * and a high-resistance sense branch to the neutral for its voltage, which the goggles show.
 */
public class TransformerBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost {
    public static final float SHUNT = 0.001f;
    public static final float SENSE = 1_000_000f;
    /** Contact resistance of a closed fused cutout on a pole can's bushing. */
    public static final float CUTOUT = 0.0005f;

    // No initialisers: buildCircuit and addBehaviours run from the superclass constructor.
    private TransformerSpec spec;
    private TransformerBlock block;
    private DeviceSpliceHost deviceHubs;
    private TransformerTapBehaviour hvTap;
    private TransformerTapBehaviour lvTap;
    private TransformerCoupling[] couplings;
    private ElectricWire[] shunts;
    private ElectricWire[] senses;
    private float[] amps;
    private float[] volts;
    private float[] syncedAmps;
    private SwitchedWire[] cutouts;
    private IElectricNode[] primaryNodes;
    private boolean cutoutOpen;

    public TransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureInit();
        setLazyTickRate(10);
    }

    private void ensureInit() {
        if(spec != null)
            return;
        var state = getBlockState();
        if(state != null && state.getBlock() instanceof TransformerBlock b) {
            block = b;
            spec = b.spec();
        } else {
            spec = TransformerSpec.PAD_10KV_240V;
        }
        int legs = spec.kind().legs().length;
        couplings = new TransformerCoupling[legs];
        shunts = new ElectricWire[legs];
        senses = new ElectricWire[legs];
        amps = new float[legs];
        volts = new float[legs];
        syncedAmps = new float[legs];
        cutouts = new SwitchedWire[spec.kind().primaries()];
        primaryNodes = new IElectricNode[spec.kind().primaries()];
    }

    public TransformerSpec spec() {
        ensureInit();
        return spec;
    }

    public TransformerKind kind() {
        return spec().kind();
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null) {
            ensureInit();
            var layout = block != null ? block.layout() : new DeviceHubs.Layout(spec.kind().pointCount(), 0, new int[0]);
            deviceHubs = new DeviceSpliceHost(this, layout);
        }
        return deviceHubs;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ensureInit();
        // Before the electric behaviour, whose construction builds the circuit at these taps.
        hvTap = new TransformerTapBehaviour(this, true);
        hvTap.withCallback(i -> applyRatio());
        lvTap = new TransformerTapBehaviour(this, false);
        lvTap.withCallback(i -> applyRatio());
        behaviours.add(hvTap);
        behaviours.add(lvTap);
        super.addBehaviours(behaviours);
    }

    // ---- circuit ----

    public int hvTap() {
        return hvTap == null ? 0 : hvTap.tap();
    }

    public int lvTap() {
        return lvTap == null ? 0 : lvTap.tap();
    }

    /** Secondary volts per primary volt for each leg's coupling at the present taps. */
    private float legRatio() {
        return spec.legRatio(hvTap(), lvTap());
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureInit();
        deviceHubs().buildCircuit(builder);
        float winding = resistance("winding");
        var kind = spec.kind();
        var neutral = builder.terminalNode(kind.neutralTerminal());
        // A pole can carries a fused cutout on each high-side bushing; open them to work the taps dead.
        for(int k = 0; k < kind.primaries(); ++k) {
            var bushing = builder.terminalNode(kind.primaryTerminal(k));
            if(spec.size().isPole()) {
                var live = builder.addInternalNode();
                cutouts[k] = builder.connectSwitch(CUTOUT, bushing, live, !cutoutOpen);
                primaryNodes[k] = live;
            } else {
                cutouts[k] = null;
                primaryNodes[k] = bushing;
            }
        }
        var legs = kind.legs();
        for(int i = 0; i < legs.length; ++i) {
            var pair = kind.primaryPair(i);
            var p1 = primaryNodes[pair[0]];
            var p2 = primaryNodes[pair[1]];
            var out = builder.terminalNode(kind.secondaryTerminal(legs[i]));
            var mid = builder.addInternalNode();
            couplings[i] = kind.reversed(i)
                    ? builder.couple(legRatio(), winding, p1, p2, neutral, mid)
                    : builder.couple(legRatio(), winding, p1, p2, mid, neutral);
            shunts[i] = builder.connect(SHUNT, mid, out);
            senses[i] = builder.connect(SENSE, out, neutral);
        }
    }

    /** Re-tunes the couplings in place; the taps are the only thing the value boxes change. */
    public void applyRatio() {
        if(couplings == null)
            return;
        for(var coupling : couplings) {
            if(coupling != null)
                coupling.setRatio(legRatio());
        }
        setChanged();
    }

    /** The highest voltage on one side, for the tap changer's arc. */
    public float liveVolts(boolean highSide) {
        float max = 0;
        if(!highSide) {
            for(float v : volts)
                max = Math.max(max, Math.abs(v));
            return max;
        }
        // Measured past the cutouts: a can with its cutouts open is dead at the winding.
        var kind = spec.kind();
        var reference = primaryNodes[0];
        for(int k = 1; k < kind.primaries(); ++k) {
            var node = primaryNodes[k];
            if(node == null || reference == null)
                continue;
            double v = Math.abs(node.getVoltage() - reference.getVoltage());
            if(Double.isFinite(v))
                max = Math.max(max, (float) v);
        }
        return max;
    }

    // ---- cutout ----

    /** Pole cans have fused cutouts on their high-side bushings. */
    public boolean hasCutout() {
        return spec().size().isPole();
    }

    public boolean isCutoutOpen() {
        return cutoutOpen;
    }

    /** Server side: pull the cutouts open or push them closed with the hot stick. */
    public void toggleCutout(Player player) {
        cutoutOpen = !cutoutOpen;
        if(cutouts != null) {
            for(var wire : cutouts) {
                if(wire != null)
                    wire.setState(!cutoutOpen);
            }
        }
        if(level != null)
            level.playSound(null, worldPosition, cutoutOpen ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.4f);
        player.displayClientMessage(Lang.builder().translate(cutoutOpen ? "message.transformer.cutout_open" : "message.transformer.cutout_closed")
                .style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
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
        tag.putBoolean("Cutout", cutoutOpen);
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
        cutoutOpen = tag.getBoolean("Cutout");
        if(cutouts != null) {
            for(var wire : cutouts) {
                if(wire != null)
                    wire.setState(!cutoutOpen);
            }
        }
        // The taps read their settings with the other behaviours; the couplings were built before them.
        applyRatio();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().add(getBlockState().getBlock().getName()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate("gui.transformer.taps",
                        String.format("%+d", hvTap()), TransformerSpec.volts(spec.hvAt(hvTap())),
                        String.format("%+d", lvTap()), TransformerSpec.volts(spec.lvAt(lvTap())))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        Lang.builder().translate("gui.transformer.rating", String.format("%.0f", spec.ratedVa() / 1000), String.format("%.0f", spec.ratedAmps()))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if(hasCutout())
            Lang.builder().translate(cutoutOpen ? "gui.transformer.cutout_open" : "gui.transformer.cutout_closed")
                    .style(cutoutOpen ? ChatFormatting.RED : ChatFormatting.GREEN).forGoggles(tooltip, 1);
        var legs = spec.kind().legs();
        for(int i = 0; i < legs.length; ++i) {
            Lang.builder()
                    .add(Component.translatable("powergrid." + spec.kind().secondaryKey(legs[i])).copy().withStyle(ChatFormatting.GRAY))
                    .text(": ")
                    .add(Lang.builder().text(String.format("%.1f ", volts[i])).add(Unit.VOLTAGE.get()).style(ChatFormatting.GOLD))
                    .text("  ")
                    .add(Lang.builder().text(String.format("%.2f ", amps[i])).add(Unit.CURRENT.get()).style(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 1);
        }
        if(spec.size().hasHubs())
            deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
