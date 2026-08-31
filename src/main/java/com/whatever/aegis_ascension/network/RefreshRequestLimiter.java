package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared configurable limiter for Perk, Aegis, Enhancement, and shop refreshes. */
final class RefreshRequestLimiter {
    private static final Map<ServerPlayer, Long> LAST_ACCEPTED_TICK = new WeakHashMap<>();

    private RefreshRequestLimiter() {
    }

    static boolean tryAcquire(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_ACCEPTED_TICK,
                PlatformServices.config().refreshPacketCooldownSeconds()
        );
    }
}
