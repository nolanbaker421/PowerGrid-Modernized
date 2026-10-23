package com.nolanbaker.pgmodernized.device.capacitor;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.special.CapacitorWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.capacitor.CapacitorBankBlock.*;

/**
 * Three capacitors in delta, each behind a contact the handle closes. On an alternating line they
 * draw a leading current that cancels the lagging current of motors and transformers upstream of
 * the point they hang on, which is what raises the power factor the CT cabinet reads; the goggles
 * show the reactive power they supply at the line's voltage and frequency. On direct current they
 * charge once and then sit there.
 */
public class CapacitorBankBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IDeviceSpliceHost {
    private static final float SENSE = 1_000_000f;

    private CapacitanceBehaviour capacitance;
    private DeviceSpliceHost deviceHubs;
    // No initialisers: buildCircuit runs from the superclass constructor.
    private SwitchedWire[] contacts;
    private CapacitorWire[] capacitors;
    private ElectricWire sense;
    private AcReadings.Filter[] readings;
    private double builtFarads = -1;
    private boolean needsRebuild;
    private boolean on;

    private float volts, amps, frequency, kvar;
    private float syncedAmps;

    public CapacitorBankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        capacitance = new CapacitanceBehaviour(this);
        capacitance.withCallback(i -> onCapacitance());
        behaviours.add(capacitance);
        super.addBehaviours(behaviours);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, LAYOUT);
        return deviceHubs;
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    /** Farads per capacitor. */
    public double farads() {
        return capacitance == null ? CapacitanceBehaviour.farads(CapacitanceBehaviour.DEFAULT) : capacitance.farads();
    }

    public boolean isOn() {
        return on;
    }

    /** A capacitor's value is fixed when it is made: a new setting lays the circuit again. */
    private void onCapacitance() {
        setChanged();
        if(Math.abs(farads() - builtFarads) < 1e-12)
            return;
        if(level == null || level.isClientSide) {
            needsRebuild = level == null;
            return;
        }
        if(electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false);
        setContacts();
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        contacts = new SwitchedWire[3];
        capacitors = new CapacitorWire[3];
        readings = new AcReadings.Filter[3];
        double farads = farads();
        builtFarads = farads;
        for(int k = 0; k < 3; ++k) {
            var a = builder.terminalNode(L1 + k);
            var b = builder.terminalNode(L1 + (k + 1) % 3);
            var mid = builder.addInternalNode();
            contacts[k] = builder.connectSwitch(resistance("contact"), a, mid, on);
            capacitors[k] = new CapacitorWire(farads, mid, b);
            builder.add(capacitors[k]);
            readings[k] = new AcReadings.Filter();
        }
        sense = ForkHooks.get().phaseSense(builder, SENSE, builder.terminalNode(L1), builder.terminalNode(L2));
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    private void setContacts() {
        if(contacts == null)
            return;
        for(var contact : contacts)
            if(contact != null)
                contact.setState(on);
    }

    @Override
    public void tick() {
        super.tick();
        if(needsRebuild && level != null && !level.isClientSide) {
            needsRebuild = false;
            onCapacitance();
        }
    }

    @Override
    public void electricalTick() {
        if(capacitors == null)
            return;
        double v = 0, i = 0, q = 0;
        boolean converged = false;
        for(int k = 0; k < 3; ++k) {
            var contact = contacts[k];
            if(contact == null || !contact.isConverged())
                continue;
            converged = true;
            // The voltage across the capacitor and the current through its contact; their product
            // is the reactive power a capacitor supplies, since it dissipates nothing.
            readings[k].sample(capacitors[k], contact, Double.NaN);
            double rv = readings[k].rmsVoltage(), ri = readings[k].rmsCurrent();
            v += rv;
            i += ri;
            q += rv * ri;
        }
        if(!converged)
            return;
        volts = (float) (v / 3);
        amps = (float) (i / 3);
        kvar = (float) (q / 1000);
        double hz = sense == null ? Double.NaN : ForkHooks.get().frequency(sense);
        frequency = Double.isFinite(hz) ? (float) hz : 0;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(Math.abs(amps - syncedAmps) > Math.max(0.05f, syncedAmps * 0.05f))
            sendData();
    }

    // ---- the handle ----

    /** Server side: puts the bank on the line or takes it off. */
    public void toggle(Player player) {
        on = !on;
        setContacts();
        if(level != null) {
            (on ? ModdedSoundEvents.CONTACTOR_ON : ModdedSoundEvents.CONTACTOR_OFF).playOnServer(level, worldPosition);
            var state = getBlockState();
            if(state.getBlock() instanceof CapacitorBankBlock)
                level.setBlock(worldPosition, state.setValue(ON, on), Block.UPDATE_ALL);
            player.displayClientMessage(Lang.builder().translate(on ? "message.capacitor_bank.on" : "message.capacitor_bank.off").style(ChatFormatting.GRAY).component(), true);
        }
        notifyUpdate();
    }

    // ---- readings ----

    /** RMS voltage across each capacitor: line to line. */
    public float volts() {
        return volts;
    }

    /** RMS current through each capacitor. */
    public float amps() {
        return amps;
    }

    public float frequency() {
        return frequency;
    }

    /** Reactive power the three capacitors supply together, kvar. */
    public float kvar() {
        return kvar;
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        tag.putBoolean("On", on);
        if(clientPacket) {
            tag.putFloat("Volts", volts);
            tag.putFloat("Amps", amps);
            tag.putFloat("Frequency", frequency);
            tag.putFloat("Kvar", kvar);
            syncedAmps = amps;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        on = tag.getBoolean("On");
        setContacts();
        if(clientPacket) {
            volts = tag.getFloat("Volts");
            amps = tag.getFloat("Amps");
            frequency = tag.getFloat("Frequency");
            kvar = tag.getFloat("Kvar");
        } else if(Math.abs(farads() - builtFarads) > 1e-12) {
            needsRebuild = true;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.capacitor_bank.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder().translate(on ? "gui.capacitor_bank.on" : "gui.capacitor_bank.off").style(on ? ChatFormatting.GREEN : ChatFormatting.RED).text("  ")
                .add(Lang.builder().translate("gui.capacitor_bank.size", Math.round(farads() * 1e6)).style(ChatFormatting.WHITE)).forGoggles(tooltip, 1);
        if(on) {
            var line = Lang.builder().text(String.format("%.1f V L-L  %.2f A", volts, amps)).style(ChatFormatting.AQUA);
            if(frequency > 0.05f)
                line.text(String.format("  %.1f Hz", frequency));
            line.forGoggles(tooltip, 1);
            Lang.builder().translate("gui.capacitor_bank.supplying", String.format("%.2f", kvar)).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        deviceHubs().addGoggleLines(tooltip, isPlayerSneaking);
        return true;
    }
}
