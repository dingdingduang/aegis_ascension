package com.whatever.aegis_ascension.util;

/** Small, Minecraft-free calculations shared by Magic Blade runtime code and tests. */
public final class MagicBladeMath {
    private MagicBladeMath() {
    }

    /** Computes a finite, float-safe replacement amount from max mana and a coefficient. */
    public static double replacementDamage(double maxMana, double coefficient) {
        if (!Double.isFinite(maxMana) || !Double.isFinite(coefficient)
                || maxMana <= 0.0D || coefficient <= 0.0D) {
            return 0.0D;
        }
        return Math.min(Float.MAX_VALUE, maxMana * coefficient);
    }

    /** Converts a seconds-based trigger cooldown to the smallest positive game-tick count. */
    public static long cooldownTicks(double cooldownSeconds) {
        if (!Double.isFinite(cooldownSeconds) || cooldownSeconds < 0.0D) {
            return 1L;
        }
        return Math.max(1L, (long) Math.ceil(cooldownSeconds * 20.0D));
    }
}
