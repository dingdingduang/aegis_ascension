package com.whatever.aegis_ascension.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persisted per-player quest state. No client data is trusted for these values. */
public final class QuestState {
    private static final String DAILY = "Daily";
    private static final String COMMON = "Common";
    private static final String CHALLENGE = "Challenge";
    private static final String CHUNK = "Chunk";
    private static final String COMPLETIONS = "QuestCompletions";
    private final List<QuestProgress> daily = new ArrayList<>();
    private final List<QuestProgress> common = new ArrayList<>();
    private final List<QuestProgress> challenges = new ArrayList<>();
    private final List<QuestProgress> chunks = new ArrayList<>();
    private final List<QuestProgress> side = new ArrayList<>();
    private long dayIndex = Long.MIN_VALUE;
    private long challengeRefreshIndex = Long.MIN_VALUE;
    private long sideRefreshIndex = Long.MIN_VALUE;
    /** Per-player server-owned preference; existing active quests are never cancelled by it. */
    private boolean autoAcceptEligibleQuests = true;
    private boolean challengePenaltyActive;
    private long lastChunkX = Long.MIN_VALUE;
    private long lastChunkZ = Long.MIN_VALUE;
    private final Map<QuestObjective, Integer> lifetime = new EnumMap<>(QuestObjective.class);
    private final Set<String> uniqueRewards = new LinkedHashSet<>();
    private final Map<String, Integer> completionCounts = new java.util.LinkedHashMap<>();
    private final Map<String, Long> completionExperience = new java.util.LinkedHashMap<>();

    public List<QuestProgress> daily() { return List.copyOf(daily); }
    public List<QuestProgress> common() { return List.copyOf(common); }
    public List<QuestProgress> challenges() { return List.copyOf(challenges); }
    public List<QuestProgress> chunks() { return List.copyOf(chunks); }
    public List<QuestProgress> side() { return List.copyOf(side); }
    public long dayIndex() { return dayIndex; }
    public long challengeRefreshIndex() { return challengeRefreshIndex; }
    public long sideRefreshIndex() { return sideRefreshIndex; }
    public boolean autoAcceptEligibleQuests() { return autoAcceptEligibleQuests; }
    public boolean challengePenaltyActive() { return challengePenaltyActive; }
    public int lifetime(QuestObjective objective) { return lifetime.getOrDefault(objective, 0); }
    public boolean hasClaimedUnique(String key) { return uniqueRewards.contains(key); }
    public void claimUnique(String key) { if (key != null && !key.isBlank()) uniqueRewards.add(key); }
    public List<String> completedQuestIds() { return List.copyOf(completionCounts.keySet()); }
    public int completionCount(String id) { return completionCounts.getOrDefault(id, 0); }
    public long completionExperience(String id) { return completionExperience.getOrDefault(id, 0L); }
    public boolean hasCompletedTemplate(String templateId) {
        if (templateId == null || templateId.isBlank()) return true;
        for (Map.Entry<String, Integer> entry : completionCounts.entrySet()) {
            String id = entry.getKey();
            if (entry.getValue() > 0 && (id.equals(templateId)
                    || id.startsWith(templateId + "#"))) return true;
        }
        return false;
    }
    public void recordCompletion(String id, int experience) {
        if (id == null || id.isBlank()) return;
        completionCounts.merge(id, 1,
                (current, added) -> current == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : current + added);
        long gained = Math.max(0L, experience);
        completionExperience.merge(id, gained,
                (current, added) -> current > Long.MAX_VALUE - added
                        ? Long.MAX_VALUE : current + added);
    }

    public void setDayIndex(long value) { dayIndex = value; }
    public void setChallengeRefreshIndex(long value) { challengeRefreshIndex = value; }
    public void setSideRefreshIndex(long value) { sideRefreshIndex = value; }
    public void setAutoAcceptEligibleQuests(boolean value) { autoAcceptEligibleQuests = value; }
    public void setPenalty(boolean value) { challengePenaltyActive = value; }
    public void setLastChunk(long x, long z) { lastChunkX = x; lastChunkZ = z; }
    public long lastChunkX() { return lastChunkX; }
    public long lastChunkZ() { return lastChunkZ; }
    public void addDaily(QuestProgress value) { if (value != null) daily.add(value); }
    public void addCommon(QuestProgress value) { if (value != null) common.add(value); }
    public void addChallenge(QuestProgress value) { if (value != null) challenges.add(value); }
    public void addChunk(QuestProgress value) { if (value != null) chunks.add(value); }
    public void addSide(QuestProgress value) { if (value != null) side.add(value); }
    public void clearDaily() { daily.clear(); }
    public void clearCommon() { common.clear(); }
    public void clearChallenges() { challenges.clear(); }
    public void clearChunks() { chunks.clear(); }
    public void clearSide() { side.clear(); }
    public void incrementLifetime(QuestObjective objective, int amount) {
        if (objective != null && amount > 0) lifetime.merge(objective, amount, Integer::sum);
    }

    /** Called by the existing progression reset item/command. */
    public void resetOnProgressionReset() {
        daily.clear();
        common.clear();
        challenges.clear();
        chunks.clear();
        side.clear();
        dayIndex = Long.MIN_VALUE;
        challengeRefreshIndex = Long.MIN_VALUE;
        sideRefreshIndex = Long.MIN_VALUE;
        challengePenaltyActive = false;
        lastChunkX = Long.MIN_VALUE;
        lastChunkZ = Long.MIN_VALUE;
        lifetime.clear();
        uniqueRewards.clear();
        completionCounts.clear();
        completionExperience.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("DayIndex", dayIndex);
        tag.putLong("ChallengeRefreshIndex", challengeRefreshIndex);
        tag.putLong("SideRefreshIndex", sideRefreshIndex);
        tag.putBoolean("AutoAcceptEligibleQuests", autoAcceptEligibleQuests);
        tag.putBoolean("ChallengePenalty", challengePenaltyActive);
        tag.putLong("LastChunkX", lastChunkX);
        tag.putLong("LastChunkZ", lastChunkZ);
        ListTag dailyTag = new ListTag(); daily.forEach(q -> dailyTag.add(q.save())); tag.put(DAILY, dailyTag);
        ListTag commonTag = new ListTag(); common.forEach(q -> commonTag.add(q.save())); tag.put(COMMON, commonTag);
        ListTag challengeTag = new ListTag(); challenges.forEach(q -> challengeTag.add(q.save())); tag.put(CHALLENGE, challengeTag);
        ListTag chunkTag = new ListTag(); chunks.forEach(q -> chunkTag.add(q.save())); tag.put(CHUNK, chunkTag);
        ListTag sideTag = new ListTag(); side.forEach(q -> sideTag.add(q.save())); tag.put("Side", sideTag);
        CompoundTag counters = new CompoundTag();
        lifetime.forEach((key, value) -> counters.putInt(key.name(), value));
        tag.put("Lifetime", counters);
        ListTag unique = new ListTag(); uniqueRewards.forEach(key -> unique.add(net.minecraft.nbt.StringTag.valueOf(key))); tag.put("UniqueRewards", unique);
        ListTag completions = new ListTag();
        completionCounts.forEach((id, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id);
            entry.putInt("Count", count);
            entry.putLong("Experience", completionExperience(id));
            completions.add(entry);
        });
        tag.put(COMPLETIONS, completions);
        return tag;
    }

    public void load(CompoundTag tag, QuestConfig config) {
        resetOnProgressionReset();
        dayIndex = tag.contains("DayIndex", Tag.TAG_LONG) ? tag.getLong("DayIndex") : Long.MIN_VALUE;
        challengeRefreshIndex = tag.contains("ChallengeRefreshIndex", Tag.TAG_LONG)
                ? tag.getLong("ChallengeRefreshIndex") : Long.MIN_VALUE;
        sideRefreshIndex = tag.contains("SideRefreshIndex", Tag.TAG_LONG)
                ? tag.getLong("SideRefreshIndex") : Long.MIN_VALUE;
        autoAcceptEligibleQuests = !tag.contains("AutoAcceptEligibleQuests", Tag.TAG_BYTE)
                || tag.getBoolean("AutoAcceptEligibleQuests");
        challengePenaltyActive = tag.getBoolean("ChallengePenalty");
        lastChunkX = tag.getLong("LastChunkX"); lastChunkZ = tag.getLong("LastChunkZ");
        ListTag dailyTag = tag.getList(DAILY, Tag.TAG_COMPOUND);
        for (int i = 0; i < dailyTag.size(); i++) addDaily(QuestProgress.load(dailyTag.getCompound(i), config));
        ListTag commonTag = tag.getList(COMMON, Tag.TAG_COMPOUND);
        for (int i = 0; i < commonTag.size(); i++) addCommon(QuestProgress.load(commonTag.getCompound(i), config));
        ListTag challengeTag = tag.getList(CHALLENGE, Tag.TAG_COMPOUND);
        for (int i = 0; i < challengeTag.size(); i++) addChallenge(QuestProgress.load(challengeTag.getCompound(i), config));
        ListTag chunkTag = tag.getList(CHUNK, Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkTag.size(); i++) addChunk(QuestProgress.load(chunkTag.getCompound(i), config));
        ListTag sideTag = tag.getList("Side", Tag.TAG_COMPOUND);
        for (int i = 0; i < sideTag.size(); i++) addSide(QuestProgress.load(sideTag.getCompound(i), config));
        CompoundTag counters = tag.getCompound("Lifetime");
        for (String key : counters.getAllKeys()) {
            try { lifetime.put(QuestObjective.valueOf(key), Math.max(0, counters.getInt(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        ListTag unique = tag.getList("UniqueRewards", Tag.TAG_STRING);
        for (int i = 0; i < unique.size(); i++) if (!unique.getString(i).isBlank()) uniqueRewards.add(unique.getString(i));
        ListTag completions = tag.getList(COMPLETIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < completions.size(); i++) {
            CompoundTag entry = completions.getCompound(i);
            String id = entry.getString("Id");
            int count = Math.max(0, entry.getInt("Count"));
            if (id.isBlank() || count <= 0) continue;
            completionCounts.put(id, count);
            completionExperience.put(id, Math.max(0L, entry.getLong("Experience")));
        }
    }
}
