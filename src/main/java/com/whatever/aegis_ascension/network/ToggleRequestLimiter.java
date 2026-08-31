package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared configurable limiter for toggles, unlocks, selections, and purchases. */
final class ToggleRequestLimiter {
    private static final Map<ServerPlayer, Long> LAST_ACCEPTED_TICK = new WeakHashMap<>();

    private ToggleRequestLimiter() {
    }

    static boolean tryAcquire(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_ACCEPTED_TICK,
                PlatformServices.config().togglePacketCooldownSeconds()
        );
    }
}
