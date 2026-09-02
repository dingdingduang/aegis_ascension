package com.whatever.aegis_ascension.util;

import com.whatever.aegis_ascension.AegisAscensionMod;

import java.util.Locale;

/** Stable IDs and JSON stat keys used by the Aegis system. */
public final class GeneralConstants {
    public static final float DEFAULT_1F_MODEL_SCALE = 0.0078125f;

    /**
     * Rarity tints, shared by perk cards, daily-shop slots, and storage rows.
     *
     * <p>Shop offers and stored items carry the resolved colour rather than a tier enum:
     * rarity only ever affects how they are drawn, so an {@code int} keeps them free of the
     * perk package and lets the client render one without knowing the tier vocabulary.</p>
     */
    public static final int RARITY_R = 0xFF55C7E8;
    public static final int RARITY_SR = 0xFFC277FF;
    public static final int RARITY_SSR = 0xFFFFC857;

    /** Canonical tier names, in ascending rarity. */
    public static final String TIER_R = "R";
    public static final String TIER_SR = "SR";
    public static final String TIER_SSR = "SSR";

    public static final String COLON = ":";
    public static final String SLASH = "/";

    /** Uppercases and trims a config tier string, falling back to R when unrecognised. */
    public static String normalizeTier(String tier) {
        if (tier != null) {
            String trimmed = tier.trim().toUpperCase(Locale.ROOT);
            if (TIER_R.equals(trimmed) || TIER_SR.equals(trimmed) || TIER_SSR.equals(trimmed)) {
                return trimmed;
            }
        }
        return TIER_R;
    }

    /**
     * Sort rank for a rarity tint: SSR 2, SR 1, R (and anything unrecognised) 0. Higher is
     * rarer, so a descending sort puts SSR first.
     */
    public static int rarityRank(int rarityColor) {
        if (rarityColor == RARITY_SSR) {
            return 2;
        }
        if (rarityColor == RARITY_SR) {
            return 1;
        }
        return 0;
    }

    /** Canonical tier name for a resolved rarity tint. */
    public static String rarityTier(int rarityColor) {
        if (rarityColor == RARITY_SSR) {
            return TIER_SSR;
        }
        if (rarityColor == RARITY_SR) {
            return TIER_SR;
        }
        return TIER_R;
    }

    /** The tint for a tier name; unrecognised names read as R. */
    public static int rarityColor(String tier) {
        return switch (normalizeTier(tier)) {
            case TIER_SSR -> RARITY_SSR;
            case TIER_SR -> RARITY_SR;
            default -> RARITY_R;
        };
    }

    private GeneralConstants() {
    }
}
