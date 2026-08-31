package com.whatever.aegis_ascension.client.sound;

import com.whatever.aegis_ascension.AegisAscensionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Set;

/** Forge 1.20.1 implementation; other target versions replace only this adapter. */
final class ForgeClientSoundAccess implements ClientSoundAccess {
    private final Set<String> reportedMissingSounds = new HashSet<>();

    @Override
    public void playUiSound(String soundEventId, float volume, float pitch) {
        if (soundEventId == null || soundEventId.isBlank()) return;
        ResourceLocation location = ResourceLocation.tryParse(soundEventId);
        if (location == null) {
            reportMissing(soundEventId, "invalid ResourceLocation");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager().getSoundEvent(location) == null) {
            reportMissing(soundEventId, "event is absent from sounds.json");
            return;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(
                location,
                SoundSource.MASTER,
                Math.max(0.0F, volume),
                Math.max(0.01F, pitch),
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        ));
    }

    private void reportMissing(String soundEventId, String reason) {
        if (reportedMissingSounds.add(soundEventId)) {
            AegisAscensionMod.getLogger().warn(
                    "Cannot play configured UI sound '{}': {}",
                    soundEventId,
                    reason
            );
        }
    }
}
