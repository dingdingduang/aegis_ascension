package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.menu.ACGInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** Client request to open the server-backed ACG inventory and 3x3 workbench. */
public record OpenACGInventoryPacket() {
    public static void encode(OpenACGInventoryPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenACGInventoryPacket decode(FriendlyByteBuf buffer) {
        return new OpenACGInventoryPacket();
    }

    public static void handle(OpenACGInventoryPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.containerMenu instanceof ACGInventoryMenu
                    || !StorageRequestLimiter.tryAcquireOpen(player)) {
                return;
            }
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (containerId, inventory, owner) ->
                            new ACGInventoryMenu(containerId, inventory),
                    getTranslatableString("screen.aegis_ascension.acg.inventory_workbench.title")
            ));
            ModNetworking.syncStorageTo(player);
        });
        context.setPacketHandled(true);
    }
}
