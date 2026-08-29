package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Stable public facade for talent runtime effects.
 *
 * <p>Implementations are separated by responsibility so existing integrations can
 * keep calling this class while progression, combat, and stat logic evolve
 * independently.</p>
 */
public final class TalentEffects {
    private TalentEffects() {
    }

    public static void onTalentAcquired(ServerPlayer player, PlayerPerkData data,
                                        Perk perk, int newRank) {
        onTalentAcquired(player, data, perk, newRank, true);
    }

    public static void onTalentAcquired(ServerPlayer player, PlayerPerkData data,
                                        Perk perk, int newRank,
                                        boolean triggerRewardChains) {
        TalentProgressionEffects.onTalentAcquired(
                player, data, perk, newRank, triggerRewardChains
        );
        SoulLinkEffects.onTalentAcquired(player, data);
    }

    public static void onTalentSelected(ServerPlayer player, PlayerPerkData data) {
        TalentProgressionEffects.onTalentSelected(player, data);
    }

    public static void triggerBreakthroughs(ServerPlayer player, PlayerPerkData data,
                                            int count) {
        TalentProgressionEffects.triggerBreakthroughs(player, data, count);
    }

    public static void recalculateAttributes(Player player, PlayerPerkData data) {
        TalentStatService.recalculateAttributes(player, data);
    }

    public static void onPlayerTick(ServerPlayer player, PlayerPerkData data) {
        TalentCombatEffects.onPlayerTick(player, data);
        SoulLinkEffects.onPlayerTick(player, data);
    }

    public static float onLivingHurt(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        return TalentCombatEffects.onLivingHurt(target, source, amount);
    }

    public static void captureLivingHurt(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        TalentCombatEffects.captureLivingHurt(target, source, amount);
    }

    public static void clearLivingHurt(LivingEntity target, DamageSource source) {
        TalentCombatEffects.clearLivingHurt(target, source);
    }

    public static boolean shouldCancelLivingHeal(LivingEntity target) {
        return TalentCombatEffects.shouldCancelLivingHeal(target);
    }

    public static float onLivingDamage(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        return TalentCombatEffects.onLivingDamage(target, source, amount);
    }

    public static boolean onLivingDeath(LivingEntity target, DamageSource source) {
        return TalentCombatEffects.onLivingDeath(target, source);
    }

    public static void onFinalLivingDamage(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        TalentCombatEffects.onFinalLivingDamage(target, source, amount);
    }

    public static double magicConversionMaximumMana(PlayerPerkData data) {
        return TalentStatService.magicConversionMaximumMana(data);
    }

    public static double frierenMaximumMana(PlayerPerkData data) {
        return TalentStatService.frierenMaximumMana(data);
    }

    public static Map<String, Double> buildDisplayStats(Player player,
                                                        PlayerPerkData data) {
        return TalentStatService.buildDisplayStats(player, data);
    }

    public static double cooldownReduction(PlayerPerkData data) {
        return TalentStatService.cooldownReduction(data);
    }

    public static double shieldGain(Player player, PlayerPerkData data) {
        return TalentStatService.shieldGain(player, data);
    }

    public static double shieldGainExcludingPerk(Player player, PlayerPerkData data,
                                                  String excludedPerkId) {
        return TalentStatService.shieldGainExcludingPerk(player, data, excludedPerkId);
    }

    public static double shieldGainMultiplier(PlayerPerkData data) {
        return TalentStatService.shieldGainMultiplier(data);
    }

    public static double luckyStrike(Player player, PlayerPerkData data) {
        return TalentStatService.luckyStrike(player, data);
    }

    public static double luckyStrikeMultiplier(Player player, PlayerPerkData data) {
        return TalentStatService.luckyStrikeMultiplier(player, data);
    }

    public static double experienceGainBonus(PlayerPerkData data) {
        return TalentStatService.experienceGainBonus(data);
    }

    public static double manaRegenerationMultiplier(PlayerPerkData data) {
        return TalentStatService.manaRegenerationMultiplier(data);
    }

    public static double talentOptionBonus(Player player, PlayerPerkData data) {
        return TalentStatService.talentOptionBonus(player, data);
    }
}
