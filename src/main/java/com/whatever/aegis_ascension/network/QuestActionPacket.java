package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record QuestActionPacket(String questId, Action action) {
    public enum Action { ACCEPT, CANCEL, SUBMIT }
    public static void encode(QuestActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId == null ? "" : packet.questId, 128);
        buffer.writeEnum(packet.action == null ? Action.ACCEPT : packet.action);
    }
    public static QuestActionPacket decode(FriendlyByteBuf buffer) {
        return new QuestActionPacket(buffer.readUtf(128), buffer.readEnum(Action.class));
    }
    public static void handle(QuestActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ToggleRequestLimiter.tryAcquire(player)) return;
            boolean changed = switch (packet.action) {
                case ACCEPT -> QuestManager.accept(player, packet.questId);
                case CANCEL -> QuestManager.cancel(player, packet.questId);
                case SUBMIT -> QuestManager.submit(player, packet.questId);
            };
            // A successful submission synchronizes itself after consuming items and
            // granting rewards. Other actions (and rejected requests) receive one full
            // authoritative response so the UI cannot retain speculative state.
            if (!(changed && packet.action == Action.SUBMIT)) {
                if (changed) {
                    QuestManager.tick(player,
                            com.whatever.aegis_ascension.data.PerkData.of(player));
                    ModNetworking.syncPerkDataTo(player);
                }
                ModNetworking.syncQuestsTo(player);
            }
        });
        context.setPacketHandled(true);
    }
}
