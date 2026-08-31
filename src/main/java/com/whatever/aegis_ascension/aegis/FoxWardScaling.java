package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT_BASE;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT_BONUS;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_DURATION_SECONDS;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_RANGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_RANGE_BONUS;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_WARD_I_COUNT;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_WARD_II_INTERVAL_REDUCTION;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_DIVINE_SAKURA_POWER;
import static com.whatever.aegis_ascension.util.GeneralIronSpellSupportMethods.readCustomData;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Public scaling facade for Fox God's Aegis ward spells.
 *
 * <p>This is the only Aegis Ascension type the ward addon needs to reference. Every
 * method takes a plain {@link LivingEntity} caster, does the player-data lookup
 * internally, and returns a plain number — so the addon depends on Aegis Ascension
 * optionally and shallowly. When the caster is not a Aegis Ascension player the
 * multipliers fall back to {@code 1.0} and the primary stat to {@code 0.0}.</p>
 *
 * <p>The constellation-driven multipliers here are the real ward effect/range
 * scaling: Divine Sakura Power C0/C3/C6 raise {@link #effectMultiplier} (ward
 * damage and healing), and C1/C6 raise {@link #rangeMultiplier} (ward AoE/attack
 * range).</p>
 */
public final class FoxWardScaling {
    private static final int MAX_WARDS_PER_CAST = 64;

    private FoxWardScaling() {
    }

    /** Whether the caster currently has Fox God's Aegis enabled. */
    public static boolean active(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player).map(FoxWardScaling::isActive).orElse(false);
    }

    /**
     * Number of wards created by one explicit ward spell cast.
     *
     * <p>Every cast creates one ward. Fox God's Aegis adds its configured summon
     * count, and Divine Sakura C5 adds its type-I-only count. Nothing here casts a
     * spell or schedules another cast.</p>
     */
    public static int summonCount(LivingEntity caster, boolean wardTypeI) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1;
        }
        return PerkData.get(player).map(data -> {
            if (!isActive(data)) {
                return 1;
            }
            int count = 1;
            count += nonNegativeInt(aegisStat(AegisConstants.WARD_SUMMON_COUNT));
            if (wardTypeI && FoxAegis.constellationCount(data) >= 5) {
                count += nonNegativeInt(requiredTalent().stat(CONSTELLATION_WARD_I_COUNT));
            }
            return Math.max(1, Math.min(MAX_WARDS_PER_CAST, count));
        }).orElse(1);
    }

    /** Whether manually cast wards receive Fox God's invulnerable-armor rule. */
    public static boolean invulnerable(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player)
                .map(data -> isActive(data)
                        && aegisStat(AegisConstants.WARD_INVULNERABLE) > 0.0D)
                .orElse(false);
    }

    /** Ward lifespan added by Divine Sakura C2, in seconds. */
    public static double durationBonusSeconds(LivingEntity caster) {
        return readCustomData(caster, data -> isActive(data)
                && FoxAegis.constellationCount(data) >= 2
                ? Math.max(0.0D, requiredTalent().stat(CONSTELLATION_DURATION_SECONDS))
                : 0.0D, 0.0D);
    }

    /** Ward Type II action-interval multiplier from Divine Sakura C4. */
    public static double wardTypeIIIntervalMultiplier(LivingEntity caster) {
        return readCustomData(caster, data -> isActive(data)
                && FoxAegis.constellationCount(data) >= 4
                ? Math.max(0.0D, 1.0D - requiredTalent().stat(
                        CONSTELLATION_WARD_II_INTERVAL_REDUCTION))
                : 1.0D, 1.0D);
    }

    /** Ward damage/healing multiplier from Divine Sakura constellations (C0/C3/C6). */
    public static double effectMultiplier(LivingEntity caster) {
        return readCustomData(caster, FoxWardScaling::computeEffectMultiplier, 1.0D);
    }

    /** Ward AoE/attack-range multiplier from Divine Sakura constellations (C1/C6). */
    public static double rangeMultiplier(LivingEntity caster) {
        return readCustomData(caster, FoxWardScaling::computeRangeMultiplier, 1.0D);
    }

    private static double computeEffectMultiplier(PlayerPerkData data) {
        if (!isActive(data)) {
            return 1.0D;
        }
        int constellation = FoxAegis.constellationCount(data);
        if (constellation < 0) {
            return 1.0D;
        }
        Perk talent = Perk.byId(PERK_DIVINE_SAKURA_POWER).orElseThrow();
        double multiplier = 1.0D + talent.stat(CONSTELLATION_EFFECT_BASE);
        if (constellation >= 3) {
            multiplier += talent.stat(CONSTELLATION_EFFECT);
        }
        if (constellation >= 6) {
            multiplier += talent.stat(CONSTELLATION_EFFECT_BONUS);
        }
        return multiplier;
    }

    private static double computeRangeMultiplier(PlayerPerkData data) {
        if (!isActive(data)) {
            return 1.0D;
        }
        int constellation = FoxAegis.constellationCount(data);
        if (constellation < 1) {
            return 1.0D;
        }
        Perk talent = Perk.byId(PERK_DIVINE_SAKURA_POWER).orElseThrow();
        double multiplier = 1.0D + talent.stat(CONSTELLATION_RANGE);
        if (constellation >= 6) {
            multiplier += talent.stat(CONSTELLATION_RANGE_BONUS);
        }
        return multiplier;
    }

    private static double aegisStat(String statKey) {
        return Aegis.byId(AegisConstants.FOX_GOD)
                .map(aegis -> aegis.stat(statKey))
                .orElse(0.0D);
    }

    private static boolean isActive(PlayerPerkData data) {
        return data.isAegisEnabled(AegisConstants.FOX_GOD);
    }

    private static Perk requiredTalent() {
        return Perk.byId(PERK_DIVINE_SAKURA_POWER).orElseThrow();
    }

    private static int nonNegativeInt(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(value));
    }
}
