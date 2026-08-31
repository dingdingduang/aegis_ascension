package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.DAMAGE_BONUS_CAP;
import static com.whatever.aegis_ascension.perk.TalentConstants.DAMAGE_BONUS_PER_TRADE;
import static com.whatever.aegis_ascension.perk.TalentConstants.FAIR_TRADE_SUCCESSFUL_TRADES;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_FAIR_TRADE;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;

/** Permanent, server-authoritative progression earned from completed NPC trades. */
public final class FairTrade {
    private FairTrade() {
    }

    /** Records exactly one successful trade. Returns true when persistent data changed. */
    public static boolean onSuccessfulTrade(PlayerPerkData data) {
        if (!data.owns(PERK_FAIR_TRADE)) {
            return false;
        }
        data.addCustomStat(FAIR_TRADE_SUCCESSFUL_TRADES, 1.0D);
        return true;
    }

    /** Resolves the current bonus from trade count so JSON edits apply to existing saves. */
    public static double damageBonus(PlayerPerkData data) {
        if (!data.owns(PERK_FAIR_TRADE)) {
            return 0.0D;
        }
        return damageBonus(data.getCustomStat(FAIR_TRADE_SUCCESSFUL_TRADES));
    }

    public static double damageBonus(double successfulTrades) {
        Perk fairTrade = Perk.byId(PERK_FAIR_TRADE).orElseThrow();
        double bonusPerTrade = Math.max(0.0D, fairTrade.stat(DAMAGE_BONUS_PER_TRADE));
        double cap = Math.max(0.0D, fairTrade.stat(DAMAGE_BONUS_CAP));
        return Math.min(cap, Math.max(0.0D, successfulTrades) * bonusPerTrade);
    }
}
