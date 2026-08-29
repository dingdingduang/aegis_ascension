package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Requests Authority Aegis's limited-use acquisition of every locked Perk offer. */
public record SelectAllOfferedPerksPacket() {
    public static void encode(SelectAllOfferedPerksPacket packet, FriendlyByteBuf buffer) {
    }

    public static SelectAllOfferedPerksPacket decode(FriendlyByteBuf buffer) {
        return new SelectAllOfferedPerksPacket();
    }

    public static void handle(SelectAllOfferedPerksPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                boolean selected = data.tryChooseAllOfferedWithAuthority(player);
                ModNetworking.syncTo(player);

                if (data.getSelectionCharges() <= 0) {
                    return;
                }
                var offers = selected ? data.rollOffers(player) : data.getPendingOffers();
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
