package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.transformer.TransformerBlockEntity;
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

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Transformer that plugs straight into OpenComputers cables as component "powergrid_transformer". */
public class OCTransformerBlockEntity extends TransformerBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_transformer");

    public OCTransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
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
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    private int legIndex(Arguments args) {
        int count = spec().kind().legs().length;
        return Math.max(0, Math.min(count - 1, args.checkInteger(0) - 1));
    }

    @Callback(direct = true, doc = "function():string -- Nameplate, e.g. \"10 kV / 480 V\".")
    public Object[] getNameplate(Context context, Arguments args) {
        return result(spec().plate());
    }

    @Callback(direct = true, doc = "function():number -- Rated apparent power (VA).")
    public Object[] getRatedVa(Context context, Arguments args) {
        return result((double) spec().ratedVa());
    }

    @Callback(direct = true, doc = "function():number -- 1 manual taps, 2 actuator moves the HV tap dead, 3 on-load tap changer.")
    public Object[] getTier(Context context, Arguments args) {
        return result(tier());
    }

    @Callback(direct = true, doc = "function():number -- HV tap, -4..4.")
    public Object[] getHvTap(Context context, Arguments args) {
        return result(hvTap());
    }

    @Callback(direct = true, doc = "function():number -- LV tap, -4..4.")
    public Object[] getLvTap(Context context, Arguments args) {
        return result(lvTap());
    }

    @Callback(direct = true, doc = "function():number -- Nominal high-side volts at the present tap.")
    public Object[] getHvVoltage(Context context, Arguments args) {
        return result((double) spec().hvAt(hvTap()));
    }

    @Callback(direct = true, doc = "function():number -- Nominal low-side volts at the present tap.")
    public Object[] getLvVoltage(Context context, Arguments args) {
        return result((double) spec().lvAt(lvTap()));
    }

    @Callback(direct = true, doc = "function():number -- Low-side legs metered.")
    public Object[] getLegCount(Context context, Arguments args) {
        return result(spec().kind().legs().length);
    }

    @Callback(direct = true, doc = "function(leg:number):number -- Measured volts of low-side leg n against the neutral.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) voltage(legIndex(args)));
    }

    @Callback(direct = true, doc = "function(leg:number):number -- Measured amperes out of low-side leg n.")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) current(legIndex(args)));
    }

    @Callback(direct = true, doc = "function():number -- Winding temperature.")
    public Object[] getTemperature(Context context, Arguments args) {
        return result((double) temperature());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the HV cutouts are open.")
    public Object[] isCutoutOpen(Context context, Arguments args) {
        return result(isCutoutOpen());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the cutout fuses have blown.")
    public Object[] areFusesBlown(Context context, Arguments args) {
        return result(fusesBlown());
    }

    @Callback(direct = true, doc = "function():number -- HV tap the actuator works towards.")
    public Object[] getHvTapTarget(Context context, Arguments args) {
        return result(hvTapTarget());
    }

    @Callback(doc = "function(tap:number) -- Set the HV tap target, -4..4; the actuator on the tank moves the tap there.")
    public Object[] setHvTapTarget(Context context, Arguments args) {
        setHvTapTarget(args.checkInteger(0));
        return result();
    }

    @Callback(direct = true, doc = "function():string -- Last tap step: ok, at_target, live, no_drive or idle.")
    public Object[] getTapStatus(Context context, Arguments args) {
        return result(tapStatus().key());
    }
}
