package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.ARONA_PRIMARY_FLAT;
import static com.whatever.aegis_ascension.perk.TalentConstants.R_ARONA;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;

/** Runtime helpers for Arona's destination-scaled flat Primary reward. */
public final class Arona {
    private Arona() {
    }

    public static Perk definition() {
        return Perk.byId(R_ARONA).orElseThrow(() ->
                new IllegalStateException("Missing Arona talent definition")
        );
    }

    /** Multiplier for Arona points when applied to the currently selected stat. */
    public static double currentPrimaryStatMultiplier(PlayerPerkData data) {
        return Math.max(0.0D, definition().primaryStatMultiplier(
                data.getPrimarySkillEnhancement().id()
        ));
    }

    /**
     * Arona's Breakthrough rewards are stored unscaled, so switching Primary Attribute
     * recalculates the entire accumulated reward for its new destination.
     */
    public static double effectiveAccumulatedPrimaryStat(PlayerPerkData data) {
        if (!data.owns(R_ARONA)) {
            return 0.0D;
        }
        return Math.max(0.0D, data.getCustomStat(ARONA_PRIMARY_FLAT))
                * currentPrimaryStatMultiplier(data);
    }
}
