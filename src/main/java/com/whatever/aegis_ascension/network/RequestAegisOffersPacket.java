package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestAegisOffersPacket() {
    public static void encode(RequestAegisOffersPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestAegisOffersPacket decode(FriendlyByteBuf buffer) {
        return new RequestAegisOffersPacket();
    }

    public static void handle(RequestAegisOffersPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !ServerCatalogSync.isReady(sender)
                    || !ProgressionRequestLimiter.tryAcquireAegisOffers(sender)) {
                return;
            }
            PerkData.get(sender).ifPresent(data -> {
                ModNetworking.syncPerkDataTo(sender);
                // Pending choices are persistent and cannot be rerolled by reopening.
                var offers = data.getPendingAegisOffers();
                if (offers.isEmpty()) {
                    offers = data.rollAegisOffers(sender);
                }
                if (!offers.isEmpty()) {
                    ModNetworking.openAegisScreen(sender, offers);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
