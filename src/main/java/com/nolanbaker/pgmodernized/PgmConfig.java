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
        SPEC = b.build();
    }

    private PgmConfig() {}
}
