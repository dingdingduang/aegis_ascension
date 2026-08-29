package com.whatever.aegis_ascension.perk.soullink;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SoulLink;
import net.minecraft.server.level.ServerPlayer;

/** Small facade shared by event hooks and the stat/progression services. */
public final class SoulLinkEffects {
    private SoulLinkEffects() {
    }

    public static void onPlayerTick(ServerPlayer player, PlayerPerkData data) {
        TeamRadiance.tick(player, data);
        ShrineMaidenTeam.tick(player, data);
    }

    /** Runs activation rewards before the caller evaluates post-acquisition caps. */
    public static void onTalentAcquired(ServerPlayer player, PlayerPerkData data) {
        TeamRadiance.refreshState(player, data, true);
        ShrineMaidenTeam.tick(player, data);
    }

    /** Removes a persisted multiplayer rank before login/respawn attribute application. */
    public static void refreshCachedState(ServerPlayer player, PlayerPerkData data) {
        TeamRadiance.refreshState(player, data, false);
    }

    public static double stat(String soulLinkId, String statKey) {
        return Perk.soulLinkById(soulLinkId)
                .map(link -> link.bonusStat(statKey))
                .orElse(0.0D);
    }

    public static SoulLink required(String soulLinkId) {
        return Perk.soulLinkById(soulLinkId).orElseThrow(() ->
                new IllegalStateException("Missing Soul Link " + soulLinkId)
        );
    }
}
