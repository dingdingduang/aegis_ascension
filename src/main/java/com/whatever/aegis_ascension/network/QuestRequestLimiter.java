package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Dedicated anti-spam bucket for full Quest Center snapshot requests. */
final class QuestRequestLimiter {
    private static final Map<ServerPlayer, Long> LAST_VIEW_TICK = new WeakHashMap<>();

    private QuestRequestLimiter() {
    }

    static boolean tryAcquireView(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_VIEW_TICK,
                PlatformServices.config().questViewPacketCooldownSeconds()
        );
    }
}
