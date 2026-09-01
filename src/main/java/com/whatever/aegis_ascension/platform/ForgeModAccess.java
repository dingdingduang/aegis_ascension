package com.whatever.aegis_ascension.platform;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Forge 1.20.1 implementation of {@link ModAccess}. */
public final class ForgeModAccess implements ModAccess {
    @Override
    public boolean isLoaded(String modId) {
        return modId != null && !modId.trim().isEmpty() && ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<Path> findModResource(String modId, String path) {
        if (!isLoaded(modId) || path == null || path.isBlank()) {
            return Optional.empty();
        }
        IModFileInfo fileInfo = ModList.get().getModFileById(modId);
        if (fileInfo == null || fileInfo.getFile() == null) {
            return Optional.empty();
        }
        try {
            Path resource = fileInfo.getFile().findResource(path);
            return Files.isRegularFile(resource) ? Optional.of(resource) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void registerGameEventHandler(Object handler) {
        MinecraftForge.EVENT_BUS.register(handler);
    }

    @Override
    public void registerEndServerTick(Runnable listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.run();
            }
        });
    }
}
