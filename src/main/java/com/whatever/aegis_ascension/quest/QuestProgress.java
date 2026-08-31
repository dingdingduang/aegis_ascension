package com.whatever.aegis_ascension.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/** Mutable persisted state for one rolled quest. */
public final class QuestProgress {
    private QuestDefinition definition;
    private int progress;
    private boolean accepted;
    private boolean completed;
    private boolean cancelled;
    private boolean expired;
    private long expiresAt;
    private long originChunkX;
    private long originChunkZ;
    private int securityDepositPaid;
    private long nextRepeatRewardAt;
    private long repeatResetAt;

    public QuestProgress(QuestDefinition definition, boolean accepted, long expiresAt) {
        this.definition = definition;
        this.accepted = accepted;
        this.expiresAt = expiresAt;
    }

    public QuestDefinition definition() { return definition; }
    public int progress() { return progress; }
    public boolean accepted() { return accepted; }
    public boolean completed() { return completed; }
    public boolean cancelled() { return cancelled; }
    public boolean expired() { return expired; }
    public long expiresAt() { return expiresAt; }
    public long originChunkX() { return originChunkX; }
    public long originChunkZ() { return originChunkZ; }
    public int securityDepositPaid() { return securityDepositPaid; }
    public long nextRepeatRewardAt() { return nextRepeatRewardAt; }
    public long repeatResetAt() { return repeatResetAt; }

    public void accept() { accepted = true; }
    public void accept(int securityDeposit) {
        accepted = true;
        securityDepositPaid = Math.max(0, securityDeposit);
    }
    public void cancel() { cancelled = true; }
    public void expire() { expired = true; }
    public int releaseSecurityDeposit() {
        int released = securityDepositPaid;
        securityDepositPaid = 0;
        return released;
    }
    /** Makes a non-terminal quest available again and clears progress earned during that run. */
    public void makeAvailableAgain() {
        if (completed || expired) return;
        progress = 0;
        accepted = false;
        cancelled = false;
    }
    public void setExpiresAt(long value) { expiresAt = value; }
    public void setOriginChunk(long x, long z) { originChunkX = x; originChunkZ = z; }
    public void setNextRepeatRewardAt(long value) {
        nextRepeatRewardAt = Math.max(0L, value);
    }
    public void scheduleRepeatReset(long value) {
        repeatResetAt = Math.max(0L, value);
    }

    public void restart(QuestDefinition nextDefinition, boolean autoAccepted) {
        definition = nextDefinition;
        progress = 0;
        accepted = autoAccepted;
        completed = false;
        cancelled = false;
        expired = false;
        expiresAt = 0L;
        securityDepositPaid = 0;
        repeatResetAt = 0L;
    }

    public void addProgress(int amount) {
        addProgress(amount, true);
    }

    public void addProgress(int amount, boolean allowCompletion) {
        if (amount <= 0 || completed || cancelled || expired) return;
        int remaining = Math.max(0, definition.target() - progress);
        progress = amount >= remaining ? definition.target() : progress + amount;
        if (allowCompletion && progress >= definition.target()) completed = true;
    }

    public boolean completeIfReady() {
        if (completed || cancelled || expired || progress < definition.target()) return false;
        completed = true;
        return true;
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(definition.target(), value));
        if (progress >= definition.target()) completed = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", definition.id());
        tag.putString("Type", definition.type() == null ? "DAILY" : definition.type().name());
        tag.putInt("Progress", progress);
        tag.putBoolean("Accepted", accepted);
        tag.putBoolean("Completed", completed);
        tag.putBoolean("Cancelled", cancelled);
        tag.putBoolean("Expired", expired);
        tag.putLong("ExpiresAt", expiresAt);
        tag.putLong("OriginChunkX", originChunkX);
        tag.putLong("OriginChunkZ", originChunkZ);
        tag.putInt("Target", definition.target());
        tag.putString("TargetId", definition.targetId());
        tag.putInt("SecurityDepositPaid", securityDepositPaid);
        tag.putInt("Experience", definition.experience());
        tag.putLong("GoldReward", definition.goldReward());
        tag.putLong("NextRepeatRewardAt", nextRepeatRewardAt);
        tag.putLong("RepeatResetAt", repeatResetAt);
        ListTag rewards = new ListTag();
        for (QuestDefinition.Reward reward : definition.rewards()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Item", reward.itemId());
            entry.putString("Virtual", reward.virtualId());
            entry.putInt("Count", reward.count());
            entry.putString("Tier", reward.tier());
            entry.putBoolean("Unique", reward.unique());
            rewards.add(entry);
        }
        tag.put("Rewards", rewards);
        return tag;
    }

    public static QuestProgress load(CompoundTag tag, QuestConfig config) {
        QuestDefinition definition = config.definition(tag.getString("Id"));
        if (definition == null) return null;
        QuestType type;
        try { type = QuestType.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException ignored) { type = QuestType.DAILY; }
        List<QuestDefinition.Reward> rewards = new java.util.ArrayList<>();
        ListTag rewardTags = tag.getList("Rewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < rewardTags.size(); i++) {
            CompoundTag entry = rewardTags.getCompound(i);
            rewards.add(new QuestDefinition.Reward(entry.getString("Item"), entry.getString("Virtual"),
                    entry.getInt("Count"), entry.getString("Tier"), entry.getBoolean("Unique")));
        }
        if (rewards.isEmpty()) rewards = definition.rewards();
        int target = tag.contains("Target", Tag.TAG_INT)
                ? Math.max(1, tag.getInt("Target")) : definition.target();
        String targetId = tag.contains("TargetId", Tag.TAG_STRING)
                ? tag.getString("TargetId") : definition.targetId();
        int experience = tag.contains("Experience", Tag.TAG_INT)
                ? Math.max(0, tag.getInt("Experience")) : definition.experience();
        long goldReward = tag.contains("GoldReward", Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong("GoldReward")) : definition.goldReward();
        definition = new QuestDefinition(tag.getString("Id"), type, definition.objective(),
                definition.title(), definition.description(), targetId,
                target, experience, goldReward, rewards, definition.story(),
                definition.profession(), definition.prerequisiteId(), definition.icon());
        QuestProgress result = new QuestProgress(definition, tag.getBoolean("Accepted"),
                tag.getLong("ExpiresAt"));
        result.securityDepositPaid = Math.max(0, tag.getInt("SecurityDepositPaid"));
        result.progress = Math.max(0, Math.min(definition.target(), tag.getInt("Progress")));
        result.completed = tag.getBoolean("Completed");
        if (tag.getBoolean("Cancelled")) result.cancel();
        if (tag.getBoolean("Expired")) result.expire();
        result.nextRepeatRewardAt = Math.max(0L, tag.getLong("NextRepeatRewardAt"));
        result.repeatResetAt = Math.max(0L, tag.getLong("RepeatResetAt"));
        result.setOriginChunk(tag.getLong("OriginChunkX"), tag.getLong("OriginChunkZ"));
        return result;
    }
}
