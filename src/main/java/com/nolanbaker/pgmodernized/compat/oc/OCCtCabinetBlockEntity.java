package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** CT cabinet that plugs straight into OpenComputers cables as component "powergrid_ct_cabinet". */
public class OCCtCabinetBlockEntity extends CtCabinetBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_ct_cabinet");
    private float reported;
    private float changeThreshold = 0;

    public OCCtCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || changeThreshold <= 0 || ocNode.network() == null)
            return;
        float value = totalPower();
        if(Math.abs(value - reported) >= changeThreshold) {
            reported = value;
            ocNode.sendToReachable("computer.signal", "power_change", (double) value);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
        if(!clientPacket && tag.contains("ChangeThreshold"))
            changeThreshold = tag.getFloat("ChangeThreshold");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
        if(!clientPacket)
            tag.putFloat("ChangeThreshold", changeThreshold);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    private int channel(Arguments args) {
        int channel = args.checkInteger(0);
        if(channel < 1 || channel > channels())
            throw new IllegalArgumentException("channel must be between 1 and " + channels());
        return channel - 1;
    }

    private static Object[] result(Object... values) {
        return values;
    }

    @Callback(direct = true, doc = "function():number -- Number of metered channels.")
    public Object[] getChannels(Context context, Arguments args) {
        return result(channels());
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Voltage (V) of the channel's In point against the Reference point.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) voltage(channel(args)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Current (A) through the channel, positive from In to Out.")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) current(channel(args)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Power (W) on the channel.")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) power(channel(args)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Power factor of the channel, real over apparent power; 1 on direct current.")
    public Object[] getPowerFactor(Context context, Arguments args) {
        return result((double) powerFactor(channel(args)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Energy (Wh) accumulated on the channel.")
    public Object[] getEnergy(Context context, Arguments args) {
        return result(energyWh(channel(args)));
    }

    @Callback(direct = true, doc = "function():number -- Power (W) summed over all channels.")
    public Object[] getTotalPower(Context context, Arguments args) {
        return result((double) totalPower());
    }

    @Callback(direct = true, doc = "function():number -- Energy (Wh) summed over all channels.")
    public Object[] getTotalEnergy(Context context, Arguments args) {
        return result(totalEnergyWh());
    }

    @Callback(direct = true, doc = "function():table -- Every channel: {voltage, current, power, powerFactor, energy}, indexed from 1.")
    public Object[] getReadings(Context context, Arguments args) {
        var table = new LinkedHashMap<Integer, Map<String, Double>>();
        for(int n = 0; n < channels(); ++n) {
            var channel = new LinkedHashMap<String, Double>();
            channel.put("voltage", (double) voltage(n));
            channel.put("current", (double) current(n));
            channel.put("power", (double) power(n));
            channel.put("powerFactor", (double) powerFactor(n));
            channel.put("energy", energyWh(n));
            table.put(n + 1, channel);
        }
        return result(table);
    }

    @Callback(doc = "function() -- Zero the energy counters.")
    public Object[] resetEnergy(Context context, Arguments args) {
        resetEnergy();
        return result();
    }

    @Callback(doc = "function(watts:number) -- Minimum change in total power that raises a 'power_change' signal; 0 disables signals.")
    public Object[] setChangeThreshold(Context context, Arguments args) {
        changeThreshold = (float) Math.max(0, args.checkDouble(0));
        setChanged();
        return result();
    }
}
