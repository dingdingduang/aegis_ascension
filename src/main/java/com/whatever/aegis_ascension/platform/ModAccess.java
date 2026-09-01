package com.whatever.aegis_ascension.platform;

import java.nio.file.Path;
import java.util.Optional;

/** Seam for loader mod discovery and dynamically registered game-event handlers. */
public interface ModAccess {
    boolean isLoaded(String modId);

    /**
     * Locates a file inside another loaded mod's own jar.
     *
     * <p>Addon progression catalogs live under {@code assets/}, which a dedicated server's
     * resource manager never loads, so they have to be read from the mod file directly
     * rather than through Minecraft's resource pipeline.</p>
     *
     * @return the file, or empty when the mod is absent or ships no such resource
     */
    Optional<Path> findModResource(String modId, String path);

    void registerGameEventHandler(Object handler);

    void registerEndServerTick(Runnable listener);
}
