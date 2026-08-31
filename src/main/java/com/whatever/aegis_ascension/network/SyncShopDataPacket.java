package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.shop.ShopType;
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
public record SyncShopDataPacket(ShopType shopType,
                                 boolean enabled,
                                 List<ShopOffer> offers,
                                 int refreshExperienceCost,
                                 int remainingRefreshes,
                                 long ticksUntilReset) {
    public SyncShopDataPacket {
        shopType = shopType == null ? ShopType.COMMON : shopType;
    }

    public static void encode(SyncShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.shopType);
        buffer.writeBoolean(packet.enabled);
        buffer.writeVarInt(packet.offers.size());
        packet.offers.forEach(offer -> offer.write(buffer));
        buffer.writeVarInt(packet.refreshExperienceCost);
        buffer.writeVarInt(packet.remainingRefreshes);
        buffer.writeVarLong(packet.ticksUntilReset);
    }

    public static SyncShopDataPacket decode(FriendlyByteBuf buffer) {
        ShopType shopType = buffer.readEnum(ShopType.class);
        boolean enabled = buffer.readBoolean();
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_SHOP_OFFERS,
                "shop offer"
        );
        List<ShopOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offers.add(ShopOffer.read(buffer));
        }
        return new SyncShopDataPacket(
                shopType,
                enabled,
                offers,
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong()
        );
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
