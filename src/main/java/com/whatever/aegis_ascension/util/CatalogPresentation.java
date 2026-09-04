package com.whatever.aegis_ascension.util;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entries are presented: their title, their description, and their icon.
 *
 * <p>Load String completely on the client side.</p>
 *
 * <p>One {@code <catalogue>_clientside.json} per catalogue, seeded on first run from the
 * bundled asset of the same name. An entry the file does not describe falls back to this
 * mod's naming convention, so a catalogue entry always has a usable key and icon even
 * when the file is missing, emptied, or predates that entry.</p>
 */
public final class CatalogPresentation {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, CatalogPresentation> LOADED = new ConcurrentHashMap<>();

    public static final ResourceLocation FALLBACK_ICON = GeneralClientMethods.fromNamespaceAndPath("aegis_ascension","textures/gui/error/fallback_icon.png");
    public static final String FALLBACK_STRING = "DEFAULT STRING MISSING";
    private static final Set<String> REPORTED_MISSING = ConcurrentHashMap.newKeySet();
    private static volatile Predicate<ResourceLocation> TEXTURE_EXISTS = location -> true;

    public Map<String, Entry> entries = new LinkedHashMap<>();

    private CatalogPresentation() {
    }

    public static CatalogPresentation of(String fileName) {
        return LOADED.computeIfAbsent(fileName, CatalogPresentation::load);
    }

    public String name(String id) {
        Entry entry = entries.get(id);
        return entry == null || blank(entry.name) ? FALLBACK_STRING : entry.name;
    }

    public String description(String id) {
        Entry entry = entries.get(id);
        return entry == null || blank(entry.description) ? FALLBACK_STRING : entry.description;
    }

    public ResourceLocation icon(String id) {
        Entry entry = entries.get(id);
        if (entry == null || blank(entry.icon)) {
            return FALLBACK_ICON;
        }
        ResourceLocation parsed = PlatformServices.resources().tryParse(entry.icon);
        if (parsed == null) {
            warnOnce(id, "not a valid texture path: " + entry.icon);
            return FALLBACK_ICON;
        }
        if (!TEXTURE_EXISTS.test(parsed)) {
            warnOnce(id, "no texture is shipped at " + parsed);
            return FALLBACK_ICON;
        }
        return parsed;
    }

    private static void warnOnce(String id, String problem) {
        if (REPORTED_MISSING.add(id)) {
            AegisAscensionMod.getLogger().warn(
                    "Falling back to the default icon for {}: {}", id, problem);
        }
    }

    public static void installTextureCheck(Predicate<ResourceLocation> check) {
        TEXTURE_EXISTS = Objects.requireNonNull(check, "check");
    }

    // Import Addon
    public static int mergeAddon(String fileName, Path file) {
        CatalogPresentation target = of(fileName);
        CatalogPresentation addon;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            addon = GSON.fromJson(reader, CatalogPresentation.class);
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn(
                    "Ignoring unreadable addon presentation {}", file, exception);
            return 0;
        }
        if (addon == null || addon.entries == null) {
            return 0;
        }
        int merged = 0;
        for (Map.Entry<String, Entry> entry : addon.entries.entrySet()) {
            if (target.entries.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                merged++;
            }
        }
        return merged;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static CatalogPresentation load(String fileName) {
        Path file = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve(fileName);
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                try (var stream = CatalogPresentation.class.getResourceAsStream(
                        "/assets/aegis_ascension/" + fileName)) {
                    if (stream != null) {
                        Files.copy(stream, file);
                    }
                }
            }
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    CatalogPresentation loaded = GSON.fromJson(reader, CatalogPresentation.class);
                    if (loaded != null) {
                        if (loaded.entries == null) {
                            loaded.entries = new LinkedHashMap<>();
                        }
                        return loaded;
                    }
                }
            }
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}", file, exception);
        }
        return new CatalogPresentation();
    }

    public static final class Entry {
        public String name;
        public String description;
        public String icon;
        @SerializedName("icon_texture_size")
        public int iconTextureSize;
    }
}
