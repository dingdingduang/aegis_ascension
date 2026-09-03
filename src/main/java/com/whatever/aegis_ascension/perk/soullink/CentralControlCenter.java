package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.MEMBER_GAIN_MULTIPLIER_BONUS;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_CENTRAL_CONTROL_CENTER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;

/** Scaling for the step conversions Hikari and Nozomi perform. */
public final class CentralControlCenter {
    private CentralControlCenter() {
    }

    /**
     * Multiplier on what a member's conversion yields, or {@code 1.0} while the Soul
     * Link is inactive.
     */
    public static double conversionMultiplier(PlayerPerkData data) {
        if (!data.hasActiveSoulLink(SOUL_CENTRAL_CONTROL_CENTER)) {
            return 1.0D;
        }
        double bonus = stat(SOUL_CENTRAL_CONTROL_CENTER, MEMBER_GAIN_MULTIPLIER_BONUS);
        return Double.isFinite(bonus) ? Math.max(0.0D, 1.0D + bonus) : 1.0D;
    }
}
