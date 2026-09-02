package com.whatever.aegis_ascension.util;

/** Minecraft-free arithmetic for talents that scale on the Gold a player is holding. */
public final class GoldScalingMath {
    private GoldScalingMath() {
    }

    /**
     * The bonus earned by holding {@code gold}: one whole {@code goldPerStack} step pays
     * {@code bonusPerStack}, and the total stops at {@code cap}. Partial steps pay
     * nothing, so the bonus only moves when a step is actually completed.
     */
    public static double bonus(long gold, double goldPerStack, double bonusPerStack,
                               double cap) {
        if (gold <= 0L || !Double.isFinite(goldPerStack) || goldPerStack <= 0.0D
                || !Double.isFinite(bonusPerStack) || bonusPerStack <= 0.0D) {
            return 0.0D;
        }
        double stacks = Math.floor(gold / goldPerStack);
        double earned = stacks * bonusPerStack;
        if (!Double.isFinite(earned) || earned <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(cap) || cap <= 0.0D) {
            return earned;
        }
        return Math.min(cap, earned);
    }
}
