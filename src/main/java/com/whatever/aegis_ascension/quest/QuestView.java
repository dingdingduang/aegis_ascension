package com.whatever.aegis_ascension.quest;

import java.util.ArrayList;
import java.util.List;

/** Network-safe projection rendered by the Quest Center screen. */
/**
 * Per-roll quest state. Everything fixed by the template - title, description, story,
 * profession, icon, constraints, and the reputation and stake requirements - is not here:
 * it arrives once in the login catalog snapshot and is read from ClientQuestCatalog, so
 * it is not repeated inside every quest on every synchronisation.
 */
public record QuestView(String id, QuestType type, QuestObjective objective,
                        String targetId,
                        int progress, int target, boolean accepted, boolean completed,
                        boolean cancelled, boolean expired, long expiresAt,
                        int experience, long goldReward, String rewardSummary,
                        boolean prerequisiteMet, int securityDepositPaid,
                        boolean repeatable, int cycle,
                        long rewardReadyAt, String tier, int securityDeposit,
                        List<Requirement> requirements,
                        List<String> rewardChoices) {
    public QuestView {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        rewardChoices = rewardChoices == null ? List.of() : List.copyOf(rewardChoices);
    }

    public QuestView withRewardChoices(List<String> nextChoices) {
        return new QuestView(id, type, objective, targetId,
                progress, target, accepted, completed,
                cancelled, expired, expiresAt, experience, goldReward, rewardSummary,
                prerequisiteMet, securityDepositPaid,
                repeatable, cycle, rewardReadyAt, tier, securityDeposit,
                requirements, nextChoices);
    }

    public QuestView withRequirements(List<Requirement> nextRequirements) {
        return new QuestView(id, type, objective, targetId,
                progress, target, accepted, completed,
                cancelled, expired, expiresAt, experience, goldReward, rewardSummary,
                prerequisiteMet, securityDepositPaid,
                repeatable, cycle, rewardReadyAt, tier, securityDeposit,
                nextRequirements, rewardChoices);
    }

    /**
     * Applies a counter batch from the compact progress sync: index zero is the main
     * objective and the rest are the extra requirements, in the same order.
     */
    public QuestView withCounters(List<Integer> counters) {
        if (counters == null || counters.isEmpty()) return this;
        List<Requirement> nextRequirements = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            nextRequirements.add(index + 1 < counters.size()
                    ? requirement.withProgress(counters.get(index + 1)) : requirement);
        }
        return new QuestView(id, type, objective, targetId,
                Math.max(0, Math.min(target, counters.get(0))), target, accepted, completed,
                cancelled, expired, expiresAt, experience, goldReward, rewardSummary,
                prerequisiteMet, securityDepositPaid,
                repeatable, cycle, rewardReadyAt, tier, securityDeposit,
                nextRequirements, rewardChoices);
    }

    /** One requirement beyond the main objective, carrying its own counter. */
    public record Requirement(QuestObjective objective, String targetId,
                              int progress, int target) {
        public Requirement {
            objective = objective == null ? QuestObjective.KILL : objective;
            targetId = targetId == null ? "" : targetId;
            target = Math.max(1, target);
            progress = Math.max(0, Math.min(target, progress));
        }

        public Requirement withProgress(int nextProgress) {
            return new Requirement(objective, targetId, nextProgress, target);
        }
    }
}
