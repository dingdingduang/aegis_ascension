package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SelectPerkPacket(String perkId) {
    public static void encode(SelectPerkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.perkId, 128);
    }

    public static SelectPerkPacket decode(FriendlyByteBuf buffer) {
        return new SelectPerkPacket(buffer.readUtf(128));
    }

    public static void handle(SelectPerkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }

            Perk.byId(packet.perkId).ifPresent(perk ->
                    PerkData.get(sender).ifPresent(data -> {
                        boolean selected = data.tryChooseOffered(perk, sender);
                        // Always correct the client, including after an invalid or stale request.
                        ModNetworking.syncTo(sender);

                        if (selected && data.getSelectionCharges() > 0) {
                            var nextOffers = data.rollOffers(sender);
                            if (!nextOffers.isEmpty()) {
                                ModNetworking.openScreen(sender, nextOffers);
                            }
                        } else if (!selected && data.getSelectionCharges() > 0) {
                            // Restore or reroll valid offers and re-enable the UI after
                            // a stale request (for example, a live config cap change).
                            var validOffers = data.getPendingOffers();
                            if (validOffers.isEmpty()) {
                                validOffers = data.rollOffers(sender);
                            }
                            if (!validOffers.isEmpty()) {
                                ModNetworking.openScreen(sender, validOffers);
                            }
                        }
                    })
            );
        });
        context.setPacketHandled(true);
    }
}
