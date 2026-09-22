package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** US conductor colour sequence, in conductor order. The name and colour show up on terminals and in the junction box GUI. */
public final class ConductorColors {
    private static final String[] KEYS = {
            "black", "red", "blue", "white", "green", "orange",
            "brown", "yellow", "gray", "purple", "pink", "tan"
    };
    private static final int[] RGB = {
            0x1a1a1a, 0xc62828, 0x1e5bc6, 0xf0f0f0, 0x2e8b3a, 0xf07f1a,
            0x6b3f1f, 0xe8c800, 0x8a8a8a, 0x7b3fa0, 0xf08fb0, 0xc9a97a
    };

    public static final int COUNT = KEYS.length;

    private ConductorColors() {}

    public static String key(int conductor) {
        return KEYS[Math.floorMod(conductor, COUNT)];
    }

    public static int rgb(int conductor) {
        return RGB[Math.floorMod(conductor, COUNT)];
    }

    /** Colour used for text on dark backgrounds; black conductors would otherwise vanish. */
    public static int textRgb(int conductor) {
        int rgb = rgb(conductor);
        return rgb == 0x1a1a1a ? 0x9a9a9a : rgb;
    }

    public static Component name(int conductor) {
        return Component.translatable("powergrid.conductor." + key(conductor))
                .withStyle(Style.EMPTY.withColor(textRgb(conductor)));
    }
}
