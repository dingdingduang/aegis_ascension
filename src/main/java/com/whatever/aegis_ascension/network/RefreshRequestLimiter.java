package com.whatever.aegis_ascension.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared server-side 10-tick limiter for Perk, Aegis, and Enhancement refreshes. */
final class RefreshRequestLimiter {
    private static final long MINIMUM_INTERVAL_TICKS = 10L;
    private static final Map<ServerPlayer, Long> LAST_ACCEPTED_TICK = new WeakHashMap<>();

    private RefreshRequestLimiter() {
    }

    static boolean tryAcquire(ServerPlayer player) {
        long currentTick = player.serverLevel().getGameTime();
        Long lastTick = LAST_ACCEPTED_TICK.get(player);
        if (lastTick != null && currentTick >= lastTick
                && currentTick - lastTick < MINIMUM_INTERVAL_TICKS) {
            return false;
        }
        LAST_ACCEPTED_TICK.put(player, currentTick);
        return true;
    }
}
