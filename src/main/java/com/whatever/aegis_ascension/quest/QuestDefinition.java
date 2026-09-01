package com.whatever.aegis_ascension.quest;

import com.whatever.aegis_ascension.util.GeneralConstants;

import java.util.List;

/** Immutable server-side quest template after it has been rolled for a player. */
public record QuestDefinition(String id, QuestType type, QuestObjective objective,
                              String title, String description, String targetId,
                              int target, int experience, long goldReward,
                              List<Reward> rewards,
                              String story, String profession, String prerequisiteId,
                              String icon, String tier,
                              List<Requirement> extraRequirements,
                              List<Reward> rewardChoices) {
    public QuestDefinition {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        targetId = targetId == null ? "" : targetId;
        story = story == null ? "" : story;
        profession = profession == null ? "" : profession;
        prerequisiteId = prerequisiteId == null ? "" : prerequisiteId;
        icon = icon == null ? "" : icon;
        tier = GeneralConstants.normalizeTier(tier);
        target = Math.max(1, target);
        experience = Math.max(0, experience);
        goldReward = Math.max(0L, goldReward);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        extraRequirements = extraRequirements == null
                ? List.of() : List.copyOf(extraRequirements);
        rewardChoices = rewardChoices == null ? List.of() : List.copyOf(rewardChoices);
    }

    /** One requirement beyond the quest's main objective. */
    public record Requirement(QuestObjective objective, String targetId, int target) {
        public Requirement {
            objective = objective == null ? QuestObjective.KILL : objective;
            targetId = targetId == null ? "" : targetId;
            target = Math.max(1, target);
        }
    }

    public record Reward(String itemId, String virtualId, int count, String tier,
                         boolean unique) {
        public Reward {
            itemId = itemId == null ? "" : itemId;
            virtualId = virtualId == null ? "" : virtualId;
            count = Math.max(1, count);
            tier = tier == null ? "R" : tier;
        }

        public boolean isVirtual() {
            return !virtualId.isBlank();
        }
    }
}
