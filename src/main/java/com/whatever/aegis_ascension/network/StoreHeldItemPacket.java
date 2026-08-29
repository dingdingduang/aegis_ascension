package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Banks the player's whole main-hand stack into virtual storage (the PUT_INTO_STORAGE_UI
 * keybind, N by default).
 *
 * <p>Carries no payload: the server reads the held stack from its own authoritative
 * inventory rather than trusting a client-supplied item, so this can't be used to
 * materialise an item the player doesn't actually hold.</p>
 */
public record StoreHeldItemPacket() {
    public static void encode(StoreHeldItemPacket packet, FriendlyByteBuf buffer) {
    }

    public static StoreHeldItemPacket decode(FriendlyByteBuf buffer) {
        return new StoreHeldItemPacket();
    }

    public static void handle(StoreHeldItemPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!StorageRequestLimiter.tryAcquireMutation(player)) {
                return;
            }

            PerkData.get(player).ifPresent(data -> {
                ItemStack held = player.getMainHandItem();
                if (store(player, data, held, true)) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            });
        });
        context.setPacketHandled(true);
    }

    /**
     * Server-authoritative common deposit path for held-item and hovered-inventory-slot
     * requests. The caller clears the source only after this returns true.
     */
    static boolean store(ServerPlayer player, PlayerPerkData data, ItemStack source,
                         boolean reportEmpty) {
        if (source.isEmpty()) {
            if (reportEmpty) {
                player.displayClientMessage(getTranslatableString(
                        "message.aegis_ascension.storage.empty_hand"), true);
            }
            return false;
        }
        var storage = data.getStorage();
        if (!storage.canAccept(source)) {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.storage.full",
                    storage.getTypeCount()), true);
            return false;
        }

        ItemStack banked = source.copy();
        if (!storage.add(banked,
                com.whatever.aegis_ascension.util.GeneralConstants.rarityColor(
                        com.whatever.aegis_ascension.shop.ShopConfig.get()
                                .tierOf(banked.getItem())))) {
            return false;
        }
        player.displayClientMessage(getTranslatableString(
                "message.aegis_ascension.storage.stored",
                banked.getCount(), banked.getHoverName()), true);
        ModNetworking.syncStorageTo(player);
        return true;
    }
}
