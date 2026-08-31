package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.perk.talents.CashBack;
import com.whatever.aegis_ascension.perk.talents.FairTrade;
import com.whatever.aegis_ascension.shop.ShopConfig;
import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.shop.ShopState;
import com.whatever.aegis_ascension.shop.ShopType;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client request to buy one shop slot. Every check that matters — slot validity, sold-out
 * status, and price — is re-evaluated here against the server's own {@link ShopState}; the
 * packet carries only the slot index, so a tampered client can't name its own price or buy
 * an item that isn't stocked.
 */
public record BuyShopItemPacket(ShopType shopType, int slotIndex) {
    public BuyShopItemPacket {
        shopType = shopType == null ? ShopType.COMMON : shopType;
    }

    public static void encode(BuyShopItemPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.shopType);
        buffer.writeVarInt(packet.slotIndex);
    }

    public static BuyShopItemPacket decode(FriendlyByteBuf buffer) {
        return new BuyShopItemPacket(
                buffer.readEnum(ShopType.class),
                buffer.readVarInt()
        );
    }

    public static void handle(BuyShopItemPacket packet,
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

            PerkData.get(player).ifPresent(data -> {
                if (!ShopConfig.get().isEnabled(packet.shopType)) {
                    ModNetworking.syncShopTo(player, packet.shopType);
                    return;
                }
                ShopState shop = data.getShopState(packet.shopType);
                ShopOffer offer = shop.getOffer(packet.slotIndex);
                if (offer == null) {
                    return;
                }
                ItemStack payload = offer.stack().copy();
                long price = Math.max(0L, offer.experienceCost());
                if (offer.purchased()) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.already_bought"), true);
                } else if (GoldCurrency.enabled()
                        ? !GoldCurrency.canAfford(data, price)
                        : player.totalExperience < price) {
                    player.displayClientMessage(getTranslatableString(
                            GoldCurrency.enabled()
                                    ? "message.aegis_ascension.shop.insufficient_gold"
                                    : "message.aegis_ascension.shop.insufficient_experience",
                            price,
                            GoldCurrency.enabled()
                                    ? data.getGoldCurrency() : player.totalExperience), true);
                } else if (offer.isVirtual()
                        && !VirtualItems.canAppearInShop(data, offer.virtualId())) {
                    VirtualItems.Definition definition = VirtualItems.byId(offer.virtualId());
                    String messageKey = definition != null
                            && definition.uniquePurchase
                            && data.isUniqueVirtualItemAcquired(offer.virtualId())
                            ? "message.aegis_ascension.shop.unique_virtual_acquired"
                            : "message.aegis_ascension.shop.virtual_unavailable";
                    player.displayClientMessage(getTranslatableString(
                            messageKey,
                            definition == null
                                    ? offer.virtualId()
                                    : getTranslatableString(definition.nameKey())
                    ), true);
                } else if (offer.isVirtual()
                        ? !data.getStorage().canAcceptVirtual(offer.virtualId())
                        : !data.getStorage().canAccept(payload)) {
                    // Checked before charging, so a full storage costs the player nothing.
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.storage.full",
                            data.getStorage().getTypeCount()), true);
                } else {
                    // Purchases go into the virtual storage, not the real inventory; the
                    // player withdraws them from the Inventory screen on their own terms.
                    shop.markPurchased(packet.slotIndex);
                    if (GoldCurrency.enabled()) {
                        GoldCurrency.trySpend(data, price);
                    } else {
                        player.giveExperiencePoints(-(int) Math.min(Integer.MAX_VALUE, price));
                    }
                    if (offer.isVirtual()) {
                        // The offer's stack is only the book's icon; banking it as a real
                        // item would hand the player an actual enchanted book instead.
                        data.getStorage().addVirtual(offer.virtualId(), payload.getCount());
                        data.recordUniqueVirtualPurchase(offer.virtualId());
                    } else {
                        data.getStorage().add(payload, offer.rarityColor());
                    }
                    if (GoldCurrency.enabled()) {
                        long refund = CashBack.refundGold(data, price);
                        GoldCurrency.grant(data, refund);
                        FairTrade.onSuccessfulTrade(data);
                    }
                    ModNetworking.syncStorageTo(player);
                    ModNetworking.syncPerkDataTo(player);
                }
                ModNetworking.syncShopTo(player, packet.shopType);
            });
        });
        context.setPacketHandled(true);
    }
}
