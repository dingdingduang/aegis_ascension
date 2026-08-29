package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenAegisScreenPacket(List<Aegis> offers) {
    public OpenAegisScreenPacket {
        offers = List.copyOf(offers);
    }

    public static void encode(OpenAegisScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.offers.size());
        packet.offers.forEach(aegis -> buffer.writeUtf(aegis.id(), 128));
    }

    public static OpenAegisScreenPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > Aegis.values().size()) {
            throw new IllegalArgumentException("Invalid Aegis offer count: " + count);
        }
        List<Aegis> offers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Aegis.byId(buffer.readUtf(128)).ifPresent(aegis -> {
                if (!offers.contains(aegis)) {
                    offers.add(aegis);
                }
            });
        }
        return new OpenAegisScreenPacket(offers);
    }

    public static void handle(OpenAegisScreenPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.openAegisScreen(packet.offers)
        ));
        context.setPacketHandled(true);
    }
}
