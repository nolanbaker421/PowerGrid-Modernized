package com.nolanbaker.pgmodernized.device.gear;

import com.nolanbaker.pgmodernized.device.breaker.BreakerFrame;
import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What one slot of a switchgear cell holds and the state of it: the kind and its turn, and for
 * a breaker or a starter the panel's breaker record, for a fuse set its rating and whether it has
 * blown, for a switch its handle and lockout, for a contactor or a starter its coil, for a
 * starter its overload relay, for a metering unit its readings. Current through switched kinds is
 * kept for goggles. Bars have a thermal element too: past the bus rating they burn out.
 */
public final class GearSlot {
    /** What the bars carry before they burn, amperes. */
    public static final int BAR_RATING = 2000;
    /** The breaker a starter is built around. */
    public static final BreakerFrame STARTER_FRAME = BreakerFrame.F50;

    @Nullable
    private GearKind kind;
    private int rotation;
    private String label = "";
    /** The breaker record, used when the kind is a breaker or a starter. */
    public final BreakerPanelBlockEntity.Breaker breaker = new BreakerPanelBlockEntity.Breaker();
    private int fuseRating;
    private boolean blown;
    private float heat;
    private boolean on, locked;
    private boolean coil;
    private boolean overload;
    private float current, syncedCurrent;
    private final float[] volts = new float[3], amps = new float[3], watts = new float[3];

    @Nullable
    public GearKind kind() {
        return kind;
    }

    public boolean installed() {
        return kind != null;
    }

    public boolean is(GearKind kind) {
        return this.kind == kind;
    }

    public int rotation() {
        return rotation;
    }

    /** Conductor bits this slot offers on that side of itself. */
    public int portMask(GearSide side) {
        return kind == null ? 0 : kind.portMask(side, rotation);
    }

    public boolean carries(GearSide side, int k) {
        return (portMask(side) & (1 << k)) != 0;
    }

    /** The side the lines come in on and the side the loads leave by, for switched kinds. */
    public GearSide lineSide() {
        return GearSide.UP.rotated(rotation);
    }

    public GearSide loadSide() {
        return GearSide.DOWN.rotated(rotation);
    }

    /** Whether the three poles conduct. */
    public boolean closed() {
        if(kind == null)
            return false;
        return switch(kind) {
            case BREAKER -> breaker.closed();
            case FUSE -> !blown;
            case SWITCH -> on;
            case CONTACTOR -> coil;
            case STARTER -> breaker.closed() && coil && !overload;
            default -> false;
        };
    }

    private boolean usesBreaker() {
        return kind != null && kind.usesBreaker();
    }

    public boolean locked() {
        return usesBreaker() ? breaker.locked() : locked;
    }

    public void setLocked(boolean value) {
        if(usesBreaker())
            breaker.setLocked(value);
        else
            locked = value;
    }

    public String label() {
        return usesBreaker() ? breaker.label() : label;
    }

    public void setLabel(String value) {
        if(usesBreaker())
            breaker.setLabel(value);
        else
            label = value;
    }

    public int fuseRating() {
        return fuseRating;
    }

    public boolean blown() {
        return blown;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean value) {
        on = value;
    }

    public boolean coil() {
        return coil;
    }

    public void setCoil(boolean value) {
        coil = value;
    }

    /** A starter's overload relay has tripped; a bare hand resets it. */
    public boolean overload() {
        return overload;
    }

    public void resetOverload() {
        overload = false;
        heat = 0;
    }

    public float current() {
        return current;
    }

    public void setCurrent(float amps) {
        current = amps;
    }

    public boolean currentChanged() {
        return Math.abs(current - syncedCurrent) > Math.max(0.05f, Math.abs(syncedCurrent) * 0.02f);
    }

    /** A metering unit's readings per phase, RMS volts to the neutral, RMS amps, real watts. */
    public float volts(int phase) {
        return volts[phase];
    }

    public float amps(int phase) {
        return amps[phase];
    }

    public float watts(int phase) {
        return watts[phase];
    }

    public void setMeter(int phase, float v, float a, float w) {
        volts[phase] = Float.isFinite(v) ? v : 0;
        amps[phase] = Float.isFinite(a) ? a : 0;
        watts[phase] = Float.isFinite(w) ? w : 0;
        current = Math.max(Math.max(amps[0], amps[1]), amps[2]);
    }

    public void rotate() {
        if(kind != null && kind.rotatable())
            rotation = (rotation + 1) & 3;
    }

    // ---- installing ----

    public void clear() {
        kind = null;
        rotation = 0;
        label = "";
        breaker.clear();
        fuseRating = 0;
        blown = false;
        heat = 0;
        on = false;
        locked = false;
        overload = false;
        current = 0;
        java.util.Arrays.fill(volts, 0);
        java.util.Arrays.fill(amps, 0);
        java.util.Arrays.fill(watts, 0);
    }

    public void install(GearKind kind, int rating) {
        clear();
        this.kind = kind;
        if(kind == GearKind.FUSE)
            fuseRating = rating;
        if(kind == GearKind.STARTER)
            breaker.installFrame(STARTER_FRAME, 3);
    }

    public void installBreaker(BreakerItem item) {
        clear();
        kind = GearKind.BREAKER;
        breaker.installFrom(item, 3);
    }

    /** The item that fills this slot, to hand back or drop. A blown fuse set is gone. */
    public ItemStack item() {
        if(kind == null)
            return ItemStack.EMPTY;
        return switch(kind) {
            case BUS -> ModItems.BUS_BAR.asStack();
            case NEUTRAL_BAR -> ModItems.NEUTRAL_BAR.asStack();
            case GROUND_BAR -> ModItems.GROUND_BAR.asStack();
            case LUGS -> ModItems.GEAR_LUGS.asStack();
            case SWITCH -> ModItems.GEAR_SWITCH.asStack();
            case CONTACTOR -> ModItems.GEAR_CONTACTOR.asStack();
            case STARTER -> ModItems.GEAR_STARTER.asStack();
            case METER -> ModItems.GEAR_METER.asStack();
            case FUSE -> blown ? ItemStack.EMPTY : ModItems.fuse(fuseRating);
            case BREAKER -> breaker.item();
        };
    }

    // ---- ticking ----

    /** The fuses carry this current; true when they blow on this tick. */
    public boolean tickFuse(float amps) {
        if(blown) {
            current = 0;
            heat = BreakerTripCurve.cool(heat);
            return false;
        }
        current = amps;
        heat = BreakerTripCurve.step(heat, amps, fuseRating);
        if(!BreakerTripCurve.trips(heat, amps, fuseRating))
            return false;
        blown = true;
        heat = 0;
        current = 0;
        return true;
    }

    /** The bars carry this current; true when they burn out on this tick. */
    public boolean tickBar(float amps) {
        current = amps;
        heat = BreakerTripCurve.step(heat, amps, BAR_RATING);
        return BreakerTripCurve.trips(heat, amps, BAR_RATING);
    }

    /** A starter that is not conducting: its elements cool. */
    public void coolStarter() {
        current = 0;
        heat = BreakerTripCurve.cool(heat);
    }

    /**
     * A conducting starter carries this current. Returns 0 while it holds, 1 when the breaker trips
     * (a short: instant, past eight times the setting) and 2 when the overload relay trips (the
     * thermal curve on the same setting).
     */
    public int tickStarter(float amps) {
        current = amps;
        int rating = breaker.rating();
        if(amps >= rating * BreakerTripCurve.MAGNETIC_MULTIPLE) {
            breaker.setState(BreakerState.TRIPPED);
            heat = 0;
            current = 0;
            return 1;
        }
        heat = BreakerTripCurve.step(heat, amps, rating);
        if(!BreakerTripCurve.trips(heat, amps, rating))
            return 0;
        overload = true;
        heat = 0;
        current = 0;
        return 2;
    }

    // ---- persistence ----

    public CompoundTag write(boolean clientPacket) {
        var tag = new CompoundTag();
        if(kind == null)
            return tag;
        tag.putString("Kind", kind.id());
        if(rotation != 0)
            tag.putByte("Rotation", (byte) rotation);
        if(!label.isEmpty())
            tag.putString("Label", label);
        switch(kind) {
            case BREAKER -> tag.put("Breaker", breaker.write(clientPacket));
            case STARTER -> {
                tag.put("Breaker", breaker.write(clientPacket));
                tag.putBoolean("Coil", coil);
                tag.putBoolean("Overload", overload);
                if(!clientPacket)
                    tag.putFloat("Heat", heat);
            }
            case FUSE -> {
                tag.putInt("Rating", fuseRating);
                tag.putBoolean("Blown", blown);
                if(!clientPacket)
                    tag.putFloat("Heat", heat);
            }
            case SWITCH -> {
                tag.putBoolean("On", on);
                tag.putBoolean("Locked", locked);
            }
            case CONTACTOR -> tag.putBoolean("Coil", coil);
            case METER -> {
                if(clientPacket) {
                    var readings = new float[9];
                    for(int k = 0; k < 3; ++k) {
                        readings[k] = volts[k];
                        readings[3 + k] = amps[k];
                        readings[6 + k] = watts[k];
                    }
                    var list = new net.minecraft.nbt.ListTag();
                    for(float f : readings)
                        list.add(net.minecraft.nbt.FloatTag.valueOf(f));
                    tag.put("Meter", list);
                }
            }
            case BUS, NEUTRAL_BAR, GROUND_BAR -> {
                if(!clientPacket)
                    tag.putFloat("Heat", heat);
            }
            default -> {}
        }
        if(clientPacket && kind != GearKind.BREAKER && kind != GearKind.METER) {
            tag.putFloat("Current", current);
            syncedCurrent = current;
        } else if(clientPacket) {
            syncedCurrent = current;
        }
        return tag;
    }

    public void read(CompoundTag tag, boolean clientPacket) {
        clear();
        kind = tag.contains("Kind") ? GearKind.byId(tag.getString("Kind")) : null;
        if(kind == null)
            return;
        rotation = tag.getByte("Rotation") & 3;
        label = tag.getString("Label");
        switch(kind) {
            case BREAKER -> {
                if(tag.contains("Breaker"))
                    breaker.read(tag.getCompound("Breaker"), clientPacket);
                if(!breaker.isBreaker())
                    kind = null;
            }
            case STARTER -> {
                if(tag.contains("Breaker"))
                    breaker.read(tag.getCompound("Breaker"), clientPacket);
                if(!breaker.isBreaker())
                    breaker.installFrame(STARTER_FRAME, 3);
                coil = tag.getBoolean("Coil");
                overload = tag.getBoolean("Overload");
                heat = tag.getFloat("Heat");
            }
            case FUSE -> {
                fuseRating = tag.getInt("Rating");
                blown = tag.getBoolean("Blown");
                heat = tag.getFloat("Heat");
            }
            case SWITCH -> {
                on = tag.getBoolean("On");
                locked = tag.getBoolean("Locked");
            }
            case CONTACTOR -> coil = tag.getBoolean("Coil");
            case METER -> {
                if(tag.contains("Meter")) {
                    var list = tag.getList("Meter", net.minecraft.nbt.Tag.TAG_FLOAT);
                    for(int k = 0; k < 3 && 6 + k < list.size(); ++k)
                        setMeter(k, list.getFloat(k), list.getFloat(3 + k), list.getFloat(6 + k));
                }
            }
            case BUS, NEUTRAL_BAR, GROUND_BAR -> heat = tag.getFloat("Heat");
            default -> {}
        }
        if(clientPacket && tag.contains("Current"))
            current = tag.getFloat("Current");
    }

    /** The breaker's handle, for the ones that have one. */
    public BreakerState breakerState() {
        return breaker.state();
    }
}
