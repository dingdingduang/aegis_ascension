package com.whatever.aegis_ascension.platform;

import net.minecraft.server.MinecraftServer;

/** Boundary for loader-specific access to the currently running server. */
public interface ServerAccess {
    MinecraftServer currentServer();
}
