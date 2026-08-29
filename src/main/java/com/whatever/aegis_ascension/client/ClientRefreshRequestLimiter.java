package com.whatever.aegis_ascension.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Shared client-side 10-tick limiter for every selection-offer refresh packet. */
public final class ClientRefreshRequestLimiter {
    private static final int MINIMUM_INTERVAL_TICKS = 10;
    private static int lastSentTick = Integer.MIN_VALUE;

    private ClientRefreshRequestLimiter() {
    }

    public static boolean tryAcquire() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        int currentTick = player.tickCount;
        if (currentTick >= lastSentTick
                && (long) currentTick - lastSentTick < MINIMUM_INTERVAL_TICKS) {
            return false;
        }
        lastSentTick = currentTick;
        return true;
    }

    public static void reset() {
        lastSentTick = Integer.MIN_VALUE;
    }
}
