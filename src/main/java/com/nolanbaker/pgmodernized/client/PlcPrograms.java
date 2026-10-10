package com.nolanbaker.pgmodernized.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * PLC programs kept on this computer: compressed NBT files under
 * {@code <game directory>/powergrid_modernized/plc/<name>.nbt}, and the same tag as SNBT text for
 * the clipboard, so a program travels between worlds, servers and players.
 */
public final class PlcPrograms {
    public static final int MAX_NAME = 32;
    private static final String EXT = ".nbt";

    private PlcPrograms() {}

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("powergrid_modernized").resolve("plc");
    }

    /** A file-safe name: letters, digits, space, dash, underscore and dot, trimmed, at most {@link #MAX_NAME} long. */
    public static String clean(String name) {
        var sb = new StringBuilder();
        for(char c : name.strip().toCharArray()) {
            if(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '.')
                sb.append(c);
            if(sb.length() >= MAX_NAME)
                break;
        }
        var out = sb.toString().strip();
        while(out.startsWith("."))
            out = out.substring(1);
        return out;
    }

    public static List<String> list() {
        var names = new ArrayList<String>();
        var dir = folder();
        if(!Files.isDirectory(dir))
            return names;
        try(Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(EXT))
                    .forEach(p -> {
                        var file = p.getFileName().toString();
                        names.add(file.substring(0, file.length() - EXT.length()));
                    });
        } catch(IOException ignored) {}
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static boolean save(String name, CompoundTag tag) {
        try {
            Files.createDirectories(folder());
            NbtIo.writeCompressed(tag, folder().resolve(name + EXT));
            return true;
        } catch(IOException e) {
            return false;
        }
    }

    @Nullable
    public static CompoundTag load(String name) {
        try {
            return NbtIo.readCompressed(folder().resolve(name + EXT), NbtAccounter.unlimitedHeap());
        } catch(IOException e) {
            return null;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(folder().resolve(name + EXT));
        } catch(IOException e) {
            return false;
        }
    }

    public static String toText(CompoundTag tag) {
        return tag.toString();
    }

    @Nullable
    public static CompoundTag fromText(String text) {
        try {
            return TagParser.parseTag(text.strip());
        } catch(Exception e) {
            return null;
        }
    }
}
