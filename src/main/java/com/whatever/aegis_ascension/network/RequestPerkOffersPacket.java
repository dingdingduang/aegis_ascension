package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestPerkOffersPacket() {
    public static void encode(RequestPerkOffersPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestPerkOffersPacket decode(FriendlyByteBuf buffer) {
        return new RequestPerkOffersPacket();
    }

    public static void handle(RequestPerkOffersPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !ServerCatalogSync.isReady(sender)
                    || !ProgressionRequestLimiter.tryAcquirePerkOffers(sender)) {
                return;
            }

            PerkData.get(sender).ifPresent(data -> {
                // Sync first so the UI always renders the server's current charge/rank state.
                ModNetworking.syncPerkDataTo(sender);
                // An offer remains locked until the player spends a charge. Closing and
                // reopening the screen must not act as a free reroll.
                var offers = data.getPendingOffers();
                if (offers.isEmpty()) {
                    offers = data.rollOffers(sender);
                }
                if (!offers.isEmpty()) {
                    ModNetworking.openScreen(sender, offers);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
