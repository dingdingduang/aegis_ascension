package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Gives up one locked perk selection in exchange for Skill Enhancement charges. */
public record ExchangePerkChargePacket() {
    public static void encode(ExchangePerkChargePacket packet, FriendlyByteBuf buffer) {
    }

    public static ExchangePerkChargePacket decode(FriendlyByteBuf buffer) {
        return new ExchangePerkChargePacket();
    }

    public static void handle(ExchangePerkChargePacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ServerCatalogSync.isReady(player)
                    || !ToggleRequestLimiter.tryAcquire(player)) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                boolean exchanged = data.exchangePerkChargeForSkillEnhancements(player);
                if (exchanged) {
                    data.applyChosenPerks(player);
                }
                ModNetworking.syncTo(player);

                if (data.getSelectionCharges() <= 0) {
                    return;
                }
                var offers = exchanged ? data.rollOffers(player) : data.getPendingOffers();
                if (offers.isEmpty()) {
                    offers = data.rollOffers(player);
                }
                if (!offers.isEmpty()) {
                    ModNetworking.openScreen(player, offers);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
