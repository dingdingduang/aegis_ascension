package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only presentation settings for the Devoured Items tab: where the rarity tiers fall
 * and how the grid is ordered.
 *
 * <p>Entirely local. A devoured item's rarity is a <em>reading</em> of how many attributes it
 * carried, not a property the server stores or acts on — nothing here changes what an item
 * grants, so it never needs to agree with the server or with another player's file.</p>
 *
 * <p>Persisted at {@code config/aegis_ascension/devour_clientside.json}, seeded on first run
 * from the bundled {@code assets/aegis_ascension/devour_clientside.json} exactly like
 * {@link com.whatever.aegis_ascension.aegis.Aegis}'s catalog and {@link ClientSettings}. The
 * shipped asset — not the field defaults below — is what a fresh install actually gets, so
 * the two are kept in step.</p>
 */
public final class DevourClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("devour_clientside.json");

    private static DevourClientSettings instance;

    /**
     * Attribute counts at which each tier is reached. A devoured item shows the highest tier
     * whose threshold it meets; anything under {@link #srMinAttributes} reads as R, so an item
     * below {@code rMinAttributes} is still R rather than untiered.
     *
     * <p>Counted against every inherited attribute the card reports, banned ones included, so
     * the gem always agrees with the {@code xN} badge printed beneath it.</p>
     */
    public int rMinAttributes = 10;
    public int srMinAttributes = 20;
    public int ssrMinAttributes = 30;

    /**
     * Devoured-grid order, by {@link com.whatever.aegis_ascension.client.screen.collectiontabs.DevouredItems.SortMode}
     * name. Kept here beside the thresholds rather than in {@link ClientSettings} because the
     * rarity sort is only meaningful in terms of the thresholds above.
     */
    public String devourSortMode = "NAME_ASC";

    private DevourClientSettings() {
    }

    /** Lazily loads once per client session; call {@link #save()} after mutating fields. */
    public static DevourClientSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static DevourClientSettings load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = DevourClientSettings.class.getResourceAsStream(
                        "/assets/aegis_ascension/devour_clientside.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/devour_clientside.json");
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                DevourClientSettings loaded = GSON.fromJson(reader, DevourClientSettings.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}, falling back to defaults", FILE, exception);
        }
        return new DevourClientSettings();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (var writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            AegisAscensionMod.getLogger().warn("Failed to write {}", FILE, exception);
        }
    }

    /**
     * Forces the thresholds into ascending order. A hand-edited file with SR above SSR would
     * otherwise make SSR unreachable — the ladder is walked from the top down — which reads as
     * the tier simply not working rather than as a bad value.
     */
    private void sanitize() {
        rMinAttributes = Math.max(1, rMinAttributes);
        srMinAttributes = Math.max(rMinAttributes, srMinAttributes);
        ssrMinAttributes = Math.max(srMinAttributes, ssrMinAttributes);
    }
}
