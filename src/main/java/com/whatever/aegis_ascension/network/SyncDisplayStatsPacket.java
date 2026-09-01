package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.util.DisplayStatScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries only the Custom Stats display values.
 *
 * <p>{@link SyncPerkDataPacket} sends the player's whole progression state — talent
 * ranks, Skill Enhancement ranks, pending offers, owned Aegises, currency, Aegis
 * Ascension progress. That is the right payload when any of it may have changed, but
 * the Custom Stats tab refreshes on a timer while it is open and only ever reads the
 * display values, so resending the rest once a second is waste. Nothing here is
 * authoritative: these are derived numbers used to draw a screen.</p>
 */
public record SyncDisplayStatsPacket(Map<String, Double> displayStats,
                                     DisplayStatScope scope) {
    public SyncDisplayStatsPacket {
        displayStats = Collections.unmodifiableMap(new LinkedHashMap<>(displayStats));
    }

    public static void encode(SyncDisplayStatsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.scope.wireValue());
        buffer.writeVarInt(packet.displayStats.size());
        packet.displayStats.forEach((key, value) -> {
            buffer.writeUtf(key, 128);
            buffer.writeDouble(value);
        });
    }

    public static SyncDisplayStatsPacket decode(FriendlyByteBuf buffer) {
        DisplayStatScope scope = DisplayStatScope.fromWireValue(buffer.readVarInt());
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_DISPLAY_STATS,
                "display stat"
        );
        Map<String, Double> displayStats = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = buffer.readUtf(128);
            double value = buffer.readDouble();
            if (!key.isBlank() && Double.isFinite(value)) {
                displayStats.put(key, value);
            }
        }
        return new SyncDisplayStatsPacket(displayStats, scope);
    }

    public static void handle(SyncDisplayStatsPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }
}
