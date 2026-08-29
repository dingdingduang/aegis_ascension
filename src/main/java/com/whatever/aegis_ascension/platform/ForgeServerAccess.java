package com.whatever.aegis_ascension.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Forge 1.20.1 implementation of {@link ServerAccess}. */
public final class ForgeServerAccess implements ServerAccess {
    @Override
    public MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
