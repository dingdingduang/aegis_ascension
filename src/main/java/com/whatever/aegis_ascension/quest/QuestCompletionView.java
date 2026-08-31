package com.whatever.aegis_ascension.quest;

/** Network-safe lifetime summary for one repeatable quest definition. */
public record QuestCompletionView(String questId, String title,
                                  QuestType type, QuestObjective objective,
                                  String icon, String profession,
                                  int completions, long experienceEarned) {
    public QuestCompletionView {
        questId = questId == null ? "" : questId;
        title = title == null ? "" : title;
        type = type == null ? QuestType.COMMON : type;
        objective = objective == null ? QuestObjective.KILL : objective;
        icon = icon == null ? "" : icon;
        profession = profession == null ? "" : profession;
        completions = Math.max(0, completions);
        experienceEarned = Math.max(0L, experienceEarned);
    }
}
