package com.whatever.aegis_ascension.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Local-only, non-gameplay preferences that belong to a player's presentation rather
 * than the server protocol. Stored at
 * {@code config/aegis_ascension/client/misc_local_setting.json}, separately from the
 * main Client Settings drawer file.
 */
public final class MiscLocalSettings {
    private static final int MAX_REMEMBERED_QUESTS = 1_024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("client")
            .resolve("misc_local_setting.json");

    private static MiscLocalSettings instance;

    private List<String> hiddenQuestTrackerIds = new ArrayList<>();

    private MiscLocalSettings() {
    }

    public static MiscLocalSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean isQuestTrackerVisible(String questId) {
        return questId == null || questId.isBlank()
                || !hiddenQuestTrackerIds.contains(questId);
    }

    /** Returns true only when the stored value actually changed. */
    public boolean setQuestTrackerVisible(String questId, boolean visible) {
        if (questId == null || questId.isBlank()) return false;
        boolean changed;
        if (visible) {
            changed = hiddenQuestTrackerIds.remove(questId);
        } else if (hiddenQuestTrackerIds.contains(questId)
                || hiddenQuestTrackerIds.size() >= MAX_REMEMBERED_QUESTS) {
            changed = false;
        } else {
            changed = hiddenQuestTrackerIds.add(questId);
        }
        if (changed) save();
        return changed;
    }

    /**
     * Forgets terminal or refreshed quest instances. An active quest with the same id is
     * retained across reconnects, while a future reroll starts visible by default.
     */
    public void retainActiveQuestIds(Collection<String> activeQuestIds) {
        Set<String> retained = activeQuestIds == null
                ? Set.of() : new LinkedHashSet<>(activeQuestIds);
        if (hiddenQuestTrackerIds.removeIf(id -> !retained.contains(id))) {
            save();
        }
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

    private static MiscLocalSettings load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                MiscLocalSettings created = new MiscLocalSettings();
                created.save();
                return created;
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                MiscLocalSettings loaded = GSON.fromJson(reader, MiscLocalSettings.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException exception) {
            AegisAscensionMod.getLogger().warn(
                    "Failed to read {}, falling back to local defaults",
                    FILE,
                    exception
            );
        }
        return new MiscLocalSettings();
    }

    private void sanitize() {
        if (hiddenQuestTrackerIds == null) {
            hiddenQuestTrackerIds = new ArrayList<>();
            return;
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String id : hiddenQuestTrackerIds) {
            if (id == null || id.isBlank()) continue;
            sanitized.add(id);
            if (sanitized.size() >= MAX_REMEMBERED_QUESTS) break;
        }
        hiddenQuestTrackerIds = new ArrayList<>(sanitized);
    }
}
