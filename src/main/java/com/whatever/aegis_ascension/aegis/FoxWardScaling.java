package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT_BASE;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_EFFECT_BONUS;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_RANGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_RANGE_BONUS;
import static com.whatever.aegis_ascension.perk.TalentConstants.R_DIVINE_SAKURA_POWER;
import static com.whatever.aegis_ascension.util.GeneralIronSpellSupportMethods.readCustomData;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.ToDoubleFunction;

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
    private FoxWardScaling() {
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
        int constellation = FoxAegis.constellationCount(data);
        if (constellation < 0) {
            return 1.0D;
        }
        Perk talent = Perk.byId(R_DIVINE_SAKURA_POWER).orElseThrow();
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
        int constellation = FoxAegis.constellationCount(data);
        if (constellation < 1) {
            return 1.0D;
        }
        Perk talent = Perk.byId(R_DIVINE_SAKURA_POWER).orElseThrow();
        double multiplier = 1.0D + talent.stat(CONSTELLATION_RANGE);
        if (constellation >= 6) {
            multiplier += talent.stat(CONSTELLATION_RANGE_BONUS);
        }
        return multiplier;
    }
}
