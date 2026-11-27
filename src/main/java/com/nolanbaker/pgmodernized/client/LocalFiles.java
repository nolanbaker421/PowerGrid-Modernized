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
 * Things kept on this computer as named, compressed NBT files under
 * {@code <game directory>/powergrid_modernized/<folder>/<name>.nbt}: PLC programs and fixture
 * designs. The same tag goes to the clipboard as SNBT text, so one travels between worlds,
 * servers and players.
 */
public final class LocalFiles {
    public static final LocalFiles PLC = new LocalFiles("plc");
    public static final LocalFiles FIXTURES = new LocalFiles("fixtures");
    public static final int MAX_NAME = 32;
    private static final String EXT = ".nbt";

    private final String folder;

    private LocalFiles(String folder) {
        this.folder = folder;
    }

    public Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("powergrid_modernized").resolve(folder);
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

    public List<String> list() {
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

    public boolean save(String name, CompoundTag tag) {
        try {
            Files.createDirectories(folder());
            NbtIo.writeCompressed(tag, folder().resolve(name + EXT));
            return true;
        } catch(IOException e) {
            return false;
        }
    }

    @Nullable
    public CompoundTag load(String name) {
        try {
            return NbtIo.readCompressed(folder().resolve(name + EXT), NbtAccounter.unlimitedHeap());
        } catch(IOException e) {
            return null;
        }
    }

    public boolean delete(String name) {
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
