package com.nolanbaker.pgmodernized;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity;

/** Common config: config/powergrid_modernized-common.toml. Read at use, so edits apply on the next reload. */
public final class PgmConfig {
    public static final ModConfigSpec SPEC;

    /** One in this many, per second of recent electrical work; 0 turns the visits off. */
    public static final ModConfigSpec.IntValue INSPECTOR_CHANCE;
    public static final ModConfigSpec.IntValue INSPECTOR_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue INSPECTOR_WORK_WINDOW_SECONDS;
    public static final ModConfigSpec.IntValue INSPECTOR_BRIBE;
    public static final ModConfigSpec.IntValue INSPECTOR_NOTICE_RANGE;
    public static final ModConfigSpec.BooleanValue TRAINING_CENTER_AT_CENTER;
    public static final ModConfigSpec.IntValue TRAINING_CENTER_WEIGHT;
    public static final ModConfigSpec.DoubleValue SCRAP_CHANCE;
    public static final ModConfigSpec.BooleanValue PUSH_BROOM_SPAWNS;
    public static final ModConfigSpec.IntValue CABLE_CHAIN_MAX_LENGTH;
    public static final ModConfigSpec.DoubleValue PINION_PITCH_RADIUS;
    public static final ModConfigSpec.DoubleValue PINION_GAIN;
    public static final ModConfigSpec.DoubleValue PINION_MAX_ACCELERATION;
    public static final ModConfigSpec.BooleanValue PINION_INVERT;
    public static final ModConfigSpec.DoubleValue PINION_FORCE;
    public static final ModConfigSpec.BooleanValue PINION_LOCK;
    public static final ModConfigSpec.IntValue INVERTER_BUFFER;
    public static final ModConfigSpec.DoubleValue WATTS_PER_SU;
    public static final ModConfigSpec.DoubleValue MOTOR_EFFICIENCY;
    public static final ModConfigSpec.DoubleValue MOTOR_MAX_CAPACITY;
    public static final ModConfigSpec.DoubleValue RANGEFINDER_MAX_RANGE;
    public static final ModConfigSpec.DoubleValue RANGEFINDER_DEFAULT_RANGE;
    public static final ModConfigSpec.IntValue RANGEFINDER_INTERVAL;
    public static final ModConfigSpec.IntValue INVERTER_MAX_INPUT;
    public static final ModConfigSpec.DoubleValue INVERTER_FE_PER_WATT;
    public static final ModConfigSpec.DoubleValue INVERTER_EFFICIENCY;

    static {
        var b = new ModConfigSpec.Builder();
        b.comment("The electrical inspector").push("inspector");
        INSPECTOR_CHANCE = b.comment("Each second a player has done electrical work in the last work window, the inspector has a one-in-this chance of spawning.",
                        "600 is about one visit per ten minutes of wiring. 0 disables the visits (spawn eggs still work).")
                .defineInRange("spawn_chance", 600, 0, 1_000_000);
        INSPECTOR_COOLDOWN_SECONDS = b.comment("Seconds after a visit before the same player can get another.")
                .defineInRange("cooldown_seconds", 600, 0, 86_400);
        INSPECTOR_WORK_WINDOW_SECONDS = b.comment("How long after a player's last electrical work they still count as working.")
                .defineInRange("work_window_seconds", 300, 1, 86_400);
        INSPECTOR_BRIBE = b.comment("Emeralds that make him forget he asked.")
                .defineInRange("bribe_emeralds", 8, 1, 64);
        INSPECTOR_NOTICE_RANGE = b.comment("An inspector who is already about, idle or leaving, asks anyone who does electrical work within this many blocks of him.")
                .defineInRange("notice_range", 16, 1, 64);
        b.pop();
        b.comment("Village generation").push("village");
        TRAINING_CENTER_AT_CENTER = b.comment("Every newly generated village gets an Electrical Training Center a few blocks east of its well or meeting point.")
                .define("training_center_at_center", true);
        TRAINING_CENTER_WEIGHT = b.comment("Weight of extra training centers in every village's house pool (vanilla house weights total 70 to 90). 0 adds none.")
                .defineInRange("training_center_weight", 3, 0, 100);
        b.pop();
        b.comment("Copper scrap").push("scrap");
        SCRAP_CHANCE = b.comment("Chance that pulling a wire into conduit, or editing a splice, leaves a piece of copper scrap.")
                .defineInRange("chance", 0.12, 0.0, 1.0);
        b.pop();
        b.comment("The push broom").push("push_broom");
        PUSH_BROOM_SPAWNS = b.comment("Whether push brooms spawn naturally at night. Spawn eggs always work.")
                .define("natural_spawns", true);
        b.pop();
        b.comment("Cable chains").push("cable_chain");
        CABLE_CHAIN_MAX_LENGTH = b.comment("Longest run, in metres, a cable chain can be strung over (measured as the anchors hang in the world).")
                .defineInRange("max_length", 32, 4, 128);
        b.pop();
        b.comment("Rack and pinion").push("pinion");
        PINION_PITCH_RADIUS = b.comment("Pitch radius of the pinion in metres: the body moves at the shaft speed times this (0.5 means 64 rpm walks 3.35 m/s).")
                .defineInRange("pitch_radius", 0.5, 0.05, 4.0);
        PINION_LOCK = b.comment("Hold the body exactly at the rim speed, like teeth in a rack: a stopped pinion locks the body on the rack, a",
                        "turning one drags it along however heavy it is, and the body is kept from turning. Solid blocks still stop it.",
                        "gain and max_acceleration are not used while locked. Off, the pinion pushes softly within gain, max_acceleration and force.")
                .define("lock", true);
        PINION_GAIN = b.comment("How hard the pinion corrects the body's speed each physics step: 1 removes the whole speed error at once, lower is softer.")
                .defineInRange("gain", 0.5, 0.01, 1.0);
        PINION_MAX_ACCELERATION = b.comment("Most the pinion can accelerate the body, in metres per second squared, however heavy it is.")
                .defineInRange("max_acceleration", 12.0, 0.1, 1000.0);
        PINION_FORCE = b.comment("Locked: how far past the rim speed the pinion may lean in, as a multiple of the rim speed, while the guides keep",
                        "dragging the body short of it (2 means up to three times the rim speed). Unlocked: multiplier on the whole push.",
                        "Raise it when a carriage sticks or crawls, lower it if it lurches when the sticking lets go.")
                .defineInRange("force", 2.0, 0.01, 100.0);
        PINION_INVERT = b.comment("Flip the direction a given shaft rotation walks the body, if it goes the wrong way for you.")
                .define("invert", false);
        b.pop();
        b.comment("Energy accounting between Create stress, Forge Energy and Power Grid watts").push("energy");
        WATTS_PER_SU = b.comment("Watts one Create stress unit is worth; the three-phase motor hands out stress it has bought at this rate.",
                        "0 uses Power Grid's own figure for its motors and generators (torqueForStress over its conversion constant,",
                        "about 0.159 W per SU: a kilowatt buys about 6,300 SU). A loop inverter -> motor -> New Age generator -> inverter",
                        "stays lossy while this is above New Age's 0.029296875 FE per SU per tick divided by the FE per watt: with",
                        "Power Grid's 10 FE per watt that floor is 0.003 W per SU. Below it the loop makes energy.")
                .defineInRange("watts_per_su", 0.0, 0.0, 1000.0);
        MOTOR_EFFICIENCY = b.comment("Fraction of the electrical power the three-phase motor turns into stress; the rest is loss.")
                .defineInRange("motor_efficiency", 0.9, 0.05, 1.0);
        MOTOR_MAX_CAPACITY = b.comment("Most stress per rpm a three-phase motor can carry, however much it is fed (1024 is four creative motors at 256 rpm).")
                .defineInRange("motor_max_capacity", 1024.0, 1.0, 1_000_000.0);
        b.pop();
        b.comment("FE inverter").push("inverter");
        INVERTER_BUFFER = b.comment("FE the inverter stores. It must cover one tick of its load, and a brownout ends once it holds a second's worth again.")
                .defineInRange("buffer", 1_000_000, 1_000, 1_000_000_000);
        INVERTER_MAX_INPUT = b.comment("Most FE it accepts per tick from cables.")
                .defineInRange("max_input", 100_000, 1, 1_000_000_000);
        INVERTER_FE_PER_WATT = b.comment("FE taken each tick for every watt delivered on the lines: the figure Power Grid's own FE Inverter and Device",
                        "Connector use (its forgeEnergyPerWatt, 10 by default: a watt is 10 FE a tick, 1 kW is 10,000 FE a tick).",
                        "0 follows Power Grid's setting, so a loop inverter -> lines -> Device Connector -> FE can only lose energy.",
                        "Set it only to price this inverter apart from Power Grid's.")
                .defineInRange("fe_per_watt", 0.0, 0.0, 1_000_000.0);
        INVERTER_EFFICIENCY = b.comment("Fraction of the FE that becomes AC power; the rest is loss.")
                .defineInRange("efficiency", 0.95, 0.05, 1.0);
        b.pop();
        b.comment("Laser rangefinder").push("rangefinder");
        RANGEFINDER_MAX_RANGE = b.comment("Farthest any rangefinder can be set to look, in blocks.")
                .defineInRange("max_range", 512.0, 1.0, 4096.0);
        RANGEFINDER_DEFAULT_RANGE = b.comment("Range of a rangefinder that has not been given its own, in blocks. Also the comparator's zero point.")
                .defineInRange("default_range", 128.0, 1.0, 4096.0);
        RANGEFINDER_INTERVAL = b.comment("Ticks between measurements. 1 is every tick; a moving crane reads smoothly at 2.")
                .defineInRange("interval", 2, 1, 100);
        b.pop();
        SPEC = b.build();
    }

    /** Create: New Age's generator rate, FE per stress unit per tick: the floor on what a stress unit may cost. */
    public static final double NEW_AGE_FE_PER_SU_TICK = 0.029296875;

    /** FE taken per watt per tick: the configured figure, or Power Grid's forgeEnergyPerWatt, which its FE Inverter and Device Connector use. */
    public static double fePerWattTick() {
        double configured = INVERTER_FE_PER_WATT.get();
        if(configured > 0)
            return configured;
        var configs = ModdedConfigs.server();
        return configs == null ? 10 : configs.electricity.forgeEnergyPerWatt.getF();
    }

    /** Watts a stress unit is worth: the configured figure, or what Power Grid's own motors and generators use. */
    public static double wattsPerSu() {
        double configured = WATTS_PER_SU.get();
        if(configured > 0)
            return configured;
        var configs = ModdedConfigs.server();
        float perStress = configs == null ? 15 : configs.kinetics.torqueForStress.getF();
        return perStress / ElectricMotorBlockEntity.CONVERSION_CONSTANT;
    }

    private PgmConfig() {}
}
