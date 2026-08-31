package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
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
    private static final Map<ServerPlayer, Long> LAST_MUTATION_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_SYNC_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_OPEN_TICK = new WeakHashMap<>();

    private StorageRequestLimiter() {
    }

    static boolean tryAcquireMutation(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_MUTATION_TICK,
                PlatformServices.config().storageMutationPacketCooldownSeconds()
        );
    }

    static boolean tryAcquireSync(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_SYNC_TICK,
                PlatformServices.config().storageViewPacketCooldownSeconds()
        );
    }

    static boolean tryAcquireOpen(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_OPEN_TICK,
                PlatformServices.config().storageViewPacketCooldownSeconds()
        );
    }
}
