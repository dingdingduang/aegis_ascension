package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Changes the sending player's persisted, server-authoritative quest preference. */
public record SetQuestAutoAcceptPacket(boolean enabled) {
    public static void encode(SetQuestAutoAcceptPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
    }

    public static SetQuestAutoAcceptPacket decode(FriendlyByteBuf buffer) {
        return new SetQuestAutoAcceptPacket(buffer.readBoolean());
    }

    public static void handle(SetQuestAutoAcceptPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ToggleRequestLimiter.tryAcquire(player)) return;
            QuestManager.setAutoAcceptEligibleQuests(player, packet.enabled);
            ModNetworking.syncQuestsTo(player);
        });
        context.setPacketHandled(true);
    }
}
