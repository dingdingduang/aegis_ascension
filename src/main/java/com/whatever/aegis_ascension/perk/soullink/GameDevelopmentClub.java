package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.MEMBER_GAIN_MULTIPLIER_BONUS;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_GAME_DEVELOPMENT_CLUB;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SoulLink;

/** Scaling for what the Game Development Club's own members earn on a kill. */
public final class GameDevelopmentClub {
    private GameDevelopmentClub() {
    }

    /**
     * Multiplier on one talent's per-kill grant, or {@code 1.0} when the Soul Link is
     * inactive or the talent is not one of its members.
     *
     * <p>Membership is read from the Soul Link's own requirements rather than named
     * here, so the set of talents this boosts follows soul_links.json.</p>
     */
    public static double memberGainMultiplier(PlayerPerkData data, Perk perk) {
        if (perk == null || !data.hasActiveSoulLink(SOUL_GAME_DEVELOPMENT_CLUB)) {
            return 1.0D;
        }
        SoulLink club = Perk.soulLinkById(SOUL_GAME_DEVELOPMENT_CLUB).orElse(null);
        if (club == null || !club.requirements().contains(perk.id())) {
            return 1.0D;
        }
        double bonus = stat(SOUL_GAME_DEVELOPMENT_CLUB, MEMBER_GAIN_MULTIPLIER_BONUS);
        return Double.isFinite(bonus) ? Math.max(0.0D, 1.0D + bonus) : 1.0D;
    }
}
