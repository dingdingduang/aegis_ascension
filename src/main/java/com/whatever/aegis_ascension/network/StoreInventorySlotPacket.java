package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Banks the stack hovered in the player's E inventory.
 *
 * <p>The client sends no item data. The server requires the same open container id,
 * resolves the menu slot itself, and accepts only slots backed by the player's Inventory.
 * That excludes crafting inputs/results and prevents a forged packet from inventing or
 * taking an item from another container.</p>
 */
public record StoreInventorySlotPacket(int containerId, int menuSlotIndex) {
    public static void encode(StoreInventorySlotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeVarInt(packet.menuSlotIndex);
    }

    public static StoreInventorySlotPacket decode(FriendlyByteBuf buffer) {
        return new StoreInventorySlotPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(StoreInventorySlotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !StorageRequestLimiter.tryAcquireMutation(player)) {
                return;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (menu.containerId != packet.containerId
                    || packet.menuSlotIndex < 0
                    || packet.menuSlotIndex >= menu.slots.size()) {
                return;
            }
            Slot slot = menu.slots.get(packet.menuSlotIndex);
            if (slot.container != player.getInventory()
                    || !slot.mayPickup(player)
                    || !slot.hasItem()) {
                return;
            }

            PerkData.get(player).ifPresent(data -> {
                ItemStack source = slot.getItem();
                if (!StoreHeldItemPacket.store(player, data, source, false)) {
                    return;
                }
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                menu.broadcastChanges();
            });
        });
        context.setPacketHandled(true);
    }
}
