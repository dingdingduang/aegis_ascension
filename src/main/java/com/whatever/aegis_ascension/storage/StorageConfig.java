package com.whatever.aegis_ascension.storage;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings for the virtual storage, at
 * {@code config/aegis_ascension/storage_serverside.json}.
 *
     * <p>Seeded from the bundled {@code assets/aegis_ascension/storage_serverside.json} on first
     * run and read back from disk thereafter — the same copy-then-read flow as
     * {@link com.whatever.aegis_ascension.aegis.Aegis}'s catalog. The shipped JSON is the
     * source of truth for the defaults; the field initialisers below only fill in keys a
     * hand-edited file omits, and are never serialised back out to create the file.</p>
 *
 */
public final class StorageConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("storage_serverside.json");

    private static StorageConfig instance;

    /** Distinct item types the storage can hold; stacking more of an existing type is always allowed. */
    public int maxItemTypes = 60;
    /**
     * Fraction of an item's shop value returned when selling it back, applied to the
     * per-unit price derived from the shop catalogue. 0.5 = half price.
     */
    public double sellExperienceRatio = 0.50D;

    private StorageConfig() {
    }

    public static StorageConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Drops the cached instance so the next {@link #get()} re-reads the file from disk. */
    public static void reload() {
        instance = null;
    }

    private static StorageConfig load() {
        StorageConfig config = new StorageConfig();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = StorageConfig.class.getResourceAsStream(
                        "/assets/aegis_ascension/storage_serverside.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/storage_serverside.json");
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                StorageConfig loaded = GSON.fromJson(reader, StorageConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error(
                    "Failed to read {}, using built-in storage defaults", FILE, exception);
            config = new StorageConfig();
        }
        config.sanitize();
        return config;
    }

    private void sanitize() {
        maxItemTypes = Math.max(1, Math.min(4096, maxItemTypes));
        sellExperienceRatio = Math.max(0.0D, Math.min(1.0D, sellExperienceRatio));
    }
}
