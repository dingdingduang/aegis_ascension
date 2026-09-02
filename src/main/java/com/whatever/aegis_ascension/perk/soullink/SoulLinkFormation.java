package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.perk.talents.OutcomeAnnouncement;
import net.minecraft.server.level.ServerPlayer;

/**
 * Announces a Soul Link the first time its talents come together, with the same banner and
 * server-wide line the gacha talents use.
 *
 * <p>"First time" is persisted per link as a custom stat rather than held in memory, so a
 * relog cannot replay the announcement. A progression reset clears those flags along with
 * everything else, which is correct: re-forming a link after a respec is news again.</p>
 */
public final class SoulLinkFormation {
    private static final String ANNOUNCED_PREFIX = "soul_link_announced_";

    private SoulLinkFormation() {
    }

    /** Announces links that became active just now, and remembers them. */
    public static void announceNewlyFormed(ServerPlayer player, PlayerPerkData data) {
        for (SoulLink link : data.getActiveSoulLinks()) {
            if (claim(data, link)) {
                OutcomeAnnouncement.announce(
                        player,
                        getTranslatableString("message.aegis_ascension.soul_link.title"),
                        link.title(),
                        "message.aegis_ascension.soul_link.broadcast"
                );
            }
        }
    }

    /**
     * Records what is already formed without announcing it. Runs at login and respawn so a
     * character who predates this feature is not congratulated for links they have had for
     * hours the next time they take a talent.
     */
    public static void markExistingFormed(PlayerPerkData data) {
        for (SoulLink link : data.getActiveSoulLinks()) {
            claim(data, link);
        }
    }

    /** @return true when this call was the one that claimed the link */
    private static boolean claim(PlayerPerkData data, SoulLink link) {
        String key = ANNOUNCED_PREFIX + link.id();
        if (data.getCustomStat(key) > 0.0D) {
            return false;
        }
        data.setCustomStat(key, 1.0D);
        return true;
    }
}
