package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.mechanic.TalentStatService.*;
import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ApothicAttributesCompat;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.soullink.MakeUpWorkClub;
import com.whatever.aegis_ascension.perk.soullink.TeamRadiance;
import com.whatever.aegis_ascension.perk.talents.FairTrade;
import com.whatever.aegis_ascension.perk.talents.PerfectAndElegantServant;
import com.whatever.aegis_ascension.perk.talents.TeamStar;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * The scalar damage formulas used by combat effects.
 *
 * <p>Each method returns one ready-to-multiply value. Damage Bonus, Critical
 * Damage, Lucky Strike, and Final Damage form the common multiplier. Physical,
 * Magic, Skill, True, Independent, and direct-melee Attack Damage Amplification
 * remain separate multiplication stages.</p>
 */
final class TalentDamageCalculations {
    private TalentDamageCalculations() {
    }

    static double damageCommonCalculation(
            ServerPlayer player,
            PlayerPerkData data,
            double situationalFinalDamage
    ) {
        return safeMultiplier(
                damageDamageBonusCalculation(player, data)
                        * damageCriticalDamageCalculation(player, data)
                        * damageLuckStrikeCalculation(player, data)
                        * damageFinalCalculation(player, data, situationalFinalDamage)
        );
    }

    static double damageDamageBonusCalculation(
            ServerPlayer player,
            PlayerPerkData data
    ) {
        double bonus = data.getCustomStat(WALK_DAMAGE)
                + data.getCustomStat(FROSTBITE_DAMAGE)
                + data.getCustomStat(INNATE_DAMAGE)
                + data.getCustomStat(TOP_DAMAGE)
                + data.getCustomStat(DOMINUS_SHIELD_DAMAGE_BONUS)
                + FairTrade.damageBonus(data)
                + TeamStar.damageBonus(player)
                + VirtualItems.statBonus(data, VirtualItems.DAMAGE_BONUS)
                + sumOwnedStat(data, DAMAGE_BONUS);
        return additiveMultiplier(bonus);
    }

    static double damagePhysicalCalculation(PlayerPerkData data) {
        double amplification = data.getCustomStat(LUNAR_DAMAGE)
                + data.getCustomStat(PHYSICAL_DAMAGE_AMPLIFICATION)
                + data.getCustomStat(CIALLO_PHYSICAL_DAMAGE_AMPLIFICATION)
                * yuzusoftFanMultiplier(data)
                + sumOwnedStat(data, PHYSICAL_DAMAGE_AMPLIFICATION);
        if (data.owns(PERK_COLLECTOR)) {
            amplification += stat(
                    PERK_COLLECTOR,
                    PHYSICAL_DAMAGE_AMPLIFICATION_PER_SOUL_LINK
            ) * data.getActiveSoulLinks().size()
                    * MakeUpWorkClub.collectorMultiplier(data);
        }
        return additiveMultiplier(amplification);
    }

    static double damageMagicCalculation(PlayerPerkData data) {
        return safeMultiplier(
                additiveMultiplier(magicDamageBonus(data))
                        * additiveMultiplier(magicAmplification(data))
        );
    }

    static double damageSkillCalculation(Player player, PlayerPerkData data) {
        return additiveMultiplier(
                skillDamageBonus(player, data, luckyStrike(player, data))
        );
    }

    /**
     * Ganyu's Blessing distance bonus. Extracted so the converted damage path can apply the
     * same curve rather than reimplementing it.
     */
    static double ganyuDistanceMultiplier(ServerPlayer attacker, PlayerPerkData data,
                                          LivingEntity target) {
        if (!data.owns(PERK_GANYUS_BLESSING)) {
            return 1.0D;
        }
        Perk ganyu = Perk.byId(PERK_GANYUS_BLESSING).orElseThrow();
        double distance = attacker.distanceTo(target);
        if (distance < ganyu.stat(MINIMUM_DAMAGE_DISTANCE)) {
            return 1.0D;
        }
        return safeMultiplier(1.0D + (distance - ganyu.stat(DISTANCE_DAMAGE_OFFSET))
                * ganyu.stat(DAMAGE_MULTIPLIER_PER_DISTANCE));
    }

    static double damageTrueCalculation(PlayerPerkData data) {
        return additiveMultiplier(
                data.getCustomStat(TRUE_DAMAGE) + sumOwnedStat(data, TRUE_DAMAGE)
        );
    }

    static double damageAttackAmplificationCalculation(PlayerPerkData data) {
        double amplification = sumOwnedStat(data, ATTACK_DAMAGE_AMPLIFICATION);
        if (data.owns(PERK_RIGHTEOUS_KNIGHT)) {
            amplification += data.getCustomStat(KNIGHT_DAMAGE);
        }
        if (data.hasActiveSoulLink(SOUL_COMBO_TECHNIQUE)) {
            amplification += bonusStat(
                    SOUL_COMBO_TECHNIQUE,
                    ATTACK_DAMAGE_AMPLIFICATION
            );
        }
        return additiveMultiplier(amplification);
    }

    static double damageIndependentCalculation(PlayerPerkData data) {
        return additiveMultiplier(
                data.getCustomStat(INDEPENDENT_DAMAGE_AMPLIFICATION)
                        + sumOwnedStat(data, INDEPENDENT_DAMAGE_AMPLIFICATION)
        );
    }

    static double damageLuckStrikeCalculation(Player player, PlayerPerkData data) {
        return Math.max(0.0D, luckyStrikeMultiplier(player, data));
    }

    /** Critical stage for normal LivingHurt input, which Apothic may already modify. */
    static double damageCriticalDamageCalculation(
            ServerPlayer player,
            PlayerPerkData data
    ) {
        if (ApothicAttributesCompat.handlesCriticalHits(player)) {
            return 1.0D;
        }
        double chance = criticalChance(data);
        if (chance <= 0.0D
                || player.getRandom().nextDouble() >= Math.min(1.0D, chance)) {
            return 1.0D;
        }
        double criticalDamage = criticalDamageBonus(data)
                + flameCriticalDamage(data, chance)
                + millenniumOverflowCriticalDamage(data, chance);
        return Math.max(1.0D, 1.5D + criticalDamage);
    }

    /** Critical stage for raw captured damage used by True Damage conversion. */
    static double damageCriticalDamageCalculationFromRaw(
            ServerPlayer player,
            PlayerPerkData data
    ) {
        if (!ApothicAttributesCompat.handlesCriticalHits(player)) {
            return damageCriticalDamageCalculation(player, data);
        }

        double chance = Math.max(0.0D,
                ApothicAttributesCompat.criticalChance(player, criticalChance(data)));
        double criticalDamage = Math.max(0.0D,
                ApothicAttributesCompat.criticalDamage(
                        player,
                        1.5D + criticalDamageBonus(data)
                ));
        double multiplier = 1.0D;
        int safety = 0;
        while (chance > 0.0D && criticalDamage > 1.0D && safety++ < 4096) {
            if (player.getRandom().nextDouble() > Math.min(1.0D, chance)) {
                break;
            }
            multiplier = safeMultiplier(multiplier * criticalDamage);
            chance -= 1.0D;
            criticalDamage *= 0.85D;
        }
        return Math.max(1.0D, multiplier);
    }

    static double damageFinalCalculation(
            ServerPlayer player,
            PlayerPerkData data,
            double situationalFinalDamage
    ) {
        double bonus = data.getCustomStat(FINAL_DAMAGE)
                + data.getCustomStat(BLAZING_BREAKTHROUGH_DAMAGE)
                + data.getCustomStat(CIALLO_FINAL_DAMAGE) * yuzusoftFanMultiplier(data)
                + PerfectAndElegantServant.finalDamage(data)
                + TeamRadiance.finalDamageBonus(data)
                + VirtualItems.statBonus(data, VirtualItems.FINAL_DAMAGE)
                + situationalFinalDamage;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            bonus += entry.getKey().stat(FINAL_DAMAGE) * entry.getValue();
        }
        if (data.owns(PERK_KOKONA)) {
            bonus += stat(PERK_KOKONA, FINAL_DAMAGE_PER_OWNED_TALENT)
                    * data.getUniqueTalentCount();
        }
        if (data.owns(PERK_FIREFLY_FLAME) && luckyStrike(player, data)
                > stat(PERK_FIREFLY_FLAME, LUCKY_STRIKE_THRESHOLD)) {
            bonus += stat(PERK_FIREFLY_FLAME, FINAL_DAMAGE_ABOVE_THRESHOLD);
        }
        if (data.isAegisEnabled(AegisConstants.HARMONY)) {
            bonus += aegisStat(AegisConstants.HARMONY, FINAL_DAMAGE)
                    * harmonyScalingFactor(data);
        }
        if (data.isAegisEnabled(AegisConstants.DESTRUCTION)) {
            bonus += Math.max(0.0D, -rawDamageResistance(data))
                    * aegisStat(
                            AegisConstants.DESTRUCTION,
                            AegisConstants.FINAL_DAMAGE_PER_NEGATIVE_DAMAGE_REDUCTION
                    );
        }
        return additiveMultiplier(bonus);
    }

    private static double additiveMultiplier(double bonus) {
        return safeMultiplier(1.0D + bonus);
    }

    private static double safeMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return value > 0.0D ? Float.MAX_VALUE : 0.0D;
        }
        return Math.min(Float.MAX_VALUE, Math.max(0.0D, value));
    }
}
