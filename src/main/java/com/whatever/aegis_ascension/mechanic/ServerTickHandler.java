package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.perk.soullink.MakeUpWorkClub;
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
                if (data.getShopState().tickAutoRefresh(player.serverLevel())) {
                    ModNetworking.syncShopTo(player);
                }
            });
        }

        PerkData.get(player).ifPresent(data -> {
            TalentEffects.onPlayerTick(player, data);
            PlayerPerkData.PerkMilestoneAwards perkAwards =
                    data.awardMilestonesForLevel(player.experienceLevel);
            int skillEnhancementsGranted =
                    data.awardSkillEnhancementMilestonesForLevel(player.experienceLevel);
            int aegisGranted = data.awardAegisChargesForLevel(player.experienceLevel);
            int immediateBreakthroughs = perkAwards.breakthroughsToTriggerImmediately();
            if (immediateBreakthroughs > 0) {
                TalentEffects.triggerBreakthroughs(player, data, immediateBreakthroughs);
            }
            if (perkAwards.chargesGranted() > 0) {
                sendAwardMessage(
                        player,
                        perkAwards.chargesGranted(),
                        data.getSelectionCharges()
                );
            }
            if (skillEnhancementsGranted > 0) {
                TalentEffects.recalculateAttributes(player, data);
                sendSkillEnhancementAwardMessage(
                        player,
                        skillEnhancementsGranted,
                        data.getSkillEnhancementCharges()
                );
            }
            if (aegisGranted > 0) {
                sendAegisAwardMessage(player, aegisGranted, data.getAegisSelectionCharges());
            }
            if (perkAwards.chargesGranted() > 0
                    || perkAwards.breakthroughsTriggered() > 0
                    || skillEnhancementsGranted > 0
                    || aegisGranted > 0) {
                ModNetworking.syncTo(player);
            }
        });
    }

    private static void sendAwardMessage(ServerPlayer player, int granted, int total) {
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.charge_awarded",
                granted,
                total
        ));
    }

    private static void sendSkillEnhancementAwardMessage(
            ServerPlayer player,
            int granted,
            int total
    ) {
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.skill_enhancement_charge_awarded",
                granted,
                total
        ));
    }

    private static void sendAegisAwardMessage(ServerPlayer player, int granted, int total) {
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.aegis_charge_awarded",
                granted,
                total
        ));
    }
}
