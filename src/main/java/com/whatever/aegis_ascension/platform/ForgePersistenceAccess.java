package com.whatever.aegis_ascension.platform;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;

/** Forge 1.20.1 implementation of {@link PersistenceAccess}. */
public final class ForgePersistenceAccess implements PersistenceAccess {
    private final ServerAccess serverAccess;

    public ForgePersistenceAccess(ServerAccess serverAccess) {
        this.serverAccess = serverAccess;
    }

    @Override
    public Path playerDataDirectory(String modId) {
        MinecraftServer server = serverAccess.currentServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve(modId);
    }

    @Override
    public CompoundTag readCompressed(Path file) throws IOException {
        return NbtIo.readCompressed(file.toFile());
    }

    @Override
    public void writeCompressed(CompoundTag tag, Path file) throws IOException {
        NbtIo.writeCompressed(tag, file.toFile());
    }

    @Override
    public void safeReplace(Path target, Path temporary, Path backup) throws IOException {
        Util.safeReplaceFile(target.toFile(), temporary.toFile(), backup.toFile());
    }
}
