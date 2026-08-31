package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Global, cross-dimension rank calculation that benefits Team Star owners only. */
public final class TeamRadiance {
    private TeamRadiance() {
    }

    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (!refreshState(player, data, true)) {
            return;
        }
        data.applyChosenPerks(player);
        ModNetworking.syncTo(player);
    }

    /** Refreshes the cached global rank; callers can apply/sync alongside their own work. */
    public static boolean refreshState(ServerPlayer player, PlayerPerkData data,
                                       boolean announce) {
        int previous = cachedRank(data);
        int updated = liveRank(player, data);
        if (previous == updated) {
            return false;
        }
        data.setCustomStat(TEAM_RADIANCE_RANK, updated);
        if (announce && updated > 0) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.team_radiance.rank",
                    updated,
                    rankCap()
            ));
        } else if (announce && previous > 0) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.team_radiance.inactive"
            ));
        }
        return true;
    }

    public static int rank(PlayerPerkData data) {
        if (!data.owns(PERK_TEAM_STAR)) {
            return 0;
        }
        return cachedRank(data);
    }

    private static int cachedRank(PlayerPerkData data) {
        return Math.min(
                rankCap(),
                Math.max(0, (int) Math.floor(data.getCustomStat(TEAM_RADIANCE_RANK)))
        );
    }

    public static int liveRank(ServerPlayer player, PlayerPerkData data) {
        if (!data.owns(PERK_TEAM_STAR)) {
            return 0;
        }
        int owners = 0;
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            if (PerkData.get(online).map(onlineData -> onlineData.owns(PERK_TEAM_STAR))
                    .orElse(false)) {
                owners++;
            }
        }
        return Math.min(rankCap(), owners);
    }

    public static double talentOptionBonus(PlayerPerkData data) {
        return unlocked(data, TEAM_RADIANCE_TALENT_OPTION_RANK)
                ? stat(SOUL_TEAM_RADIANCE, TALENT_OPTION_BONUS) : 0.0D;
    }

    public static double breakthroughEffectBonus(PlayerPerkData data) {
        return unlocked(data, TEAM_RADIANCE_BREAKTHROUGH_RANK)
                ? stat(SOUL_TEAM_RADIANCE, BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS) : 0.0D;
    }

    public static double allSkillEnhancementBonus(PlayerPerkData data) {
        return unlocked(data, TEAM_RADIANCE_ALL_SKILL_RANK)
                ? stat(SOUL_TEAM_RADIANCE, ALL_SKILL_ENHANCEMENT_ATTRIBUTE) : 0.0D;
    }

    public static double finalDamageBonus(PlayerPerkData data) {
        return unlocked(data, TEAM_RADIANCE_FINAL_DAMAGE_RANK)
                ? stat(SOUL_TEAM_RADIANCE, FINAL_DAMAGE) : 0.0D;
    }

    private static boolean unlocked(PlayerPerkData data, String thresholdKey) {
        int threshold = Math.max(1, (int) Math.round(
                stat(SOUL_TEAM_RADIANCE, thresholdKey)
        ));
        return rank(data) >= threshold;
    }

    private static int rankCap() {
        return Math.max(1, (int) Math.round(
                stat(SOUL_TEAM_RADIANCE, TEAM_RADIANCE_RANK_CAP)
        ));
    }
}
