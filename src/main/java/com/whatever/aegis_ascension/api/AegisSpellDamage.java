package com.whatever.aegis_ascension.api;

import com.whatever.aegis_ascension.compat.ArsNouveauCompat;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import net.minecraft.world.damagesource.DamageSource;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Reusable spell-origin API for damage sources that do not have a dedicated
 * spell {@link DamageSource} class.
 *
 * <p>Iron's Spells and Ars Nouveau sources are recognized automatically. Other
 * spell addons can wrap the synchronous call to {@code LivingEntity.hurt} in
 * {@link #runAsSpellDamage(DamageSource, Runnable)} or
 * {@link #callAsSpellDamage(DamageSource, BooleanSupplier)}. The marker controls
 * Skill Damage only; the damage type/tag still independently decides whether
 * Physical or Magic Damage applies.</p>
 */
public final class AegisSpellDamage {
    private static final ThreadLocal<Map<DamageSource, Integer>> ACTIVE_SOURCES =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private AegisSpellDamage() {
    }

    /** Returns whether this hit originated from a supported or explicitly marked spell. */
    public static boolean isSpellDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        return ACTIVE_SOURCES.get().containsKey(source)
                || IronSpellsCompat.isIronSpellDamage(source)
                || ArsNouveauCompat.isArsSpellDamage(source);
    }

    /** Runs a synchronous damage action while its source is classified as a spell. */
    public static void runAsSpellDamage(DamageSource source, Runnable damageAction) {
        Objects.requireNonNull(damageAction, "damageAction");
        enter(source);
        try {
            damageAction.run();
        } finally {
            exit(source);
        }
    }

    /** Runs a synchronous boolean damage action while its source is classified as a spell. */
    public static boolean callAsSpellDamage(
            DamageSource source,
            BooleanSupplier damageAction
    ) {
        Objects.requireNonNull(damageAction, "damageAction");
        enter(source);
        try {
            return damageAction.getAsBoolean();
        } finally {
            exit(source);
        }
    }

    private static void enter(DamageSource source) {
        Objects.requireNonNull(source, "source");
        ACTIVE_SOURCES.get().merge(source, 1, Integer::sum);
    }

    private static void exit(DamageSource source) {
        Map<DamageSource, Integer> active = ACTIVE_SOURCES.get();
        int depth = active.getOrDefault(source, 0);
        if (depth <= 1) {
            active.remove(source);
        } else {
            active.put(source, depth - 1);
        }
        if (active.isEmpty()) {
            ACTIVE_SOURCES.remove();
        }
    }
}
