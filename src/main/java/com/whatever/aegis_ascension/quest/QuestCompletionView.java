package com.whatever.aegis_ascension.quest;

/**
 * One quest's lifetime record for this player.
 *
 * <p>Only what varies per player is here. The quest's title, icon, profession, objective
 * and type are fixed by its template and arrive once in the login catalog snapshot, so
 * they are read from ClientQuestCatalog rather than repeated for every completion on
 * every synchronisation.</p>
 */
public record QuestCompletionView(String questId, int completions, long experienceEarned) {
    public QuestCompletionView {
        questId = questId == null ? "" : questId;
        completions = Math.max(0, completions);
        experienceEarned = Math.max(0L, experienceEarned);
    }
}
