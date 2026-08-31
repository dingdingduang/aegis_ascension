package com.whatever.aegis_ascension.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestQuestDataPacket() {
    public static void encode(RequestQuestDataPacket packet, FriendlyByteBuf buffer) {}
    public static RequestQuestDataPacket decode(FriendlyByteBuf buffer) { return new RequestQuestDataPacket(); }
    public static void handle(RequestQuestDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && QuestRequestLimiter.tryAcquireView(player)) {
                // Refresh the economy mode/balance together with the quest snapshot so a
                // config reload is reflected the next time the Quest Center is opened.
                ModNetworking.syncPerkDataTo(player);
                ModNetworking.syncQuestsTo(player);
            }
        });
        context.setPacketHandled(true);
    }
}
