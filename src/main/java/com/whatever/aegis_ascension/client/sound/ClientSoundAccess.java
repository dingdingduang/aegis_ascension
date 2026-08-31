package com.whatever.aegis_ascension.client.sound;

/**
 * Version-specific boundary for local UI sounds. Keeping the shared call in terms of a
 * string id avoids leaking Minecraft's version-dependent SoundEvent API into quest code.
 */
interface ClientSoundAccess {
    void playUiSound(String soundEventId, float volume, float pitch);
}
