package com.whatever.aegis_ascension.util;

import java.util.Locale;

/**
 * Helpers that do not depend on Minecraft, Forge, or a physical side.
 *
 * <p>This class is intentionally Java 8 compatible so it can be shared with a
 * Forge 1.16.5 target.</p>
 */
public final class GeneralCommonMethods {
    private GeneralCommonMethods() {
    }

    public static String formatPercent(double value) {
        return compact(value * 100.0D) + "%";
    }

    public static String compact(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    public static int nonNegativeCount(double amount) {
        long rounded = Math.round(amount);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rounded));
    }
}
