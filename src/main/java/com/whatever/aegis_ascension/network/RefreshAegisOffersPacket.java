package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Spends a persisted Aegis refresh charge to replace the locked Aegis offer. */
public record RefreshAegisOffersPacket() {
    public static void encode(RefreshAegisOffersPacket packet, FriendlyByteBuf buffer) {
    }

    public static RefreshAegisOffersPacket decode(FriendlyByteBuf buffer) {
        return new RefreshAegisOffersPacket();
    }

    public static void handle(RefreshAegisOffersPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!RefreshRequestLimiter.tryAcquire(player)) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                var offers = data.refreshAegisOffers(player);
                ModNetworking.syncTo(player);
                if (!offers.isEmpty() && data.getAegisSelectionCharges() > 0) {
                    ModNetworking.openAegisScreen(player, offers);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
