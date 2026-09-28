package com.nolanbaker.pgmodernized.compat.cc.transformer;

import com.nolanbaker.pgmodernized.device.transformer.TransformerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A transformer's readings over its network jack: nameplate, taps, every leg's voltage and current,
 * temperature and the cutouts. On a tier 2 or 3 unit a computer also sets the HV tap target that the
 * tap actuator on the tank works towards.
 */
public class TransformerPeripheral implements IPeripheral {
    private final TransformerBlockEntity transformer;

    public TransformerPeripheral(TransformerBlockEntity transformer) {
        this.transformer = transformer;
    }

    /** "10 kV / 480 V". */
    @LuaFunction
    public String getNameplate() {
        return transformer.spec().plate();
    }

    @LuaFunction
    public double getRatedVa() {
        return transformer.spec().ratedVa();
    }

    /** 1: manual taps. 2: an actuator moves the HV tap when the unit is dead. 3: on-load tap changer. */
    @LuaFunction
    public int getTier() {
        return transformer.tier();
    }

    @LuaFunction
    public int getHvTap() {
        return transformer.hvTap();
    }

    @LuaFunction
    public int getLvTap() {
        return transformer.lvTap();
    }

    /** Nominal high-side volts at the present HV tap. */
    @LuaFunction
    public double getHvVoltage() {
        return transformer.spec().hvAt(transformer.hvTap());
    }

    /** Nominal low-side volts at the present LV tap. */
    @LuaFunction
    public double getLvVoltage() {
        return transformer.spec().lvAt(transformer.lvTap());
    }

    @LuaFunction
    public int getLegCount() {
        return transformer.spec().kind().legs().length;
    }

    /** Measured volts of low-side leg n (1-based) against the neutral. */
    @LuaFunction
    public double getVoltage(int leg) {
        return transformer.voltage(legIndex(leg));
    }

    /** Measured amperes out of low-side leg n (1-based). */
    @LuaFunction
    public double getCurrent(int leg) {
        return transformer.current(legIndex(leg));
    }

    @LuaFunction
    public double getTemperature() {
        return transformer.temperature();
    }

    @LuaFunction
    public boolean isCutoutOpen() {
        return transformer.isCutoutOpen();
    }

    @LuaFunction
    public boolean areFusesBlown() {
        return transformer.fusesBlown();
    }

    @LuaFunction
    public int getHvTapTarget() {
        return transformer.hvTapTarget();
    }

    /** -4..4; the actuator on the tank moves the HV tap there, live on tier 3, dead only on tier 2. */
    @LuaFunction(mainThread = true)
    public void setHvTapTarget(int tap) {
        transformer.setHvTapTarget(tap);
    }

    /** Why the last tap step did or did not happen: "ok", "at_target", "live", "no_drive", "idle". */
    @LuaFunction
    public String getTapStatus() {
        return transformer.tapStatus().key();
    }

    private int legIndex(int leg) {
        int count = transformer.spec().kind().legs().length;
        return Math.max(0, Math.min(count - 1, leg - 1));
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_transformer";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof TransformerPeripheral that && this.transformer == that.transformer;
    }

    @Override
    public int hashCode() {
        return transformer.hashCode();
    }
}
