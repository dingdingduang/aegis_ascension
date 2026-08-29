package com.whatever.aegis_ascension.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Requests Devour history separately so ordinary stat refreshes stay small. */
public record RequestDevourDataPacket() {
    private static final long MINIMUM_INTERVAL_TICKS = 10L;
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static void encode(RequestDevourDataPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestDevourDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestDevourDataPacket();
    }

    public static void handle(RequestDevourDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !tryAcquire(player)) {
                return;
            }
            ModNetworking.syncDevourDataTo(player);
        });
        context.setPacketHandled(true);
    }

    private static boolean tryAcquire(ServerPlayer player) {
        long currentTick = player.serverLevel().getGameTime();
        Long lastTick = LAST_REQUEST_TICK.get(player);
        if (lastTick != null && currentTick >= lastTick
                && currentTick - lastTick < MINIMUM_INTERVAL_TICKS) {
            return false;
        }
        LAST_REQUEST_TICK.put(player, currentTick);
        return true;
    }
}
