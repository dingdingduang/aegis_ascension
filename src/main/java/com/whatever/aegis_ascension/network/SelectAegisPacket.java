package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SelectAegisPacket(String aegisId) {
    public static void encode(SelectAegisPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.aegisId, 128);
    }

    public static SelectAegisPacket decode(FriendlyByteBuf buffer) {
        return new SelectAegisPacket(buffer.readUtf(128));
    }

    public static void handle(SelectAegisPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            PerkData.get(sender).ifPresent(data ->
                    Aegis.byId(packet.aegisId).ifPresent(aegis -> {
                        boolean selected = data.tryChooseOfferedAegis(aegis, sender);
                        // Always correct the client, including after an invalid or stale request.
                        ModNetworking.syncTo(sender);

                        if (selected && data.getAegisSelectionCharges() > 0) {
                            var nextOffers = data.rollAegisOffers(sender);
                            if (!nextOffers.isEmpty()) {
                                ModNetworking.openAegisScreen(sender, nextOffers);
                            }
                        } else if (!selected && data.getAegisSelectionCharges() > 0) {
                            // Restore or reroll valid offers and re-enable the UI after
                            // a stale request (for example, a live config cap change).
                            var validOffers = data.getPendingAegisOffers();
                            if (validOffers.isEmpty()) {
                                validOffers = data.rollAegisOffers(sender);
                            }
                            if (!validOffers.isEmpty()) {
                                ModNetworking.openAegisScreen(sender, validOffers);
                            }
                        }
                    })
            );
        });
        context.setPacketHandled(true);
    }
}
