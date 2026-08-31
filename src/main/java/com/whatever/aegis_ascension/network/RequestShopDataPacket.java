package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.shop.ShopType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asking for its current shop view, sent when the shop tab opens. Also performs the
 * daily-rollover check first, so a player who logs in after the reset hour sees fresh stock
 * immediately rather than waiting for the next periodic tick sweep.
 */
public record RequestShopDataPacket(ShopType shopType) {
    public RequestShopDataPacket {
        shopType = shopType == null ? ShopType.COMMON : shopType;
    }

    public static void encode(RequestShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.shopType);
    }

    public static RequestShopDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestShopDataPacket(buffer.readEnum(ShopType.class));
    }

    public static void handle(RequestShopDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            PerkData.get(player).ifPresent(data ->
                    data.getShopState(packet.shopType)
                            .tickAutoRefresh(player.serverLevel(), data));
            ModNetworking.syncPerkDataTo(player);
            ModNetworking.syncShopTo(player, packet.shopType);
        });
        context.setPacketHandled(true);
    }
}
