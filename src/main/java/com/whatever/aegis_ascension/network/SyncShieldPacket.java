package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientShieldState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the local player's current total shield, for the shield HUD. */
public record SyncShieldPacket(float total) {
    public static void encode(SyncShieldPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.total);
    }

    public static SyncShieldPacket decode(FriendlyByteBuf buffer) {
        return new SyncShieldPacket(buffer.readFloat());
    }

    public static void handle(SyncShieldPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientShieldState.set(packet.total)
        ));
        context.setPacketHandled(true);
    }
}
