package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.shop.ShopOffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: the authoritative shop view for one player. Carries the prices
 * alongside the stock so the client never consults the server-side {@code ShopConfig}
 * (which it doesn't have) to render a cost.
 */
public record SyncShopDataPacket(List<ShopOffer> offers,
                                 int refreshExperienceCost,
                                 int refreshCount,
                                 long ticksUntilReset) {
    public static void encode(SyncShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.offers.size());
        packet.offers.forEach(offer -> offer.write(buffer));
        buffer.writeVarInt(packet.refreshExperienceCost);
        buffer.writeVarInt(packet.refreshCount);
        buffer.writeVarLong(packet.ticksUntilReset);
    }

    public static SyncShopDataPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ShopOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offers.add(ShopOffer.read(buffer));
        }
        return new SyncShopDataPacket(offers, buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarLong());
    }

    public static void handle(SyncShopDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }
}
