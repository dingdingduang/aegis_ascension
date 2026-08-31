package com.whatever.aegis_ascension.platform;

import java.util.Set;

/**
 * Loader-neutral view of Aegis Ascension's effective configuration.
 *
 * <p>Gameplay, data, client, and network code depend on these semantic values
 * instead of a loader's config holder types. Each target supplies the backing
 * implementation and remains responsible for registration and validation.</p>
 */
public interface ConfigAccess {
    boolean resetPerksOnDeath();

    boolean preserveInventoryOnDeathReset();

    int baseMaxTalentSlots();

    Set<String> hiddenTalentIds();

    boolean isTalentHidden(String talentId);

    boolean isMysteriousDollOutcomeBanned(String outcomeId);

    boolean liveCustomStatsRefreshEnabled();

    int skillEnhancementLevelsPerCharge();

    int maximumSkillEnhancementChargesFromExperience();

    int skillEnhancementRefreshExperienceCost();

    int perkLevelsPerCharge();

    int maximumPerkChargesFromExperience();

    int maximumBreakthroughsFromExperience();

    boolean triggerBreakthroughOnPerkSelection();

    boolean resetTalentRefreshOnBreakthrough();

    int maximumPerkOptions();

    int skillEnhancementChargesPerPerkExchange();

    int aegisLevelsPerCharge();

    int maximumAegisCharges();

    int aegisRefreshChargesPerCharge();

    boolean allowDevourAgainAfterDiscard();

    boolean convertFlatAttackSpeedToPercentage();

    boolean isDevourAttributeBlacklisted(String attributeId);

    boolean useMinecraftDefaultLevel();

    boolean useGoldCurrency();

    long aegisAscensionBaseXp();

    double aegisAscensionGrowthRate();

    int aegisAscensionMaximumRank();

    double storageMutationPacketCooldownSeconds();

    double storageViewPacketCooldownSeconds();

    double togglePacketCooldownSeconds();

    double refreshPacketCooldownSeconds();

    double devourItemPacketCooldownSeconds();

    double devourDataPacketCooldownSeconds();

    double discardDevourPacketCooldownSeconds();

    double perkDataPacketCooldownSeconds();

    double livePerkDataPacketCooldownSeconds();

    double sharedFortunePacketCooldownSeconds();

    double questProgressSyncIntervalSeconds();

    double questViewPacketCooldownSeconds();
}
