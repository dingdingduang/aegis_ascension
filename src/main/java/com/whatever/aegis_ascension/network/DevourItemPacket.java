package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Client request to activate Devour Aegis on the main-hand item. */
public record DevourItemPacket() {
    private static final long MINIMUM_INTERVAL_TICKS = 5L;
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static void encode(DevourItemPacket packet, FriendlyByteBuf buffer) {
    }

    public static DevourItemPacket decode(FriendlyByteBuf buffer) {
        return new DevourItemPacket();
    }

    public static void handle(DevourItemPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !tryAcquire(player)) {
                return;
            }
            PerkData.get(player).ifPresent(data -> {
                if (DevourAegis.tryDevour(player, data)) {
                    ModNetworking.syncTo(player);
                }
            });
        });
        context.setPacketHandled(true);
    }

    private static boolean tryAcquire(ServerPlayer player) {
        long currentTick = player.serverLevel().getGameTime();
        Long lastTick = LAST_REQUEST_TICK.get(player);
        if (lastTick != null && currentTick >= lastTick
                && currentTick - lastTick < MINIMUM_INTERVAL_TICKS) {
            return false;
        }
        LAST_REQUEST_TICK.put(player, currentTick);
        return true;
    }
}
