package com.whatever.aegis_ascension.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Shared conversion and bookkeeping for configurable server-side packet cooldowns. */
final class PacketRequestLimiter {
    private static final double TICKS_PER_SECOND = 20.0D;

    private PacketRequestLimiter() {
    }

    static boolean tryAcquire(
            ServerPlayer player,
            Map<ServerPlayer, Long> acceptedTicks,
            double cooldownSeconds
    ) {
        long minimumIntervalTicks = toTicks(cooldownSeconds);
        if (minimumIntervalTicks <= 0L) {
            // Drop an old timestamp too, so disabling and then re-enabling a config entry
            // cannot make the first request appear to be inside a stale cooldown window.
            acceptedTicks.remove(player);
            return true;
        }

        long currentTick = player.serverLevel().getGameTime();
        Long lastTick = acceptedTicks.get(player);
        if (lastTick != null && currentTick >= lastTick
                && currentTick - lastTick < minimumIntervalTicks) {
            return false;
        }
        acceptedTicks.put(player, currentTick);
        return true;
    }

    private static long toTicks(double cooldownSeconds) {
        if (!Double.isFinite(cooldownSeconds) || cooldownSeconds <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil(cooldownSeconds * TICKS_PER_SECOND));
    }
}
