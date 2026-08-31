package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.api.AegisSpellDamage;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/** Independent classifications used by the outgoing damage pipeline. */
record DamageCalculationContext(
        ServerPlayer attacker,
        LivingEntity target,
        DamageSource source,
        boolean spellDamage,
        boolean magicDamage,
        boolean physicalDamage,
        boolean directMeleeAttack
) {
    private static final TagKey<DamageType> FORGE_MAGIC_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            PlatformServices.resources().create("forge", "is_magic")
    );
    private static final TagKey<DamageType> NEOFORGE_MAGIC_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            PlatformServices.resources().create("neoforge", "is_magic")
    );

    static DamageCalculationContext create(
            ServerPlayer attacker,
            LivingEntity target,
            DamageSource source
    ) {
        boolean spellDamage = AegisSpellDamage.isSpellDamage(source);
        boolean magicDamage = isMagicDamage(source);
        boolean directMeleeAttack = !spellDamage
                && source.getEntity() == attacker
                && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK);
        return new DamageCalculationContext(
                attacker,
                target,
                source,
                spellDamage,
                magicDamage,
                !magicDamage,
                directMeleeAttack
        );
    }

    static boolean isMagicDamage(DamageSource source) {
        return source.is(FORGE_MAGIC_DAMAGE)
                || source.is(NEOFORGE_MAGIC_DAMAGE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC);
    }
}
