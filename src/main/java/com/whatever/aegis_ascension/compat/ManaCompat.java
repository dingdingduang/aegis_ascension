package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import net.minecraft.world.entity.player.Player;

/** Selects one effective live Max Mana pool without double-counting shared talent bonuses. */
public final class ManaCompat {
    private ManaCompat() {
    }

    /**
     * Uses the largest live pool when multiple mana mods are installed. Taking the maximum
     * rather than the sum prevents Magic Conversion and Frieren from being counted once per mod.
     */
    public static double maximumMana(Player player, PlayerPerkData data) {
        double maximum = TalentEffects.magicConversionMaximumMana(data)
                + TalentEffects.frierenMaximumMana(data);
        if (IronSpellsCompat.isLoaded()) {
            maximum = Math.max(maximum, IronSpellsCompat.maximumMana(player, data));
        }
        if (ArsNouveauCompat.isLoaded()) {
            maximum = Math.max(maximum, ArsNouveauCompat.maximumMana(player, data));
        }
        return Math.max(0.0D, maximum);
    }

    /** Restores the same fraction of every installed supported mana pool. */
    public static void restoreFraction(Player player, PlayerPerkData data, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0.0D) {
            return;
        }
        if (IronSpellsCompat.isLoaded()) {
            IronSpellsCompat.restoreMana(
                    player,
                    IronSpellsCompat.maximumMana(player, data) * fraction
            );
        }
        if (ArsNouveauCompat.isLoaded()) {
            ArsNouveauCompat.restoreMana(
                    player,
                    ArsNouveauCompat.maximumMana(player, data) * fraction
            );
        }
    }
}
