package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** One login-time snapshot of every catalog used by catalog-dependent packets and UI. */
public record SyncServerCatalogPacket(
        String hash,
        String talentsJson,
        String aegisesJson,
        String skillEnhancementsJson,
        String virtualItemsJson,
        String soulLinksJson,
        String mysteriousDollJson,
        String shrineMaidenDanceJson,
        String questsJson
) {
    public SyncServerCatalogPacket {
        hash = Objects.requireNonNull(hash, "hash");
        talentsJson = Objects.requireNonNull(talentsJson, "talentsJson");
        aegisesJson = Objects.requireNonNull(aegisesJson, "aegisesJson");
        skillEnhancementsJson = Objects.requireNonNull(
                skillEnhancementsJson,
                "skillEnhancementsJson"
        );
        virtualItemsJson = Objects.requireNonNull(virtualItemsJson, "virtualItemsJson");
        soulLinksJson = Objects.requireNonNull(soulLinksJson, "soulLinksJson");
        mysteriousDollJson = Objects.requireNonNull(
                mysteriousDollJson,
                "mysteriousDollJson"
        );
        shrineMaidenDanceJson = Objects.requireNonNull(
                shrineMaidenDanceJson,
                "shrineMaidenDanceJson"
        );
        questsJson = Objects.requireNonNull(questsJson, "questsJson");
        requireLength(hash, NetworkLimits.MAX_CATALOG_HASH_CHARS, "catalog hash");
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Catalog hash is not a SHA-256 hex digest");
        }
        requireLength(talentsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS, "talent catalog");
        requireLength(aegisesJson, NetworkLimits.MAX_CATALOG_JSON_CHARS, "Aegis catalog");
        requireLength(
                skillEnhancementsJson,
                NetworkLimits.MAX_CATALOG_JSON_CHARS,
                "skill enhancement catalog"
        );
        requireLength(
                virtualItemsJson,
                NetworkLimits.MAX_CATALOG_JSON_CHARS,
                "virtual item catalog"
        );
        requireLength(soulLinksJson, NetworkLimits.MAX_CATALOG_JSON_CHARS, "Soul Link catalog");
        requireLength(questsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS, "quest catalog");
        requireLength(
                mysteriousDollJson,
                NetworkLimits.MAX_CATALOG_JSON_CHARS,
                "Mysterious Doll catalog"
        );
        requireLength(
                shrineMaidenDanceJson,
                NetworkLimits.MAX_CATALOG_JSON_CHARS,
                "Shrine Maiden Dance catalog"
        );
        long totalLength = (long) talentsJson.length()
                + aegisesJson.length()
                + skillEnhancementsJson.length()
                + virtualItemsJson.length()
                + soulLinksJson.length()
                + mysteriousDollJson.length()
                + shrineMaidenDanceJson.length();
        if (totalLength > NetworkLimits.MAX_TOTAL_CATALOG_CHARS) {
            throw new IllegalArgumentException(
                    "Combined server catalogs exceed protocol limit of "
                            + NetworkLimits.MAX_TOTAL_CATALOG_CHARS + " characters"
            );
        }
    }

    public static void encode(SyncServerCatalogPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.hash, NetworkLimits.MAX_CATALOG_HASH_CHARS);
        buffer.writeUtf(packet.talentsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.aegisesJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.skillEnhancementsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.virtualItemsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.soulLinksJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.mysteriousDollJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.shrineMaidenDanceJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
        buffer.writeUtf(packet.questsJson, NetworkLimits.MAX_CATALOG_JSON_CHARS);
    }

    public static SyncServerCatalogPacket decode(FriendlyByteBuf buffer) {
        return new SyncServerCatalogPacket(
                buffer.readUtf(NetworkLimits.MAX_CATALOG_HASH_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS),
                buffer.readUtf(NetworkLimits.MAX_CATALOG_JSON_CHARS)
        );
    }

    public static void handle(
            SyncServerCatalogPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }

    private static void requireLength(String value, int maximum, String description) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(
                    description + " exceeds protocol limit of " + maximum + " characters"
            );
        }
    }
}
