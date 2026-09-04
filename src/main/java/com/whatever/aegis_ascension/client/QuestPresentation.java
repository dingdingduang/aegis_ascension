package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.quest.QuestConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How quests read, kept entirely on the client.
 *
 * <p>A quest's title, objective line, flavour text and icon change nothing the server
 * enforces, so they are not sent with the login catalogue - they are laid over it from
 * this file once it arrives. That keeps about half the quest payload off the wire and
 * leaves a player's own wording intact on someone else's server.</p>
 *
 * <p>Persisted at {@code config/aegis_ascension/quest_clientside.json}, seeded on first
 * run from the bundled asset of the same name.</p>
 */
public final class QuestPresentation {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("quest_clientside.json");

    private static QuestPresentation instance;

    /** Quest template id to how it reads. */
    public Map<String, Entry> entries = new LinkedHashMap<>();

    private QuestPresentation() {
    }

    public static QuestPresentation get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Fills in the presentation the server did not send.
     *
     * <p>Only blanks are filled, so a runtime-generated quest - which carries its own
     * keys because its id cannot be known ahead of time - keeps them.</p>
     */
    public static void overlay(Map<String, QuestConfig.CatalogEntry> installed) {
        Map<String, Entry> local = get().entries;
        installed.forEach((id, entry) -> {
            Entry presentation = local.get(id);
            if (presentation != null) {
                if (blank(entry.title)) {
                    entry.title = presentation.title;
                }
                if (blank(entry.description)) {
                    entry.description = presentation.description;
                }
                if (blank(entry.story)) {
                    entry.story = presentation.story;
                }
                if (blank(entry.icon)) {
                    entry.icon = presentation.icon;
                }
            }
            // The wire omits these entirely rather than sending blanks, so anything this
            // file did not describe arrives null. Screens read them as plain strings.
            entry.title = orEmpty(entry.title);
            entry.description = orEmpty(entry.description);
            entry.story = orEmpty(entry.story);
            entry.icon = orEmpty(entry.icon);
        });
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static QuestPresentation load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = QuestPresentation.class.getResourceAsStream(
                        "/assets/aegis_ascension/quest_clientside.json")) {
                    if (stream != null) {
                        Files.copy(stream, FILE);
                    }
                }
            }
            if (Files.exists(FILE)) {
                try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    QuestPresentation loaded = GSON.fromJson(reader, QuestPresentation.class);
                    if (loaded != null) {
                        if (loaded.entries == null) {
                            loaded.entries = new LinkedHashMap<>();
                        }
                        return loaded;
                    }
                }
            }
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}", FILE, exception);
        }
        return new QuestPresentation();
    }

    /** One quest's presentation. Any field left out keeps whatever the server sent. */
    public static final class Entry {
        public String title;
        public String description;
        public String story;
        public String icon;
    }
}
