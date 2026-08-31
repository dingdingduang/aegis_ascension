package com.whatever.aegis_ascension.quest;

import java.util.List;

/** Immutable server-side quest template after it has been rolled for a player. */
public record QuestDefinition(String id, QuestType type, QuestObjective objective,
                              String title, String description, String targetId,
                              int target, int experience, long goldReward,
                              List<Reward> rewards,
                              String story, String profession, String prerequisiteId,
                              String icon) {
    public QuestDefinition {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        targetId = targetId == null ? "" : targetId;
        story = story == null ? "" : story;
        profession = profession == null ? "" : profession;
        prerequisiteId = prerequisiteId == null ? "" : prerequisiteId;
        icon = icon == null ? "" : icon;
        target = Math.max(1, target);
        experience = Math.max(0, experience);
        goldReward = Math.max(0L, goldReward);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public QuestDefinition(String id, QuestType type, QuestObjective objective,
                           String title, String description, String targetId,
                           int target, int experience, List<Reward> rewards) {
        this(id, type, objective, title, description, targetId, target, experience,
                0L, rewards, "", "", "", "");
    }

    public QuestDefinition(String id, QuestType type, QuestObjective objective,
                           String title, String description, String targetId,
                           int target, int experience, List<Reward> rewards,
                           String story, String profession, String prerequisiteId) {
        this(id, type, objective, title, description, targetId, target, experience,
                0L, rewards, story, profession, prerequisiteId, "");
    }

    public QuestDefinition(String id, QuestType type, QuestObjective objective,
                           String title, String description, String targetId,
                           int target, int experience, List<Reward> rewards,
                           String story, String profession, String prerequisiteId,
                           String icon) {
        this(id, type, objective, title, description, targetId, target, experience,
                0L, rewards, story, profession, prerequisiteId, icon);
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
