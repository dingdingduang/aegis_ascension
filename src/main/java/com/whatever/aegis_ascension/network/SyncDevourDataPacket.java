package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** On-demand, display-only snapshot of all inherited Devour attributes. */
public record SyncDevourDataPacket(
        List<Entry> entries,
        boolean allowAgainAfterDiscard
) {
    private static final int MAX_ENTRIES = 65_536;

    public SyncDevourDataPacket {
        entries = List.copyOf(entries);
    }

    public static SyncDevourDataPacket from(PlayerPerkData data) {
        double inheritance = Aegis.byId(AegisConstants.DEVOUR)
                .map(aegis -> aegis.stat(AegisConstants.DEVOUR_STAT_INHERITANCE))
                .orElse(1.0D);
        List<Entry> entries = new ArrayList<>(Math.min(
                data.getDevouredAttributes().size(),
                MAX_ENTRIES
        ));
        for (var attribute : data.getDevouredAttributes()) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            DevourAegis.EffectiveModifier effective =
                    DevourAegis.effectiveModifier(attribute);
            double amount = effective.amount() * inheritance;
            if (!Double.isFinite(amount)) {
                continue;
            }
            entries.add(new Entry(
                    attribute.itemId(),
                    attribute.attributeId(),
                    amount,
                    effective.operation(),
                    PlatformServices.config().isDevourAttributeBlacklisted(
                            attribute.attributeId()
                    )
            ));
        }
        return new SyncDevourDataPacket(
                entries,
                PlatformServices.config().allowDevourAgainAfterDiscard()
        );
    }

    public static void encode(SyncDevourDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        packet.entries.forEach(entry -> {
            buffer.writeUtf(entry.itemId, 256);
            buffer.writeUtf(entry.attributeId, 256);
            buffer.writeDouble(entry.amount);
            buffer.writeByte(entry.operation.wireValue());
            buffer.writeBoolean(entry.blacklisted);
        });
        buffer.writeBoolean(packet.allowAgainAfterDiscard);
    }

    public static SyncDevourDataPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid Devour attribute count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String itemId = buffer.readUtf(256);
            String attributeId = buffer.readUtf(256);
            double amount = buffer.readDouble();
            AttributeOperation operation = AttributeOperation.fromWireValue(
                    buffer.readUnsignedByte()
            );
            boolean blacklisted = buffer.readBoolean();
            if (ResourceLocation.tryParse(itemId) != null
                    && ResourceLocation.tryParse(attributeId) != null
                    && Double.isFinite(amount)) {
                entries.add(new Entry(
                        itemId,
                        attributeId,
                        amount,
                        operation,
                        blacklisted
                ));
            }
        }
        return new SyncDevourDataPacket(entries, buffer.readBoolean());
    }

    public static void handle(SyncDevourDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }

    public record Entry(
            String itemId,
            String attributeId,
            double amount,
            AttributeOperation operation,
            boolean blacklisted
    ) {
    }
}
