package com.whatever.aegis_ascension.platform;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Boundary for world-root discovery and compressed NBT file operations.
 *
 * <p>The save format remains owned by {@code PlayerPerkData}; this interface only
 * isolates the loader/server-specific filesystem and NBT I/O operations.</p>
 */
public interface PersistenceAccess {
    Path playerDataDirectory(String modId);

    CompoundTag readCompressed(Path file) throws IOException;

    void writeCompressed(CompoundTag tag, Path file) throws IOException;

    void safeReplace(Path target, Path temporary, Path backup) throws IOException;
}
