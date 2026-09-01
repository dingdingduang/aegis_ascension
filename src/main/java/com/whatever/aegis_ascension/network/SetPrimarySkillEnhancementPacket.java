package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to change the stat that receives Primary Attribute bonuses. */
public record SetPrimarySkillEnhancementPacket(String enhancementId) {
    public static void encode(SetPrimarySkillEnhancementPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.enhancementId, 128);
    }

    public static SetPrimarySkillEnhancementPacket decode(FriendlyByteBuf buffer) {
        return new SetPrimarySkillEnhancementPacket(buffer.readUtf(128));
    }

    public static void handle(SetPrimarySkillEnhancementPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ServerCatalogSync.isReady(player)
                    || !ToggleRequestLimiter.tryAcquire(player)) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                SkillEnhancement.byId(packet.enhancementId).ifPresent(enhancement -> {
                    if (data.setPrimarySkillEnhancement(enhancement)) {
                        data.applyChosenPerks(player);
                    }
                });
                // Correct stale or unauthorized optimistic client selections.
                ModNetworking.syncTo(player);
            });
        });
        context.setPacketHandled(true);
    }
}
