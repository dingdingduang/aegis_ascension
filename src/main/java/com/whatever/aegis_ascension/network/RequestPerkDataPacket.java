package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.DisplayStatScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Requests authoritative player data, primarily before opening Collection.
 *
 * @param includeAttribution set only by the Custom Stats tab, which is the one screen
 *                           that renders per-source stat records. Other Collection tabs
 *                           use the same request but do not display them, so they leave
 *                           several kilobytes off the reply.
 */
public record RequestPerkDataPacket(boolean liveRefresh, boolean includeAttribution) {
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static void encode(RequestPerkDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.liveRefresh);
        buffer.writeBoolean(packet.includeAttribution);
    }

    public static RequestPerkDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestPerkDataPacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(RequestPerkDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ServerCatalogSync.isReady(player)) {
                return;
            }

            if (packet.liveRefresh
                    && !PlatformServices.config().liveCustomStatsRefreshEnabled()) {
                return;
            }

            double cooldownSeconds = packet.liveRefresh
                    ? PlatformServices.config().livePerkDataPacketCooldownSeconds()
                    : PlatformServices.config().perkDataPacketCooldownSeconds();
            if (!PacketRequestLimiter.tryAcquire(
                    player,
                    LAST_REQUEST_TICK,
                    cooldownSeconds
            )) {
                return;
            }
            DisplayStatScope scope = packet.includeAttribution
                    ? DisplayStatScope.FULL : DisplayStatScope.VALUES;
            // This request can run continuously while Custom Stats is open. Sending the
            // full quest catalogue here would bypass quest batching entirely.
            if (packet.liveRefresh) {
                // The periodic refresh only redraws stat values; talent ranks, offers,
                // and currency reach the client through their own events, so the whole
                // progression packet does not need to be rebuilt once a second.
                ModNetworking.syncDisplayStatsTo(player, scope);
                return;
            }
            ModNetworking.syncPerkDataTo(player, scope);
        });
        context.setPacketHandled(true);
    }
}
