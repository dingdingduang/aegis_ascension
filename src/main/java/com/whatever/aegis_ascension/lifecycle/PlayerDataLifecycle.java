package com.whatever.aegis_ascension.lifecycle;

import com.whatever.aegis_ascension.data.PerkStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Loader-event-neutral bridge for player data persistence and entity replacement. */
public final class PlayerDataLifecycle {
    private PlayerDataLifecycle() {
    }

    public static void onPlayerClone(UUID playerId, boolean wasDeath,
                                     boolean resetOnDeath, boolean keepInventory) {
        if (wasDeath && resetOnDeath) {
            PerkStore.reset(playerId, keepInventory);
        }
    }

    public static void onPlayerLoad(ServerPlayer player) {
        PerkStore.load(player);
    }

    public static void onPlayerSave(ServerPlayer player) {
        PerkStore.save(player);
    }

    public static void onPlayerLogout(UUID playerId) {
        // The loader's final player-save event occurs after logout, so eviction is delayed
        // until PerkStore.save has written the session.
        PerkStore.markForEviction(playerId);
    }

    public static void onServerStopped() {
        PerkStore.clear();
    }
}
