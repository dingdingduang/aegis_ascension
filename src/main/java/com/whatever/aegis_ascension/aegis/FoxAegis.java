package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.perk.TalentConstants.DIVINE_SAKURA_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.MAX_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_DIVINE_SAKURA_POWER;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;

/**
 * Fox God's Aegis progression helpers.
 *
 * <p>Ward spells are cast explicitly through Iron's Spells and are never summoned or
 * refreshed merely because Fox God's Aegis is selected. This class only exposes the
 * Divine Sakura constellation state used by ward and Breakthrough scaling.</p>
 */
public final class FoxAegis {
    private FoxAegis() {
    }

    /**
     * Effective constellation count for the caster, or {@code -1} when Divine Sakura
     * Power is not owned. Both unlock paths feed this — obtaining the talent again
     * (rank) and spending experience (stored counter) — capped at the talent's max.
     */
    public static int constellationCount(PlayerPerkData data) {
        Perk talent = Perk.byId(PERK_DIVINE_SAKURA_POWER).orElse(null);
        if (talent == null || !data.owns(talent.id())) {
            return -1;
        }
        int fromRanks = Math.max(0, data.getRank(talent) - 1);
        int fromExperience = (int) Math.max(0.0D,
                data.getCustomStat(DIVINE_SAKURA_CONSTELLATIONS));
        int max = (int) Math.max(0.0D, talent.stat(MAX_CONSTELLATIONS));
        return Math.min(max, fromRanks + fromExperience);
    }

}
