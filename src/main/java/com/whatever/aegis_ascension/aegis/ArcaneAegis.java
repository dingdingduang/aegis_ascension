package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.perk.TalentConstants.ALL_SKILL_ENHANCEMENT_ATTRIBUTE;
import static com.whatever.aegis_ascension.perk.TalentConstants.CRITICAL_DAMAGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.LUCKY_STRIKE;
import static com.whatever.aegis_ascension.perk.TalentConstants.PRIMARY_FLAT;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Arcane Aegis: the barrage aegis.
 *
 * <p>This is the whole implementation of the aegis's {@code aegises.json} entry, and the
 * public facade the barrage addon calls. Nothing here links against the addon directly:
 * barrages are identified by string id so the optional integration remains loader-safe.</p>
 *
 * <h2>① Breakthrough</h2>
 * <p>Each Breakthrough permanently adds {@code barrage_missile_speed},
 * {@code barrage_damage}, and {@code barrage_area} (applied in
 * {@code TalentEffects#triggerBreakthrough}, scaled by the usual Breakthrough-effect
 * multiplier). {@link #missileSpeedMultiplier}, {@link #damageMultiplier}, and
 * {@link #areaMultiplier} expose the accumulated totals as ready-to-use multipliers.</p>
 *
 * <h2>② Per-barrage permanent bonuses</h2>
 * <p>Every fired barrage rolls {@code barrage_trigger_chance}; on success the barrage's
 * own permanent bonus is granted, up to that barrage's max trigger count. See
 * {@link #onBarrageFired}.</p>
 *
 * <h2>Damage multiplier roll and the speed cap</h2>
 * <p>Every barrage shot rolls x{@code extra_dmg_multiplier_one} or
 * x{@code extra_dmg_multiplier_two} ({@link #rollExtraDamageMultiplier}). Missile speed is
 * capped at {@code barrage_missile_speed_cap}; every excess 1% is converted into barrage
 * damage instead of being wasted.</p>
 */
public final class ArcaneAegis {
    private ArcaneAegis() {
    }

    /** Whether this caster currently has Arcane Aegis selected and enabled. */
    public static boolean active(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player)
                .map(data -> data.isAegisEnabled(AegisConstants.ARCANE))
                .orElse(false);
    }

    /** The five barrages, each with its bonus stat, bonus size, and trigger cap. */
    public enum Barrage {
        LIGHTNING("lightning_barrage",
                PRIMARY_FLAT,
                AegisConstants.LIGHTNING_BARRAGE_PRIMARY_STAT_BONUS,
                AegisConstants.LIGHTNING_BONUS_MAX_TRIGGER_COUNT,
                AegisConstants.ARCANE_LIGHTNING_TRIGGERS),
        MULTI_TARGET("multi_target_barrage",
                ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                AegisConstants.MULTI_BARRAGE_ALL_SKILL_ENHANCEMENT_ATTRIBUTE_STAT_BONUS,
                AegisConstants.MULTI_TARGET_BONUS_MAX_TRIGGER_COUNT,
                AegisConstants.ARCANE_MULTI_TARGET_TRIGGERS),
        FIRE("fire_barrage",
                CRITICAL_DAMAGE,
                AegisConstants.FIRE_BARRAGE_CRITICAL_DAMAGE_STAT_BONUS,
                AegisConstants.FIRE_BONUS_MAX_TRIGGER_COUNT,
                AegisConstants.ARCANE_FIRE_TRIGGERS),
        PHOENIX("phoenix_barrage",
                AegisConstants.ARCANE_BARRAGE_DAMAGE_BONUS,
                AegisConstants.PHOENIX_BARRAGE_DAMAGE_BOOST_STAT_BONUS,
                AegisConstants.PHOENIX_BONUS_MAX_TRIGGER_COUNT,
                AegisConstants.ARCANE_PHOENIX_TRIGGERS),
        ARMOR_SHRED("armor_shred_barrage",
                LUCKY_STRIKE,
                AegisConstants.ARMOR_SHRED_LUCK_STRIKE_STAT_BONUS,
                AegisConstants.ARMOR_SHRED_BONUS_MAX_TRIGGER_COUNT,
                AegisConstants.ARCANE_ARMOR_SHRED_TRIGGERS);

        private final String id;
        private final String bonusStat;
        private final String bonusPerTriggerKey;
        private final String maxTriggerCountKey;
        private final String triggerCountStat;

        Barrage(String id, String bonusStat, String bonusPerTriggerKey,
                String maxTriggerCountKey, String triggerCountStat) {
            this.id = id;
            this.bonusStat = bonusStat;
            this.bonusPerTriggerKey = bonusPerTriggerKey;
            this.maxTriggerCountKey = maxTriggerCountKey;
            this.triggerCountStat = triggerCountStat;
        }

        public String id() {
            return id;
        }

        /** The custom stat this barrage's bonus accumulates into. */
        public String bonusStat() {
            return bonusStat;
        }

        /** The custom stat holding how many times this barrage's bonus has triggered. */
        public String triggerCountStat() {
            return triggerCountStat;
        }

        /** Resolves a barrage by its config id, accepting the full spell id too. */
        public static Barrage byId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            String normalized = id.toLowerCase(Locale.ROOT);
            int colon = normalized.indexOf(':');
            if (colon >= 0) {
                normalized = normalized.substring(colon + 1);
            }
            for (Barrage barrage : values()) {
                if (barrage.id.equals(normalized)) {
                    return barrage;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    // ② Per-barrage permanent bonuses
    // ------------------------------------------------------------------

    /**
     * Rolls a fired barrage's permanent-bonus chance and grants the bonus on success.
     *
     * <p>Call once per volley fired, with the barrage's config id (for example
     * {@code "lightning_barrage"} or {@code "myweirdironspellbookaddon:lightning_barrage"}).
     * Does nothing unless the caster is a player with Arcane Aegis enabled.</p>
     *
     * @return true when a bonus was granted this call
     */
    public static boolean onBarrageFired(LivingEntity caster, String barrageId) {
        Barrage barrage = Barrage.byId(barrageId);
        if (barrage == null || !(caster instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player)
                .map(data -> grantBonus(player, data, barrage))
                .orElse(false);
    }

    private static boolean grantBonus(ServerPlayer player, PlayerPerkData data, Barrage barrage) {
        if (!data.isAegisEnabled(AegisConstants.ARCANE)) {
            return false;
        }
        double chance = clamp01(aegisStat(AegisConstants.BARRAGE_TRIGGER_CHANCE));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return false;
        }
        // Each barrage's bonus stops accumulating once it has triggered its cap.
        int triggers = (int) Math.max(0.0D, data.getCustomStat(barrage.triggerCountStat));
        int maxTriggers = (int) Math.max(0.0D, aegisStat(barrage.maxTriggerCountKey));
        if (triggers >= maxTriggers) {
            return false;
        }
        double bonus = aegisStat(barrage.bonusPerTriggerKey);
        if (bonus == 0.0D) {
            return false;
        }
        data.addCustomStat(barrage.triggerCountStat, 1.0D);
        data.addCustomStat(barrage.bonusStat, bonus);
        TalentEffects.recalculateAttributes(player, data);
        ModNetworking.syncTo(player);
        return true;
    }

    // ------------------------------------------------------------------
    // ① Breakthrough totals, exposed as multipliers
    // ------------------------------------------------------------------

    /**
     * Barrage missile-speed multiplier, capped at
     * {@code 1 + barrage_missile_speed_cap}. Speed past the cap is not lost — it is
     * converted into damage by {@link #damageMultiplier}.
     */
    public static double missileSpeedMultiplier(LivingEntity caster) {
        return readAegis(caster, data ->
                1.0D + Math.min(accumulatedMissileSpeed(data), missileSpeedCap()));
    }

    /**
     * Barrage damage multiplier: the accumulated {@code barrage_damage} plus the damage
     * converted from missile speed above the cap.
     */
    public static double damageMultiplier(LivingEntity caster) {
        return readAegis(caster, data -> {
            double damage = data.getCustomStat(AegisConstants.BARRAGE_DAMAGE);
            double excessSpeed = Math.max(0.0D, accumulatedMissileSpeed(data) - missileSpeedCap());
            if (excessSpeed > 0.0D) {
                // "each excessive 1% missile speed grants N% damage": the stat is the
                // damage granted per one percentage point of excess speed.
                double perPercent = aegisStat(AegisConstants
                        .PER_ONE_PERCENT_BARRAGE_MISSILE_SPEED_CONVERT_TO_BARRAGE_MISSILE_DAMAGE);
                damage += excessSpeed * 100.0D * perPercent;
            }
            return 1.0D + damage;
        });
    }

    /** Barrage target-acquisition range multiplier from accumulated {@code barrage_area}. */
    public static double areaMultiplier(LivingEntity caster) {
        return readAegis(caster, data -> 1.0D + data.getCustomStat(AegisConstants.BARRAGE_AREA));
    }

    // ------------------------------------------------------------------
    // Per-shot damage multiplier roll
    // ------------------------------------------------------------------

    /**
     * Rolls the barrage damage multiplier for one shot: x{@code extra_dmg_multiplier_two}
     * at its chance, otherwise x{@code extra_dmg_multiplier_one} at its chance, otherwise
     * x1. Returns 1 when the aegis is inactive, so callers can multiply unconditionally.
     */
    public static double rollExtraDamageMultiplier(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1.0D;
        }
        return PerkData.get(player).map(data -> {
            if (!data.isAegisEnabled(AegisConstants.ARCANE)) {
                return 1.0D;
            }
            double twoChance = clamp01(aegisStat(AegisConstants.EXTRA_DMG_MULTIPLIER_TWO_CHANCE));
            double oneChance = Math.min(
                    clamp01(aegisStat(AegisConstants.EXTRA_DMG_MULTIPLIER_ONE_CHANCE)),
                    1.0D - twoChance);
            double roll = player.getRandom().nextDouble();
            if (roll < twoChance) {
                return Math.max(1.0D, aegisStat(AegisConstants.EXTRA_DMG_MULTIPLIER_TWO));
            }
            if (roll < twoChance + oneChance) {
                return Math.max(1.0D, aegisStat(AegisConstants.EXTRA_DMG_MULTIPLIER_ONE));
            }
            return 1.0D;
        }).orElse(1.0D);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static double accumulatedMissileSpeed(PlayerPerkData data) {
        return Math.max(0.0D, data.getCustomStat(AegisConstants.BARRAGE_MISSILE_SPEED));
    }

    private static double missileSpeedCap() {
        double cap = aegisStat(AegisConstants.BARRAGE_MISSILE_SPEED_CAP);
        return cap > 0.0D ? cap : Double.MAX_VALUE;
    }

    /** Reads a value from the caster's data, or 1 when Arcane Aegis is not active. */
    private static double readAegis(LivingEntity caster,
                                    java.util.function.ToDoubleFunction<PlayerPerkData> reader) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1.0D;
        }
        return PerkData.get(player)
                .map(data -> data.isAegisEnabled(AegisConstants.ARCANE)
                        ? reader.applyAsDouble(data)
                        : 1.0D)
                .orElse(1.0D);
    }

    private static double aegisStat(String statKey) {
        return Aegis.byId(AegisConstants.ARCANE).map(aegis -> aegis.stat(statKey)).orElse(0.0D);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
