package com.whatever.aegis_ascension.data;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.PersistenceAccess;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every online player's {@link PlayerPerkData}, keyed by UUID, and persists each
 * player to their own NBT file under {@code <world>/modid -> <aegis_ascension>/<uuid>.nbt}.
 *
 * <p>Keying by UUID rather than by player entity is the point of this class. Minecraft
 * rebuilds the {@code ServerPlayer} object on respawn and on the End-portal return, and every
 * mechanism that hangs data off the entity has to copy it across that seam - which is exactly
 * where this mod kept losing progression. A UUID survives both, so the data never moves and
 * there is nothing to copy.</p>
 *
 * <p>Persistence is deliberately plain file I/O rather than a Forge capability or a
 * {@code SavedData}. The stable file shape remains portable between Forge 1.16.5, Forge 1.20.1,
 * and NeoForge; the target-specific filesystem and NBT calls are isolated by
 * {@link PersistenceAccess}.</p>
 *
 * <p>Writes go through a temp file and {@link PersistenceAccess#safeReplace}, leaving the previous
 * version as {@code <uuid>.nbt_old}. A player's entire progression lives in one small file,
 * so a torn write during a crash would otherwise cost them everything.</p>
 */
public final class PerkStore {
    private static final PersistenceAccess PERSISTENCE = PlatformServices.persistence();

    /** Bumped whenever the on-disk shape changes, so a future port can migrate old files. */
    private static final int CURRENT_DATA_VERSION = 1;
    private static final String EXTENSION = ".nbt";
    /** Replaces {@link #EXTENSION} rather than appending to it: {@code <uuid>.nbt_old}. */
    private static final String BACKUP_EXTENSION = ".nbt_old";
    private static final String DATA_VERSION_TAG = "DataVersion";
    private static final String LAST_KNOWN_NAME_TAG = "LastKnownName";

    private static final Map<UUID, PlayerPerkData> LIVE = new ConcurrentHashMap<>();
    /**
     * Logout fires at {@code PlayerList.remove} line 346, but the player is not saved until
     * line 349 - so eviction cannot happen in the logout handler without discarding the
     * session. Logout flags the player here instead and {@link #save} evicts after writing.
     */
    private static final Set<UUID> PENDING_EVICT = ConcurrentHashMap.newKeySet();
    /**
     * Players whose file exists but could not be read. Their data must never be written back,
     * or a transient read failure would overwrite a good file with an empty default.
     */
    private static final Set<UUID> BLOCKED_SAVES = ConcurrentHashMap.newKeySet();

    private PerkStore() {
    }

    /** The player's data, created empty if this is their first time on the server. */
    public static PlayerPerkData get(UUID id) {
        return LIVE.computeIfAbsent(id, ignored -> new PlayerPerkData());
    }

    /**
     * Discards a player's progression, for {@code resetPerksOnDeath}.
     *
     * @param keepInventory when true, spares ordinary banked storage, current shop stock,
     *                      and ordinary virtual-item use counts by resetting in place
     *                      through {@link PlayerPerkData#resetAll()} - the same thing
     *                      {@code /perk reset} does. Devour Cores are progression and are
     *                      removed in either mode. When false, everything goes.
     */
    public static void reset(UUID id, boolean keepInventory) {
        if (keepInventory) {
            get(id).resetAll();
            return;
        }
        LIVE.put(id, new PlayerPerkData());
    }

    /**
     * Reads a player's file during login, before they are added to the world.
     *
     * <p>Populates the existing instance rather than replacing it, so any reference handed
     * out before login stays valid.</p>
     */
    public static void load(ServerPlayer player) {
        UUID id = player.getUUID();
        BLOCKED_SAVES.remove(id);
        PENDING_EVICT.remove(id);
        PlayerPerkData data = get(id);

        Path directory = directory();
        if (directory == null) {
            return;
        }
        Path file = directory.resolve(id + EXTENSION);
        Path backup = directory.resolve(id + BACKUP_EXTENSION);

        boolean existed = Files.exists(file) || Files.exists(backup);
        CompoundTag tag = read(file);
        if (tag == null) {
            tag = read(backup);
            if (tag != null) {
                AegisAscensionMod.getLogger().warn(
                        "Perk data for {} was unreadable, recovered from the {} backup",
                        player.getGameProfile().getName(), BACKUP_EXTENSION);
            }
        }

        if (tag != null) {
            data.deserializeNBT(tag);
            return;
        }

        if (existed) {
            // Both copies are present but unreadable. Writing would destroy whatever is
            // still on disk, so refuse to save this player until they reconnect.
            BLOCKED_SAVES.add(id);
            AegisAscensionMod.getLogger().error(
                    "Perk data for {} exists but could not be read from either file - saving is "
                            + "disabled for this session so the files are not overwritten. Restore "
                            + "{} manually or delete it to start fresh.",
                    player.getGameProfile().getName(), file);
            return;
        }

    }

    /**
     * Writes a player's file. Called from {@code PlayerEvent.SaveToFile}, so it runs on every
     * autosave, on {@code /save-all}, and once more as the player disconnects.
     */
    public static void save(ServerPlayer player) {
        UUID id = player.getUUID();
        try {
            if (BLOCKED_SAVES.contains(id)) {
                return;
            }
            PlayerPerkData data = LIVE.get(id);
            Path directory = directory();
            if (data == null || directory == null) {
                return;
            }

            CompoundTag tag = data.serializeNBT();
            tag.putInt(DATA_VERSION_TAG, CURRENT_DATA_VERSION);
            tag.putString(LAST_KNOWN_NAME_TAG, player.getGameProfile().getName());

            Files.createDirectories(directory);
            Path target = directory.resolve(id + EXTENSION);
            Path backup = directory.resolve(id + BACKUP_EXTENSION);
            Path temporary = Files.createTempFile(directory, id + "-", EXTENSION);
            PERSISTENCE.writeCompressed(tag, temporary);
            PERSISTENCE.safeReplace(target, temporary, backup);
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error("Failed to save perk data for {}",
                    player.getGameProfile().getName(), exception);
        } finally {
            if (PENDING_EVICT.remove(id)) {
                LIVE.remove(id);
                BLOCKED_SAVES.remove(id);
            }
        }
    }

    /** Marks a disconnecting player for eviction once their final save has been written. */
    public static void markForEviction(UUID id) {
        PENDING_EVICT.add(id);
    }

    /**
     * Drops everything on server shutdown.
     *
     * <p>Required, not tidiness: in singleplayer this static map outlives the integrated
     * server, so without it, quitting to the title screen and opening a different world would
     * carry the first world's progression into the second.</p>
     */
    public static void clear() {
        LIVE.clear();
        PENDING_EVICT.clear();
        BLOCKED_SAVES.clear();
    }

    private static Path directory() {
        Path directory = PERSISTENCE.playerDataDirectory(AegisAscensionMod.MOD_ID);
        if (directory == null) {
            AegisAscensionMod.getLogger().error("No server available - perk data cannot be reached");
        }
        return directory;
    }

    /** Returns null when the file is absent or unreadable; the caller distinguishes the two. */
    private static CompoundTag read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return PERSISTENCE.readCompressed(file);
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error("Could not read perk data file {}", file, exception);
            return null;
        }
    }
}
