package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side login handshake for catalog-dependent protocol state. */
public final class ServerCatalogSync {
    private static final Map<UUID, String> EXPECTED_HASHES = new ConcurrentHashMap<>();
    private static final Set<UUID> READY_PLAYERS = ConcurrentHashMap.newKeySet();

    private ServerCatalogSync() {
    }

    public static void begin(ServerPlayer player) {
        Snapshot snapshot = createSnapshot();
        UUID playerId = player.getUUID();
        READY_PLAYERS.remove(playerId);
        EXPECTED_HASHES.put(playerId, snapshot.hash());
        PlatformServices.network().sendToPlayer(player, snapshot.packet());
    }

    public static boolean isReady(ServerPlayer player) {
        return READY_PLAYERS.contains(player.getUUID());
    }

    public static void acknowledge(ServerPlayer player, String hash) {
        UUID playerId = player.getUUID();
        String expected = EXPECTED_HASHES.get(playerId);
        if (expected == null || !expected.equals(hash)) {
            AegisAscensionMod.getLogger().warn(
                    "Rejected catalog acknowledgement from {}: expected {}, received {}",
                    player.getGameProfile().getName(),
                    expected,
                    hash
            );
            begin(player);
            return;
        }
        EXPECTED_HASHES.remove(playerId);
        READY_PLAYERS.add(playerId);
        ModNetworking.syncTo(player);
    }

    public static void clear(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EXPECTED_HASHES.remove(playerId);
        READY_PLAYERS.remove(playerId);
    }

    public static void clearAll() {
        EXPECTED_HASHES.clear();
        READY_PLAYERS.clear();
    }

    private static Snapshot createSnapshot() {
        requireCatalogSize(Perk.values().size(), NetworkLimits.MAX_TALENTS, "talents");
        requireCatalogSize(Aegis.values().size(), NetworkLimits.MAX_AEGISES, "Aegises");
        requireCatalogSize(
                SkillEnhancement.values().size(),
                NetworkLimits.MAX_SKILL_ENHANCEMENTS,
                "skill enhancements"
        );
        requireCatalogSize(
                VirtualItems.all().size(),
                NetworkLimits.MAX_VIRTUAL_ITEMS,
                "virtual items"
        );
        String talents = Perk.exportCatalogJson();
        String aegises = Aegis.exportCatalogJson();
        String enhancements = SkillEnhancement.exportCatalogJson();
        String virtualItems = VirtualItems.exportCatalogJson();
        String hash = sha256(talents, aegises, enhancements, virtualItems);
        return new Snapshot(
                hash,
                new SyncServerCatalogPacket(
                        hash,
                        talents,
                        aegises,
                        enhancements,
                        virtualItems
                )
        );
    }

    private static String sha256(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static void requireCatalogSize(int actual, int maximum, String description) {
        if (actual > maximum) {
            throw new IllegalStateException(
                    "Configured " + description + " exceed protocol limit: "
                            + actual + " > " + maximum
            );
        }
    }

    private record Snapshot(String hash, SyncServerCatalogPacket packet) {
    }
}
