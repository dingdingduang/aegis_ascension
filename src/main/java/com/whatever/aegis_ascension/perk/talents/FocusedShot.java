package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.SR_FOCUSED_SHOT;
import static com.whatever.aegis_ascension.perk.TalentConstants.TRUE_SHOT_DAMAGE_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.TalentConstants.TRUE_SHOT_TRIGGER_CHANCE;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;

/** Runtime behavior for Focused Shot's True Shot arrow roll. */
public final class FocusedShot {
    private static final String ROLLED_TAG =
            "aegis_ascension:focused_shot_rolled";
    private static final String TRIGGERED_TAG =
            "aegis_ascension:focused_shot_triggered";

    private FocusedShot() {
    }

    /**
     * Returns the configured arrow multiplier after rolling exactly once per arrow.
     * Persisting the result on the projectile makes piercing and other multi-hit
     * arrows keep the same True Shot outcome for every entity they strike.
     */
    public static double arrowDamageMultiplier(
            ServerPlayer attacker,
            PlayerPerkData data,
            DamageSource source
    ) {
        if (!data.owns(SR_FOCUSED_SHOT)
                || !(source.getDirectEntity() instanceof AbstractArrow arrow)
                || arrow instanceof ThrownTrident
                || arrow.getOwner() != attacker) {
            return 1.0D;
        }

        CompoundTag projectileData = arrow.getPersistentData();
        if (!projectileData.getBoolean(ROLLED_TAG)) {
            Perk perk = perk();
            double chance = Mth.clamp(
                    perk.stat(TRUE_SHOT_TRIGGER_CHANCE),
                    0.0D,
                    1.0D
            );
            projectileData.putBoolean(ROLLED_TAG, true);
            projectileData.putBoolean(
                    TRIGGERED_TAG,
                    attacker.getRandom().nextDouble() < chance
            );
        }

        return projectileData.getBoolean(TRIGGERED_TAG)
                ? Math.max(0.0D, stat(
                        TRUE_SHOT_DAMAGE_MULTIPLIER
                ))
                : 1.0D;
    }

    private static double stat(String statKey) {
        return perk().stat(statKey);
    }

    private static Perk perk() {
        return Perk.byId(SR_FOCUSED_SHOT).orElseThrow(() ->
                new IllegalStateException(
                        "Missing configured perk: " + SR_FOCUSED_SHOT
                ));
    }
}
