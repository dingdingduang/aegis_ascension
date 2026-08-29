package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.platform.NetworkAccess;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;

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
        PerkData.get(player).ifPresent(data ->
                NETWORK.sendToPlayer(
                        player,
                        new SyncShopDataPacket(
                                data.getShopState().getOffers(),
                                Math.max(0, com.whatever.aegis_ascension.shop.ShopConfig.get()
                                        .manualRefreshExperienceCost),
                                data.getShopState().getRemainingManualRefreshes(),
                                data.getShopState().ticksUntilReset(player.serverLevel())
                        )
                )
        );
    }

    /** Pushes the player's current total shield to their client for the shield HUD. */
    public static void sendShield(ServerPlayer player, float total) {
        NETWORK.sendToPlayer(player, new SyncShieldPacket(total));
    }

    public static void sendToServer(Object packet) {
        NETWORK.sendToServer(packet);
    }

    public static void syncTo(ServerPlayer player) {
        PerkData.get(player).ifPresent(data ->
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
                                data.getSharedFortunePartnerId().orElse(null),
                                data.getSharedFortunePartnerName(),
                                data.getSharedFortuneRebindCooldownSeconds(),
                                PlatformServices.config().hiddenTalentIds(),
                                data.getPerkRanks(),
                                data.getEnabledManualTalents(),
                                TalentEffects.buildDisplayStats(player, data),
                                data.getSkillEnhancementRanks(),
                                data.getPendingSkillEnhancementOffers(),
                                data.getPrimarySkillEnhancement(),
                                data.hasChosenPrimarySkillEnhancement(),
                                data.getChosenAegises(),
                                data.getDisabledManualAegises()
                        )
                )
        );
    }

    public static void syncDevourDataTo(ServerPlayer player) {
        PerkData.get(player).ifPresent(data ->
                NETWORK.sendToPlayer(player, SyncDevourDataPacket.from(data))
        );
    }

    public static void openScreen(
            ServerPlayer player,
            java.util.List<com.whatever.aegis_ascension.perk.Perk> offers) {
        NETWORK.sendToPlayer(player, new OpenPerkScreenPacket(offers));
    }

    public static void openAegisScreen(
            ServerPlayer player,
            java.util.List<com.whatever.aegis_ascension.aegis.Aegis> offers) {
        NETWORK.sendToPlayer(player, new OpenAegisScreenPacket(offers));
    }
}
