package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Compact server-to-client batch for progress-only quest changes. */
public record SyncQuestProgressPacket(Map<String, List<Integer>> progressByQuestId) {
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
        for (Map.Entry<String, List<Integer>> update : packet.progressByQuestId.entrySet()) {
            if (written++ >= count) break;
            buffer.writeUtf(update.getKey(), 128);
            List<Integer> counters = update.getValue();
            // Index zero is the main objective; the rest are extra requirements in order.
            int size = Math.min(NetworkLimits.MAX_QUEST_REQUIREMENTS, counters.size());
            buffer.writeVarInt(size);
            for (int index = 0; index < size; index++) {
                buffer.writeVarInt(Math.max(0, counters.get(index)));
            }
        }
    }

    public static SyncQuestProgressPacket decode(FriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_QUEST_PROGRESS_UPDATES,
                "quest progress update"
        );
        Map<String, List<Integer>> updates = new LinkedHashMap<>(count);
        for (int index = 0; index < count; index++) {
            String questId = buffer.readUtf(128);
            int size = NetworkLimits.readBoundedCount(buffer,
                    NetworkLimits.MAX_QUEST_REQUIREMENTS, "quest requirement");
            List<Integer> counters = new ArrayList<>(size);
            for (int counter = 0; counter < size; counter++) {
                counters.add(Math.max(0, buffer.readVarInt()));
            }
            updates.put(questId, counters);
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
