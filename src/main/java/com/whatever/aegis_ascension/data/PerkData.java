package com.whatever.aegis_ascension.data;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * The single accessor for a player's {@link PlayerPerkData}.
 *
 * <p>Backed by {@link PerkStore}. Nothing outside this package should know where the data
 * actually lives - that answer is version-specific (a UUID-keyed map with per-player NBT
 * files here, data attachments on NeoForge), and routing every lookup through this class is
 * what keeps a port confined to one file rather than ~50 call sites.</p>
 *
 * <p>Server side only. In singleplayer the integrated server shares a JVM with the client, so
 * handing client-side {@code Player} instances the server's data would work locally and then
 * break on a dedicated server. {@link #get(Player)} answers empty for anything that is not a
 * {@link ServerPlayer}; clients get their data pushed to them over the network instead.</p>
 */
public final class PerkData {
    private PerkData() {
    }

    /**
     * The player's perk data, or empty for a client-side player.
     *
     * <p>Returns {@link Optional} rather than a bare value to mirror the capability lookup it
     * replaced, so the existing call sites kept their exact control flow.</p>
     */
    public static Optional<PlayerPerkData> get(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return Optional.empty();
        }
        return Optional.of(PerkStore.get(serverPlayer.getUUID()));
    }

    /** The player's perk data, never null. */
    public static PlayerPerkData of(ServerPlayer player) {
        return PerkStore.get(player.getUUID());
    }
}
