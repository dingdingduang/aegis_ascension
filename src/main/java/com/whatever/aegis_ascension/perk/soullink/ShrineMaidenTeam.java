package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;

/** One-time reward issued when Shrine Maiden Team first becomes active after a reset. */
public final class ShrineMaidenTeam {
    private ShrineMaidenTeam() {
    }

    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (!data.hasActiveSoulLink(SOUL_SHRINE_MAIDEN_TEAM)
                || data.getCustomStat(SHRINE_MAIDEN_TEAM_REWARD_CLAIMED) > 0.0D) {
            return;
        }
        data.setCustomStat(SHRINE_MAIDEN_TEAM_REWARD_CLAIMED, 1.0D);
        int count = Math.max(0, (int) Math.round(
                stat(SOUL_SHRINE_MAIDEN_TEAM, RANDOM_AEGIS_COUNT)
        ));
        for (int index = 0; index < count; index++) {
            Aegis aegis = data.grantRandomUnownedAegis(player).orElse(null);
            if (aegis == null) {
                break;
            }
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shrine_maiden_team.aegis",
                    aegis.title()
            ));
        }
        data.applyChosenPerks(player);
        ModNetworking.syncTo(player);
    }
}
