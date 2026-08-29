package com.whatever.aegis_ascension.lifecycle;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.AngelsAegis;
import com.whatever.aegis_ascension.aegis.FoxAegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Loader-neutral orchestration for player login, logout, respawn, and travel. */
public final class PlayerSessionLifecycle {
    private PlayerSessionLifecycle() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        repairNonFiniteHealth(player);
        updateApplyAndSync(player, true);
    }

    public static void onPlayerLogout(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            AngelsAegis.resetTimer(serverPlayer);
            FoxAegis.resetSummonTimer(serverPlayer);
            ShieldMechanic.clear(serverPlayer);
        }
        PlayerDataLifecycle.onPlayerLogout(player.getUUID());
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        AngelsAegis.resetTimer(player);
        FoxAegis.resetSummonTimer(player);
        ShieldMechanic.clear(player);
        updateApplyAndSync(player, false);
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        AngelsAegis.resetTimer(player);
        FoxAegis.resetSummonTimer(player);
        ShieldMechanic.onPlayerChangedDimension(player);
        updateApplyAndSync(player, false);
    }

    private static void updateApplyAndSync(
            ServerPlayer player,
            boolean announceNewCharges
    ) {
        PerkData.get(player).ifPresent(data -> {
            PlayerPerkData.PerkMilestoneAwards perkAwards =
                    data.awardMilestonesForLevel(player.experienceLevel);
            int skillEnhancementsGranted =
                    data.awardSkillEnhancementMilestonesForLevel(player.experienceLevel);
            int aegisGranted = data.awardAegisChargesForLevel(player.experienceLevel);
            SoulLinkEffects.refreshCachedState(player, data);
            data.applyChosenPerks(player);
            SummonCompat.refreshOwnedSummons(player, data);
            int immediateBreakthroughs = perkAwards.breakthroughsToTriggerImmediately();
            if (immediateBreakthroughs > 0) {
                TalentEffects.triggerBreakthroughs(
                        player,
                        data,
                        immediateBreakthroughs
                );
            }
            if (announceNewCharges && perkAwards.chargesGranted() > 0) {
                sendAwardMessage(
                        player,
                        perkAwards.chargesGranted(),
                        data.getSelectionCharges()
                );
            }
            if (announceNewCharges && skillEnhancementsGranted > 0) {
                sendSkillEnhancementAwardMessage(
                        player,
                        skillEnhancementsGranted,
                        data.getSkillEnhancementCharges()
                );
            }
            if (announceNewCharges && aegisGranted > 0) {
                sendAegisAwardMessage(player, aegisGranted, data.getAegisSelectionCharges());
            }
            ModNetworking.syncTo(player);
        });
    }

    /** Repairs a non-finite health value before it can permanently brick the player. */
    private static void repairNonFiniteHealth(ServerPlayer player) {
        if (GeneralServerMethods.repairNonFiniteVitals(player)) {
            AegisAscensionMod.getLogger().warn(
                    "Repaired dead-alive state for {} at login: absorption and/or health were "
                            + "non-finite. Players can also fix this themselves with /perk repair.",
                    player.getGameProfile().getName()
            );
        }
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
