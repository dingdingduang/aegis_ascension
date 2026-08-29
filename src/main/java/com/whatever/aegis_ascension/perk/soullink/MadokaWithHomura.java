package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import net.minecraft.server.level.ServerPlayer;

/** Converts penalties negated by Law of Cycle into configurable positive effects. */
public final class MadokaWithHomura {
    private MadokaWithHomura() {
    }

    public static boolean isActive(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MADOKA_WITH_HOMURA);
    }

    public static double convertFlatPenalty(PlayerPerkData data, double penalty) {
        return isActive(data)
                ? Math.abs(penalty) * Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, FLAT_PENALTY_CONVERSION_MULTIPLIER
        )) : 0.0D;
    }

    public static double convertPercentagePenalty(PlayerPerkData data, double penalty) {
        return isActive(data)
                ? Math.abs(penalty) * Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, PERCENTAGE_PENALTY_CONVERSION_MULTIPLIER
        )) : 0.0D;
    }

    public static double convertDamageReductionPenalty(PlayerPerkData data,
                                                        double penalty) {
        return isActive(data)
                ? Math.abs(penalty) * Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, DAMAGE_REDUCTION_CONVERSION_MULTIPLIER
        )) : 0.0D;
    }

    public static double fixedMaxHealthMultiplierBonus(PlayerPerkData data) {
        return isActive(data) ? Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, FIXED_MAX_HEALTH_MULTIPLIER_BONUS
        )) : 0.0D;
    }

    /** Replaces a Law-negated nonnumeric punishment with one weighted random charge. */
    public static boolean grantNonnumericReward(ServerPlayer player, PlayerPerkData data) {
        if (!isActive(data)) {
            return false;
        }
        double refreshWeight = Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, NONNUMERIC_PERK_REFRESH_WEIGHT
        ));
        double perkWeight = Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, NONNUMERIC_PERK_CHARGE_WEIGHT
        ));
        double skillWeight = Math.max(0.0D, stat(
                SOUL_MADOKA_WITH_HOMURA, NONNUMERIC_SKILL_CHARGE_WEIGHT
        ));
        double total = refreshWeight + perkWeight + skillWeight;
        if (total <= 0.0D) {
            return false;
        }

        double roll = player.getRandom().nextDouble() * total;
        if (roll < refreshWeight) {
            int amount = integerStat(NONNUMERIC_PERK_REFRESH_AMOUNT);
            data.addPerkRefreshCharges(amount);
            notify(player, "perk_refresh", amount);
        } else if (roll < refreshWeight + perkWeight) {
            int amount = integerStat(NONNUMERIC_PERK_CHARGE_AMOUNT);
            data.addSelectionCharges(amount);
            notify(player, "perk", amount);
        } else {
            int amount = integerStat(NONNUMERIC_SKILL_CHARGE_AMOUNT);
            data.addSkillEnhancementCharges(amount);
            notify(player, "skill", amount);
        }
        return true;
    }

    private static int integerStat(String key) {
        return Math.max(0, (int) Math.round(stat(SOUL_MADOKA_WITH_HOMURA, key)));
    }

    private static void notify(ServerPlayer player, String reward, int amount) {
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.madoka_homura." + reward,
                amount
        ));
    }
}
