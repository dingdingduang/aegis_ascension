package com.whatever.aegis_ascension.platform;

import com.whatever.aegis_ascension.AegisAscensionConfig;
import com.whatever.aegis_ascension.perk.Perk;

import java.util.LinkedHashSet;
import java.util.Set;

/** Forge 1.20.1-backed implementation of the effective common configuration. */
public final class ForgeConfigAccess implements ConfigAccess {
    @Override
    public boolean resetPerksOnDeath() {
        return AegisAscensionConfig.RESET_PERKS_ON_DEATH.get();
    }

    @Override
    public boolean preserveInventoryOnDeathReset() {
        return AegisAscensionConfig.RESET_PERKS_ON_DEATH_EXCEPT_INVENTORY.get();
    }

    @Override
    public int baseMaxTalentSlots() {
        return AegisAscensionConfig.BASE_MAX_TALENT_SLOTS.get();
    }

    @Override
    public Set<String> hiddenTalentIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String configuredId : AegisAscensionConfig.HIDDEN_TALENT_IDS.get()) {
            String id = configuredId.trim();
            Perk.byId(id).ifPresent(perk -> ids.add(perk.id()));
        }
        Perk.values().stream()
                .filter(perk -> !perk.areRequiredModsLoaded())
                .map(Perk::id)
                .forEach(ids::add);
        return Set.copyOf(ids);
    }

    @Override
    public boolean isTalentHidden(String talentId) {
        if (talentId == null) {
            return false;
        }
        for (String configuredId : AegisAscensionConfig.HIDDEN_TALENT_IDS.get()) {
            if (talentId.equals(configuredId.trim())) {
                return true;
            }
        }
        return Perk.byId(talentId)
                .map(perk -> !perk.areRequiredModsLoaded())
                .orElse(false);
    }

    @Override
    public boolean isMysteriousDollOutcomeBanned(String outcomeId) {
        if (outcomeId == null) {
            return false;
        }
        for (String configuredId : AegisAscensionConfig.MYSTERIOUS_DOLL_BANNED_OUTCOMES.get()) {
            if (outcomeId.equalsIgnoreCase(configuredId.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean liveCustomStatsRefreshEnabled() {
        return AegisAscensionConfig.LIVE_CUSTOM_STATS_REFRESH.get();
    }

    @Override
    public int skillEnhancementLevelsPerCharge() {
        return AegisAscensionConfig.SKILL_ENHANCEMENT_LEVEL_INTERVAL.get();
    }

    @Override
    public int maximumSkillEnhancementChargesFromExperience() {
        return AegisAscensionConfig.MAXIMUM_SKILL_ENHANCEMENT_CHARGES_FROM_EXPERIENCE.get();
    }

    @Override
    public int skillEnhancementRefreshExperienceCost() {
        return AegisAscensionConfig.SKILL_ENHANCEMENT_REFRESH_EXPERIENCE_COST.get();
    }

    @Override
    public int perkLevelsPerCharge() {
        return AegisAscensionConfig.PERK_LEVEL_INTERVAL.get();
    }

    @Override
    public int maximumPerkChargesFromExperience() {
        return AegisAscensionConfig.MAXIMUM_PERK_CHARGES_FROM_EXPERIENCE.get();
    }

    @Override
    public int maximumBreakthroughsFromExperience() {
        return AegisAscensionConfig.MAXIMUM_BREAKTHROUGHS_FROM_EXPERIENCE.get();
    }

    @Override
    public boolean triggerBreakthroughOnPerkSelection() {
        return AegisAscensionConfig.TRIGGER_BREAKTHROUGH_ON_PERK_SELECTION.get();
    }

    @Override
    public boolean resetTalentRefreshOnBreakthrough() {
        return AegisAscensionConfig.RESET_TALENT_REFRESH_ON_BREAKTHROUGH.get();
    }

    @Override
    public int maximumPerkOptions() {
        return AegisAscensionConfig.MAXIMUM_PERK_OPTIONS.get();
    }

    @Override
    public int skillEnhancementChargesPerPerkExchange() {
        return AegisAscensionConfig.SKILL_ENHANCEMENT_CHARGES_PER_PERK_EXCHANGE.get();
    }

    @Override
    public int aegisLevelsPerCharge() {
        return AegisAscensionConfig.AEGIS_LEVEL_INTERVAL.get();
    }

    @Override
    public int maximumAegisCharges() {
        return AegisAscensionConfig.MAXIMUM_AEGIS_CHARGES.get();
    }

    @Override
    public int aegisRefreshChargesPerCharge() {
        return AegisAscensionConfig.AEGIS_REFRESH_CHARGES_PER_CHARGE.get();
    }

    @Override
    public boolean allowDevourAgainAfterDiscard() {
        return AegisAscensionConfig.DEVOUR_ALLOW_AGAIN_AFTER_DISCARD.get();
    }

    @Override
    public boolean convertFlatAttackSpeedToPercentage() {
        return AegisAscensionConfig.DEVOUR_CONVERT_FLAT_ATTACK_SPEED_TO_PERCENTAGE.get();
    }

    @Override
    public boolean isDevourAttributeBlacklisted(String attributeId) {
        if (attributeId == null) {
            return false;
        }
        for (String configuredId : AegisAscensionConfig.DEVOUR_ATTRIBUTE_BLACKLIST.get()) {
            if (attributeId.equals(configuredId.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean useMinecraftDefaultLevel() {
        return AegisAscensionConfig.USE_MINECRAFT_DEFAULT_LEVEL.get();
    }

    @Override
    public boolean useGoldCurrency() {
        return AegisAscensionConfig.USE_GOLD_CURRENCY.get();
    }

    @Override
    public long aegisAscensionBaseXp() {
        return AegisAscensionConfig.AEGIS_ASCENSION_BASE_XP.get();
    }

    @Override
    public double aegisAscensionGrowthRate() {
        return AegisAscensionConfig.AEGIS_ASCENSION_GROWTH_RATE.get();
    }

    @Override
    public int aegisAscensionMaximumRank() {
        return AegisAscensionConfig.AEGIS_ASCENSION_MAXIMUM_RANK.get();
    }

    @Override
    public double storageMutationPacketCooldownSeconds() {
        return AegisAscensionConfig.STORAGE_MUTATION_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double storageViewPacketCooldownSeconds() {
        return AegisAscensionConfig.STORAGE_VIEW_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double togglePacketCooldownSeconds() {
        return AegisAscensionConfig.TOGGLE_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double refreshPacketCooldownSeconds() {
        return AegisAscensionConfig.REFRESH_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double devourItemPacketCooldownSeconds() {
        return AegisAscensionConfig.DEVOUR_ITEM_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double devourDataPacketCooldownSeconds() {
        return AegisAscensionConfig.DEVOUR_DATA_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double discardDevourPacketCooldownSeconds() {
        return AegisAscensionConfig.DISCARD_DEVOUR_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double perkDataPacketCooldownSeconds() {
        return AegisAscensionConfig.PERK_DATA_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double livePerkDataPacketCooldownSeconds() {
        return AegisAscensionConfig.LIVE_PERK_DATA_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double sharedFortunePacketCooldownSeconds() {
        return AegisAscensionConfig.SHARED_FORTUNE_PACKET_COOLDOWN_SECONDS.get();
    }

    @Override
    public double questProgressSyncIntervalSeconds() {
        return AegisAscensionConfig.QUEST_PROGRESS_SYNC_INTERVAL_SECONDS.get();
    }

    @Override
    public double questViewPacketCooldownSeconds() {
        return AegisAscensionConfig.QUEST_VIEW_PACKET_COOLDOWN_SECONDS.get();
    }
}
