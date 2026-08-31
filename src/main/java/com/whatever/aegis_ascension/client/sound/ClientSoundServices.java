package com.whatever.aegis_ascension.client.sound;

/** Client-only entry point for data-driven UI sounds. */
public final class ClientSoundServices {
    private static final ClientSoundAccess ACCESS = new ForgeClientSoundAccess();

    private ClientSoundServices() {
    }

    public static void playUiSound(String soundEventId) {
        ACCESS.playUiSound(soundEventId, 1.0F, 1.0F);
    }

    public static void playUiSound(String soundEventId, float volume, float pitch) {
        ACCESS.playUiSound(soundEventId, volume, pitch);
    }
}
