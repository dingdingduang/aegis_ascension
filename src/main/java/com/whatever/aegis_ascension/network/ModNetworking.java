package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.util.DisplayStatScope;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.platform.NetworkAccess;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.shop.ShopConfig;
import com.whatever.aegis_ascension.shop.ShopType;
import com.whatever.aegis_ascension.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;

/** Loader-neutral facade for Aegis Ascension's packet-based actions and synchronization. */
public final class ModNetworking {
    private static final NetworkAccess NETWORK = PlatformServices.network();

    private ModNetworking() {
    }

    public static void register() {
        NETWORK.registerPackets();
    }

    /** Pushes the player's virtual storage rows, per-row sell values, and sort state. */
    public static void syncStorageTo(ServerPlayer player) {
        if (!ServerCatalogSync.isReady(player)) return;
        PerkData.get(player).ifPresent(data -> {
            var storage = data.getStorage();
            var rows = storage.getItems();
            java.util.List<Integer> sellValues = new java.util.ArrayList<>(rows.size());
            for (var row : rows) {
                sellValues.add(com.whatever.aegis_ascension.shop.ShopConfig.get()
                        .sellUnitExperience(row.prototype().getItem()));
            }
            NETWORK.sendToPlayer(
                    player,
                    new SyncStorageDataPacket(
                            rows,
                            sellValues,
                            // Effective cap, not the raw config value: Storage Expansion
                            // books raise it per player.
                            storage.getMaxTypes()
                    )
            );
        });
    }

    /** Pushes the player's authoritative shop stock, prices, and reset countdown to their client. */
    public static void syncShopTo(ServerPlayer player) {
        syncShopTo(player, ShopType.COMMON);
    }

    /** Pushes one independently stocked Common or Discovery shop view. */
    public static void syncShopTo(ServerPlayer player, ShopType shopType) {
        if (!ServerCatalogSync.isReady(player)) return;
        ShopType resolvedType = shopType == null ? ShopType.COMMON : shopType;
        PerkData.get(player).ifPresent(data -> {
            var shop = data.getShopState(resolvedType);
            ShopConfig config = ShopConfig.get();
            NETWORK.sendToPlayer(
                    player,
                    new SyncShopDataPacket(
                            resolvedType,
                            config.isEnabled(resolvedType),
                            shop.getOffers(),
                            config.manualRefreshExperienceCost(resolvedType),
                            shop.getRemainingManualRefreshes(),
                            shop.ticksUntilReset(player.serverLevel())
                    )
            );
        });
    }

    /** Pushes the player's current total shield to their client for the shield HUD. */
    public static void sendShield(ServerPlayer player, float total) {
        NETWORK.sendToPlayer(player, new SyncShieldPacket(total));
    }

    public static void sendToServer(Object packet) {
        NETWORK.sendToServer(packet);
    }

    public static void syncTo(ServerPlayer player) {
        if (!ServerCatalogSync.isReady(player)) return;
        syncPerkDataTo(player);
        // Quest penalties can be cleared by progression actions such as acquiring an Aegis;
        // callers that synchronize the entire player state retain that behavior.
        syncQuestsTo(player);
    }

    /**
     * Pushes only the Custom Stats display values.
     *
     * <p>For the tab's periodic refresh, which reads nothing else. The full progression
     * packet still carries these values whenever it is sent for another reason, so this
     * never becomes the only route by which they arrive.</p>
     */
    public static void syncDisplayStatsTo(ServerPlayer player, DisplayStatScope scope) {
        if (!ServerCatalogSync.isReady(player)) return;
        PerkData.get(player).ifPresent(data -> NETWORK.sendToPlayer(
                player,
                new SyncDisplayStatsPacket(
                        TalentEffects.buildDisplayStats(player, data, scope), scope)
        ));
    }

    /** Pushes perk/progression state without attaching the much larger quest catalogue. */
    public static void syncPerkDataTo(ServerPlayer player) {
        syncPerkDataTo(player, DisplayStatScope.ESSENTIAL);
    }

    /**
     * @param scope how much of the display map to attach. A shop purchase or a storage
     *              action would otherwise carry kilobytes of stat values for a screen
     *              that is almost always closed, so those syncs send only the few values
     *              other screens read.
     */
    public static void syncPerkDataTo(ServerPlayer player, DisplayStatScope scope) {
        if (!ServerCatalogSync.isReady(player)) return;
        PerkData.get(player).ifPresent(data -> {
            AegisExperienceSystem.Snapshot progression =
                    AegisExperienceSystem.snapshot(player, data);
            NETWORK.sendToPlayer(
                        player,
                        new SyncPerkDataPacket(
                                data.getSelectionCharges(),
                                data.getPendingBreakthroughTriggers(),
                                data.getPerkRefreshCharges(),
                                data.getMaxTalentSlots(),
                                data.getSkillEnhancementCharges(),
                                PlatformServices.config()
                                        .skillEnhancementChargesPerPerkExchange(),
                                PlatformServices.config()
                                        .skillEnhancementRefreshExperienceCost(),
                                data.hasActiveSoulLink(
                                        com.whatever.aegis_ascension.perk.TalentConstants
                                                .SOUL_LOGISTICS_COMBO
                                ),
                                data.getAegisSelectionCharges(),
                                data.getAegisRefreshCharges(),
                                PlatformServices.config().liveCustomStatsRefreshEnabled(),
                                progression.usesMinecraftDefaultLevel(),
                                PlatformServices.config().useGoldCurrency(),
                                data.getGoldCurrency(),
                                progression.progressionLevel(),
                                progression.aegisAscensionRank(),
                                progression.aegisAscensionExperience(),
                                progression.experienceToNextRank(),
                                progression.maximumRank(),
                                data.getSharedFortunePartnerId().orElse(null),
                                data.getSharedFortunePartnerName(),
                                data.getSharedFortuneRebindCooldownSeconds(),
                                PlatformServices.config().hiddenTalentIds(),
                                data.getPerkRanks(),
                                data.getEnabledManualTalents(),
                                TalentEffects.buildDisplayStats(player, data, scope),
                                scope,
                                data.getSkillEnhancementRanks(),
                                data.getPendingSkillEnhancementOffers(),
                                data.getPrimarySkillEnhancement(),
                                data.hasChosenPrimarySkillEnhancement(),
                                data.getChosenAegises(),
                                data.getDisabledManualAegises()
                        )
                );
        });
    }

    public static void syncDevourDataTo(ServerPlayer player) {
        if (!ServerCatalogSync.isReady(player)) return;
        PerkData.get(player).ifPresent(data ->
                NETWORK.sendToPlayer(player, SyncDevourDataPacket.from(data))
        );
    }

    public static void syncQuestsTo(ServerPlayer player) {
        if (!ServerCatalogSync.isReady(player)) return;
        QuestManager.clearPendingProgressSync(player);
        PerkData.get(player).ifPresent(data -> NETWORK.sendToPlayer(player,
                new SyncQuestDataPacket(QuestManager.views(player, data),
                        QuestManager.completionViews(data),
                        data.isChallengePenaltyActive(),
                        Math.max(0, com.whatever.aegis_ascension.quest.QuestConfig.get()
                                .challengeSecurityDepositExperience),
                        PlatformServices.config().useMinecraftDefaultLevel(),
                        data.getQuestState().autoAcceptEligibleQuests(),
                        com.whatever.aegis_ascension.quest.QuestConfig.get()
                                .questCompleteSound,
                        com.whatever.aegis_ascension.quest.QuestConfig.get()
                                .reputationIcon,
                        QuestManager.lifetimeTotals(data))));
    }

    /** Sends only counters for quests dirtied since the previous batched flush. */
    public static void syncQuestProgressTo(ServerPlayer player, Set<String> questIds) {
        if (!ServerCatalogSync.isReady(player)) return;
        if (questIds == null || questIds.isEmpty()) return;
        PerkData.get(player).ifPresent(data -> {
            Map<String, java.util.List<Integer>> progress =
                    QuestManager.progressValues(data, questIds);
            if (!progress.isEmpty()) {
                NETWORK.sendToPlayer(player, new SyncQuestProgressPacket(progress));
            }
        });
    }

    public static void openScreen(
            ServerPlayer player,
            java.util.List<com.whatever.aegis_ascension.perk.Perk> offers) {
        if (!ServerCatalogSync.isReady(player)) return;
        NETWORK.sendToPlayer(player, new OpenPerkScreenPacket(offers));
    }

    public static void openAegisScreen(
            ServerPlayer player,
            java.util.List<com.whatever.aegis_ascension.aegis.Aegis> offers) {
        if (!ServerCatalogSync.isReady(player)) return;
        NETWORK.sendToPlayer(player, new OpenAegisScreenPacket(offers));
    }
}
