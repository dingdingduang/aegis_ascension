package com.whatever.aegis_ascension.client;

/** Client-side mirror of the local player's total shield, fed by SyncShieldPacket. */
public final class ClientShieldState {
    private static volatile float shield;

    private ClientShieldState() {
    }

    public static void set(float value) {
        shield = Math.max(0.0F, value);
    }

    public static float get() {
        return shield;
    }
}
