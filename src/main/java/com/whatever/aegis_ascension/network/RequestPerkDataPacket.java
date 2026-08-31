package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Requests authoritative player data, primarily before opening Collection. */
public record RequestPerkDataPacket(boolean liveRefresh) {
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static void encode(RequestPerkDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.liveRefresh);
    }

    public static RequestPerkDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestPerkDataPacket(buffer.readBoolean());
    }

    public static void handle(RequestPerkDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
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
            // This request can run continuously while Custom Stats is open. Sending the
            // full quest catalogue here would bypass quest batching entirely.
            ModNetworking.syncPerkDataTo(player);
        });
        context.setPacketHandled(true);
    }
}
