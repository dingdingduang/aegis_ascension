package com.whatever.aegis_ascension.util;

/** Minecraft-free arithmetic for the Dodge Chance mechanic. */
public final class DodgeMath {
    private DodgeMath() {
    }

    /**
     * The share of accumulated Dodge Chance a roll may actually use. Accumulated
     * Dodge Chance stays uncapped so Clear Mind State can still convert the part
     * the cap discards.
     */
    public static double effectiveChance(double accumulatedChance, double maximumChance) {
        if (!Double.isFinite(accumulatedChance) || accumulatedChance <= 0.0D) {
            return 0.0D;
        }
        double maximum = Double.isFinite(maximumChance)
                ? Math.max(0.0D, maximumChance)
                : 0.0D;
        return Math.min(maximum, accumulatedChance);
    }

    /**
     * Clear Mind State's Calling the Blue Waves conversion: every {@code chanceStep}
     * of accumulated Dodge Chance grants {@code skillDamagePerStep} Skill Damage.
     * The accumulated chance is deliberately the uncapped one.
     */
    public static double skillDamage(double accumulatedChance, double chanceStep,
                                     double skillDamagePerStep) {
        if (!Double.isFinite(accumulatedChance) || !Double.isFinite(chanceStep)
                || !Double.isFinite(skillDamagePerStep) || chanceStep <= 0.0D) {
            return 0.0D;
        }
        double converted = Math.max(0.0D, accumulatedChance) / chanceStep
                * skillDamagePerStep;
        return Double.isFinite(converted) ? converted : 0.0D;
    }
}
