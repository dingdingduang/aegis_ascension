package com.whatever.aegis_ascension.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client asking for its current storage view, sent when the Inventory tab opens. */
public record RequestStorageDataPacket() {
    public static void encode(RequestStorageDataPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestStorageDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestStorageDataPacket();
    }

    public static void handle(RequestStorageDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && StorageRequestLimiter.tryAcquireSync(player)) {
                ModNetworking.syncStorageTo(player);
            }
        });
        context.setPacketHandled(true);
    }
}
