package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to change an owned, manually-toggleable talent. */
public record ToggleTalentPacket(String perkId, boolean enabled) {
    public static void encode(ToggleTalentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.perkId, 128);
        buffer.writeBoolean(packet.enabled);
    }

    public static ToggleTalentPacket decode(FriendlyByteBuf buffer) {
        return new ToggleTalentPacket(buffer.readUtf(128), buffer.readBoolean());
    }

    public static void handle(ToggleTalentPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ServerCatalogSync.isReady(player)) {
                return;
            }
            if (!ToggleRequestLimiter.tryAcquire(player)) {
                return;
            }

            Perk.byId(packet.perkId).ifPresent(perk ->
                    PerkData.get(player).ifPresent(data -> {
                        if (data.setTalentEnabled(perk, packet.enabled)) {
                            TalentEffects.recalculateAttributes(player, data);
                        }
                    })
            );
            // Always resync so invalid or stale client requests are corrected.
            ModNetworking.syncTo(player);
        });
        context.setPacketHandled(true);
    }
}
