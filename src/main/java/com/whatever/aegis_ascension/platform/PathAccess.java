package com.whatever.aegis_ascension.platform;

import java.nio.file.Path;

/** Boundary for loader-provided game and configuration paths. */
public interface PathAccess {
    Path configDirectory();

    default Path modConfigDirectory(String modId) {
        return configDirectory().resolve(modId);
    }
}
