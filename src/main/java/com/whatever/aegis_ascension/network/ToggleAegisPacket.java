package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to change an owned, manually-toggleable Aegis. */
public record ToggleAegisPacket(String aegisId, boolean enabled) {
    public static void encode(ToggleAegisPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.aegisId, 128);
        buffer.writeBoolean(packet.enabled);
    }

    public static ToggleAegisPacket decode(FriendlyByteBuf buffer) {
        return new ToggleAegisPacket(buffer.readUtf(128), buffer.readBoolean());
    }

    public static void handle(ToggleAegisPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!ToggleRequestLimiter.tryAcquire(player)) {
                return;
            }

            Aegis.byId(packet.aegisId).ifPresent(aegis ->
                    PerkData.get(player).ifPresent(data -> {
                        if (data.setAegisEnabled(aegis, packet.enabled)) {
                            TalentEffects.recalculateAttributes(player, data);
                        }
                    })
            );
            // Correct stale or invalid client requests with authoritative state.
            ModNetworking.syncTo(player);
        });
        context.setPacketHandled(true);
    }
}
