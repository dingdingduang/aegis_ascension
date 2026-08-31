package com.whatever.aegis_ascension.quest;

/** Network-safe projection rendered by the Quest Center screen. */
public record QuestView(String id, QuestType type, QuestObjective objective,
                        String title, String description, String targetId,
                        int progress, int target, boolean accepted, boolean completed,
                        boolean cancelled, boolean expired, long expiresAt,
                        int experience, long goldReward, String rewardSummary, String story,
                        String profession, String prerequisiteTitle,
                        boolean prerequisiteMet, int securityDepositPaid,
                        String icon, boolean repeatable, int cycle,
                        long rewardReadyAt) {
    public QuestView withProgress(int nextProgress) {
        return new QuestView(id, type, objective, title, description, targetId,
                Math.max(0, Math.min(target, nextProgress)), target, accepted, completed,
                cancelled, expired, expiresAt, experience, goldReward, rewardSummary, story,
                profession, prerequisiteTitle, prerequisiteMet, securityDepositPaid,
                icon, repeatable, cycle, rewardReadyAt);
    }
}
