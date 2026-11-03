package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.util.AcReadings;
import com.nolanbaker.pgmodernized.util.ShockDamage;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.nolanbaker.pgmodernized.device.pole.SwitchContactBlock.*;

/**
 * One contact of a pole-top switch: a blade between the line and load lugs, closed or open. The
 * open gap holds off what the insulators under it allow, two kilovolts for the head itself and
 * eighteen more for every insulator in the stack; more voltage than that across the open blade
 * flashes over and arcs until the line is dead.
 */
public class SwitchContactBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private boolean closed, arcing;
    private float amps, volts;
    private int withstand = SwitchInsulatorBlock.BASE_WITHSTAND;
    private SwitchedWire contact, arc;
    private ElectricWire sense;
    private AcReadings.Filter currentReading, gapReading;
    private int syncTimer, sparkTimer;

    public SwitchContactBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(TERMINAL_COUNT);
        var line = builder.terminalNode(LINE);
        var load = builder.terminalNode(LOAD);
        contact = builder.connectSwitch(resistance("contact"), line, load, closed);
        arc = builder.connectSwitch(resistance("arc"), line, load, arcing);
        sense = builder.connect(resistance("sense"), line, load);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        if(level == null || level.isClientSide || contact == null)
            return;
        if(currentReading == null) {
            currentReading = new AcReadings.Filter();
            gapReading = new AcReadings.Filter();
        }
        var carrying = arcing ? arc : contact;
        if(carrying.isConverged())
            currentReading.sample(carrying);
        if(sense.isConverged())
            gapReading.sample(sense);
        amps = closed || arcing ? (float) AcReadings.finite(currentReading.rmsCurrent()) : 0;
        volts = (float) AcReadings.finite(gapReading.rmsVoltage());
    }

    @Override
    public void tick() {
        super.tick();
        if(level == null)
            return;
        if(level.isClientSide) {
            if(arcing && ++sparkTimer >= 8) {
                sparkTimer = 0;
                sparks(3);
            }
            return;
        }
        boolean shouldArc = !closed && volts > withstand;
        if(arcing && !closed && volts < withstand * 0.25f)
            shouldArc = false;
        if(closed)
            shouldArc = false;
        boolean changed = false;
        if(shouldArc != arcing) {
            arcing = shouldArc;
            if(arc != null)
                arc.setState(arcing);
            if(arcing)
                level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6f, 1.6f);
            changed = true;
        }
        if(++syncTimer >= 20 || changed) {
            syncTimer = 0;
            setChanged();
            sendData();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level != null && !level.isClientSide)
            withstand = SwitchInsulatorBlock.withstand(level, worldPosition);
    }

    private void sparks(int count) {
        var facing = SwitchContactBlock.facing(getBlockState());
        var local = PanelLayout.fromNorthFrame(new Vec3(8, 7, 8), facing).scale(1 / 16.0);
        var pos = local.add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        SparkParticleData.explodeParticles(level, pos.x, pos.y, pos.z, facing, count);
    }

    // ---- throwing ----

    public boolean isClosed() {
        return closed;
    }

    public boolean isArcing() {
        return arcing;
    }

    public int withstand() {
        return withstand;
    }

    /**
     * Server side: the blade closes or opens. By hand on a gap with voltage across it the hand is
     * on the live parts, a shock; from an operator at the foot of the stack or a hot stick, none.
     */
    public void setClosed(boolean value, @Nullable Player player, boolean byHand) {
        if(level == null || level.isClientSide || value == closed)
            return;
        if(byHand && player != null && volts > ShockDamage.SAFE_VOLTS)
            ShockDamage.shockVolts(player, volts);
        closed = value;
        if(contact != null)
            contact.setState(closed);
        level.playSound(null, worldPosition, closed ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, 0.8f);
        if(player != null)
            player.displayClientMessage(Lang.translate(closed ? "message.switch_contact.closed" : "message.switch_contact.open").style(ChatFormatting.GRAY).component(), true);
        setChanged();
        sendData();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.switch_contact.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        String state = arcing ? "gui.cutout.flashover" : closed ? "gui.cutout.closed" : "gui.cutout.open";
        var style = arcing ? ChatFormatting.RED : closed ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        var line = Lang.builder().translate(state).style(style);
        if(closed || arcing)
            line.text("  ").add(Lang.builder().text(String.format("%.1f A", amps)).style(ChatFormatting.GOLD));
        else if(volts > 1)
            line.text("  ").add(Lang.builder().text(String.format("%.0f V", volts)).style(ChatFormatting.AQUA));
        line.forGoggles(tooltip, 1);
        Lang.builder().translate("gui.switch_contact.withstand", withstand / 1000).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }

    // ---- nbt ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Closed", closed);
        tag.putBoolean("Arcing", arcing);
        tag.putInt("Withstand", withstand);
        if(clientPacket) {
            tag.putFloat("Amps", amps);
            tag.putFloat("Volts", volts);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        closed = tag.getBoolean("Closed");
        arcing = tag.getBoolean("Arcing");
        if(tag.contains("Withstand"))
            withstand = tag.getInt("Withstand");
        amps = tag.getFloat("Amps");
        volts = tag.getFloat("Volts");
        if(contact != null) {
            contact.setState(closed);
            arc.setState(arcing);
        }
    }
}
