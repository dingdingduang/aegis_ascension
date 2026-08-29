package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenPerkScreenPacket(List<Perk> offers) {
    public OpenPerkScreenPacket {
        offers = List.copyOf(offers);
    }

    public static void encode(OpenPerkScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.offers.size());
        packet.offers.forEach(perk -> buffer.writeUtf(perk.id(), 128));
    }

    public static OpenPerkScreenPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(Math.max(0, buffer.readVarInt()), Perk.values().size());
        List<Perk> offers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Perk.byId(buffer.readUtf(128)).ifPresent(offers::add);
        }
        return new OpenPerkScreenPacket(offers);
    }

    public static void handle(OpenPerkScreenPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.openScreen(packet.offers)
        ));
        context.setPacketHandled(true);
    }
}
