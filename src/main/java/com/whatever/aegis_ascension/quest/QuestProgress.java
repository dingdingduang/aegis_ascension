package com.whatever.aegis_ascension.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    /**
     * World features already counted towards this quest, so one of them can never be
     * credited twice. Persisted with the quest rather than held for the session, which
     * would let a relog re-credit the same structure; bounded by the quest's own target
     * and discarded along with it.
     */
    private final Set<String> creditedInstances = new LinkedHashSet<>();
    /** Counters for the definition's extra requirements, parallel to that list. */
    private int[] extraProgress = new int[0];
    /** Which offered reward the player took; -1 while the choice is still open. */
    private int chosenRewardIndex = -1;
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
        extraProgress = new int[definition.extraRequirements().size()];
        // Releasing a quest forfeits its stake exactly as cancelling does, so retaking
        // it charges afresh rather than refunding what was already paid.
        securityDepositPaid = 0;
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
        creditedInstances.clear();
        chosenRewardIndex = -1;
        extraProgress = new int[nextDefinition.extraRequirements().size()];
    }

    /** Marks one world feature counted, returning false when it already was. */
    public boolean creditInstance(String key) {
        return key != null && !key.isBlank() && creditedInstances.add(key);
    }

    public void addProgress(int amount) {
        addProgress(amount, true);
    }

    public void addProgress(int amount, boolean allowCompletion) {
        if (amount <= 0 || completed || cancelled || expired) return;
        int remaining = Math.max(0, definition.target() - progress);
        progress = amount >= remaining ? definition.target() : progress + amount;
        markCompleteIfMet(allowCompletion);
    }

    /** Advances one extra requirement by its index in the definition's list. */
    public void addExtraProgress(int index, int amount, boolean allowCompletion) {
        if (amount <= 0 || completed || cancelled || expired) return;
        alignExtraProgress();
        if (index < 0 || index >= extraProgress.length) return;
        int target = definition.extraRequirements().get(index).target();
        extraProgress[index] = Math.min(target, extraProgress[index] + amount);
        markCompleteIfMet(allowCompletion);
    }

    public int chosenRewardIndex() { return chosenRewardIndex; }

    /** True once the quest is finished but its offered reward has not been taken. */
    public boolean awaitingRewardChoice() {
        return completed && chosenRewardIndex < 0
                && !definition.rewardChoices().isEmpty();
    }

    /** Records the taken reward; refuses a second attempt or an index out of range. */
    public boolean chooseReward(int index) {
        if (!awaitingRewardChoice()
                || index < 0 || index >= definition.rewardChoices().size()) {
            return false;
        }
        chosenRewardIndex = index;
        return true;
    }

    public int extraProgress(int index) {
        alignExtraProgress();
        return index < 0 || index >= extraProgress.length ? 0 : extraProgress[index];
    }

    public int[] extraProgressSnapshot() {
        alignExtraProgress();
        return extraProgress.clone();
    }

    /**
     * A compound quest is finished only once every part of it is, so completion asks the
     * whole definition rather than the main counter alone.
     */
    public boolean allRequirementsMet() {
        if (progress < definition.target()) return false;
        alignExtraProgress();
        List<QuestDefinition.Requirement> requirements = definition.extraRequirements();
        for (int index = 0; index < requirements.size(); index++) {
            if (extraProgress[index] < requirements.get(index).target()) return false;
        }
        return true;
    }

    private void markCompleteIfMet(boolean allowCompletion) {
        if (allowCompletion && allRequirementsMet()) completed = true;
    }

    /** Resizes the counters when a definition is rolled, reloaded, or restarted. */
    private void alignExtraProgress() {
        int required = definition.extraRequirements().size();
        if (extraProgress.length != required) {
            extraProgress = Arrays.copyOf(extraProgress, required);
        }
    }

    public boolean completeIfReady() {
        if (completed || cancelled || expired || !allRequirementsMet()) return false;
        completed = true;
        return true;
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(definition.target(), value));
        markCompleteIfMet(true);
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
        tag.putString("QuestTier", definition.tier());
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
        if (!definition.extraRequirements().isEmpty()) {
            ListTag requirements = new ListTag();
            for (QuestDefinition.Requirement requirement : definition.extraRequirements()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Objective", requirement.objective().name());
                entry.putString("TargetId", requirement.targetId());
                entry.putInt("Target", requirement.target());
                requirements.add(entry);
            }
            tag.put("ExtraRequirements", requirements);
        }
        if (extraProgress.length > 0) tag.putIntArray("ExtraProgress", extraProgress);
        if (!definition.rewardChoices().isEmpty()) {
            ListTag choices = new ListTag();
            for (QuestDefinition.Reward reward : definition.rewardChoices()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Item", reward.itemId());
                entry.putString("Virtual", reward.virtualId());
                entry.putInt("Count", reward.count());
                entry.putString("Tier", reward.tier());
                entry.putBoolean("Unique", reward.unique());
                choices.add(entry);
            }
            tag.put("RewardChoices", choices);
        }
        tag.putInt("ChosenRewardIndex", chosenRewardIndex);
        if (!creditedInstances.isEmpty()) {
            ListTag credited = new ListTag();
            for (String key : creditedInstances) credited.add(StringTag.valueOf(key));
            tag.put("CreditedInstances", credited);
        }
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
        // Quests saved before rarities existed carry no tier and reload as R.
        String tier = tag.contains("QuestTier", Tag.TAG_STRING)
                ? tag.getString("QuestTier") : definition.tier();
        List<QuestDefinition.Reward> rewardChoices = new java.util.ArrayList<>();
        ListTag choiceTags = tag.getList("RewardChoices", Tag.TAG_COMPOUND);
        for (int i = 0; i < choiceTags.size(); i++) {
            CompoundTag entry = choiceTags.getCompound(i);
            rewardChoices.add(new QuestDefinition.Reward(entry.getString("Item"),
                    entry.getString("Virtual"), entry.getInt("Count"),
                    entry.getString("Tier"), entry.getBoolean("Unique")));
        }
        // Requirement targets were rolled when the quest was generated and cannot be
        // recovered from the template, so they are restored from the save like rewards.
        List<QuestDefinition.Requirement> extraRequirements = new java.util.ArrayList<>();
        ListTag requirementTags = tag.getList("ExtraRequirements", Tag.TAG_COMPOUND);
        for (int i = 0; i < requirementTags.size(); i++) {
            CompoundTag entry = requirementTags.getCompound(i);
            QuestObjective objective;
            try {
                objective = QuestObjective.valueOf(entry.getString("Objective"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            extraRequirements.add(new QuestDefinition.Requirement(objective,
                    entry.getString("TargetId"), entry.getInt("Target")));
        }
        definition = new QuestDefinition(tag.getString("Id"), type, definition.objective(),
                definition.title(), definition.description(), targetId,
                target, experience, goldReward, rewards, definition.story(),
                definition.profession(), definition.prerequisiteId(), definition.icon(), tier,
                extraRequirements, rewardChoices);
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
        if (tag.contains("ExtraProgress", Tag.TAG_INT_ARRAY)) {
            int[] saved = tag.getIntArray("ExtraProgress");
            // The definition is rebuilt from the catalogue on load, so a template edited
            // since saving may declare a different number of requirements than the save.
            result.extraProgress = Arrays.copyOf(saved,
                    definition.extraRequirements().size());
        }
        result.chosenRewardIndex = tag.contains("ChosenRewardIndex", Tag.TAG_INT)
                ? tag.getInt("ChosenRewardIndex") : -1;
        ListTag credited = tag.getList("CreditedInstances", Tag.TAG_STRING);
        for (int i = 0; i < credited.size(); i++) {
            result.creditInstance(credited.getString(i));
        }
        return result;
    }
}
