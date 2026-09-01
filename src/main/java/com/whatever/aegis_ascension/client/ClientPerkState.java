package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.network.SyncDevourDataPacket;
import com.whatever.aegis_ascension.util.DisplayStatScope;
import com.whatever.aegis_ascension.util.StatAttribution;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientPerkState {
    private static int selectionCharges;
    private static int pendingBreakthroughTriggers;
    private static int perkRefreshCharges;
    private static int maxTalentSlots = 33;
    private static boolean offerSessionActive;
    private static boolean liveCustomStatsRefreshAllowed;
    private static UUID sharedFortunePartnerId;
    private static String sharedFortunePartnerName = "";
    private static long sharedFortuneRebindAvailableAtMillis;
    private static int skillEnhancementCharges;
    private static int skillEnhancementChargesPerPerkExchange = 2;
    private static int skillEnhancementRefreshExperienceCost = 100;
    private static boolean skillEnhancementRefreshFree;
    private static int aegisSelectionCharges;
    private static int aegisRefreshCharges;
    private static boolean aegisOfferSessionActive;
    private static boolean usesMinecraftDefaultLevel = true;
    private static boolean usesGoldCurrency;
    private static long goldCurrency;
    private static int progressionLevel;
    private static int aegisAscensionRank = 1;
    private static long aegisAscensionExperience;
    private static long aegisAscensionExperienceToNextRank;
    private static int aegisAscensionMaximumRank = 1000;
    private static final Map<Perk, Integer> PERK_RANKS = new LinkedHashMap<>();
    private static final Map<SkillEnhancement, Integer> SKILL_ENHANCEMENT_RANKS =
            new LinkedHashMap<>();
    private static List<SkillEnhancement> skillEnhancementOffers = List.of();
    private static SkillEnhancement primarySkillEnhancement =
            SkillEnhancement.defaultPrimary();
    private static boolean primarySkillEnhancementChosen;
    private static final Map<String, Double> DISPLAY_STATS = new LinkedHashMap<>();
    private static final Set<String> HIDDEN_TALENT_IDS = new LinkedHashSet<>();
    private static final Set<String> ENABLED_MANUAL_TALENTS = new LinkedHashSet<>();
    private static final Set<Aegis> CHOSEN_AEGISES = new LinkedHashSet<>();
    private static final Set<String> DISABLED_MANUAL_AEGISES = new LinkedHashSet<>();
    private static List<SyncDevourDataPacket.Entry> devouredAttributes = List.of();
    private static boolean devourAllowAgainAfterDiscard;

    private ClientPerkState() {
    }

    public static int getSelectionCharges() {
        return selectionCharges;
    }

    public static int getPendingBreakthroughTriggers() {
        return pendingBreakthroughTriggers;
    }

    public static int getMaxTalentSlots() {
        return maxTalentSlots;
    }

    public static int getPerkRefreshCharges() {
        return perkRefreshCharges;
    }

    public static int getSkillEnhancementCharges() {
        return skillEnhancementCharges;
    }

    public static int getSkillEnhancementChargesPerPerkExchange() {
        return skillEnhancementChargesPerPerkExchange;
    }

    public static int getSkillEnhancementRefreshExperienceCost() {
        return skillEnhancementRefreshExperienceCost;
    }

    public static boolean isSkillEnhancementRefreshFree() {
        return skillEnhancementRefreshFree;
    }

    public static int getSkillEnhancementRank(SkillEnhancement enhancement) {
        return SKILL_ENHANCEMENT_RANKS.getOrDefault(enhancement, 0);
    }

    public static Map<SkillEnhancement, Integer> getSkillEnhancementRanks() {
        return Map.copyOf(SKILL_ENHANCEMENT_RANKS);
    }

    public static List<SkillEnhancement> getSkillEnhancementOffers() {
        return skillEnhancementOffers;
    }

    public static SkillEnhancement getPrimarySkillEnhancement() {
        return primarySkillEnhancement;
    }

    public static boolean hasChosenPrimarySkillEnhancement() {
        return primarySkillEnhancementChosen;
    }

    public static void setPrimarySkillEnhancement(SkillEnhancement enhancement) {
        primarySkillEnhancement = enhancement;
        primarySkillEnhancementChosen = true;
    }

    public static int getAegisSelectionCharges() {
        return aegisSelectionCharges;
    }

    public static int getAegisRefreshCharges() {
        return aegisRefreshCharges;
    }

    public static boolean usesMinecraftDefaultLevel() {
        return usesMinecraftDefaultLevel;
    }

    public static boolean usesGoldCurrency() {
        return usesGoldCurrency;
    }

    public static long getGoldCurrency() {
        return goldCurrency;
    }

    public static int getProgressionLevel() {
        return progressionLevel;
    }

    public static int getAegisAscensionRank() {
        return aegisAscensionRank;
    }

    public static long getAegisAscensionExperience() {
        return aegisAscensionExperience;
    }

    public static long getAegisAscensionExperienceToNextRank() {
        return aegisAscensionExperienceToNextRank;
    }

    public static int getAegisAscensionMaximumRank() {
        return aegisAscensionMaximumRank;
    }

    public static boolean ownsAegis(Aegis aegis) {
        return CHOSEN_AEGISES.contains(aegis);
    }

    public static Set<Aegis> getChosenAegises() {
        return Set.copyOf(CHOSEN_AEGISES);
    }

    public static List<SyncDevourDataPacket.Entry> getDevouredAttributes() {
        return devouredAttributes;
    }

    public static void setDevouredAttributes(
            List<SyncDevourDataPacket.Entry> syncedDevouredAttributes,
            boolean syncedAllowAgainAfterDiscard) {
        devouredAttributes = List.copyOf(syncedDevouredAttributes);
        devourAllowAgainAfterDiscard = syncedAllowAgainAfterDiscard;
    }

    public static boolean isDevourAllowedAgainAfterDiscard() {
        return devourAllowAgainAfterDiscard;
    }

    public static boolean isAegisEnabled(Aegis aegis) {
        return CHOSEN_AEGISES.contains(aegis)
                && (!aegis.manuallyToggleable()
                || !DISABLED_MANUAL_AEGISES.contains(aegis.id()));
    }

    public static void setAegisEnabled(Aegis aegis, boolean enabled) {
        if (enabled) {
            DISABLED_MANUAL_AEGISES.remove(aegis.id());
        } else {
            DISABLED_MANUAL_AEGISES.add(aegis.id());
        }
    }

    public static boolean hasAvailableAegisChoice() {
        boolean initial = CHOSEN_AEGISES.isEmpty();
        return Aegis.values().stream().anyMatch(aegis ->
                !CHOSEN_AEGISES.contains(aegis) && aegis.canOffer(initial)
        );
    }

    public static boolean isAegisOfferSessionActive() {
        return aegisOfferSessionActive;
    }

    public static void beginAegisOfferSession() {
        aegisOfferSessionActive = true;
    }

    public static void endAegisOfferSession() {
        aegisOfferSessionActive = false;
    }

    public static int getUsedTalentSlots() {
        return PERK_RANKS.size();
    }

    public static boolean isTalentHidden(String perkId) {
        return HIDDEN_TALENT_IDS.contains(perkId);
    }

    public static boolean isTalentHidden(Perk perk) {
        return isTalentHidden(perk.id());
    }

    public static boolean isTalentSlotCapReached() {
        return getUsedTalentSlots() >= maxTalentSlots;
    }

    public static int getRank(Perk perk) {
        return PERK_RANKS.getOrDefault(perk, 0);
    }

    public static boolean owns(String perkId) {
        return !isTalentHidden(perkId)
                && Perk.byId(perkId).map(perk -> getRank(perk) > 0).orElse(false);
    }

    public static boolean isTalentEnabled(Perk perk) {
        return ENABLED_MANUAL_TALENTS.contains(perk.id());
    }

    public static void setTalentEnabled(String perkId, boolean enabled) {
        if (enabled) {
            ENABLED_MANUAL_TALENTS.add(perkId);
        } else {
            ENABLED_MANUAL_TALENTS.remove(perkId);
        }
    }

    public static List<Perk> getOwnedPerks() {
        return Perk.values().stream()
                .filter(perk -> !isTalentHidden(perk))
                .filter(perk -> getRank(perk) > 0)
                .toList();
    }

    public static Map<String, Double> getDisplayStats() {
        return Map.copyOf(DISPLAY_STATS);
    }

    public static double getDisplayStat(String key) {
        return DISPLAY_STATS.getOrDefault(key, 0.0D);
    }

    /**
     * Applies synchronized display values according to how much the sender carried.
     *
     * <p>{@code ESSENTIAL} is a fragment sent with routine progression syncs, so it is
     * merged over what is already held; replacing the map with it would blank the
     * Collection screen every time the player bought something. {@code VALUES} and
     * {@code FULL} are complete sets and replace the values outright.</p>
     *
     * <p>The per-source records are kept across a {@code VALUES} sync: only the Custom
     * Stats tab asks for them, and a sync from another tab must not blank its source
     * panel. A {@code FULL} sync is authoritative for them and replaces the set, so a
     * record that decayed to zero on the server does disappear.</p>
     */
    public static void setDisplayStats(Map<String, Double> displayStats,
                                       DisplayStatScope scope) {
        if (!scope.isComplete()) {
            DISPLAY_STATS.putAll(displayStats);
            return;
        }
        Map<String, Double> carriedRecords = null;
        if (!scope.includesAttribution()) {
            carriedRecords = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : DISPLAY_STATS.entrySet()) {
                if (isAttributionRecord(entry.getKey())) {
                    carriedRecords.put(entry.getKey(), entry.getValue());
                }
            }
        }
        DISPLAY_STATS.clear();
        DISPLAY_STATS.putAll(displayStats);
        if (carriedRecords != null) {
            DISPLAY_STATS.putAll(carriedRecords);
        }
    }

    /** Whether a synced display key is a per-source stat record rather than a value. */
    private static boolean isAttributionRecord(String key) {
        return key.startsWith(StatAttribution.SYNCED_PREFIX);
    }

    public static boolean hasDisplayStat(String key) {
        return DISPLAY_STATS.containsKey(key);
    }

    public static boolean isSoulLinkActive(SoulLink soulLink) {
        return !isSoulLinkDisabled(soulLink)
                && soulLink.isActive(ClientPerkState::owns);
    }

    public static boolean isSoulLinkDisabled(SoulLink soulLink) {
        return !soulLink.enabled() || soulLink.requirements().stream()
                .anyMatch(ClientPerkState::isTalentHidden);
    }

    public static boolean hasAvailableChoice() {
        for (Perk perk : Perk.values()) {
            if (isTalentHidden(perk)) {
                continue;
            }
            int currentRank = getRank(perk);
            if (perk.isUnlockedForPool(
                    ClientPerkState::owns,
                    soulLinkId -> Perk.soulLinkById(soulLinkId)
                            .map(ClientPerkState::isSoulLinkActive)
                            .orElse(false)
            ) && perk.canAcquire(currentRank)
                    && (currentRank > 0 || !isTalentSlotCapReached())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOfferSessionActive() {
        return offerSessionActive;
    }

    public static boolean isLiveCustomStatsRefreshAllowed() {
        return liveCustomStatsRefreshAllowed;
    }

    public static UUID getSharedFortunePartnerId() {
        return sharedFortunePartnerId;
    }

    public static String getSharedFortunePartnerName() {
        return sharedFortunePartnerName;
    }

    public static int getSharedFortuneRebindCooldownSeconds() {
        long remainingMillis = Math.max(
                0L,
                sharedFortuneRebindAvailableAtMillis - System.currentTimeMillis()
        );
        return (int) Math.min(
                Integer.MAX_VALUE,
                (remainingMillis + 999L) / 1_000L
        );
    }

    public static void beginOfferSession() {
        offerSessionActive = true;
    }

    public static void endOfferSession() {
        offerSessionActive = false;
    }

    public static void update(int charges, int syncedPendingBreakthroughTriggers,
                              int syncedPerkRefreshCharges,
                              int syncedMaxTalentSlots,
                              int syncedSkillEnhancementCharges,
                              int syncedSkillEnhancementChargesPerPerkExchange,
                              int syncedSkillEnhancementRefreshExperienceCost,
                              boolean syncedSkillEnhancementRefreshFree,
                              int syncedAegisSelectionCharges,
                              int syncedAegisRefreshCharges,
                              boolean syncedLiveCustomStatsRefreshAllowed,
                              boolean syncedUsesMinecraftDefaultLevel,
                              boolean syncedUsesGoldCurrency,
                              long syncedGoldCurrency,
                              int syncedProgressionLevel,
                              int syncedAegisAscensionRank,
                              long syncedAegisAscensionExperience,
                              long syncedAegisAscensionExperienceToNextRank,
                              int syncedAegisAscensionMaximumRank,
                              UUID syncedSharedFortunePartnerId,
                              String syncedSharedFortunePartnerName,
                              int syncedSharedFortuneRebindCooldownSeconds,
                              Set<String> hiddenTalentIds,
                              Map<Perk, Integer> perkRanks,
                              Set<String> enabledManualTalents,
                              Map<String, Double> displayStats,
            DisplayStatScope displayStatScope,
                              Map<SkillEnhancement, Integer> skillEnhancementRanks,
                              List<SkillEnhancement> syncedSkillEnhancementOffers,
                              SkillEnhancement syncedPrimarySkillEnhancement,
                              boolean syncedPrimarySkillEnhancementChosen,
                              Set<Aegis> chosenAegises,
                              Set<String> disabledManualAegises) {
        selectionCharges = Math.max(0, charges);
        pendingBreakthroughTriggers = Math.max(0, syncedPendingBreakthroughTriggers);
        perkRefreshCharges = Math.max(0, syncedPerkRefreshCharges);
        maxTalentSlots = Math.max(1, syncedMaxTalentSlots);
        skillEnhancementCharges = Math.max(0, syncedSkillEnhancementCharges);
        skillEnhancementChargesPerPerkExchange = Math.max(
                1,
                syncedSkillEnhancementChargesPerPerkExchange
        );
        skillEnhancementRefreshExperienceCost = Math.max(
                0,
                syncedSkillEnhancementRefreshExperienceCost
        );
        skillEnhancementRefreshFree = syncedSkillEnhancementRefreshFree;
        aegisSelectionCharges = Math.max(0, syncedAegisSelectionCharges);
        aegisRefreshCharges = Math.max(0, syncedAegisRefreshCharges);
        liveCustomStatsRefreshAllowed = syncedLiveCustomStatsRefreshAllowed;
        usesMinecraftDefaultLevel = syncedUsesMinecraftDefaultLevel;
        usesGoldCurrency = syncedUsesGoldCurrency;
        goldCurrency = Math.max(0L, syncedGoldCurrency);
        progressionLevel = Math.max(0, syncedProgressionLevel);
        aegisAscensionRank = Math.max(1, syncedAegisAscensionRank);
        aegisAscensionExperience = Math.max(0L, syncedAegisAscensionExperience);
        aegisAscensionExperienceToNextRank = Math.max(
                0L, syncedAegisAscensionExperienceToNextRank);
        aegisAscensionMaximumRank = Math.max(1, syncedAegisAscensionMaximumRank);
        sharedFortunePartnerId = syncedSharedFortunePartnerId;
        sharedFortunePartnerName = syncedSharedFortunePartnerId == null
                ? ""
                : syncedSharedFortunePartnerName;
        sharedFortuneRebindAvailableAtMillis = System.currentTimeMillis()
                + Math.max(0, syncedSharedFortuneRebindCooldownSeconds) * 1_000L;
        HIDDEN_TALENT_IDS.clear();
        HIDDEN_TALENT_IDS.addAll(hiddenTalentIds);
        PERK_RANKS.clear();
        PERK_RANKS.putAll(perkRanks);
        ENABLED_MANUAL_TALENTS.clear();
        ENABLED_MANUAL_TALENTS.addAll(enabledManualTalents);
        setDisplayStats(displayStats, displayStatScope);
        SKILL_ENHANCEMENT_RANKS.clear();
        SKILL_ENHANCEMENT_RANKS.putAll(skillEnhancementRanks);
        skillEnhancementOffers = List.copyOf(syncedSkillEnhancementOffers);
        primarySkillEnhancement = syncedPrimarySkillEnhancement;
        primarySkillEnhancementChosen = syncedPrimarySkillEnhancementChosen;
        CHOSEN_AEGISES.clear();
        CHOSEN_AEGISES.addAll(chosenAegises);
        if (chosenAegises.stream().noneMatch(aegis ->
                aegis.id().equals(com.whatever.aegis_ascension.aegis.AegisConstants.DEVOUR))) {
            devouredAttributes = List.of();
        }
        DISABLED_MANUAL_AEGISES.clear();
        DISABLED_MANUAL_AEGISES.addAll(disabledManualAegises);
    }

    public static void clear() {
        ClientRefreshRequestLimiter.reset();
        selectionCharges = 0;
        pendingBreakthroughTriggers = 0;
        perkRefreshCharges = 0;
        maxTalentSlots = 33;
        offerSessionActive = false;
        liveCustomStatsRefreshAllowed = false;
        sharedFortunePartnerId = null;
        sharedFortunePartnerName = "";
        sharedFortuneRebindAvailableAtMillis = 0L;
        skillEnhancementCharges = 0;
        skillEnhancementChargesPerPerkExchange = 2;
        skillEnhancementRefreshExperienceCost = 100;
        skillEnhancementRefreshFree = false;
        aegisSelectionCharges = 0;
        aegisRefreshCharges = 0;
        aegisOfferSessionActive = false;
        usesMinecraftDefaultLevel = true;
        usesGoldCurrency = false;
        goldCurrency = 0L;
        progressionLevel = 0;
        aegisAscensionRank = 1;
        aegisAscensionExperience = 0L;
        aegisAscensionExperienceToNextRank = 0L;
        aegisAscensionMaximumRank = 1000;
        HIDDEN_TALENT_IDS.clear();
        PERK_RANKS.clear();
        ENABLED_MANUAL_TALENTS.clear();
        DISPLAY_STATS.clear();
        SKILL_ENHANCEMENT_RANKS.clear();
        skillEnhancementOffers = List.of();
        primarySkillEnhancement = SkillEnhancement.defaultPrimary();
        primarySkillEnhancementChosen = false;
        CHOSEN_AEGISES.clear();
        DISABLED_MANUAL_AEGISES.clear();
        devouredAttributes = List.of();
        devourAllowAgainAfterDiscard = false;
    }
}
