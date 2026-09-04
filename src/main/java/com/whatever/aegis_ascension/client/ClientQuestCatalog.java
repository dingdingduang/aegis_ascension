package com.whatever.aegis_ascension.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.quest.QuestConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's quest presentation, held for the session.
 *
 * <p>Title, description, story, profession, icon, constraints and the reputation and
 * stake requirements are fixed by the template rather than rolled, so they are sent once
 * with the login catalog snapshot instead of repeated inside every quest on every sync.
 *
 * <p>The gameplay half - profession, constraints, reputation and stake requirements -
 * comes from the server on purpose. A client's catalogue may be stale or edited, and a
 * screen that showed a requirement the server does not enforce would read as the server
 * being broken.</p>
 *
 * <p>Title, description, story and icon do not: they decide nothing the server enforces,
 * so they are read from this client's own {@code quest_clientside.json} and never sent,
 * which keeps roughly half the catalogue off the wire. Quests generated at runtime are
 * the exception - their ids do not exist until the server builds them, so those carry
 * their own keys and are left alone.</p>
 */
public final class ClientQuestCatalog {
    private static final Gson GSON = new Gson();
    private static final QuestConfig.CatalogEntry EMPTY = new QuestConfig.CatalogEntry();

    private static Map<String, QuestConfig.CatalogEntry> entries = Map.of();

    private ClientQuestCatalog() {
    }

    public static void install(String json) {
        if (json == null || json.isBlank()) {
            entries = Map.of();
            return;
        }
        try {
            List<QuestConfig.CatalogEntry> parsed = GSON.fromJson(json,
                    new TypeToken<List<QuestConfig.CatalogEntry>>() { }.getType());
            Map<String, QuestConfig.CatalogEntry> installed = new LinkedHashMap<>();
            if (parsed != null) {
                for (QuestConfig.CatalogEntry entry : parsed) {
                    if (entry == null || entry.id == null || entry.id.isBlank()) continue;
                    installed.putIfAbsent(entry.id, entry);
                }
            }
            QuestPresentation.overlay(installed);
            entries = Map.copyOf(installed);
        } catch (Exception exception) {
            // A quest whose presentation cannot be read still has to be playable, so the
            // screen falls back to blanks rather than the whole page failing to draw.
            entries = Map.of();
            AegisAscensionMod.getLogger().error(
                    "Could not read the server's quest catalog; quest text will be blank "
                            + "until the next login", exception);
        }
    }

    /**
     * Presentation for a rolled quest id. Rolled ids carry a {@code #type} suffix that
     * the catalogue is not keyed by, so it is trimmed here rather than at every call.
     * An unknown id yields blank fields instead of null.
     */
    public static QuestConfig.CatalogEntry get(String questId) {
        if (questId == null || questId.isBlank()) return EMPTY;
        String base = questId;
        int marker = base.indexOf('#');
        if (marker >= 0) base = base.substring(0, marker);
        return entries.getOrDefault(base, EMPTY);
    }

    public static boolean isEmpty() {
        return entries.isEmpty();
    }

    public static void clear() {
        entries = Map.of();
    }
}
