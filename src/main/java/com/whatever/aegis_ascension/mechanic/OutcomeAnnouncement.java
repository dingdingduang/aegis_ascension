package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.config.ServerSettings;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared presentation for the gacha talents: a fading banner for the player who drew, and
 * one chat line for everyone else. Kept in one place so Shrine Maiden Dance and Mysterious
 * Doll cannot drift apart, and so the timing lives in server settings rather than being
 * duplicated in each talent's own catalog.
 */
public final class OutcomeAnnouncement {
    private OutcomeAnnouncement() {
    }

    /**
     * @param outcome      the short "what you got" label, shown as the subtitle and
     *                     inlined into the broadcast
     * @param broadcastKey a translation key taking the player name and that label
     */
    public static void announce(ServerPlayer player, Component title, Component outcome,
                                String broadcastKey) {
        ServerSettings settings = ServerSettings.get();
        GeneralServerMethods.sendTitle(
                player,
                title,
                outcome,
                settings.outcomeBannerFadeInTicks(),
                settings.outcomeBannerStayTicks(),
                settings.outcomeBannerFadeOutTicks()
        );
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Component broadcast = getTranslatableString(
                broadcastKey,
                player.getGameProfile().getName(),
                outcome
        );
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            online.sendSystemMessage(broadcast);
        }
    }
}
