package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.network.SyncQuestDataPacket;
import com.whatever.aegis_ascension.quest.QuestCompletionView;
import com.whatever.aegis_ascension.quest.QuestView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only client mirror; it never decides quest validity or rewards. */
public final class ClientQuestState {
    private static List<QuestView> quests = List.of();
    private static List<QuestCompletionView> completions = List.of();
    private static boolean penalty;
    private static int depositCost;
    private static String reputationIcon = "minecraft:emerald";
    private static java.util.Map<com.whatever.aegis_ascension.quest.QuestObjective, Integer>
            lifetimeTotals = java.util.Map.of();
    private static boolean usesMinecraftDefaultLevel = true;
    private static boolean autoAcceptEligibleQuests = true;
    private ClientQuestState() {}
    public static void accept(SyncQuestDataPacket packet) {
        quests = List.copyOf(packet.quests());
        MiscLocalSettings.get().retainActiveQuestIds(quests.stream()
                .filter(ClientQuestState::isActive)
                .map(QuestView::id)
                .toList());
        completions = List.copyOf(packet.completions());
        penalty = packet.penaltyActive();
        depositCost = packet.depositExperienceCost();
        reputationIcon = packet.reputationIcon() == null || packet.reputationIcon().isBlank()
                ? "minecraft:emerald" : packet.reputationIcon();
        lifetimeTotals = packet.lifetimeTotals();
        usesMinecraftDefaultLevel = packet.usesMinecraftDefaultLevel();
        autoAcceptEligibleQuests = packet.autoAcceptEligibleQuests();
    }
    public static boolean applyProgress(Map<String, List<Integer>> progressByQuestId) {
        if (progressByQuestId == null || progressByQuestId.isEmpty()) return false;
        boolean changed = false;
        List<QuestView> updated = new ArrayList<>(quests.size());
        for (QuestView quest : quests) {
            List<Integer> counters = progressByQuestId.get(quest.id());
            QuestView next = counters == null ? quest : quest.withCounters(counters);
            if (next.equals(quest)) {
                updated.add(quest);
                continue;
            }
            updated.add(next);
            changed = true;
        }
        if (changed) quests = List.copyOf(updated);
        return changed;
    }
    public static List<QuestView> quests() { return quests; }
    public static List<QuestCompletionView> completions() { return completions; }
    public static long totalCompleted() {
        long total = 0L;
        for (QuestCompletionView completion : completions) total += completion.completions();
        return total;
    }
    public static long totalExperienceEarned() {
        long total = 0L;
        for (QuestCompletionView completion : completions) {
            if (total > Long.MAX_VALUE - completion.experienceEarned()) return Long.MAX_VALUE;
            total += completion.experienceEarned();
        }
        return total;
    }
    public static boolean penaltyActive() { return penalty; }
    public static int depositCost() { return depositCost; }
    public static String reputationIcon() { return reputationIcon; }
    public static java.util.Map<com.whatever.aegis_ascension.quest.QuestObjective, Integer>
            lifetimeTotals() { return lifetimeTotals; }
    public static boolean usesMinecraftDefaultLevel() { return usesMinecraftDefaultLevel; }
    public static boolean autoAcceptEligibleQuests() { return autoAcceptEligibleQuests; }
    public static String experienceLabel() {
        return usesMinecraftDefaultLevel ? "XP" : "AAE";
    }
    public static String experienceDisplayName() {
        return usesMinecraftDefaultLevel ? "XP" : "Aegis Ascension Experience";
    }
    public static List<QuestView> byType(com.whatever.aegis_ascension.quest.QuestType type) {
        List<QuestView> result = new ArrayList<>();
        for (QuestView q : quests) if (q.type() == type) result.add(q);
        return result;
    }

    private static boolean isActive(QuestView quest) {
        return quest.accepted() && !quest.completed()
                && !quest.cancelled() && !quest.expired();
    }
    public static void clear() {
        quests = List.of(); completions = List.of(); penalty = false; depositCost = 0;
        usesMinecraftDefaultLevel = true; autoAcceptEligibleQuests = true;
    }
}
