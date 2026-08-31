package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Compact server-to-client batch for progress-only quest changes. */
public record SyncQuestProgressPacket(Map<String, Integer> progressByQuestId) {
    public SyncQuestProgressPacket {
        progressByQuestId = progressByQuestId == null
                ? Map.of() : Map.copyOf(progressByQuestId);
    }

    public static void encode(SyncQuestProgressPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(
                NetworkLimits.MAX_QUEST_PROGRESS_UPDATES,
                packet.progressByQuestId.size()
        );
        buffer.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, Integer> update : packet.progressByQuestId.entrySet()) {
            if (written++ >= count) break;
            buffer.writeUtf(update.getKey(), 128);
            buffer.writeVarInt(Math.max(0, update.getValue()));
        }
    }

    public static SyncQuestProgressPacket decode(FriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_QUEST_PROGRESS_UPDATES,
                "quest progress update"
        );
        Map<String, Integer> updates = new LinkedHashMap<>(count);
        for (int index = 0; index < count; index++) {
            updates.put(buffer.readUtf(128), Math.max(0, buffer.readVarInt()));
        }
        return new SyncQuestProgressPacket(updates);
    }

    public static void handle(SyncQuestProgressPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)));
        context.setPacketHandled(true);
    }
}
