package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;

/** Scaling helpers for Cirno and Great Fairy while Misty Lake Fairies is active. */
public final class MistyLake {
    private MistyLake() {
    }

    public static double cirnoPrimaryStatBonus(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MISTY_LAKE)
                ? stat(SOUL_MISTY_LAKE, CIRNO_PRIMARY_STAT_BONUS) : 0.0D;
    }

    public static double greatFairyMultiplier(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MISTY_LAKE)
                ? Math.max(0.0D, stat(SOUL_MISTY_LAKE, GREAT_FAIRY_EFFECT_MULTIPLIER))
                : 1.0D;
    }
}
