package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.shop.ShopConfig;
import com.whatever.aegis_ascension.shop.ShopType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request for a paid reroll of the daily shop stock. */
public record ManualRefreshShopPacket(ShopType shopType) {
    public ManualRefreshShopPacket {
        shopType = shopType == null ? ShopType.COMMON : shopType;
    }

    public static void encode(ManualRefreshShopPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.shopType);
    }

    public static ManualRefreshShopPacket decode(FriendlyByteBuf buffer) {
        return new ManualRefreshShopPacket(buffer.readEnum(ShopType.class));
    }

    public static void handle(ManualRefreshShopPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!RefreshRequestLimiter.tryAcquire(player)) {
                return;
            }

            PerkData.get(player).ifPresent(data -> {
                ShopConfig config = ShopConfig.get();
                int cost = config.manualRefreshExperienceCost(packet.shopType);
                long currencyCost = Math.max(0L, cost);
                var shop = data.getShopState(packet.shopType);
                // Allowance is checked before the currency check so an exhausted player is
                // told why rather than being asked for a balance they cannot spend anyway.
                if (!shop.canManualRefresh()) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.refresh_limit",
                            config.maxManualRefreshes(packet.shopType)), true);
                } else if (GoldCurrency.enabled()
                        ? !GoldCurrency.canAfford(data, currencyCost)
                        : player.totalExperience < cost) {
                    player.displayClientMessage(getTranslatableString(
                            GoldCurrency.enabled()
                                    ? "message.aegis_ascension.shop.insufficient_gold"
                                    : "message.aegis_ascension.shop.insufficient_experience",
                            currencyCost,
                            GoldCurrency.enabled()
                                    ? data.getGoldCurrency() : player.totalExperience), true);
                } else if (shop.manualRefresh(player.serverLevel().getRandom(), data)) {
                    // Charged only once the reroll actually happened.
                    if (cost > 0) {
                        if (GoldCurrency.enabled()) GoldCurrency.trySpend(data, currencyCost);
                        else player.giveExperiencePoints(-cost);
                    }
                    ModNetworking.syncPerkDataTo(player);
                }
                ModNetworking.syncShopTo(player, packet.shopType);
            });
        });
        context.setPacketHandled(true);
    }
}
