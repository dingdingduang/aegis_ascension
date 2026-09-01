package com.whatever.aegis_ascension.util;

/** Minecraft-free arithmetic for the Celestia Sprite shield grant. */
public final class KoharuShieldMath {
    private KoharuShieldMath() {
    }

    /**
     * Returns the fraction of Primary Attribute granted by one Koharu tick.
     * Invalid arithmetic is treated as no shield so a malformed value cannot poison
     * the shared shield queue.
     */
    public static double shieldRatio(double shieldGain, double shieldGainPerLevel,
                                     int level, double primaryMultiplier, int rank) {
        double ratio = (shieldGain + shieldGainPerLevel * level)
                * primaryMultiplier * Math.max(1, rank);
        return Double.isFinite(ratio) ? Math.max(0.0D, ratio) : 0.0D;
    }

    /** Returns a finite, non-negative shield amount for the given Primary Attribute. */
    public static double shieldAmount(double primaryStat, double shieldGain,
                                      double shieldGainPerLevel, int level,
                                      double primaryMultiplier, int rank) {
        double amount = primaryStat * shieldRatio(
                shieldGain, shieldGainPerLevel, level, primaryMultiplier, rank
        );
        return Double.isFinite(amount)
                ? Math.min((double) Float.MAX_VALUE, Math.max(0.0D, amount))
                : 0.0D;
    }
}
