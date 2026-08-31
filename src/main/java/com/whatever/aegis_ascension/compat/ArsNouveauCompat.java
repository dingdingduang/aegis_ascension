package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;

/** Optional Ars Nouveau bridge; Ars API symbols are isolated in lazily loaded classes. */
public final class ArsNouveauCompat {
    public static final String MOD_ID = "ars_nouveau";
    private static final String MANA_HANDLER_CLASS =
            "com.whatever.aegis_ascension.compat.ArsNouveauManaHandler";
    private static final String SPELL_DAMAGE_SOURCE_CLASS =
            "com.hollingsworth.arsnouveau.api.util.DamageUtil$SpellDamageSource";

    private static boolean handlersRegistered;

    private ArsNouveauCompat() {
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /** Runtime-name check keeps ordinary combat code free of a hard Ars dependency. */
    public static boolean isArsSpellDamage(DamageSource source) {
        if (!isLoaded()) {
            return false;
        }
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            if (SPELL_DAMAGE_SOURCE_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Registers the API-linked event handler only when Ars Nouveau is installed. */
    public static void registerOptionalHandlers() {
        if (!isLoaded() || handlersRegistered) {
            return;
        }
        try {
            Class<?> handler = Class.forName(
                    MANA_HANDLER_CLASS,
                    true,
                    ArsNouveauCompat.class.getClassLoader()
            );
            PlatformServices.mods().registerGameEventHandler(handler);
            handlersRegistered = true;
            AegisAscensionMod.getLogger().info(
                    "Enabled optional Ars Nouveau mana compatibility"
            );
        } catch (ReflectiveOperationException | LinkageError exception) {
            AegisAscensionMod.getLogger().error(
                    "Ars Nouveau is installed, but its mana bridge could not load",
                    exception
            );
        }
    }

    /**
     * Returns Ars Nouveau's calculated Max Mana, including our MaxManaCalcEvent bonus.
     * The internal talent value remains available if Ars is missing or its API changed.
     */
    public static double maximumMana(Player player, PlayerPerkData data) {
        double fallback = TalentEffects.magicConversionMaximumMana(data)
                + TalentEffects.frierenMaximumMana(data);
        if (!isLoaded()) {
            return fallback;
        }
        try {
            return Math.max(fallback, ArsApi.maximumMana(player));
        } catch (LinkageError | RuntimeException exception) {
            return fallback;
        }
    }

    /** Restores live Ars Nouveau mana through its public mana capability. */
    public static void restoreMana(Player player, double amount) {
        if (!isLoaded() || amount <= 0.0D || !Double.isFinite(amount)) {
            return;
        }
        try {
            ArsApi.restoreMana(player, amount);
        } catch (LinkageError | RuntimeException exception) {
            // Optional API missing or changed: leave the other supported pools usable.
        }
    }

    /** Direct Ars API references live here and are resolved only after the mod check. */
    private static final class ArsApi {
        private ArsApi() {
        }

        private static double maximumMana(Player player) {
            return Math.max(
                    0,
                    com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(player)
            );
        }

        private static void restoreMana(Player player, double amount) {
            com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry
                    .getMana(player)
                    .ifPresent(mana -> mana.addMana(amount));
        }
    }
}
