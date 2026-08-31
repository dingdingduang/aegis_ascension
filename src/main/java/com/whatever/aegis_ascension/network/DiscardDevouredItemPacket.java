package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Server-authoritative request to discard one devoured item's inherited bonuses. */
public record DiscardDevouredItemPacket(String itemId) {
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static void encode(DiscardDevouredItemPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.itemId, 256);
    }

    public static DiscardDevouredItemPacket decode(FriendlyByteBuf buffer) {
        return new DiscardDevouredItemPacket(buffer.readUtf(256));
    }

    public static void handle(DiscardDevouredItemPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || ResourceLocation.tryParse(packet.itemId) == null
                    || !tryAcquire(player)) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                if (!data.hasAegis(AegisConstants.DEVOUR)
                        || !data.discardDevouredItemAttributes(packet.itemId)) {
                    ModNetworking.syncDevourDataTo(player);
                    return;
                }
                data.applyChosenPerks(player);
                ModNetworking.syncTo(player);
                ModNetworking.syncDevourDataTo(player);
            });
        });
        context.setPacketHandled(true);
    }

    private static boolean tryAcquire(ServerPlayer player) {
        return PacketRequestLimiter.tryAcquire(
                player,
                LAST_REQUEST_TICK,
                PlatformServices.config().discardDevourPacketCooldownSeconds()
        );
    }
}
