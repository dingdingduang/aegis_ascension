package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Separate per-feature buckets for read-only progression/UI synchronization requests. */
final class ProgressionRequestLimiter {
    private static final Map<ServerPlayer, Long> PERK_OFFERS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> AEGIS_OFFERS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> SKILL_OFFERS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> SHOP_DATA = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> CATALOG_ACKS = new WeakHashMap<>();

    private ProgressionRequestLimiter() {
    }

    static boolean tryAcquirePerkOffers(ServerPlayer player) {
        return tryAcquireView(player, PERK_OFFERS);
    }

    static boolean tryAcquireAegisOffers(ServerPlayer player) {
        return tryAcquireView(player, AEGIS_OFFERS);
    }

    static boolean tryAcquireSkillOffers(ServerPlayer player) {
        return tryAcquireView(player, SKILL_OFFERS);
    }

    static boolean tryAcquireShopData(ServerPlayer player) {
        return tryAcquireView(player, SHOP_DATA);
    }

    static boolean tryAcquireCatalogAcknowledgement(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                CATALOG_ACKS,
                PlatformServices.config().togglePacketCooldownSeconds()
        );
    }

    private static boolean tryAcquireView(ServerPlayer player, Map<ServerPlayer, Long> bucket) {
        return PacketRequestLimiter.tryAcquire(
                player,
                bucket,
                PlatformServices.config().perkDataPacketCooldownSeconds()
        );
    }
}
