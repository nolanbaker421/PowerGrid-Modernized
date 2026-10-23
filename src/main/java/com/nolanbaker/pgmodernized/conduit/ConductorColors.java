package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Conductor colour sequence, in pull order: black, white, red, blue, then the rest, so the first
 * wire of a pull is a hot, the second the neutral and the third and fourth the other hots. The NEC
 * only reserves white and gray for the grounded conductor (200.6) and green for ground (250.119);
 * the hot colours are trade practice. A pulled wire can be given any of these from the splice
 * editor whatever its slot. The name and colour show up on terminals and in the junction box GUI.
 * <p>
 * Hardware terminals take their colours from here too, by what the terminal <em>is</em> rather
 * than by its index, so a lug reads like the wire that lands on it: {@link #hot(int)} for the
 * lines of a 120/208/240 V system (L1 black, L2 red, L3 blue), {@link #high(int)} for a 277/480 V
 * system (brown, orange, yellow, the usual second set where two voltage systems share a building,
 * 210.5(C)(1)), {@link #NEUTRAL} white (gray on the 480 V system) and {@link #GROUND} green.
 * Hardware with no fixed voltage, such as a panel, uses the low set; a transformer picks a set per
 * winding from its nameplate with {@link #hot(int, float)}. Above 600 V the NEC assigns no
 * colour, so medium-voltage windings borrow the higher-system set.
 */
public final class ConductorColors {
    private static final String[] KEYS = {
            "black", "white", "red", "blue", "orange", "brown",
            "yellow", "purple", "pink", "tan", "gray", "green"
    };
    private static final int[] RGB = {
            0x1a1a1a, 0xf0f0f0, 0xc62828, 0x1e5bc6, 0xf07f1a, 0x6b3f1f,
            0xe8c800, 0x7b3fa0, 0xf08fb0, 0xc9a97a, 0x8a8a8a, 0x2e8b3a
    };
    /** The same for text on dark backgrounds, with black and brown lifted so they do not vanish. */
    private static final int[] TEXT_RGB = {
            0x9a9a9a, 0xf0f0f0, 0xc62828, 0x1e5bc6, 0xf07f1a, 0xb0683a,
            0xe8c800, 0x7b3fa0, 0xf08fb0, 0xc9a97a, 0x8a8a8a, 0x2e8b3a
    };

    public static final int COUNT = KEYS.length;

    public static final int BLACK = 0, WHITE = 1, RED = 2, BLUE = 3, ORANGE = 4, BROWN = 5,
            YELLOW = 6, PURPLE = 7, PINK = 8, TAN = 9, GRAY = 10, GREEN = 11;

    /** The grounded conductor and the equipment ground, the two colours the NEC actually reserves. */
    public static final int NEUTRAL = WHITE, GROUND = GREEN;

    /** Line-to-line volts above which a system takes the brown, orange, yellow set and a gray neutral. */
    public static final float HIGH_SYSTEM_ABOVE_VOLTS = 300;

    private static final int[] HOT = {BLACK, RED, BLUE};
    private static final int[] HIGH = {BROWN, ORANGE, YELLOW};

    private ConductorColors() {}

    public static String key(int conductor) {
        return KEYS[Math.floorMod(conductor, COUNT)];
    }

    public static int rgb(int conductor) {
        return RGB[Math.floorMod(conductor, COUNT)];
    }

    /** Colour used for text on dark backgrounds; black conductors would otherwise vanish. */
    public static int textRgb(int conductor) {
        return TEXT_RGB[Math.floorMod(conductor, COUNT)];
    }

    public static Component name(int conductor) {
        return Component.translatable("powergrid.conductor." + key(conductor))
                .withStyle(Style.EMPTY.withColor(textRgb(conductor)));
    }

    // ---- by role ----

    /** Line {@code line} (0-based) of a 120/208/240 V system: black, red, blue. */
    public static int hot(int line) {
        return HOT[Math.floorMod(line, 3)];
    }

    /** Line {@code line} (0-based) of a 277/480 V system: brown, orange, yellow. */
    public static int high(int line) {
        return HIGH[Math.floorMod(line, 3)];
    }

    public static boolean highSystem(float lineToLineVolts) {
        return lineToLineVolts > HIGH_SYSTEM_ABOVE_VOLTS;
    }

    /** A line of a system at the given line-to-line volts, low set or high set. */
    public static int hot(int line, float lineToLineVolts) {
        return highSystem(lineToLineVolts) ? high(line) : hot(line);
    }

    /** The neutral of a system at the given line-to-line volts: white, or gray on the high set. */
    public static int neutral(float lineToLineVolts) {
        return highSystem(lineToLineVolts) ? GRAY : NEUTRAL;
    }

    public static int hotRgb(int line) {
        return rgb(hot(line));
    }

    public static int hotTextRgb(int line) {
        return textRgb(hot(line));
    }

    public static int neutralRgb() {
        return rgb(NEUTRAL);
    }

    public static int neutralTextRgb() {
        return textRgb(NEUTRAL);
    }

    public static int groundRgb() {
        return rgb(GROUND);
    }

    /** A terminal name, {@code powergrid.<key>}, in its conductor's text colour. */
    public static Component label(String key, int conductor, Object... args) {
        return Component.translatable("powergrid." + key, args).withStyle(Style.EMPTY.withColor(textRgb(conductor)));
    }

    public static Component hotLabel(String key, int line, Object... args) {
        return label(key, hot(line), args);
    }

    public static Component neutralLabel(String key, Object... args) {
        return label(key, NEUTRAL, args);
    }

    public static Component groundLabel(String key, Object... args) {
        return label(key, GROUND, args);
    }
}
