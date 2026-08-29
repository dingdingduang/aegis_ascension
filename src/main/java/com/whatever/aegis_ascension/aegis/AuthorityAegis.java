package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.aegis.AegisConstants.AUTHORITY;
import static com.whatever.aegis_ascension.aegis.AegisConstants.AUTHORITY_PRIMARY_FLAT;
import static com.whatever.aegis_ascension.aegis.AegisConstants.AUTHORITY_SELECT_ALL_USES;
import static com.whatever.aegis_ascension.aegis.AegisConstants.PRIMARY_STAT_PER_OWNED_TALENT;
import static com.whatever.aegis_ascension.aegis.AegisConstants.SELECT_ALL_MAX_USES;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import net.minecraft.util.Mth;

/** Runtime rules unique to Authority Aegis. */
public final class AuthorityAegis {
    private AuthorityAegis() {
    }

    public static Aegis definition() {
        return Aegis.byId(AUTHORITY).orElseThrow(() ->
                new IllegalStateException("Missing Authority Aegis definition")
        );
    }

    public static int maximumSelectAllUses() {
        return Math.max(0, Mth.floor(definition().stat(SELECT_ALL_MAX_USES)));
    }

    public static int selectAllUses(PlayerPerkData data) {
        return Math.max(0, Mth.floor(data.getCustomStat(AUTHORITY_SELECT_ALL_USES)));
    }

    public static boolean canSelectAll(PlayerPerkData data) {
        return data.isAegisEnabled(AUTHORITY)
                && selectAllUses(data) < maximumSelectAllUses();
    }

    public static boolean consumeSelectAllUse(PlayerPerkData data) {
        if (!canSelectAll(data)) {
            return false;
        }
        data.setCustomStat(AUTHORITY_SELECT_ALL_USES, selectAllUses(data) + 1.0D);
        return true;
    }

    /** Multiplier for Authority points when applied to the currently selected stat. */
    public static double currentPrimaryStatMultiplier(PlayerPerkData data) {
        return Math.max(0.0D, definition().primaryStatMultiplier(
                data.getPrimarySkillEnhancement().id()
        ));
    }

    /**
     * Authority rewards are stored as unscaled Primary points. Scaling them here makes
     * the configured reduction follow the destination stat whenever Primary changes.
     */
    public static double effectiveAccumulatedPrimaryStat(PlayerPerkData data) {
        if (!data.isAegisEnabled(AUTHORITY)) {
            return 0.0D;
        }
        return Math.max(0.0D, data.getCustomStat(AUTHORITY_PRIMARY_FLAT))
                * currentPrimaryStatMultiplier(data);
    }

    /**
     * Applies one paid-selection growth step using the talent count after that talent was
     * acquired. Bulk selection deliberately calls this once per acquired offer. The
     * unscaled reward is stored so changing Primary Attribute cannot bypass its multiplier.
     */
    public static double grantPrimaryStatForSelection(PlayerPerkData data) {
        if (!data.isAegisEnabled(AUTHORITY)) {
            return 0.0D;
        }
        Aegis authority = definition();
        double perOwnedTalent = authority.stat(PRIMARY_STAT_PER_OWNED_TALENT);
        double unscaledGain = Math.max(0.0D, perOwnedTalent)
                * data.getUniqueTalentCount();
        if (unscaledGain > 0.0D) {
            data.addCustomStat(AUTHORITY_PRIMARY_FLAT, unscaledGain);
        }
        return unscaledGain * currentPrimaryStatMultiplier(data);
    }
}
