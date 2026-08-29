package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SelectSkillEnhancementPacket(String enhancementId) {
    public static void encode(SelectSkillEnhancementPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.enhancementId, 128);
    }

    public static SelectSkillEnhancementPacket decode(FriendlyByteBuf buffer) {
        return new SelectSkillEnhancementPacket(buffer.readUtf(128));
    }

    public static void handle(SelectSkillEnhancementPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }

            PerkData.get(sender).ifPresent(data -> {
                boolean selected = SkillEnhancement.byId(packet.enhancementId)
                        .map(enhancement ->
                                data.tryChooseOfferedSkillEnhancement(enhancement, sender)
                        )
                        .orElse(false);

                if (data.getSkillEnhancementCharges() > 0
                        && (selected
                        || data.getPendingSkillEnhancementOffers().isEmpty())) {
                    data.rollSkillEnhancementOffers(sender);
                }
                // Always return authoritative charges, ranks, and offers to the client.
                ModNetworking.syncTo(sender);
            });
        });
        context.setPacketHandled(true);
    }
}
