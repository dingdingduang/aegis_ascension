package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.shop.ShopConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request for a paid reroll of the daily shop stock. */
public record ManualRefreshShopPacket() {
    public static void encode(ManualRefreshShopPacket packet, FriendlyByteBuf buffer) {
    }

    public static ManualRefreshShopPacket decode(FriendlyByteBuf buffer) {
        return new ManualRefreshShopPacket();
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
                int cost = Math.max(0, ShopConfig.get().manualRefreshExperienceCost);
                var shop = data.getShopState();
                // Allowance is checked before the XP check so an exhausted player is told
                // why rather than being asked for experience they can't spend anyway.
                if (!shop.canManualRefresh()) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.refresh_limit",
                            ShopConfig.get().maxManualRefreshes), true);
                } else if (player.totalExperience < cost) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.insufficient_experience",
                            cost,
                            player.totalExperience), true);
                } else if (shop.manualRefresh(player.serverLevel().getRandom())) {
                    // Charged only once the reroll actually happened.
                    if (cost > 0) {
                        player.giveExperiencePoints(-cost);
                    }
                }
                ModNetworking.syncShopTo(player);
            });
        });
        context.setPacketHandled(true);
    }
}
