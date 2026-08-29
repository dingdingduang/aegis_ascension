package com.whatever.aegis_ascension.capability;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.aegis.AuthorityAegis;
import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.perk.talents.SharedFortune;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.mechanic.BreakthroughReleasePolicy;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.shop.ShopState;
import com.whatever.aegis_ascension.storage.PlayerStorage;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerPerkData {
    private enum TalentAcquisitionContext {
        STANDARD(true),
        RANDOM_REWARD(false);

        private final boolean triggerRewardChains;

        TalentAcquisitionContext(boolean triggerRewardChains) {
            this.triggerRewardChains = triggerRewardChains;
        }
    }

    private static final String CHARGES_TAG = "SelectionCharges";
    private static final String HIGHEST_PERK_LEVEL_TAG = "HighestPerkLevel";
    private static final String STARTING_PERK_CHARGE_AWARDED_TAG =
            "StartingPerkChargeAwarded";
    private static final String EXPERIENCE_PERK_CHARGES_AWARDED_TAG =
            "ExperiencePerkChargesAwarded";
    private static final String EXPERIENCE_BREAKTHROUGHS_TRIGGERED_TAG =
            "ExperienceBreakthroughsTriggered";
    private static final String PENDING_BREAKTHROUGH_TRIGGERS_TAG =
            "PendingBreakthroughTriggers";
    private static final String CHOSEN_TAG = "ChosenPerks";
    private static final String PENDING_OFFERS_TAG = "PendingOffers";
    private static final String PERK_REFRESH_CHARGES_TAG = "PerkRefreshCharges";
    private static final String CUSTOM_STATS_TAG = "CustomStats";
    private static final String ENABLED_TALENTS_TAG = "EnabledManualTalents";
    private static final String SHARED_FORTUNE_PARTNER_TAG = "SharedFortunePartner";
    private static final String SHARED_FORTUNE_PARTNER_NAME_TAG =
            "SharedFortunePartnerName";
    private static final String SHARED_FORTUNE_REBIND_AVAILABLE_AT_TAG =
            "SharedFortuneRebindAvailableAt";
    private static final String SKILL_ENHANCEMENT_CHARGES_TAG = "SkillEnhancementCharges";
    private static final String HIGHEST_SKILL_ENHANCEMENT_LEVEL_TAG =
            "HighestSkillEnhancementLevel";
    private static final String EXPERIENCE_SKILL_ENHANCEMENT_CHARGES_AWARDED_TAG =
            "ExperienceSkillEnhancementChargesAwarded";
    private static final String HOLY_BLESSING_SKILL_ENHANCEMENT_START_LEVEL_TAG =
            "HolyBlessingSkillEnhancementStartLevel";
    private static final String SKILL_ENHANCEMENT_RANKS_TAG = "SkillEnhancementRanks";
    private static final String PENDING_SKILL_ENHANCEMENT_OFFERS_TAG =
            "PendingSkillEnhancementOffers";
    private static final String PRIMARY_SKILL_ENHANCEMENT_TAG =
            "PrimarySkillEnhancement";
    private static final String PRIMARY_SKILL_ENHANCEMENT_CHOSEN_TAG =
            "PrimarySkillEnhancementChosen";
    private static final String AEGIS_CHARGES_TAG = "AegisSelectionCharges";
    private static final String AEGIS_CHARGES_AWARDED_TAG = "AegisChargesAwarded";
    private static final String HIGHEST_AEGIS_LEVEL_TAG = "HighestAegisLevel";
    private static final String CHOSEN_AEGISES_TAG = "ChosenAegises";
    private static final String PENDING_AEGIS_OFFERS_TAG = "PendingAegisOffers";
    private static final String DISABLED_AEGISES_TAG = "DisabledManualAegises";
    private static final String AEGIS_REFRESH_CHARGES_TAG = "AegisRefreshCharges";
    private static final String DEVOURED_ITEMS_TAG = "DevouredItems";
    private static final String DEVOURED_ATTRIBUTES_TAG = "DevouredAttributes";
    private static final String SHOP_TAG = "DailyShop";
    private static final String STORAGE_TAG = "VirtualStorage";
    private static final String VIRTUAL_ITEM_USES_TAG = "VirtualItemUses";

    private int selectionCharges;
    private int highestPerkLevel;
    private boolean startingPerkChargeAwarded;
    private int experiencePerkChargesAwarded;
    private int experienceBreakthroughsTriggered;
    private int pendingBreakthroughTriggers;
    private final Map<Perk, Integer> perkRanks = new LinkedHashMap<>();
    private final List<Perk> pendingOffers = new ArrayList<>();
    private int perkRefreshCharges;
    private final Map<String, Double> customStats = new LinkedHashMap<>();
    private final Set<String> enabledManualTalents = new LinkedHashSet<>();
    private UUID sharedFortunePartnerId;
    private String sharedFortunePartnerName = "";
    private long sharedFortuneRebindAvailableAtMillis;
    private int skillEnhancementCharges;
    private int highestSkillEnhancementLevel;
    private int experienceSkillEnhancementChargesAwarded;
    private int holyBlessingSkillEnhancementStartLevel = -1;
    private final Map<SkillEnhancement, Integer> skillEnhancementRanks =
            new LinkedHashMap<>();
    private final List<SkillEnhancement> pendingSkillEnhancementOffers = new ArrayList<>();
    private SkillEnhancement selectedPrimarySkillEnhancement =
            SkillEnhancement.defaultPrimary();
    private boolean primarySkillEnhancementChosen;
    private int aegisSelectionCharges;
    private int aegisChargesAwarded;
    private int highestAegisLevel;
    private final Set<Aegis> chosenAegises = new LinkedHashSet<>();
    private final List<Aegis> pendingAegisOffers = new ArrayList<>();
    private final Set<String> disabledManualAegises = new LinkedHashSet<>();
    private int aegisRefreshCharges;
    private final ShopState shopState = new ShopState();
    private final PlayerStorage storage = new PlayerStorage();

    {
        // Storage's type cap is the config value plus whatever Storage Expansion books this
        // player has consumed. Wired as a supplier because the count lives here, not there.
        storage.setBonusTypeSlotSupplier(
                () -> VirtualItems.bonusInt(this, VirtualItems.Effect.STORAGE_SLOTS));
    }
    private final Map<String, Integer> virtualItemUses = new LinkedHashMap<>();
    private final Set<String> devouredItems = new LinkedHashSet<>();
    private final List<DevourAegis.InheritedAttribute> devouredAttributes = new ArrayList<>();

    public int getSelectionCharges() {
        return selectionCharges;
    }

    public int getPerkRefreshCharges() {
        return perkRefreshCharges;
    }

    public int getPendingBreakthroughTriggers() {
        return pendingBreakthroughTriggers;
    }

    public void addSelectionCharges(int amount) {
        if (amount > 0) {
            long chargesToAdd = amount;
            if (isAegisEnabled(AegisConstants.FORTUNE)) {
                Aegis fortune = Aegis.byId(AegisConstants.FORTUNE).orElseThrow();
                chargesToAdd += Math.max(0L, Math.round(
                        amount * fortune.stat(AegisConstants.FREE_PERK_CHOICES)
                ));
            }
            selectionCharges = (int) Math.min(
                    Integer.MAX_VALUE,
                    (long) selectionCharges + chargesToAdd
            );
        }
    }

    /** Grants the Perk refresh rewards that occur once per actual Breakthrough. */
    public void grantBreakthroughPerkRefreshCharges() {
        double refreshCharges = 0.0D;
        if (isAegisEnabled(AegisConstants.FORTUNE)) {
            refreshCharges += Aegis.byId(AegisConstants.FORTUNE)
                    .orElseThrow()
                    .stat(AegisConstants.PERK_REFRESH_CHARGE_PER_CHARGE);
        }
        if (isAegisEnabled(AegisConstants.FROST_MOON)) {
            refreshCharges += Aegis.byId(AegisConstants.FROST_MOON)
                    .orElseThrow()
                    .stat(AegisConstants.PERK_REFRESH_CHARGE_PER_CHARGE);
        }
        if (owns(R_WHITE_STAR_OBSIDIAN)) {
            Perk whiteStarObsidian = Perk.byId(R_WHITE_STAR_OBSIDIAN)
                    .orElseThrow();
            refreshCharges += whiteStarObsidian.stat(
                    PERK_REFRESH_CHARGE_PER_CHARGE
            ) * getRank(whiteStarObsidian);
        }
        addPerkRefreshCharges((int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, Math.round(refreshCharges))
        ));
    }

    public void addPerkRefreshCharges(int amount) {
        if (amount > 0) {
            perkRefreshCharges = (int) Math.min(
                    Integer.MAX_VALUE,
                    (long) perkRefreshCharges + amount
            );
        }
    }

    public void resetPerkRefreshCharges() {
        perkRefreshCharges = 0;
    }

    public int getSkillEnhancementCharges() {
        return skillEnhancementCharges;
    }

    public void addSkillEnhancementCharges(int amount) {
        if (amount > 0) {
            skillEnhancementCharges = (int) Math.min(
                    Integer.MAX_VALUE,
                    (long) skillEnhancementCharges + amount
            );
        }
    }

    public Map<SkillEnhancement, Integer> getSkillEnhancementRanks() {
        return Collections.unmodifiableMap(skillEnhancementRanks);
    }

    public int getSkillEnhancementRank(SkillEnhancement enhancement) {
        return skillEnhancementRanks.getOrDefault(enhancement, 0);
    }

    public SkillEnhancement getPrimarySkillEnhancement() {
        return selectedPrimarySkillEnhancement;
    }

    public boolean hasChosenPrimarySkillEnhancement() {
        return primarySkillEnhancementChosen;
    }

    /**
     * Allows one permanent initial choice. Matter-to-Magic Conversion unlocks
     * later changes without spending a skill-enhancement charge.
     */
    public boolean setPrimarySkillEnhancement(SkillEnhancement enhancement) {
        if (!SkillEnhancement.values().contains(enhancement)
                || (primarySkillEnhancementChosen
                && !owns(R_MATTER_TO_MAGIC_CONVERSION))) {
            return false;
        }
        selectedPrimarySkillEnhancement = enhancement;
        primarySkillEnhancementChosen = true;
        return true;
    }

    public int getAegisSelectionCharges() {
        return aegisSelectionCharges;
    }

    public int getAegisRefreshCharges() {
        return aegisRefreshCharges;
    }

    public int getAegisChargesAwarded() {
        return aegisChargesAwarded;
    }

    public Set<Aegis> getChosenAegises() {
        return Collections.unmodifiableSet(chosenAegises);
    }

    public boolean hasAvailableRandomAegis() {
        return Aegis.values().stream().anyMatch(aegis ->
                !chosenAegises.contains(aegis) && aegis.canOffer(false)
        );
    }

    /** Grants one random enabled, unowned Aegis without spending a selection charge. */
    public Optional<Aegis> grantRandomUnownedAegis(ServerPlayer player) {
        List<Aegis> pool = Aegis.values().stream()
                .filter(aegis -> !chosenAegises.contains(aegis))
                .filter(aegis -> aegis.canOffer(false))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        boolean alreadyOwnedAnAegis = !chosenAegises.isEmpty();
        Aegis granted = pool.get(player.getRandom().nextInt(pool.size()));
        chosenAegises.add(granted);
        pendingAegisOffers.remove(granted);
        if (granted.id().equals(AegisConstants.LUCKY) && alreadyOwnedAnAegis) {
            grantRandomInactiveSoulLinkSet(player);
        }
        if (granted.id().equals(AegisConstants.MIRACLE)) {
            grantMiracleAegises(granted, player);
        }
        applyChosenPerks(player);
        return Optional.of(granted);
    }

    public boolean hasAegis(String aegisId) {
        return Aegis.byId(aegisId).map(chosenAegises::contains).orElse(false);
    }

    public boolean isAegisEnabled(String aegisId) {
        return Aegis.byId(aegisId).map(this::isAegisEnabled).orElse(false);
    }

    public boolean isAegisEnabled(Aegis aegis) {
        return chosenAegises.contains(aegis)
                && (!aegis.manuallyToggleable()
                || !disabledManualAegises.contains(aegis.id()));
    }

    public Set<String> getDisabledManualAegises() {
        return Collections.unmodifiableSet(disabledManualAegises);
    }

    public boolean hasDevouredItem(String itemId) {
        return devouredItems.contains(itemId);
    }

    public List<DevourAegis.InheritedAttribute> getDevouredAttributes() {
        return List.copyOf(devouredAttributes);
    }

    /** This player's daily shop stock, reroll counter, and rollover tracking. */
    public ShopState getShopState() {
        return shopState;
    }

    /** This player's virtual item bank, where shop purchases land. */
    public PlayerStorage getStorage() {
        return storage;
    }

    /** How many times this player has consumed a given virtual book, for its lifetime cap. */
    public int getVirtualItemUses(String virtualId) {
        return Math.max(0, virtualItemUses.getOrDefault(virtualId, 0));
    }

    /** Records one consumption. The cap itself is enforced by the caller against the config. */
    public void addVirtualItemUse(String virtualId, int amount) {
        if (virtualId == null || virtualId.isEmpty() || amount <= 0) {
            return;
        }
        virtualItemUses.merge(virtualId, amount, Integer::sum);
    }

    public Map<String, Integer> getVirtualItemUses() {
        return Map.copyOf(virtualItemUses);
    }

    /** Records one item ID and its immutable attribute snapshot exactly once. */
    public boolean recordDevouredItem(
            String itemId,
            List<DevourAegis.InheritedAttribute> inheritedAttributes) {
        if (itemId == null || itemId.isBlank() || hasDevouredItem(itemId)) {
            return false;
        }
        devouredItems.add(itemId);
        devouredAttributes.addAll(inheritedAttributes);
        return true;
    }

    /** Removes an item's inherited bonuses and applies the configured history policy. */
    public boolean discardDevouredItemAttributes(String itemId) {
        if (!devouredItems.contains(itemId)) {
            return false;
        }
        boolean removed = devouredAttributes.removeIf(attribute ->
                attribute.itemId().equals(itemId));
        if (removed && PlatformServices.config().allowDevourAgainAfterDiscard()) {
            devouredItems.remove(itemId);
        }
        return removed;
    }

    /**
     * Clears every devoured item and its inherited bonuses.
     *
     * <p>Unlike {@link #discardDevouredItemAttributes}, this ignores
     * {@code DEVOUR_ALLOW_AGAIN_AFTER_DISCARD} and always forgets the item ids too: the
     * point of a reset is to hand back a clean slate, and keeping the ids would leave the
     * player unable to re-devour the very items they just paid to clear.</p>
     *
     * @return false when there was nothing to reset, so callers can avoid consuming an item.
     */
    public boolean resetDevouredItems() {
        if (devouredItems.isEmpty() && devouredAttributes.isEmpty()) {
            return false;
        }
        devouredItems.clear();
        devouredAttributes.clear();
        return true;
    }

    /** Returns true when the requested state belongs to an owned toggleable Aegis. */
    public boolean setAegisEnabled(Aegis aegis, boolean enabled) {
        if (!aegis.manuallyToggleable() || !chosenAegises.contains(aegis)) {
            return false;
        }
        if (enabled) {
            disabledManualAegises.remove(aegis.id());
        } else {
            disabledManualAegises.add(aegis.id());
        }
        return true;
    }

    public Map<Perk, Integer> getPerkRanks() {
        Map<Perk, Integer> visibleRanks = new LinkedHashMap<>();
        perkRanks.forEach((perk, rank) -> {
            if (!PlatformServices.config().isTalentHidden(perk.id())) {
                visibleRanks.put(perk, rank);
            }
        });
        return Collections.unmodifiableMap(visibleRanks);
    }

    public int getRank(Perk perk) {
        return perkRanks.getOrDefault(perk, 0);
    }

    public int getRank(String perkId) {
        return Perk.byId(perkId).map(this::getRank).orElse(0);
    }

    public boolean owns(String perkId) {
        return !PlatformServices.config().isTalentHidden(perkId) && getRank(perkId) > 0;
    }

    public Set<String> getEnabledManualTalents() {
        Set<String> visibleEnabledTalents = new LinkedHashSet<>();
        enabledManualTalents.stream()
                .filter(perkId -> !PlatformServices.config().isTalentHidden(perkId))
                .forEach(visibleEnabledTalents::add);
        return Collections.unmodifiableSet(visibleEnabledTalents);
    }

    public boolean isTalentEnabled(String perkId) {
        return !PlatformServices.config().isTalentHidden(perkId)
                && enabledManualTalents.contains(perkId);
    }

    /** Returns true when the requested state was valid, even if it was already set. */
    public boolean setTalentEnabled(Perk perk, boolean enabled) {
        if (PlatformServices.config().isTalentHidden(perk.id())
                || !perk.manuallyToggleable() || getRank(perk) <= 0) {
            return false;
        }
        if (enabled) {
            enabledManualTalents.add(perk.id());
        } else {
            enabledManualTalents.remove(perk.id());
        }
        return true;
    }

    public Optional<UUID> getSharedFortunePartnerId() {
        return Optional.ofNullable(sharedFortunePartnerId);
    }

    public String getSharedFortunePartnerName() {
        return sharedFortunePartnerName;
    }

    public int getSharedFortuneRebindCooldownSeconds() {
        long remainingMillis = Math.max(
                0L,
                sharedFortuneRebindAvailableAtMillis - System.currentTimeMillis()
        );
        return (int) Math.min(
                Integer.MAX_VALUE,
                (remainingMillis + 999L) / 1_000L
        );
    }

    public void setSharedFortunePartner(UUID partnerId, String partnerName,
                                        long rebindAvailableAtMillis) {
        sharedFortunePartnerId = partnerId;
        sharedFortunePartnerName = partnerName == null ? "" : partnerName;
        sharedFortuneRebindAvailableAtMillis = Math.max(0L, rebindAvailableAtMillis);
    }

    public void clearSharedFortunePartner() {
        sharedFortunePartnerId = null;
        sharedFortunePartnerName = "";
    }

    public int getUniqueTalentCount() {
        return getPerkRanks().size();
    }

    public boolean isTalentSlotCapReached() {
        return getUniqueTalentCount() >= getMaxTalentSlots();
    }

    public int getMaxTalentSlots() {
        long bonusSlots = Math.max(0L, Mth.floor(getCustomStat(ADDITIONAL_TALENT_SLOTS)));
        long configuredSlots = (long) PlatformServices.config().baseMaxTalentSlots()
                + bonusSlots;
        for (Map.Entry<Perk, Integer> entry : getPerkRanks().entrySet()) {
            configuredSlots += Math.max(
                    0L,
                    (long) Mth.floor(entry.getKey().stat(EXTRA_TALENT_SLOTS)
                            * entry.getValue())
            );
        }
        if (isAegisEnabled(AegisConstants.AUTHORITY)) {
            long authorityBonus = Math.max(0L, Aegis.byId(AegisConstants.AUTHORITY)
                    .map(aegis -> (long) Mth.floor(
                            aegis.stat(AegisConstants.EXTRA_TALENT_SLOTS)
                    ))
                    .orElse(0L));
            configuredSlots += authorityBonus;
        }
        return (int) Math.min(Integer.MAX_VALUE, configuredSlots);
    }

    /** Hook for future talents or systems that permanently grant talent slots. */
    public void addTalentSlots(int amount) {
        if (amount > 0) {
            addCustomStat(ADDITIONAL_TALENT_SLOTS, amount);
        }
    }

    public boolean canAcquireTalent(Perk perk) {
        int currentRank = getRank(perk);
        boolean expandsTalentSlots = perk.stat(EXTRA_TALENT_SLOTS) > 0.0D;
        return !PlatformServices.config().isTalentHidden(perk.id())
                && (!perk.id().equals(R_SUSPENSION_OF_DISBELIEF)
                || getCustomStat(SUSPENSION_OF_DISBELIEF_USED) <= 0.0D
                && hasSuspensionRewardCandidate())
                && perk.isUnlockedForPool(this)
                && perk.canAcquire(currentRank)
                && (currentRank > 0
                || getUniqueTalentCount() < getMaxTalentSlots()
                || expandsTalentSlots);
    }

    private boolean hasSuspensionRewardCandidate() {
        return Perk.values().stream()
                .filter(candidate -> !candidate.id().equals(R_SUSPENSION_OF_DISBELIEF))
                .filter(Perk::randomRewardEligible)
                .anyMatch(this::canAcquireTalent);
    }

    public boolean hasAcquirableTalent() {
        return Perk.values().stream().anyMatch(this::canAcquireTalent);
    }

    /**
     * True while an unowned slot-expanding Aegis can still be selected now or from a
     * future configured Aegis milestone. Slot-expanding talents are allowed through
     * {@link #canAcquireTalent} even at the current cap, so they are covered by
     * {@link #hasAcquirableTalent()} before this method is consulted.
     */
    public boolean canStillObtainExtraTalentSlots() {
        boolean hasAegisOpportunity = aegisSelectionCharges > 0
                || aegisChargesAwarded < PlatformServices.config().maximumAegisCharges();
        if (!hasAegisOpportunity) {
            return false;
        }
        return Aegis.values().stream().anyMatch(aegis ->
                !chosenAegises.contains(aegis)
                        && aegis.canOffer(false)
                        && aegis.stat(AegisConstants.EXTRA_TALENT_SLOTS) > 0.0D
        );
    }

    public boolean canAcquireRandomTalent(Perk perk) {
        return !perk.id().equals(R_SUSPENSION_OF_DISBELIEF)
                && perk.randomRewardEligible()
                && canAcquireTalent(perk);
    }

    public boolean hasAvailableTalentOfTier(Perk.Tier tier) {
        return Perk.values().stream().anyMatch(perk ->
                perk.tier() == tier && canAcquireRandomTalent(perk)
        );
    }

    /** Grants one random available talent of the requested tier without a charge. */
    public Optional<Perk> grantRandomTalentOfTier(ServerPlayer player, Perk.Tier tier) {
        List<Perk> pool = Perk.values().stream()
                .filter(perk -> perk.tier() == tier)
                .filter(this::canAcquireRandomTalent)
                .toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Perk granted = pool.get(player.getRandom().nextInt(pool.size()));
        acquireTalent(granted, player, TalentAcquisitionContext.RANDOM_REWARD);
        return Optional.of(granted);
    }

    /** Grants a Shared Fortune copy without treating it as another manual selection. */
    public boolean grantSharedFortuneCopy(ServerPlayer player, Perk perk) {
        if (!canAcquireTalent(perk)) {
            return false;
        }
        acquireTalent(perk, player, TalentAcquisitionContext.STANDARD);
        applyChosenPerks(player);
        releaseRemainingBreakthroughsIfNeeded(player);
        return true;
    }

    public List<SoulLink> getActiveSoulLinks() {
        return Perk.soulLinks().stream().filter(link -> link.isActive(this)).toList();
    }

    public boolean hasActiveSoulLink(String soulLinkId) {
        return Perk.soulLinks().stream()
                .filter(link -> link.id().equals(soulLinkId))
                .anyMatch(link -> link.isActive(this));
    }

    public double getCustomStat(String key) {
        return customStats.getOrDefault(key, 0.0D);
    }

    public Map<String, Double> getCustomStats() {
        return Collections.unmodifiableMap(customStats);
    }

    public void setCustomStat(String key, double value) {
        if (Math.abs(value) < 1.0E-9D) {
            customStats.remove(key);
        } else {
            customStats.put(key, value);
        }
    }

    public double addCustomStat(String key, double amount) {
        double updated = getCustomStat(key) + amount;
        setCustomStat(key, updated);
        return updated;
    }

    public List<Perk> getPendingOffers() {
        pendingOffers.removeIf(perk -> !canAcquireTalent(perk));
        int maximumOptions = PlatformServices.config().maximumPerkOptions();
        if (pendingOffers.size() > maximumOptions) {
            pendingOffers.subList(maximumOptions, pendingOffers.size()).clear();
        }
        return List.copyOf(pendingOffers);
    }

    /**
     * Awards the free starting charge and any newly crossed experience milestones.
     * XP-derived Perk charges and Breakthrough triggers have independent lifetime caps.
     */
    public PerkMilestoneAwards awardMilestonesForLevel(int experienceLevel) {
        int currentLevel = Math.max(0, experienceLevel);
        int chargesGranted = 0;
        int breakthroughsTriggered = 0;
        if (!startingPerkChargeAwarded) {
            startingPerkChargeAwarded = true;
            // The free starting charge keeps its existing Breakthrough behavior, but is
            // not an experience reward and therefore does not consume either cap.
            chargesGranted++;
            breakthroughsTriggered++;
        }

        if (currentLevel > highestPerkLevel) {
            int interval = PlatformServices.config().perkLevelsPerCharge();
            int crossedMilestones = Math.max(
                    0,
                    currentLevel / interval - highestPerkLevel / interval
            );
            highestPerkLevel = currentLevel;

            int remainingChargeAwards = Math.max(
                    0,
                    PlatformServices.config().maximumPerkChargesFromExperience()
                            - experiencePerkChargesAwarded
            );
            int experienceChargesGranted = Math.min(
                    crossedMilestones,
                    remainingChargeAwards
            );
            experiencePerkChargesAwarded += experienceChargesGranted;
            chargesGranted += experienceChargesGranted;

            int remainingBreakthroughTriggers = Math.max(
                    0,
                    PlatformServices.config().maximumBreakthroughsFromExperience()
                            - experienceBreakthroughsTriggered
            );
            int experienceBreakthroughs = Math.min(
                    crossedMilestones,
                    remainingBreakthroughTriggers
            );
            experienceBreakthroughsTriggered += experienceBreakthroughs;
            breakthroughsTriggered += experienceBreakthroughs;
        }
        if (chargesGranted > 0) {
            addSelectionCharges(chargesGranted);
        }
        int immediateBreakthroughs;
        if (PlatformServices.config().triggerBreakthroughOnPerkSelection()) {
            addPendingBreakthroughTriggers(breakthroughsTriggered);
            immediateBreakthroughs = BreakthroughReleasePolicy.shouldReleaseRemaining(this)
                    ? takeAllPendingBreakthroughTriggers()
                    : 0;
        } else {
            immediateBreakthroughs = saturatingAdd(
                    breakthroughsTriggered,
                    takeAllPendingBreakthroughTriggers()
            );
        }
        return new PerkMilestoneAwards(
                chargesGranted,
                breakthroughsTriggered,
                immediateBreakthroughs
        );
    }

    public record PerkMilestoneAwards(int chargesGranted, int breakthroughsTriggered,
                                       int breakthroughsToTriggerImmediately) {
    }

    private void addPendingBreakthroughTriggers(int amount) {
        if (amount > 0) {
            pendingBreakthroughTriggers = saturatingAdd(
                    pendingBreakthroughTriggers,
                    amount
            );
        }
    }

    /**
     * Consumes one lifetime-awarded Breakthrough trigger.
     *
     * @return {@code true} only when a stored trigger actually existed
     */
    private boolean consumeOnePendingBreakthroughTrigger() {
        if (pendingBreakthroughTriggers <= 0) {
            return false;
        }
        pendingBreakthroughTriggers--;
        return true;
    }

    private int takeAllPendingBreakthroughTriggers() {
        int count = pendingBreakthroughTriggers;
        pendingBreakthroughTriggers = 0;
        return count;
    }

    private static int saturatingAdd(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
    }

    /**
     * Awards normal XP charges up to their lifetime cap. Once that cap is reached,
     * Holy Blessing may continue awarding attempts at its own JSON-configured interval.
     */
    public int awardSkillEnhancementMilestonesForLevel(int experienceLevel) {
        int currentLevel = Math.max(0, experienceLevel);
        if (currentLevel <= highestSkillEnhancementLevel) {
            return 0;
        }

        int previousHighestLevel = highestSkillEnhancementLevel;
        int interval = PlatformServices.config().skillEnhancementLevelsPerCharge();
        int normalCap = PlatformServices.config()
                .maximumSkillEnhancementChargesFromExperience();

        // A live cap increase pauses Holy Blessing until the new normal cap is reached.
        // A live cap decrease starts post-cap progression from the current saved high level.
        if (experienceSkillEnhancementChargesAwarded < normalCap) {
            holyBlessingSkillEnhancementStartLevel = -1;
        } else if (holyBlessingSkillEnhancementStartLevel < 0) {
            holyBlessingSkillEnhancementStartLevel = previousHighestLevel;
        }

        int crossedNormalMilestones = Math.max(
                0,
                currentLevel / interval - previousHighestLevel / interval
        );
        int remainingNormalAwards = Math.max(
                0,
                normalCap - experienceSkillEnhancementChargesAwarded
        );
        int normalGranted = Math.min(crossedNormalMilestones, remainingNormalAwards);

        if (normalGranted > 0
                && experienceSkillEnhancementChargesAwarded < normalCap
                && experienceSkillEnhancementChargesAwarded + normalGranted >= normalCap) {
            int milestonesNeededToReachCap = normalCap
                    - experienceSkillEnhancementChargesAwarded;
            long capMilestone = ((long) previousHighestLevel / interval
                    + milestonesNeededToReachCap) * interval;
            holyBlessingSkillEnhancementStartLevel = (int) Math.min(
                    Integer.MAX_VALUE,
                    capMilestone
            );
        }
        experienceSkillEnhancementChargesAwarded += normalGranted;

        int holyBlessingGranted = 0;
        if (experienceSkillEnhancementChargesAwarded >= normalCap
                && holyBlessingSkillEnhancementStartLevel >= 0
                && isAegisEnabled(AegisConstants.HOLY_BLESSING)) {
            Aegis holyBlessing = Aegis.byId(AegisConstants.HOLY_BLESSING).orElseThrow();
            int bonusInterval = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(1L, Math.round(holyBlessing.stat(
                            AegisConstants.BONUS_SKILL_ENHANCEMENT_LEVEL_INTERVAL
                    )))
            );
            int oldBonusMilestones = Math.max(
                    0,
                    previousHighestLevel - holyBlessingSkillEnhancementStartLevel
            )
                    / bonusInterval;
            int newBonusMilestones = Math.max(
                    0,
                    currentLevel - holyBlessingSkillEnhancementStartLevel
            )
                    / bonusInterval;
            holyBlessingGranted = Math.max(0, newBonusMilestones - oldBonusMilestones);
        }

        highestSkillEnhancementLevel = currentLevel;
        int granted = normalGranted + holyBlessingGranted;
        if (granted > 0) {
            addSkillEnhancementCharges(granted);
        }
        return granted;
    }

    /** Grants one starting Aegis charge, then one per configured level interval. */
    public int awardAegisChargesForLevel(int experienceLevel) {
        highestAegisLevel = Math.max(highestAegisLevel, Math.max(0, experienceLevel));
        int interval = PlatformServices.config().aegisLevelsPerCharge();
        int maximum = PlatformServices.config().maximumAegisCharges();
        long entitled = 1L + highestAegisLevel / interval;
        int targetAwarded = (int) Math.min(maximum, entitled);
        if (targetAwarded <= aegisChargesAwarded) {
            return 0;
        }
        int granted = targetAwarded - aegisChargesAwarded;
        aegisChargesAwarded = targetAwarded;
        aegisSelectionCharges = (int) Math.min(
                Integer.MAX_VALUE,
                (long) aegisSelectionCharges + granted
        );
        long refreshes = (long) granted
                * PlatformServices.config().aegisRefreshChargesPerCharge();
        aegisRefreshCharges = (int) Math.min(
                Integer.MAX_VALUE,
                (long) aegisRefreshCharges + refreshes
        );
        return granted;
    }

    /** Rolls unique options using workbook rarity weights: R 90%, SR 8%, SSR 2%. */
    public List<Perk> rollOffers(ServerPlayer player) {
        pendingOffers.clear();
        if (selectionCharges <= 0) {
            return List.of();
        }

        List<Perk> eligible = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            if (canAcquireTalent(perk)) {
                eligible.add(perk);
            }
        }

        int luckBonus = Math.max(
                0,
                Mth.floor(GeneralServerMethods.getAttributeValue(player, Attributes.LUCK))
        );
        int optionBonus = Mth.floor(TalentEffects.talentOptionBonus(player, this));
        long requestedOptions = Math.max(1L, 3L + luckBonus + optionBonus);
        int offerCount = Math.min(
                (int) Math.min(
                        requestedOptions,
                        PlatformServices.config().maximumPerkOptions()
                ),
                eligible.size()
        );
        while (pendingOffers.size() < offerCount && !eligible.isEmpty()) {
            Perk.Tier rolledTier = rollTier(player);
            List<Perk> tierCandidates = eligible.stream()
                    .filter(perk -> perk.tier() == rolledTier)
                    .toList();
            List<Perk> candidates = tierCandidates.isEmpty() ? eligible : tierCandidates;
            Perk chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
            pendingOffers.add(chosen);
            eligible.remove(chosen);
        }
        return List.copyOf(pendingOffers);
    }

    /** Spends one refresh charge and guarantees at least one different perk option. */
    public List<Perk> refreshPerkOffers(ServerPlayer player) {
        List<Perk> previous = getPendingOffers();
        if (previous.isEmpty()) {
            return rollOffers(player);
        }
        if (selectionCharges <= 0 || perkRefreshCharges <= 0) {
            return previous;
        }

        List<Perk> alternatives = Perk.values().stream()
                .filter(this::canAcquireTalent)
                .filter(perk -> !previous.contains(perk))
                .toList();
        if (alternatives.isEmpty()) {
            return previous;
        }

        List<Perk> refreshed = rollOffers(player);
        if (refreshed.isEmpty()) {
            pendingOffers.clear();
            pendingOffers.addAll(previous);
            return previous;
        }
        if (new LinkedHashSet<>(refreshed).equals(new LinkedHashSet<>(previous))) {
            Perk replacement = alternatives.get(player.getRandom().nextInt(alternatives.size()));
            pendingOffers.set(player.getRandom().nextInt(pendingOffers.size()), replacement);
        }
        perkRefreshCharges--;
        return List.copyOf(pendingOffers);
    }

    private static Perk.Tier rollTier(ServerPlayer player) {
        int total = Perk.rarityWeight(Perk.Tier.R)
                + Perk.rarityWeight(Perk.Tier.SR)
                + Perk.rarityWeight(Perk.Tier.SSR);
        int roll = player.getRandom().nextInt(Math.max(1, total));
        int rCutoff = Perk.rarityWeight(Perk.Tier.R);
        if (roll < rCutoff) {
            return Perk.Tier.R;
        }
        int srCutoff = rCutoff + Perk.rarityWeight(Perk.Tier.SR);
        return roll < srCutoff ? Perk.Tier.SR : Perk.Tier.SSR;
    }

    public List<SkillEnhancement> getPendingSkillEnhancementOffers() {
        if (skillEnhancementCharges <= 0) {
            pendingSkillEnhancementOffers.clear();
            return List.of();
        }
        pendingSkillEnhancementOffers.removeIf(
                enhancement -> !SkillEnhancement.values().contains(enhancement)
        );
        return List.copyOf(pendingSkillEnhancementOffers);
    }

    /** Rolls distinct, repeatable enhancement choices and locks them until selection. */
    public List<SkillEnhancement> rollSkillEnhancementOffers(ServerPlayer player) {
        pendingSkillEnhancementOffers.clear();
        if (skillEnhancementCharges <= 0) {
            return List.of();
        }

        List<SkillEnhancement> pool = new ArrayList<>(SkillEnhancement.values());
        int offerCount = Math.min(
                Math.max(1, 3 + skillEnhancementOptionBonus()),
                pool.size()
        );
        while (pendingSkillEnhancementOffers.size() < offerCount && !pool.isEmpty()) {
            SkillEnhancement chosen = pool.remove(player.getRandom().nextInt(pool.size()));
            pendingSkillEnhancementOffers.add(chosen);
        }
        return List.copyOf(pendingSkillEnhancementOffers);
    }

    /** Replaces a locked enhancement roll and guarantees a different option set. */
    public boolean refreshSkillEnhancementOffers(ServerPlayer player) {
        List<SkillEnhancement> previous = getPendingSkillEnhancementOffers();
        if (skillEnhancementCharges <= 0 || previous.isEmpty()) {
            return false;
        }

        List<SkillEnhancement> alternatives = SkillEnhancement.values().stream()
                .filter(enhancement -> !previous.contains(enhancement))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (alternatives.isEmpty()) {
            return false;
        }

        int offerCount = Math.min(
                Math.max(1, 3 + skillEnhancementOptionBonus()),
                SkillEnhancement.values().size()
        );
        SkillEnhancement guaranteedNew = alternatives.remove(
                player.getRandom().nextInt(alternatives.size())
        );
        List<SkillEnhancement> pool = new ArrayList<>(SkillEnhancement.values());
        pool.remove(guaranteedNew);
        pendingSkillEnhancementOffers.clear();
        pendingSkillEnhancementOffers.add(guaranteedNew);
        while (pendingSkillEnhancementOffers.size() < offerCount && !pool.isEmpty()) {
            pendingSkillEnhancementOffers.add(
                    pool.remove(player.getRandom().nextInt(pool.size()))
            );
        }
        return true;
    }

    public List<Aegis> getPendingAegisOffers() {
        if (aegisSelectionCharges <= 0) {
            pendingAegisOffers.clear();
            return List.of();
        }
        boolean initialSelection = chosenAegises.isEmpty();
        pendingAegisOffers.removeIf(aegis ->
                chosenAegises.contains(aegis) || !aegis.canOffer(initialSelection)
        );
        return List.copyOf(pendingAegisOffers);
    }

    /** Rolls distinct choices, including talent-configured option bonuses. */
    public List<Aegis> rollAegisOffers(ServerPlayer player) {
        pendingAegisOffers.clear();
        if (aegisSelectionCharges <= 0) {
            return List.of();
        }
        boolean initialSelection = chosenAegises.isEmpty();
        List<Aegis> pool = Aegis.values().stream()
                .filter(aegis -> !chosenAegises.contains(aegis))
                .filter(aegis -> aegis.canOffer(initialSelection))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int offerCount = Math.min(
                Math.max(1, 3 + aegisOptionBonus()),
                pool.size()
        );
        while (pendingAegisOffers.size() < offerCount) {
            pendingAegisOffers.add(pool.remove(player.getRandom().nextInt(pool.size())));
        }
        return List.copyOf(pendingAegisOffers);
    }

    /** Spends one refresh charge and guarantees at least one different Aegis option. */
    public List<Aegis> refreshAegisOffers(ServerPlayer player) {
        List<Aegis> previous = getPendingAegisOffers();
        if (previous.isEmpty()) {
            return rollAegisOffers(player);
        }
        if (aegisSelectionCharges <= 0 || aegisRefreshCharges <= 0) {
            return previous;
        }

        boolean initialSelection = chosenAegises.isEmpty();
        List<Aegis> alternatives = Aegis.values().stream()
                .filter(aegis -> !chosenAegises.contains(aegis))
                .filter(aegis -> aegis.canOffer(initialSelection))
                .filter(aegis -> !previous.contains(aegis))
                .toList();
        if (alternatives.isEmpty()) {
            return previous;
        }

        List<Aegis> refreshed = rollAegisOffers(player);
        if (refreshed.isEmpty()) {
            pendingAegisOffers.clear();
            pendingAegisOffers.addAll(previous);
            return previous;
        }
        if (new LinkedHashSet<>(refreshed).equals(new LinkedHashSet<>(previous))) {
            Aegis replacement = alternatives.get(
                    player.getRandom().nextInt(alternatives.size())
            );
            pendingAegisOffers.set(
                    player.getRandom().nextInt(pendingAegisOffers.size()),
                    replacement
            );
        }
        aegisRefreshCharges--;
        return List.copyOf(pendingAegisOffers);
    }

    private int skillEnhancementOptionBonus() {
        double bonus = getCustomStat(SKILL_ENHANCEMENT_OPTION_BONUS);
        for (Map.Entry<Perk, Integer> entry : getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.manuallyToggleable() || isTalentEnabled(perk.id())) {
                bonus += perk.stat(SKILL_ENHANCEMENT_OPTION_BONUS) * entry.getValue();
            }
        }
        return Math.max(0, Mth.floor(bonus));
    }

    private int aegisOptionBonus() {
        double bonus = getCustomStat(AEGIS_OPTION_BONUS);
        for (Map.Entry<Perk, Integer> entry : getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.manuallyToggleable() || isTalentEnabled(perk.id())) {
                bonus += perk.stat(AEGIS_OPTION_BONUS) * entry.getValue();
            }
        }
        return Math.max(0, Mth.floor(bonus));
    }

    public boolean tryChooseOffered(Perk perk, ServerPlayer player) {
        if (selectionCharges <= 0 || !pendingOffers.contains(perk)
                || !canAcquireTalent(perk)) {
            return false;
        }

        selectionCharges--;
        pendingOffers.clear();
        // The paid choice's Breakthrough uses only the ranks owned before this purchase.
        // A newly selected talent (or newly purchased repeatable rank) starts affecting
        // the next Breakthrough instead of its own selection-generated Breakthrough.
        triggerBreakthroughForSpentPerkCharge(player);
        boolean normalTalentPurchase = false;
        if (isAegisEnabled(AegisConstants.BLISS)) {
            grantBlissPerks(player);
        } else if (perk.id().equals(R_SUSPENSION_OF_DISBELIEF)) {
            grantSuspensionTalents(player, perk);
        } else {
            acquireTalent(perk, player, TalentAcquisitionContext.STANDARD);
            SharedFortune.onManualTalentSelected(player, perk);
            normalTalentPurchase = true;
        }
        AuthorityAegis.grantPrimaryStatForSelection(this);
        if (normalTalentPurchase) {
            grantSunoharaShunBonusTalent(player);
            TalentEffects.onTalentSelected(player, this);
        }
        applyChosenPerks(player);
        releaseRemainingBreakthroughsIfNeeded(player);
        return true;
    }

    /**
     * Authority Aegis spends one Perk charge to acquire every currently locked offer.
     * Each offer is processed in order so Primary Stat growth uses the talent count after
     * that individual acquisition instead of multiplying every offer by the final count.
     */
    public boolean tryChooseAllOfferedWithAuthority(ServerPlayer player) {
        if (selectionCharges <= 0 || pendingOffers.isEmpty()
                || !AuthorityAegis.canSelectAll(this)) {
            return false;
        }

        List<Perk> offers = pendingOffers.stream()
                .filter(this::canAcquireTalent)
                .toList();
        if (offers.isEmpty()) {
            return false;
        }
        if (!AuthorityAegis.consumeSelectAllUse(this)) {
            return false;
        }

        selectionCharges--;
        pendingOffers.clear();
        triggerBreakthroughForSpentPerkCharge(player);

        boolean acquiredNormalTalent = false;
        for (int index = 0; index < offers.size(); index++) {
            Perk offered = offers.get(index);
            // Match a normal paid selection: the first locked offer is guaranteed even
            // if the pre-purchase Breakthrough happens to fill the last talent slot.
            // Later offers still respect the resulting live slot/rank limits.
            if (index > 0 && !canAcquireTalent(offered)) {
                continue;
            }
            if (offered.id().equals(R_SUSPENSION_OF_DISBELIEF)) {
                grantSuspensionTalents(player, offered);
            } else {
                acquireTalent(offered, player, TalentAcquisitionContext.STANDARD);
                SharedFortune.onManualTalentSelected(player, offered);
                acquiredNormalTalent = true;
            }
            AuthorityAegis.grantPrimaryStatForSelection(this);
        }

        if (acquiredNormalTalent) {
            grantSunoharaShunBonusTalent(player);
            TalentEffects.onTalentSelected(player, this);
        }
        applyChosenPerks(player);
        releaseRemainingBreakthroughsIfNeeded(player);
        return true;
    }

    /** Converts one current perk choice into configured Skill Enhancement charges. */
    public boolean exchangePerkChargeForSkillEnhancements(ServerPlayer player) {
        if (selectionCharges <= 0 || pendingOffers.isEmpty()) {
            return false;
        }
        selectionCharges--;
        pendingOffers.clear();
        triggerBreakthroughForSpentPerkCharge(player);
        addSkillEnhancementCharges(
                PlatformServices.config().skillEnhancementChargesPerPerkExchange()
        );
        releaseRemainingBreakthroughsIfNeeded(player);
        return true;
    }

    private void triggerBreakthroughForSpentPerkCharge(ServerPlayer player) {
        if (!PlatformServices.config().triggerBreakthroughOnPerkSelection()) {
            return;
        }
        // A Perk charge determines when a stored Breakthrough is released; spending
        // the charge must not manufacture a new trigger after the stored total reaches
        // zero. This also prevents Butterfly's Gentle Touch from refunding its own
        // charge forever.
        if (consumeOnePendingBreakthroughTrigger()) {
            TalentEffects.triggerBreakthroughs(player, this, 1);
        }
    }

    private void triggerAllRemainingBreakthroughs(ServerPlayer player) {
        if (!PlatformServices.config().triggerBreakthroughOnPerkSelection()) {
            return;
        }
        int remaining = takeAllPendingBreakthroughTriggers();
        if (remaining > 0) {
            TalentEffects.triggerBreakthroughs(player, this, remaining);
        }
    }

    private void releaseRemainingBreakthroughsIfNeeded(ServerPlayer player) {
        if (BreakthroughReleasePolicy.shouldReleaseRemaining(this)) {
            triggerAllRemainingBreakthroughs(player);
        }
    }

    private void acquireTalent(Perk perk, ServerPlayer player,
                               TalentAcquisitionContext context) {
        boolean plumBlossomGardenWasActive = hasActiveSoulLink(
                SOUL_PLUM_BLOSSOM_GARDEN
        );
        int newRank = getRank(perk) + 1;
        perkRanks.put(perk, newRank);
        TalentEffects.onTalentAcquired(
                player, this, perk, newRank, context.triggerRewardChains
        );
        if (context.triggerRewardChains
                && !plumBlossomGardenWasActive
                && hasActiveSoulLink(SOUL_PLUM_BLOSSOM_GARDEN)) {
            grantPlumBlossomGardenRewards(player);
        }
    }

    /**
     * Suspension of Disbelief replaces itself with a small random talent batch. The
     * replacement talents initialize their passive state, but cannot launch another
     * acquisition reward chain or create additional Breakthroughs.
     */
    private void grantSuspensionTalents(ServerPlayer player, Perk suspension) {
        setCustomStat(SUSPENSION_OF_DISBELIEF_USED, 1.0D);
        int minimum = Math.max(0, (int) Math.round(
                suspension.stat(RANDOM_PERK_MIN)
        ));
        int maximum = Math.max(minimum, (int) Math.round(
                suspension.stat(RANDOM_PERK_MAX)
        ));
        int count = minimum;
        if (maximum > minimum) {
            count += player.getRandom().nextInt(maximum - minimum + 1);
        }
        for (int index = 0; index < count; index++) {
            List<Perk> eligible = Perk.values().stream()
                    .filter(this::canAcquireRandomTalent)
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            Perk.Tier tier = rollTier(player);
            List<Perk> tierCandidates = eligible.stream()
                    .filter(candidate -> candidate.tier() == tier)
                    .toList();
            List<Perk> candidates = tierCandidates.isEmpty()
                    ? eligible : tierCandidates;
            acquireTalent(
                    candidates.get(player.getRandom().nextInt(candidates.size())),
                    player,
                    TalentAcquisitionContext.RANDOM_REWARD
            );
        }
    }

    /** Shun rolls once per paid perk selection; granted talents never recurse. */
    private void grantSunoharaShunBonusTalent(ServerPlayer player) {
        if (!owns(R_SUNOHARA_SHUN)) {
            return;
        }
        Perk shun = Perk.byId(R_SUNOHARA_SHUN).orElseThrow();
        double chance = Mth.clamp(
                shun.stat(ADDITIONAL_RANDOM_TALENT_CHANCE),
                0.0D,
                1.0D
        );
        if (player.getRandom().nextDouble() >= chance) {
            return;
        }

        double oneTalentWeight = Math.max(
                0.0D,
                shun.stat(ADDITIONAL_RANDOM_ONE_TALENT_WEIGHT)
        );
        double twoTalentsWeight = Math.max(
                0.0D,
                shun.stat(ADDITIONAL_RANDOM_TWO_TALENTS_WEIGHT)
        );
        double threeTalentsWeight = Math.max(
                0.0D,
                shun.stat(ADDITIONAL_RANDOM_THREE_TALENTS_WEIGHT)
        );
        double totalCountWeight = oneTalentWeight
                + twoTalentsWeight
                + threeTalentsWeight;
        int rewardCount = 1;
        if (totalCountWeight > 0.0D) {
            double countRoll = player.getRandom().nextDouble() * totalCountWeight;
            rewardCount = countRoll < oneTalentWeight
                    ? 1
                    : countRoll < oneTalentWeight + twoTalentsWeight ? 2 : 3;
        }
        for (int rewardIndex = 0; rewardIndex < rewardCount; rewardIndex++) {
            if (!grantOneSunoharaShunBonusTalent(player, shun)) {
                break;
            }
        }
    }

    private boolean grantOneSunoharaShunBonusTalent(ServerPlayer player, Perk shun) {
        List<Perk> eligible = Perk.values().stream()
                .filter(this::canAcquireRandomTalent)
                .toList();
        if (eligible.isEmpty()) {
            return false;
        }

        double rWeight = eligible.stream().anyMatch(candidate ->
                candidate.tier() == Perk.Tier.R)
                ? Math.max(0.0D, shun.stat(ADDITIONAL_RANDOM_R_WEIGHT)) : 0.0D;
        double srWeight = eligible.stream().anyMatch(candidate ->
                candidate.tier() == Perk.Tier.SR)
                ? Math.max(0.0D, shun.stat(ADDITIONAL_RANDOM_SR_WEIGHT)) : 0.0D;
        double ssrWeight = eligible.stream().anyMatch(candidate ->
                candidate.tier() == Perk.Tier.SSR)
                ? Math.max(0.0D, shun.stat(ADDITIONAL_RANDOM_SSR_WEIGHT)) : 0.0D;
        double totalWeight = rWeight + srWeight + ssrWeight;
        List<Perk> candidates = eligible;
        if (totalWeight > 0.0D) {
            double rarityRoll = player.getRandom().nextDouble() * totalWeight;
            Perk.Tier tier = rarityRoll < rWeight
                    ? Perk.Tier.R
                    : rarityRoll < rWeight + srWeight ? Perk.Tier.SR : Perk.Tier.SSR;
            candidates = eligible.stream()
                    .filter(candidate -> candidate.tier() == tier)
                    .toList();
        }
        acquireTalent(
                candidates.get(player.getRandom().nextInt(candidates.size())),
                player,
                TalentAcquisitionContext.RANDOM_REWARD
        );
        return true;
    }

    /** Applies the one-time reward when Kokona and Shun first activate their Soul Link. */
    private void grantPlumBlossomGardenRewards(ServerPlayer player) {
        SoulLink plumBlossomGarden = Perk.soulLinkById(SOUL_PLUM_BLOSSOM_GARDEN)
                .orElseThrow();
        List<Perk> eligible = Perk.values().stream()
                .filter(candidate -> candidate.tier() == Perk.Tier.SSR)
                .filter(this::canAcquireRandomTalent)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (eligible.isEmpty()) {
            return;
        }

        double gainAllChance = Mth.clamp(
                plumBlossomGarden.bonusStat(GAIN_ALL_SSR_CHANCE),
                0.0D,
                1.0D
        );
        int rewardCount;
        if (player.getRandom().nextDouble() < gainAllChance) {
            rewardCount = eligible.size();
        } else {
            int minimum = Math.max(0, (int) Math.round(
                    plumBlossomGarden.bonusStat(RANDOM_SSR_MIN)
            ));
            int maximum = Math.max(minimum, (int) Math.round(
                    plumBlossomGarden.bonusStat(RANDOM_SSR_MAX)
            ));
            rewardCount = minimum + player.getRandom().nextInt(maximum - minimum + 1);
        }

        while (rewardCount-- > 0 && !eligible.isEmpty()) {
            Perk reward = eligible.remove(player.getRandom().nextInt(eligible.size()));
            if (canAcquireRandomTalent(reward)) {
                acquireTalent(reward, player, TalentAcquisitionContext.RANDOM_REWARD);
            }
        }
    }

    private void grantBlissPerks(ServerPlayer player) {
        Aegis bliss = Aegis.byId(AegisConstants.BLISS).orElseThrow();
        double roll = player.getRandom().nextDouble();
        int count = roll < bliss.stat(AegisConstants.RANDOM_PERK_ONE_CHANCE)
                ? 1
                : roll < bliss.stat(AegisConstants.RANDOM_PERK_ONE_CHANCE)
                + bliss.stat(AegisConstants.RANDOM_PERK_TWO_CHANCE) ? 2 : 3;
        count = Mth.clamp(
                count,
                Math.max(1, (int) Math.round(bliss.stat(AegisConstants.RANDOM_PERK_MIN))),
                Math.max(1, (int) Math.round(bliss.stat(AegisConstants.RANDOM_PERK_MAX)))
        );

        for (int index = 0; index < count; index++) {
            List<Perk> eligible = Perk.values().stream()
                    .filter(this::canAcquireRandomTalent)
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            Perk.Tier tier = rollTier(player);
            List<Perk> tierCandidates = eligible.stream()
                    .filter(candidate -> candidate.tier() == tier)
                    .toList();
            List<Perk> candidates = tierCandidates.isEmpty() ? eligible : tierCandidates;
            acquireTalent(
                    candidates.get(player.getRandom().nextInt(candidates.size())),
                    player,
                    TalentAcquisitionContext.RANDOM_REWARD
            );
            int received = (int) Math.round(addCustomStat(
                    AegisConstants.BLISS_PERKS_RECEIVED,
                    1.0D
            ));
            int interval = Math.max(1, (int) Math.round(
                    bliss.stat(AegisConstants.SSR_PER_PERKS_RECEIVED)
            ));
            if (received % interval == 0) {
                List<Perk> ssr = Perk.values().stream()
                        .filter(candidate -> candidate.tier() == Perk.Tier.SSR)
                        .filter(this::canAcquireRandomTalent)
                        .toList();
                if (!ssr.isEmpty()) {
                    acquireTalent(
                            ssr.get(player.getRandom().nextInt(ssr.size())),
                            player,
                            TalentAcquisitionContext.RANDOM_REWARD
                    );
                }
            }
        }
    }

    public boolean tryChooseOfferedSkillEnhancement(SkillEnhancement enhancement,
                                                     ServerPlayer player) {
        if (skillEnhancementCharges <= 0
                || !pendingSkillEnhancementOffers.contains(enhancement)) {
            return false;
        }

        skillEnhancementCharges--;
        skillEnhancementRanks.merge(enhancement, 1, (oldRank, added) ->
                oldRank == Integer.MAX_VALUE ? oldRank : oldRank + added
        );
        enhancement.customStat()
                .filter(key -> !key.equals(CRITICAL_CHANCE)
                        && !key.equals(CRITICAL_DAMAGE))
                .ifPresent(key -> addCustomStat(key, enhancement.amount()));
        pendingSkillEnhancementOffers.clear();
        applyChosenPerks(player);
        return true;
    }

    public boolean tryChooseOfferedAegis(Aegis aegis, ServerPlayer player) {
        if (aegisSelectionCharges <= 0
                || chosenAegises.contains(aegis)
                || !pendingAegisOffers.contains(aegis)) {
            return false;
        }
        boolean alreadyOwnedAnAegis = !chosenAegises.isEmpty();
        aegisSelectionCharges--;
        chosenAegises.add(aegis);
        pendingAegisOffers.clear();
        if (aegis.id().equals(AegisConstants.LUCKY) && alreadyOwnedAnAegis) {
            grantRandomInactiveSoulLinkSet(player);
        }
        if (aegis.id().equals(AegisConstants.MIRACLE)) {
            grantMiracleAegises(aegis, player);
        }
        applyChosenPerks(player);
        releaseRemainingBreakthroughsIfNeeded(player);
        return true;
    }

    private void grantMiracleAegises(Aegis miracle, ServerPlayer player) {
        double roll = player.getRandom().nextDouble();
        int count;
        double oneCutoff = miracle.stat(AegisConstants.MIRACLE_ONE_CHANCE);
        double twoCutoff = oneCutoff + miracle.stat(AegisConstants.MIRACLE_TWO_CHANCE);
        if (roll < oneCutoff) {
            count = 1;
        } else if (roll < twoCutoff) {
            count = 2;
        } else {
            count = 3;
        }
        int minimum = Math.max(1, (int) Math.round(
                miracle.stat(AegisConstants.RANDOM_AEGIS_MIN)
        ));
        int maximum = Math.max(minimum, (int) Math.round(
                miracle.stat(AegisConstants.RANDOM_AEGIS_MAX)
        ));
        count = Mth.clamp(count, minimum, maximum);

        List<Aegis> pool = Aegis.values().stream()
                .filter(candidate -> !candidate.id().equals(AegisConstants.MIRACLE))
                .filter(candidate -> !chosenAegises.contains(candidate))
                .filter(candidate -> candidate.canOffer(false))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while (count-- > 0 && !pool.isEmpty()) {
            Aegis granted = pool.remove(player.getRandom().nextInt(pool.size()));
            chosenAegises.add(granted);
            if (granted.id().equals(AegisConstants.LUCKY)) {
                grantRandomInactiveSoulLinkSet(player);
            }
        }
    }

    /** Grants every missing talent required by one random inactive Soul Link. */
    public boolean grantRandomInactiveSoulLinkSet(ServerPlayer player) {
        if (!isAegisEnabled(AegisConstants.LUCKY)) {
            return false;
        }
        Aegis lucky = Aegis.byId(AegisConstants.LUCKY).orElseThrow();
        int grantedSets = Math.max(0, Mth.floor(
                getCustomStat(AegisConstants.LUCKY_SOUL_LINK_SETS_GRANTED)
        ));
        int maximumSets = Math.max(0, (int) Math.round(
                lucky.stat(AegisConstants.MAX_SOUL_LINK_SETS)
        ));
        if (grantedSets >= maximumSets) {
            return false;
        }

        List<SoulLink> candidates = Perk.soulLinks().stream()
                .filter(SoulLink::enabled)
                .filter(link -> !link.requirements().isEmpty())
                .filter(link -> link.requirements().stream()
                        .noneMatch(PlatformServices.config()::isTalentHidden))
                .filter(link -> !link.isActive(this))
                .filter(link -> link.requirements().stream()
                        .allMatch(perkId -> Perk.byId(perkId).isPresent()))
                .filter(link -> link.requirements().stream().allMatch(perkId ->
                        Perk.byId(perkId).map(perk ->
                                getRank(perk) > 0 || perk.randomRewardEligible()
                        ).orElse(false)
                ))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }

        SoulLink selected = candidates.get(player.getRandom().nextInt(candidates.size()));
        for (String perkId : selected.requirements()) {
            Perk.byId(perkId).ifPresent(perk -> {
                if (getRank(perk) <= 0) {
                    // Aegis-granted sets are guaranteed and therefore may exceed the
                    // normal talent-slot limit.
                    acquireTalent(perk, player, TalentAcquisitionContext.STANDARD);
                }
            });
        }
        if (!selected.isActive(this)) {
            return false;
        }
        addCustomStat(AegisConstants.LUCKY_SOUL_LINK_SETS_GRANTED, 1.0D);
        return true;
    }

    public void applyChosenPerks(Player player) {
        TalentEffects.recalculateAttributes(player, this);
    }

    /** Clears every system and its milestone history, matching a full death reset. */
    public void resetAll() {
        selectionCharges = 0;
        perkRefreshCharges = 0;
        highestPerkLevel = 0;
        startingPerkChargeAwarded = false;
        experiencePerkChargesAwarded = 0;
        experienceBreakthroughsTriggered = 0;
        pendingBreakthroughTriggers = 0;
        perkRanks.clear();
        pendingOffers.clear();
        customStats.clear();
        enabledManualTalents.clear();
        sharedFortunePartnerId = null;
        sharedFortunePartnerName = "";
        sharedFortuneRebindAvailableAtMillis = 0L;

        skillEnhancementCharges = 0;
        highestSkillEnhancementLevel = 0;
        experienceSkillEnhancementChargesAwarded = 0;
        holyBlessingSkillEnhancementStartLevel = -1;
        skillEnhancementRanks.clear();
        pendingSkillEnhancementOffers.clear();
        selectedPrimarySkillEnhancement = SkillEnhancement.defaultPrimary();
        primarySkillEnhancementChosen = false;

        aegisSelectionCharges = 0;
        aegisRefreshCharges = 0;
        highestAegisLevel = 0;
        aegisChargesAwarded = 0;
        chosenAegises.clear();
        pendingAegisOffers.clear();
        disabledManualAegises.clear();
        devouredItems.clear();
        devouredAttributes.clear();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(CHARGES_TAG, selectionCharges);
        tag.putInt(PERK_REFRESH_CHARGES_TAG, perkRefreshCharges);
        tag.putInt(HIGHEST_PERK_LEVEL_TAG, highestPerkLevel);
        tag.putBoolean(STARTING_PERK_CHARGE_AWARDED_TAG, startingPerkChargeAwarded);
        tag.putInt(
                EXPERIENCE_PERK_CHARGES_AWARDED_TAG,
                experiencePerkChargesAwarded
        );
        tag.putInt(
                EXPERIENCE_BREAKTHROUGHS_TRIGGERED_TAG,
                experienceBreakthroughsTriggered
        );
        tag.putInt(PENDING_BREAKTHROUGH_TRIGGERS_TAG, pendingBreakthroughTriggers);

        ListTag chosen = new ListTag();
        perkRanks.forEach((perk, rank) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", perk.id());
            entry.putInt("Rank", rank);
            chosen.add(entry);
        });
        tag.put(CHOSEN_TAG, chosen);

        ListTag offers = new ListTag();
        pendingOffers.forEach(perk -> offers.add(StringTag.valueOf(perk.id())));
        tag.put(PENDING_OFFERS_TAG, offers);

        ListTag stats = new ListTag();
        customStats.forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", key);
            entry.putDouble("Value", value);
            stats.add(entry);
        });
        tag.put(CUSTOM_STATS_TAG, stats);

        ListTag enabledTalents = new ListTag();
        enabledManualTalents.forEach(perkId -> enabledTalents.add(StringTag.valueOf(perkId)));
        tag.put(ENABLED_TALENTS_TAG, enabledTalents);
        if (sharedFortunePartnerId != null) {
            tag.putUUID(SHARED_FORTUNE_PARTNER_TAG, sharedFortunePartnerId);
            tag.putString(SHARED_FORTUNE_PARTNER_NAME_TAG, sharedFortunePartnerName);
        }
        tag.putLong(
                SHARED_FORTUNE_REBIND_AVAILABLE_AT_TAG,
                sharedFortuneRebindAvailableAtMillis
        );

        tag.putInt(SKILL_ENHANCEMENT_CHARGES_TAG, skillEnhancementCharges);
        tag.putInt(HIGHEST_SKILL_ENHANCEMENT_LEVEL_TAG, highestSkillEnhancementLevel);
        tag.putInt(
                EXPERIENCE_SKILL_ENHANCEMENT_CHARGES_AWARDED_TAG,
                experienceSkillEnhancementChargesAwarded
        );
        tag.putInt(
                HOLY_BLESSING_SKILL_ENHANCEMENT_START_LEVEL_TAG,
                holyBlessingSkillEnhancementStartLevel
        );

        ListTag enhancementRanks = new ListTag();
        skillEnhancementRanks.forEach((enhancement, rank) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", enhancement.id());
            entry.putInt("Rank", rank);
            enhancementRanks.add(entry);
        });
        tag.put(SKILL_ENHANCEMENT_RANKS_TAG, enhancementRanks);

        ListTag enhancementOffers = new ListTag();
        pendingSkillEnhancementOffers.forEach(enhancement ->
                enhancementOffers.add(StringTag.valueOf(enhancement.id()))
        );
        tag.put(PENDING_SKILL_ENHANCEMENT_OFFERS_TAG, enhancementOffers);
        tag.putString(PRIMARY_SKILL_ENHANCEMENT_TAG,
                selectedPrimarySkillEnhancement.id());
        tag.putBoolean(PRIMARY_SKILL_ENHANCEMENT_CHOSEN_TAG,
                primarySkillEnhancementChosen);

        tag.putInt(AEGIS_CHARGES_TAG, aegisSelectionCharges);
        tag.putInt(AEGIS_REFRESH_CHARGES_TAG, aegisRefreshCharges);
        tag.putInt(AEGIS_CHARGES_AWARDED_TAG, aegisChargesAwarded);
        tag.putInt(HIGHEST_AEGIS_LEVEL_TAG, highestAegisLevel);
        ListTag chosenAegisTags = new ListTag();
        chosenAegises.forEach(aegis -> chosenAegisTags.add(StringTag.valueOf(aegis.id())));
        tag.put(CHOSEN_AEGISES_TAG, chosenAegisTags);
        ListTag aegisOffers = new ListTag();
        pendingAegisOffers.forEach(aegis -> aegisOffers.add(StringTag.valueOf(aegis.id())));
        tag.put(PENDING_AEGIS_OFFERS_TAG, aegisOffers);
        ListTag disabledAegises = new ListTag();
        disabledManualAegises.forEach(aegisId ->
                disabledAegises.add(StringTag.valueOf(aegisId))
        );
        tag.put(DISABLED_AEGISES_TAG, disabledAegises);

        ListTag devouredItemTags = new ListTag();
        devouredItems.forEach(itemId -> devouredItemTags.add(StringTag.valueOf(itemId)));
        tag.put(DEVOURED_ITEMS_TAG, devouredItemTags);

        ListTag devouredAttributeTags = new ListTag();
        devouredAttributes.forEach(attribute ->
                devouredAttributeTags.add(attribute.serializeNBT())
        );
        tag.put(DEVOURED_ATTRIBUTES_TAG, devouredAttributeTags);

        tag.put(SHOP_TAG, shopState.save());
        tag.put(STORAGE_TAG, storage.save());

        CompoundTag virtualUses = new CompoundTag();
        virtualItemUses.forEach(virtualUses::putInt);
        tag.put(VIRTUAL_ITEM_USES_TAG, virtualUses);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        selectionCharges = Math.max(0, tag.getInt(CHARGES_TAG));
        perkRefreshCharges = Math.max(0, tag.getInt(PERK_REFRESH_CHARGES_TAG));
        startingPerkChargeAwarded = tag.getBoolean(STARTING_PERK_CHARGE_AWARDED_TAG);
        highestPerkLevel = Math.max(0, tag.getInt(HIGHEST_PERK_LEVEL_TAG));
        experiencePerkChargesAwarded = Math.max(
                0,
                tag.getInt(EXPERIENCE_PERK_CHARGES_AWARDED_TAG)
        );
        pendingBreakthroughTriggers = Math.max(
                0,
                tag.getInt(PENDING_BREAKTHROUGH_TRIGGERS_TAG)
        );
        experienceBreakthroughsTriggered = Math.max(
                0,
                tag.getInt(EXPERIENCE_BREAKTHROUGHS_TRIGGERED_TAG)
        );
        perkRanks.clear();
        pendingOffers.clear();
        customStats.clear();
        enabledManualTalents.clear();
        sharedFortunePartnerId = tag.hasUUID(SHARED_FORTUNE_PARTNER_TAG)
                ? tag.getUUID(SHARED_FORTUNE_PARTNER_TAG)
                : null;
        sharedFortunePartnerName = sharedFortunePartnerId == null
                ? ""
                : tag.getString(SHARED_FORTUNE_PARTNER_NAME_TAG);
        sharedFortuneRebindAvailableAtMillis = Math.max(
                0L,
                tag.getLong(SHARED_FORTUNE_REBIND_AVAILABLE_AT_TAG)
        );
        skillEnhancementCharges = Math.max(0, tag.getInt(SKILL_ENHANCEMENT_CHARGES_TAG));
        highestSkillEnhancementLevel = Math.max(
                0,
                tag.getInt(HIGHEST_SKILL_ENHANCEMENT_LEVEL_TAG)
        );
        experienceSkillEnhancementChargesAwarded = Math.max(
                0,
                tag.getInt(EXPERIENCE_SKILL_ENHANCEMENT_CHARGES_AWARDED_TAG)
        );
        holyBlessingSkillEnhancementStartLevel = Math.max(
                -1,
                tag.getInt(HOLY_BLESSING_SKILL_ENHANCEMENT_START_LEVEL_TAG)
        );
        skillEnhancementRanks.clear();
        pendingSkillEnhancementOffers.clear();
        selectedPrimarySkillEnhancement = SkillEnhancement.defaultPrimary();
        primarySkillEnhancementChosen = false;
        aegisSelectionCharges = Math.max(0, tag.getInt(AEGIS_CHARGES_TAG));
        aegisRefreshCharges = Math.max(0, tag.getInt(AEGIS_REFRESH_CHARGES_TAG));
        highestAegisLevel = Math.max(0, tag.getInt(HIGHEST_AEGIS_LEVEL_TAG));
        chosenAegises.clear();
        pendingAegisOffers.clear();
        disabledManualAegises.clear();
        devouredItems.clear();
        devouredAttributes.clear();

        ListTag chosen = tag.getList(CHOSEN_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < chosen.size(); index++) {
            CompoundTag entry = chosen.getCompound(index);
            int rank = Math.max(0, entry.getInt("Rank"));
            Perk.byId(entry.getString("Id")).ifPresent(perk -> {
                if (rank > 0) {
                    perkRanks.put(perk, Math.min(rank, perk.maxRank()));
                }
            });
        }

        ListTag offers = tag.getList(PENDING_OFFERS_TAG, Tag.TAG_STRING);
        for (int index = 0; index < offers.size() && pendingOffers.size() < Perk.values().size(); index++) {
            Perk.byId(offers.getString(index)).ifPresent(perk -> {
                // Custom stats (including future bonus slots) load immediately
                // after this list. Final slot validation occurs in getPendingOffers().
                if (perk.canAcquire(getRank(perk)) && !pendingOffers.contains(perk)) {
                    pendingOffers.add(perk);
                }
            });
        }

        ListTag stats = tag.getList(CUSTOM_STATS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < stats.size(); index++) {
            CompoundTag entry = stats.getCompound(index);
            String key = entry.getString("Key");
            double value = entry.getDouble("Value");
            if (!key.isBlank() && Double.isFinite(value)) {
                setCustomStat(key, value);
            }
        }
        ListTag enabledTalents = tag.getList(ENABLED_TALENTS_TAG, Tag.TAG_STRING);
        for (int index = 0; index < enabledTalents.size(); index++) {
            Perk.byId(enabledTalents.getString(index)).ifPresent(perk -> {
                if (perk.manuallyToggleable() && getRank(perk) > 0) {
                    enabledManualTalents.add(perk.id());
                }
            });
        }

        ListTag enhancementRanks = tag.getList(
                SKILL_ENHANCEMENT_RANKS_TAG,
                Tag.TAG_COMPOUND
        );
        for (int index = 0; index < enhancementRanks.size(); index++) {
            CompoundTag entry = enhancementRanks.getCompound(index);
            int rank = Math.max(0, entry.getInt("Rank"));
            SkillEnhancement.byId(entry.getString("Id")).ifPresent(enhancement -> {
                if (rank > 0) {
                    skillEnhancementRanks.put(enhancement, rank);
                }
            });
        }

        ListTag enhancementOffers = tag.getList(
                PENDING_SKILL_ENHANCEMENT_OFFERS_TAG,
                Tag.TAG_STRING
        );
        for (int index = 0; index < enhancementOffers.size()
                && pendingSkillEnhancementOffers.size() < SkillEnhancement.values().size();
             index++) {
            SkillEnhancement.byId(enhancementOffers.getString(index)).ifPresent(enhancement -> {
                if (!pendingSkillEnhancementOffers.contains(enhancement)) {
                    pendingSkillEnhancementOffers.add(enhancement);
                }
            });
        }

        SkillEnhancement.byId(tag.getString(PRIMARY_SKILL_ENHANCEMENT_TAG))
                .ifPresent(enhancement -> selectedPrimarySkillEnhancement = enhancement);
        primarySkillEnhancementChosen = tag.getBoolean(
                PRIMARY_SKILL_ENHANCEMENT_CHOSEN_TAG
        );

        ListTag chosenAegisTags = tag.getList(CHOSEN_AEGISES_TAG, Tag.TAG_STRING);
        for (int index = 0; index < chosenAegisTags.size(); index++) {
            Aegis.byId(chosenAegisTags.getString(index)).ifPresent(chosenAegises::add);
        }

        ListTag devouredItemTags = tag.getList(DEVOURED_ITEMS_TAG, Tag.TAG_STRING);
        for (int index = 0; index < devouredItemTags.size(); index++) {
            String itemId = devouredItemTags.getString(index);
            if (!itemId.isBlank()) {
                devouredItems.add(itemId);
            }
        }

        ListTag devouredAttributeTags = tag.getList(
                DEVOURED_ATTRIBUTES_TAG,
                Tag.TAG_COMPOUND
        );
        for (int index = 0; index < devouredAttributeTags.size(); index++) {
            DevourAegis.InheritedAttribute.deserializeNBT(
                    devouredAttributeTags.getCompound(index)
            ).ifPresent(devouredAttributes::add);
        }

        shopState.load(tag.getCompound(SHOP_TAG));
        storage.load(tag.getCompound(STORAGE_TAG));

        virtualItemUses.clear();
        CompoundTag virtualUses = tag.getCompound(VIRTUAL_ITEM_USES_TAG);
        for (String key : virtualUses.getAllKeys()) {
            int used = virtualUses.getInt(key);
            if (used > 0) {
                virtualItemUses.put(key, used);
            }
        }
        aegisChargesAwarded = Math.max(0, tag.getInt(AEGIS_CHARGES_AWARDED_TAG));

        ListTag aegisOffers = tag.getList(PENDING_AEGIS_OFFERS_TAG, Tag.TAG_STRING);
        for (int index = 0; index < aegisOffers.size()
                && pendingAegisOffers.size() < Aegis.values().size(); index++) {
            Aegis.byId(aegisOffers.getString(index)).ifPresent(aegis -> {
                if (!chosenAegises.contains(aegis) && !pendingAegisOffers.contains(aegis)) {
                    pendingAegisOffers.add(aegis);
                }
            });
        }

        ListTag disabledAegises = tag.getList(DISABLED_AEGISES_TAG, Tag.TAG_STRING);
        for (int index = 0; index < disabledAegises.size(); index++) {
            Aegis.byId(disabledAegises.getString(index)).ifPresent(aegis -> {
                if (aegis.manuallyToggleable() && chosenAegises.contains(aegis)) {
                    disabledManualAegises.add(aegis.id());
                }
            });
        }
    }

}
