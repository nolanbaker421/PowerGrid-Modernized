package com.nolanbaker.pgmodernized.device.fixture;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A designed fixture: its parts in the 48-pixel cube, how it mounts, and a name. Lives on the
 * designer item, on the fixture item it builds, and in the placed fixture's block entity, as the
 * same tag throughout.
 */
public final class FixtureDesign {
    /** Which neighbour holds the fixture up: the block above, the block behind, or the block below. */
    public enum Mount implements StringRepresentable {
        CEILING, WALL, FLOOR;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final int MAX_PARTS = 24, MAX_SOCKETS = 6, NAME_LENGTH = 24;

    public final List<FixturePart> parts = new ArrayList<>();
    public Mount mount = Mount.CEILING;
    public String name = "";

    /** A starting point: a canopy, a chain, a shade and a socket, hung from a ceiling. */
    public static FixtureDesign pendant() {
        var d = new FixtureDesign();
        d.name = "Pendant";
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 20, 30, 20, 8, 2, 8, Direction.DOWN, 0xB8BCC2, FixturePart.Material.METAL));
        d.parts.add(new FixturePart(FixturePart.Kind.CHAIN, 23, 18, 23, 2, 12, 2, Direction.DOWN, 0x8E9298, FixturePart.Material.METAL));
        var shade = new FixturePart(FixturePart.Kind.CONE, 17, 10, 17, 14, 8, 14, Direction.DOWN, 0x2E7D6B, FixturePart.Material.MATTE);
        shade.taper = 45;
        d.parts.add(shade);
        d.parts.add(new FixturePart(FixturePart.Kind.SOCKET, 22, 14, 22, 4, 4, 4, Direction.DOWN, 0x3A3C40, FixturePart.Material.MATTE));
        return d;
    }

    /** A wall sconce: a back plate on the wall, a short arm, and a frosted glass shade open at the top. */
    public static FixtureDesign sconce() {
        var d = new FixtureDesign();
        d.name = "Sconce";
        d.mount = Mount.WALL;
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 21, 18, 30, 6, 12, 2, Direction.NORTH, 0xB8BCC2, FixturePart.Material.METAL));
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 23, 23, 26, 2, 2, 4, Direction.NORTH, 0xB8BCC2, FixturePart.Material.METAL));
        d.parts.add(new FixturePart(FixturePart.Kind.SOCKET, 22, 23, 22, 4, 3, 4, Direction.UP, 0x3A3C40, FixturePart.Material.MATTE));
        var shade = new FixturePart(FixturePart.Kind.CONE, 19, 25, 19, 10, 9, 10, Direction.UP, 0xF2F2EE, FixturePart.Material.GLASS);
        shade.taper = 65;
        d.parts.add(shade);
        return d;
    }

    /** A table lamp: a base, a stem, a socket and a wide fabric shade, standing on the floor. */
    public static FixtureDesign tableLamp() {
        var d = new FixtureDesign();
        d.name = "Table Lamp";
        d.mount = Mount.FLOOR;
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 19, 19, 19, 10, 2, 10, Direction.UP, 0x8B5A2B, FixturePart.Material.MATTE));
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 23, 21, 23, 2, 12, 2, Direction.UP, 0xC88A3C, FixturePart.Material.METAL));
        d.parts.add(new FixturePart(FixturePart.Kind.SOCKET, 22, 33, 22, 4, 3, 4, Direction.UP, 0x3A3C40, FixturePart.Material.MATTE));
        var shade = new FixturePart(FixturePart.Kind.CONE, 17, 34, 17, 14, 10, 14, Direction.UP, 0xE8D8B0, FixturePart.Material.MATTE);
        shade.taper = 70;
        d.parts.add(shade);
        return d;
    }

    /** A lit sign on a wall: a dark cabinet standing off the wall with glowing, backlit lettering on its face. */
    public static FixtureDesign sign() {
        var d = new FixtureDesign();
        d.name = "Sign";
        d.mount = Mount.WALL;
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 4, 18, 28, 40, 12, 3, Direction.NORTH, 0x111111, FixturePart.Material.METAL));
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 5, 19, 27, 38, 10, 1, Direction.NORTH, 0x3A3C40, FixturePart.Material.MATTE));
        var text = new FixturePart(FixturePart.Kind.TEXT, 6, 20, 27, 36, 8, 1, Direction.NORTH, 0xFF3040, FixturePart.Material.GLOW);
        text.text = "OPEN";
        d.parts.add(text);
        return d;
    }

    public FixtureDesign copy() {
        var d = new FixtureDesign();
        d.mount = mount;
        d.name = name;
        for(var p : parts)
            d.parts.add(p.copy());
        return d;
    }

    /** Lamp holders: every socket, and every text part, which is backlit by a hidden one. */
    public int socketCount() {
        int n = 0;
        for(var p : parts)
            if(p.kind == FixturePart.Kind.SOCKET || p.kind == FixturePart.Kind.TEXT)
                ++n;
        return Math.min(n, MAX_SOCKETS);
    }

    /** Whether any lamp is out in the open: a socket lights the room, backlit text lights only itself. */
    public boolean hasOpenLamp() {
        for(var p : parts)
            if(p.kind == FixturePart.Kind.SOCKET)
                return true;
        return false;
    }

    /** The parts' boxes that fall inside the anchor block, as the block's shape in the north frame. */
    public VoxelShape anchorShape() {
        var shape = Shapes.empty();
        for(var p : parts) {
            var box = new AABB(p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d)
                    .intersect(new AABB(FixturePart.ANCHOR_MIN, FixturePart.ANCHOR_MIN, FixturePart.ANCHOR_MIN, FixturePart.ANCHOR_MAX, FixturePart.ANCHOR_MAX, FixturePart.ANCHOR_MAX));
            if(box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0)
                continue;
            shape = Shapes.or(shape, Shapes.box((box.minX - 16) / 16, (box.minY - 16) / 16, (box.minZ - 16) / 16,
                    (box.maxX - 16) / 16, (box.maxY - 16) / 16, (box.maxZ - 16) / 16));
        }
        return shape;
    }

    // ---- nbt ----

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Mount", mount.name());
        tag.putString("Name", name);
        var list = new ListTag();
        for(var p : parts)
            list.add(p.write());
        tag.put("Parts", list);
        return tag;
    }

    public static FixtureDesign read(CompoundTag tag) {
        var d = new FixtureDesign();
        try {
            d.mount = Mount.valueOf(tag.getString("Mount"));
        } catch(IllegalArgumentException ignored) {}
        d.name = tag.getString("Name");
        if(d.name.length() > NAME_LENGTH)
            d.name = d.name.substring(0, NAME_LENGTH);
        for(var t : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            if(d.parts.size() >= MAX_PARTS)
                break;
            d.parts.add(FixturePart.read((CompoundTag) t));
        }
        return d;
    }

    /** The design on a designer or fixture item, or null when it carries none. */
    public static FixtureDesign of(ItemStack stack) {
        var tag = stack.get(ModDataComponents.FIXTURE_DESIGN.get());
        return tag == null ? null : read(tag);
    }

    public void putOn(ItemStack stack) {
        stack.set(ModDataComponents.FIXTURE_DESIGN.get(), write());
    }
}
