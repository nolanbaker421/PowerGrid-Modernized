package com.nolanbaker.pgmodernized.device.fixture;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One piece of a designed fixture, in the design's 48-pixel cube: the anchor block is pixels 16
 * to 32 on every axis, up is +y, and a wall-mounted design's wall is the +z face. Boxes are
 * plain; a cone tapers from its base toward {@code face}; a globe is a ball; a chain is links
 * running along {@code face}; a socket is a lamp holder whose bulb points along {@code face}; a
 * text part is lettering in the game's font on the box's {@code face} side, scaled to fit it,
 * backlit by a hidden lamp holder of its own: it takes a bulb like a socket, never shows it, and
 * when its material is glow the lettering is full-bright while the lamp is on. A cylinder is a
 * round tube along {@code face}, filling its box: stems, arms, candle tubes, neon.
 */
public final class FixturePart {
    public enum Kind { BOX, CONE, GLOBE, CHAIN, SOCKET, TEXT, CYLINDER }

    /** Matte takes its colour; metal is the same with brighter tops; glass is see-through; glow lights up with the lamp. */
    public enum Material { MATTE, METAL, GLASS, GLOW }

    /**
     * The design's reach in pixels: two blocks each way round the anchor block (pixels 16 to 32),
     * five blocks in all. Designs from before 0.101 used 0 to 48 and still fit.
     */
    public static final int SPAN_MIN = -16, SPAN_MAX = 64, ANCHOR_MIN = 16, ANCHOR_MAX = 32, TEXT_LENGTH = 16;

    public Kind kind = Kind.BOX;
    public int x = 20, y = 20, z = 20, w = 8, h = 8, d = 8;
    /** For cones, chains and sockets: the way the part points. */
    public Direction face = Direction.DOWN;
    public int color = 0xD8D8D0;
    public Material material = Material.MATTE;
    /** Cones: the far end's size as a percentage of the base, 0 to 100. */
    public int taper = 40;
    /** Text parts: the lettering, up to {@link #TEXT_LENGTH} characters. */
    public String text = "";
    /** The part's turn about its own centre, in degrees: about X first, then Y, then Z. */
    public int rx, ry, rz;

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
        p.text = text;
        p.rx = rx;
        p.ry = ry;
        p.rz = rz;
        return p;
    }

    /** Keeps every value inside the cube and the sizes sane. */
    public void clamp() {
        w = Mth.clamp(w, 1, SPAN_MAX - SPAN_MIN);
        h = Mth.clamp(h, 1, SPAN_MAX - SPAN_MIN);
        d = Mth.clamp(d, 1, SPAN_MAX - SPAN_MIN);
        x = Mth.clamp(x, SPAN_MIN, SPAN_MAX - 1);
        y = Mth.clamp(y, SPAN_MIN, SPAN_MAX - 1);
        z = Mth.clamp(z, SPAN_MIN, SPAN_MAX - 1);
        taper = Mth.clamp(taper, 0, 100);
        rx = Math.floorMod(rx, 360);
        ry = Math.floorMod(ry, 360);
        rz = Math.floorMod(rz, 360);
        color &= 0xFFFFFF;
        if(text == null)
            text = "";
        if(text.length() > TEXT_LENGTH)
            text = text.substring(0, TEXT_LENGTH);
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putIntArray("Box", new int[] {x, y, z, w, h, d});
        tag.putInt("Face", face.get3DDataValue());
        tag.putInt("Color", color);
        tag.putString("Material", material.name());
        tag.putInt("Taper", taper);
        if(!text.isEmpty())
            tag.putString("Text", text);
        if(rotated())
            tag.putIntArray("Rot", new int[] {rx, ry, rz});
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
        p.text = tag.getString("Text");
        var rot = tag.getIntArray("Rot");
        if(rot.length == 3) {
            p.rx = rot[0];
            p.ry = rot[1];
            p.rz = rot[2];
        }
        p.clamp();
        return p;
    }

    public boolean rotated() {
        return rx != 0 || ry != 0 || rz != 0;
    }

    /** The turn as a rotation, X first, then Y, then Z, for a pose or a point. */
    public Quaternionf rotation() {
        return new Quaternionf().rotateZ((float) Math.toRadians(rz)).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx));
    }

    /** The part's box as placed, in design pixels: the box itself, or the box round its turned corners. */
    public AABB bounds() {
        if(!rotated())
            return new AABB(x, y, z, x + w, y + h, z + d);
        var q = rotation();
        float cx = x + w / 2f, cy = y + h / 2f, cz = z + d / 2f;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for(int c = 0; c < 8; ++c) {
            var v = corner(c, q);
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Corner {@code c} (bits: 1 = +w, 2 = +h, 4 = +d) of the box after the turn {@code q} about its centre, in design pixels. */
    public Vector3f corner(int c, Quaternionf q) {
        float cx = x + w / 2f, cy = y + h / 2f, cz = z + d / 2f;
        var v = new Vector3f(x + ((c & 1) == 0 ? 0 : w) - cx, y + ((c & 2) == 0 ? 0 : h) - cy, z + ((c & 4) == 0 ? 0 : d) - cz);
        return q.transform(v).add(cx, cy, cz);
    }
}
