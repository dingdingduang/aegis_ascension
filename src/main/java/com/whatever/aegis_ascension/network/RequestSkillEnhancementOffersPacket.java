package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestSkillEnhancementOffersPacket() {
    public static void encode(RequestSkillEnhancementOffersPacket packet,
                              FriendlyByteBuf buffer) {
    }

    public static RequestSkillEnhancementOffersPacket decode(FriendlyByteBuf buffer) {
        return new RequestSkillEnhancementOffersPacket();
    }

    public static void handle(RequestSkillEnhancementOffersPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            PerkData.get(sender).ifPresent(data -> {
                // Keep an existing roll locked so reopening this tab cannot reroll it.
                if (data.getPendingSkillEnhancementOffers().isEmpty()) {
                    data.rollSkillEnhancementOffers(sender);
                }
                ModNetworking.syncTo(sender);
            });
        });
        context.setPacketHandled(true);
    }
}
