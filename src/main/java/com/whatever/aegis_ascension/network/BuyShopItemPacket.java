package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.shop.ShopState;
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
public record BuyShopItemPacket(int slotIndex) {
    public static void encode(BuyShopItemPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slotIndex);
    }

    public static BuyShopItemPacket decode(FriendlyByteBuf buffer) {
        return new BuyShopItemPacket(buffer.readVarInt());
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
                ShopState shop = data.getShopState();
                ShopOffer offer = shop.getOffer(packet.slotIndex);
                if (offer == null) {
                    return;
                }
                ItemStack payload = offer.stack().copy();
                if (offer.purchased()) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.already_bought"), true);
                } else if (player.totalExperience < offer.experienceCost()) {
                    player.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.shop.insufficient_experience",
                            offer.experienceCost(),
                            player.totalExperience), true);
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
                    player.giveExperiencePoints(-offer.experienceCost());
                    if (offer.isVirtual()) {
                        // The offer's stack is only the book's icon; banking it as a real
                        // item would hand the player an actual enchanted book instead.
                        data.getStorage().addVirtual(offer.virtualId(), payload.getCount());
                    } else {
                        data.getStorage().add(payload, offer.rarityColor());
                    }
                    ModNetworking.syncStorageTo(player);
                }
                ModNetworking.syncShopTo(player);
            });
        });
        context.setPacketHandled(true);
    }
}
