package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Spends a persisted Fortune refresh charge to replace the locked perk offer. */
public record RefreshPerkOffersPacket() {
    public static void encode(RefreshPerkOffersPacket packet, FriendlyByteBuf buffer) {
    }

    public static RefreshPerkOffersPacket decode(FriendlyByteBuf buffer) {
        return new RefreshPerkOffersPacket();
    }

    public static void handle(RefreshPerkOffersPacket packet,
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
                var offers = data.refreshPerkOffers(player);
                ModNetworking.syncTo(player);
                if (!offers.isEmpty() && data.getSelectionCharges() > 0) {
                    ModNetworking.openScreen(player, offers);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
