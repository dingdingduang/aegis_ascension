package com.whatever.aegis_ascension.client;

import net.minecraft.client.Minecraft;

/** Guards refresh sends until a player exists; the server owns the configurable cooldown. */
public final class ClientRefreshRequestLimiter {
    private ClientRefreshRequestLimiter() {
    }

    public static boolean tryAcquire() {
        return Minecraft.getInstance().player != null;
    }

    public static void reset() {
        // Kept for callers; there is no client-side clock now.
    }
}
