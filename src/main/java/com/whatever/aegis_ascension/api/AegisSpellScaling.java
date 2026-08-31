package com.whatever.aegis_ascension.api;

import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentStatService;
import com.whatever.aegis_ascension.perk.TalentConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Small, stable integration API for spell addons.
 *
 * <p>Addons can snapshot these values when a spell/projectile is created. Prefer
 * {@link AegisSpellDamage} for synchronous damage calls so the central pipeline
 * applies Skill Damage exactly once; this class remains useful for effect radius
 * and damage systems that cannot enter Minecraft's ordinary hurt pipeline. The
 * API has no Iron's Spells types, keeping it reusable across spell systems.</p>
 */
public final class AegisSpellScaling {
    private AegisSpellScaling() {
    }

    /** Additive Skill Damage expressed as a ready-to-use multiplier. */
    public static double skillDamageMultiplier(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1.0D;
        }
        return PerkData.get(player)
                .map(data -> TalentStatService.skillDamageMultiplier(player, data))
                .orElse(1.0D);
    }

    /** Skill Area and Independent Skill Area combined as independent multipliers. */
    public static double skillAreaMultiplier(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1.0D;
        }
        return PerkData.get(player)
                .map(AegisSpellScaling::skillAreaMultiplier)
                .orElse(1.0D);
    }

    public static double scaleSpellArea(LivingEntity caster, double baseArea) {
        if (!Double.isFinite(baseArea) || baseArea <= 0.0D) {
            return Math.max(0.0D, baseArea);
        }
        return baseArea * skillAreaMultiplier(caster);
    }

    public static float scaleSpellArea(LivingEntity caster, float baseArea) {
        return (float) Math.min(Float.MAX_VALUE, scaleSpellArea(caster, (double) baseArea));
    }

    private static double skillAreaMultiplier(PlayerPerkData data) {
        double skillArea = data.getCustomStat(AegisConstants.SKILL_AREA);
        double independentArea = data.getCustomStat(TalentConstants.INDEPENDENT_SKILL_AREA);
        return Math.max(0.0D, 1.0D + skillArea)
                * Math.max(0.0D, 1.0D + independentArea);
    }
}
