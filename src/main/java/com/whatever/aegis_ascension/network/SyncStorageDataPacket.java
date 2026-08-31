package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.storage.StoredItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: the whole storage view. Carries each row's sell value alongside it so
 * the client never needs the server-only shop catalogue to price a sale.
 */
public record SyncStorageDataPacket(List<StoredItem> items,
                                    List<Integer> sellUnitValues,
                                    int maxItemTypes) {
    public static void encode(SyncStorageDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.items.size());
        for (int i = 0; i < packet.items.size(); i++) {
            packet.items.get(i).write(buffer);
            buffer.writeVarInt(packet.sellUnitValues.get(i));
        }
        buffer.writeVarInt(packet.maxItemTypes);
    }

    public static SyncStorageDataPacket decode(FriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_STORAGE_ROWS,
                "storage row"
        );
        List<StoredItem> items = new ArrayList<>(count);
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(StoredItem.read(buffer));
            values.add(buffer.readVarInt());
        }
        return new SyncStorageDataPacket(items, values, buffer.readVarInt());
    }

    public static void handle(SyncStorageDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }
}
