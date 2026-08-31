package com.whatever.aegis_ascension.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** Client acknowledgement that gates all packets whose ids depend on the synchronized catalogs. */
public record AcknowledgeServerCatalogPacket(String hash) {
    public AcknowledgeServerCatalogPacket {
        hash = Objects.requireNonNull(hash, "hash");
        if (hash.length() > NetworkLimits.MAX_CATALOG_HASH_CHARS) {
            throw new IllegalArgumentException("Catalog hash is too long");
        }
    }

    public static void encode(AcknowledgeServerCatalogPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.hash, NetworkLimits.MAX_CATALOG_HASH_CHARS);
    }

    public static AcknowledgeServerCatalogPacket decode(FriendlyByteBuf buffer) {
        return new AcknowledgeServerCatalogPacket(
                buffer.readUtf(NetworkLimits.MAX_CATALOG_HASH_CHARS)
        );
    }

    public static void handle(
            AcknowledgeServerCatalogPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ServerCatalogSync.acknowledge(sender, packet.hash));
        }
        context.setPacketHandled(true);
    }
}
