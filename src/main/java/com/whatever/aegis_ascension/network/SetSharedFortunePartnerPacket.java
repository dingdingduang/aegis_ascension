package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.perk.talents.SharedFortune;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Client request to bind or unbind Shared Fortune's persistent teammate UUID. */
public record SetSharedFortunePartnerPacket(UUID partnerId) {
    private static final UUID UNBOUND = new UUID(0L, 0L);
    private static final long MINIMUM_INTERVAL_TICKS = 10L;
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK = new WeakHashMap<>();

    public static SetSharedFortunePartnerPacket unbind() {
        return new SetSharedFortunePartnerPacket(UNBOUND);
    }

    public static void encode(SetSharedFortunePartnerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.partnerId);
    }

    public static SetSharedFortunePartnerPacket decode(FriendlyByteBuf buffer) {
        return new SetSharedFortunePartnerPacket(buffer.readUUID());
    }

    public static void handle(SetSharedFortunePartnerPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !tryAcquire(sender)) {
                return;
            }
            if (UNBOUND.equals(packet.partnerId)) {
                SharedFortune.unbind(sender);
            } else {
                SharedFortune.bind(sender, packet.partnerId);
            }
            ModNetworking.syncTo(sender);
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
