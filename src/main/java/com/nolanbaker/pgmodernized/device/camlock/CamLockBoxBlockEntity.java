package com.nolanbaker.pgmodernized.device.camlock;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceSpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.IDeviceSpliceHost;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
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

import java.util.List;

/** Five poles, each a contact between its receptacle and the inner end the knockout conductors splice onto. */
public class CamLockBoxBlockEntity extends ElectricBlockEntity implements IDeviceSpliceHost, IHaveGoggleInformation {
    private DeviceSpliceHost deviceHubs;

    // No initialisers: buildCircuit runs from the superclass constructor.
    private ElectricWire[] contacts;
    private float[] amps;
    private float[] syncedAmps;

    public CamLockBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public DeviceSpliceHost deviceHubs() {
        if(deviceHubs == null)
            deviceHubs = new DeviceSpliceHost(this, CamLockBoxBlock.LAYOUT);
        return deviceHubs;
    }

    @Override
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    public int ratedAmps() {
        return getBlockState().getBlock() instanceof CamLockBoxBlock box ? box.ratedAmps() : 0;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        deviceHubs().buildCircuit(builder);
        contacts = new ElectricWire[5];
        amps = new float[5];
        syncedAmps = new float[5];
        float r = resistance("contact");
        for(int k = 0; k < 5; ++k)
            contacts[k] = builder.connect(r, builder.terminalNode(k), builder.terminalNode(CamLockBoxBlock.INNER + k));
    }

    @Override
    public ConduitSize maxConduit() {
        return ConduitSize.FOUR;
    }

    @Override
    public void electricalTick() {
        if(contacts == null)
            return;
        for(int k = 0; k < 5; ++k) {
            double i = ForkHooks.get().rmsCurrent(contacts[k]);
            amps[k] = Double.isFinite(i) ? (float) Math.abs(i) : 0;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if(level == null || level.isClientSide)
            return;
        deviceHubs().lazyTick();
        if(amps == null)
            return;
        for(int k = 0; k < 5; ++k) {
            if(Math.abs(amps[k] - syncedAmps[k]) > Math.max(0.5f, syncedAmps[k] * 0.05f)) {
                sendData();
                return;
            }
        }
    }

    public float amps(int pole) {
        return amps == null ? 0 : amps[pole];
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        deviceHubs().write(tag, registries, clientPacket);
        if(clientPacket && amps != null) {
            for(int k = 0; k < 5; ++k) {
                tag.putFloat("Amps" + k, amps[k]);
                syncedAmps[k] = amps[k];
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        deviceHubs().read(tag, registries, clientPacket);
        if(clientPacket) {
            if(amps == null)
                amps = new float[5];
            for(int k = 0; k < 5; ++k)
                amps[k] = tag.getFloat("Amps" + k);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int rated = ratedAmps();
        Lang.builder().translate("gui.cam_lock.title", rated).style(ChatFormatting.GRAY).forGoggles(tooltip);
        String[] poles = {"L1", "L2", "L3", "N", "G"};
        float worst = 0;
        for(int k = 0; k < 5; ++k) {
            float a = amps(k);
            worst = Math.max(worst, a);
            Lang.builder().text(poles[k] + "  ").style(ChatFormatting.WHITE)
                    .add(Lang.builder().text(String.format("%.1f A", a)).style(a > rated ? ChatFormatting.RED : ChatFormatting.AQUA))
                    .forGoggles(tooltip, 1);
        }
        if(worst > rated)
            Lang.builder().translate("gui.cam_lock.overload", rated).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        deviceHubs().addGoggleLines(tooltip);
        return true;
    }
}
