package com.whatever.aegis_ascension.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side anti-spam buckets for the storage feature.
 *
 * <p>Mutations, view syncs, and menu-open requests deliberately use separate buckets.
 * Opening the integrated inventory therefore cannot make the player's first deposit or
 * extraction fail, switching from the original storage view cannot block the menu-open
 * request, and storage traffic no longer consumes the cooldown used by talent/Aegis
 * toggles or shop purchases.</p>
 */
final class StorageRequestLimiter {
    /** Matches the existing one-second protection previously inherited from toggles. */
    private static final long MUTATION_INTERVAL_TICKS = 20L;
    /** View/open requests are cheap, but each can amplify into a menu or full sync. */
    private static final long VIEW_INTERVAL_TICKS = 10L;

    private static final Map<ServerPlayer, Long> LAST_MUTATION_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_SYNC_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_OPEN_TICK = new WeakHashMap<>();

    private StorageRequestLimiter() {
    }

    static boolean tryAcquireMutation(ServerPlayer player) {
        return tryAcquire(player, LAST_MUTATION_TICK, MUTATION_INTERVAL_TICKS);
    }

    static boolean tryAcquireSync(ServerPlayer player) {
        return tryAcquire(player, LAST_SYNC_TICK, VIEW_INTERVAL_TICKS);
    }

    static boolean tryAcquireOpen(ServerPlayer player) {
        return tryAcquire(player, LAST_OPEN_TICK, VIEW_INTERVAL_TICKS);
    }

    private static boolean tryAcquire(ServerPlayer player,
                                      Map<ServerPlayer, Long> acceptedTicks,
                                      long minimumIntervalTicks) {
        long currentTick = player.serverLevel().getGameTime();
        Long lastTick = acceptedTicks.get(player);
        if (lastTick != null && currentTick >= lastTick
                && currentTick - lastTick < minimumIntervalTicks) {
            return false;
        }
        acceptedTicks.put(player, currentTick);
        return true;
    }
}
