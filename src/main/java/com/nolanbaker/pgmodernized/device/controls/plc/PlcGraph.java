package com.nolanbaker.pgmodernized.device.controls.plc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A PLC program as drawn: blocks on a canvas and wires from output pins to input pins. Each input
 * pin takes at most one wire; an output may feed any number. The block entity holds the program
 * and the editor works on a copy it sends back whole.
 */
public final class PlcGraph {
    public static final int MAX_NODES = 128, MAX_TEXT = 16_000;

    public static final class Node {
        public final int id;
        public final NodeType type;
        public int x, y;
        public String text;
        public double[] nums;
        /** What the block is called on the canvas, in place of its type name; empty for none. */
        public String name = "";

        public Node(int id, NodeType type, int x, int y) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.text = type.defaultText();
            this.nums = type.defaults();
        }

        public int inputs() {
            return type.inputNames(this).length;
        }

        public int outputs() {
            return type.outputNames(this).length;
        }

        public double num(int index) {
            return index < nums.length ? nums[index] : 0;
        }

        Node copy() {
            var n = new Node(id, type, x, y);
            n.text = text;
            n.name = name;
            n.nums = nums.clone();
            return n;
        }
    }

    public record Link(int from, int fromPin, int to, int toPin) {}

    public final List<Node> nodes = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    private int nextId = 1;

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    @Nullable
    public Node node(int id) {
        for(var n : nodes)
            if(n.id == id)
                return n;
        return null;
    }

    @Nullable
    public Node add(NodeType type, int x, int y) {
        if(nodes.size() >= MAX_NODES)
            return null;
        var n = new Node(nextId++, type, x, y);
        nodes.add(n);
        return n;
    }

    public void remove(int id) {
        nodes.removeIf(n -> n.id == id);
        links.removeIf(l -> l.from == id || l.to == id);
    }

    /** Wires an output to an input, replacing whatever fed that input. */
    public boolean link(int from, int fromPin, int to, int toPin) {
        var a = node(from);
        var b = node(to);
        if(a == null || b == null || from == to || fromPin < 0 || fromPin >= a.outputs() || toPin < 0 || toPin >= b.inputs())
            return false;
        unlinkInput(to, toPin);
        links.add(new Link(from, fromPin, to, toPin));
        return true;
    }

    public void unlinkInput(int to, int toPin) {
        links.removeIf(l -> l.to == to && l.toPin == toPin);
    }

    @Nullable
    public Link linkInto(int to, int toPin) {
        for(var l : links)
            if(l.to == to && l.toPin == toPin)
                return l;
        return null;
    }

    /** Drops wires to pins that no longer exist, after a block's pin count changed. */
    public void prune() {
        links.removeIf(l -> {
            var a = node(l.from);
            var b = node(l.to);
            return a == null || b == null || l.fromPin >= a.outputs() || l.toPin >= b.inputs();
        });
    }

    public PlcGraph copy() {
        var g = new PlcGraph();
        for(var n : nodes)
            g.nodes.add(n.copy());
        g.links.addAll(links);
        g.nextId = nextId;
        return g;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var nodeList = new ListTag();
        for(var n : nodes) {
            var t = new CompoundTag();
            t.putInt("Id", n.id);
            t.putString("Type", n.type.key());
            t.putInt("X", n.x);
            t.putInt("Y", n.y);
            if(!n.name.isEmpty())
                t.putString("Name", n.name);
            if(!n.text.isEmpty())
                t.putString("Text", n.text);
            if(n.nums.length > 0) {
                var nums = new ListTag();
                for(double d : n.nums)
                    nums.add(net.minecraft.nbt.DoubleTag.valueOf(d));
                t.put("Nums", nums);
            }
            nodeList.add(t);
        }
        tag.put("Nodes", nodeList);
        var linkList = new ListTag();
        for(var l : links)
            linkList.add(new net.minecraft.nbt.IntArrayTag(new int[] {l.from, l.fromPin, l.to, l.toPin}));
        tag.put("Links", linkList);
        tag.putInt("Next", nextId);
        return tag;
    }

    public static PlcGraph load(CompoundTag tag) {
        var g = new PlcGraph();
        var nodeList = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for(int i = 0; i < nodeList.size() && g.nodes.size() < MAX_NODES; ++i) {
            var t = nodeList.getCompound(i);
            var type = NodeType.byKey(t.getString("Type"));
            if(type == null)
                continue;
            var n = new Node(t.getInt("Id"), type, t.getInt("X"), t.getInt("Y"));
            if(t.contains("Name")) {
                var name = t.getString("Name");
                n.name = name.length() > 32 ? name.substring(0, 32) : name;
            }
            if(t.contains("Text")) {
                var text = t.getString("Text");
                n.text = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
            }
            var defaults = type.defaults();
            var nums = t.getList("Nums", Tag.TAG_DOUBLE);
            for(int k = 0; k < Math.min(nums.size(), defaults.length); ++k)
                defaults[k] = nums.getDouble(k);
            n.nums = defaults;
            if(g.node(n.id) == null && n.id > 0)
                g.nodes.add(n);
        }
        var linkList = tag.getList("Links", Tag.TAG_INT_ARRAY);
        for(int i = 0; i < linkList.size(); ++i) {
            var a = linkList.getIntArray(i);
            if(a.length == 4)
                g.link(a[0], a[1], a[2], a[3]);
        }
        int next = tag.getInt("Next");
        for(var n : g.nodes)
            next = Math.max(next, n.id + 1);
        g.nextId = Math.max(1, next);
        return g;
    }

    /** Clamps every setting into its parameter's range, after an edit or a load. */
    public void sanitise() {
        for(var n : nodes) {
            for(var p : n.type.params) {
                if(p.isText())
                    continue;
                if(p.index() < n.nums.length)
                    n.nums[p.index()] = Math.max(p.min(), Math.min(p.max(), n.nums[p.index()]));
            }
            if(n.text.length() > MAX_TEXT)
                n.text = n.text.substring(0, MAX_TEXT);
        }
        prune();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlcGraph g && save().equals(g.save());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {nodes.size(), links.size()});
    }
}
