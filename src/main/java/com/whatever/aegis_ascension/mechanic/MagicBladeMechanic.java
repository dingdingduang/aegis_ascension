package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.MAGIC_DAMAGE_PER_MAX_MANA;
import static com.whatever.aegis_ascension.perk.TalentConstants.MAX_MANA_RESTORE_PER_ATTACK;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_MAGIC_BLADE;
import static com.whatever.aegis_ascension.perk.TalentConstants.RESTORE_COOLDOWN_SECONDS;
import static com.whatever.aegis_ascension.mechanic.TalentStatService.sumOwnedStat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ManaCompat;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.MagicBladeMath;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/** Converts a player's ordinary melee damage into Magic Blade damage. */
public final class MagicBladeMechanic {
    static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformServices.resources().create("aegis_ascension", "magic_blade")
    );

    private MagicBladeMechanic() {
    }

    /** Returns true only for the custom source created by this mechanic. */
    public static boolean isMagicBladeDamage(DamageSource source) {
        return source.is(DAMAGE_TYPE);
    }

    /**
     * Replaces one ordinary player melee hit with a custom magic hit.
     *
     * <p>The original attack event is canceled by the caller and this method re-enters
     * {@link LivingEntity#hurt(DamageSource, float)} with a data-driven magic source. The
     * source keeps the player as both causing and direct entity, so kill credit, summon
     * ownership, and direct-melee talent checks remain intact.</p>
     *
     * @return true when the attack was a Magic Blade attack and the original event should
     *         be canceled, including when the replacement amount is zero
     */
    public static boolean convertAttack(LivingEntity target, DamageSource source) {
        if (!source.is(DamageTypes.PLAYER_ATTACK)
                || !(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker) {
            return false;
        }

        PlayerPerkData data = PerkData.of(attacker);
        if (!data.owns(PERK_MAGIC_BLADE) || !data.isTalentEnabled(PERK_MAGIC_BLADE)) {
            return false;
        }

        DamageSource magicSource = createDamageSource(attacker);
        if (magicSource == null) {
            // Keep a malformed/missing data pack from deleting ordinary melee damage.
            return false;
        }

        double maxMana = ManaCompat.maximumMana(attacker, data);
        double damage = MagicBladeMath.replacementDamage(
                maxMana,
                sumOwnedStat(data, MAGIC_DAMAGE_PER_MAX_MANA)
        );
        if (damage <= 0.0D) {
            return true;
        }

        boolean applied = target.hurt(magicSource, (float) damage);
        if (applied) {
            double cooldown = sumOwnedStat(data, RESTORE_COOLDOWN_SECONDS);
            if (data.tryReserveMagicBladeManaRestore(
                    attacker.getServer() == null
                            ? attacker.tickCount
                            : attacker.getServer().getTickCount(),
                    cooldown
            )) {
                double restoreFraction = sumOwnedStat(data, MAX_MANA_RESTORE_PER_ATTACK);
                ManaCompat.restoreFraction(attacker, data, restoreFraction);
            }
        }
        return true;
    }

    private static DamageSource createDamageSource(ServerPlayer attacker) {
        Registry<DamageType> damageTypes = attacker.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = damageTypes.getHolder(DAMAGE_TYPE).orElse(null);
        return holder == null ? null : new DamageSource(holder, attacker, attacker);
    }
}
