package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.perk.soullink.MakeUpWorkClub;
import com.whatever.aegis_ascension.shop.ShopType;
import com.whatever.aegis_ascension.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Loader-neutral entry points for the common server-side tick pipeline. */
public final class ServerTickHandler {
    /** How often each player's shop is checked for daily rollover (~5s at 20 tps). */
    private static final int SHOP_RESET_CHECK_INTERVAL_TICKS = 100;

    private ServerTickHandler() {
    }

    public static void onLivingTick(LivingEntity living) {
        if (!living.level().isClientSide()) {
            MakeUpWorkClub.tick(living);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        // ShopState compares day indices, so a late sweep or server downtime still
        // rerolls exactly once after the 24000-tick boundary is crossed.
        if (player.tickCount % SHOP_RESET_CHECK_INTERVAL_TICKS == 0) {
            PerkData.get(player).ifPresent(data -> {
                for (ShopType shopType : ShopType.values()) {
                    if (data.getShopState(shopType)
                            .tickAutoRefresh(player.serverLevel(), data)) {
                        ModNetworking.syncShopTo(player, shopType);
                    }
                }
            });
        }

        PerkData.get(player).ifPresent(data -> {
            boolean questStructureChanged = QuestManager.tick(player, data);
            QuestManager.sampleWalkMovement(player, data);
            if (player.tickCount % 20 == 0) {
                QuestManager.onBiomeVisited(player);
            }
            TalentEffects.onPlayerTick(player, data);
            AegisExperienceSystem.MilestoneResult milestones =
                    AegisExperienceSystem.awardMilestones(player, data, true);
            if (milestones.changed()) {
                ModNetworking.syncPerkDataTo(player);
            }
            if (questStructureChanged) {
                ModNetworking.syncQuestsTo(player);
            }
            QuestManager.flushPendingProgressSync(player);
        });
    }

}
