package com.nolanbaker.pgmodernized.device.fixture;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One piece of a designed fixture, in the design's 48-pixel cube: the anchor block is pixels 16
 * to 32 on every axis, up is +y, and a wall-mounted design's wall is the +z face. Boxes are
 * plain; a cone tapers from its base toward {@code face}; a globe is a ball; a chain is links
 * running along {@code face}; a socket is a lamp holder whose bulb points along {@code face}.
 */
public final class FixturePart {
    public enum Kind { BOX, CONE, GLOBE, CHAIN, SOCKET }

    /** Matte takes its colour; metal is the same with brighter tops; glass is see-through; glow lights up with the lamp. */
    public enum Material { MATTE, METAL, GLASS, GLOW }

    public static final int CUBE = 48, ANCHOR_MIN = 16, ANCHOR_MAX = 32;

    public Kind kind = Kind.BOX;
    public int x = 20, y = 20, z = 20, w = 8, h = 8, d = 8;
    /** For cones, chains and sockets: the way the part points. */
    public Direction face = Direction.DOWN;
    public int color = 0xD8D8D0;
    public Material material = Material.MATTE;
    /** Cones: the far end's size as a percentage of the base, 0 to 100. */
    public int taper = 40;

    public FixturePart() {}

    public FixturePart(Kind kind, int x, int y, int z, int w, int h, int d, Direction face, int color, Material material) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        this.h = h;
        this.d = d;
        this.face = face;
        this.color = color;
        this.material = material;
    }

    public FixturePart copy() {
        var p = new FixturePart(kind, x, y, z, w, h, d, face, color, material);
        p.taper = taper;
        return p;
    }

    /** Keeps every value inside the cube and the sizes sane. */
    public void clamp() {
        w = Mth.clamp(w, 1, CUBE);
        h = Mth.clamp(h, 1, CUBE);
        d = Mth.clamp(d, 1, CUBE);
        x = Mth.clamp(x, 0, CUBE - 1);
        y = Mth.clamp(y, 0, CUBE - 1);
        z = Mth.clamp(z, 0, CUBE - 1);
        taper = Mth.clamp(taper, 0, 100);
        color &= 0xFFFFFF;
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putIntArray("Box", new int[] {x, y, z, w, h, d});
        tag.putInt("Face", face.get3DDataValue());
        tag.putInt("Color", color);
        tag.putString("Material", material.name());
        tag.putInt("Taper", taper);
        return tag;
    }

    public static FixturePart read(CompoundTag tag) {
        var p = new FixturePart();
        try {
            p.kind = Kind.valueOf(tag.getString("Kind"));
        } catch(IllegalArgumentException ignored) {}
        var box = tag.getIntArray("Box");
        if(box.length == 6) {
            p.x = box[0];
            p.y = box[1];
            p.z = box[2];
            p.w = box[3];
            p.h = box[4];
            p.d = box[5];
        }
        p.face = Direction.from3DDataValue(tag.getInt("Face"));
        p.color = tag.getInt("Color");
        try {
            p.material = Material.valueOf(tag.getString("Material"));
        } catch(IllegalArgumentException ignored) {}
        p.taper = tag.contains("Taper") ? tag.getInt("Taper") : 40;
        p.clamp();
        return p;
    }
}
