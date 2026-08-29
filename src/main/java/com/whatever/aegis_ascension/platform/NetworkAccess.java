package com.whatever.aegis_ascension.platform;

import net.minecraft.server.level.ServerPlayer;

/** Boundary for loader-specific packet registration and transport. */
public interface NetworkAccess {
    void registerPackets();

    void sendToServer(Object packet);

    void sendToPlayer(ServerPlayer player, Object packet);
}
