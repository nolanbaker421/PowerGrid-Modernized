package com.nolanbaker.pgmodernized;

import net.neoforged.neoforge.common.ModConfigSpec;

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
        PINION_LOCK = b.comment("Hold the body exactly at the rim speed, like teeth in a rack: a stopped pinion locks the body on the rack and a",
                        "turning one drags it along however heavy it is. Off, the pinion pushes softly within gain, max_acceleration and force.")
                .define("lock", true);
        PINION_GAIN = b.comment("How hard the pinion corrects the body's speed each physics step: 1 removes the whole speed error at once, lower is softer.")
                .defineInRange("gain", 0.5, 0.01, 1.0);
        PINION_MAX_ACCELERATION = b.comment("Most the pinion can accelerate the body, in metres per second squared, however heavy it is.")
                .defineInRange("max_acceleration", 12.0, 0.1, 1000.0);
        PINION_FORCE = b.comment("Multiplier on the pinion's push. Locked, it scales how much extra push may build up against friction and drag;",
                        "unlocked, it scales the whole push. Raise it when a carriage crawls below the rim speed, lower it if it lurches.")
                .defineInRange("force", 1.0, 0.01, 100.0);
        PINION_INVERT = b.comment("Flip the direction a given shaft rotation walks the body, if it goes the wrong way for you.")
                .define("invert", false);
        b.pop();
        SPEC = b.build();
    }

    private PgmConfig() {}
}
