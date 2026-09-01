package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.mechanic.TalentStatService.*;
import static com.whatever.aegis_ascension.mechanic.TalentDamageCalculations.*;
import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.api.AegisSpellDamage;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.config.ServerSettings;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.talents.FocusedShot;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Owns conversion to True Damage and transports its value across Forge's two
 * damage stages.
 *
 * <p>The raw hit is captured before Apothic's {@code LivingHurtEvent} critical
 * handler. The final True Damage value is restored during
 * {@code LivingDamageEvent}, after armor, Resistance, enchantment protection,
 * and ordinary incoming multipliers have run. Vanilla absorption is then
 * reconstructed and the mod's shield handler remains free to absorb the health
 * damage that is left.</p>
 */
final class TrueDamageMechanic {
    private static final ThreadLocal<Map<LivingEntity, HurtCapture>> CAPTURES =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Map<LivingEntity, PendingTrueDamage>> PENDING =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private TrueDamageMechanic() {
    }

    static void captureRawHurt(LivingEntity target, DamageSource source, float amount) {
        CAPTURES.get().put(target, new HurtCapture(
                source,
                Math.max(0.0F, amount),
                Math.max(0.0F, target.getAbsorptionAmount())
        ));
        // A previous hit that never reached LivingDamage must not leak into this hit.
        PENDING.get().remove(target);
    }

    static HurtPlan prepare(LivingEntity target, DamageSource source,
                            float currentHurtAmount) {
        HurtCapture capture = CAPTURES.get().remove(target);
        if (capture == null || capture.source() != source) {
            capture = new HurtCapture(
                    source,
                    Math.max(0.0F, currentHurtAmount),
                    Math.max(0.0F, target.getAbsorptionAmount())
            );
        }

        ServerPlayer owner = SummonCompat.findDamageOwner(source);
        if (owner == null) {
            return HurtPlan.NONE;
        }

        HurtCapture finalCapture = capture;
        return PerkData.get(owner).map(data -> prepare(
                owner,
                data,
                target,
                source,
                finalCapture
        )).orElse(HurtPlan.NONE);
    }

    private static HurtPlan prepare(ServerPlayer owner, PlayerPerkData data,
                                    LivingEntity target, DamageSource source,
                                    HurtCapture capture) {
        // Child of Magic converts every hit, which bypasses the whole outgoing
        // pipeline, so it honours the manual toggle the same way Skill Damage
        // Conversion does - a disabled talent must convert nothing.
        boolean sevenColoredMagician = data.owns(PERK_SEVEN_COLORED_MAGICIAN)
                && data.isTalentEnabled(PERK_SEVEN_COLORED_MAGICIAN);
        boolean supportedSpell = AegisSpellDamage.isSpellDamage(source);
        boolean skillConversion = data.owns(PERK_SKILL_DAMAGE_CONVERSION)
                && data.isTalentEnabled(PERK_SKILL_DAMAGE_CONVERSION)
                && supportedSpell;
        boolean fullConversion = sevenColoredMagician || skillConversion;

        double princessBase = princessTrueDamageBase(owner, data, source);
        if (!fullConversion && princessBase <= 0.0D) {
            return HurtPlan.NONE;
        }

        double commonMultiplier = damageTrueCalculation(data);
        ServerSettings settings = ServerSettings.get();
        if (settings.trueDamageAffectedByCriticalDamage()) {
            commonMultiplier *= damageCriticalDamageCalculationFromRaw(owner, data);
        }
        if (settings.trueDamageAffectedByLuckyStrike()) {
            commonMultiplier *= damageLuckStrikeCalculation(owner, data);
        }
        if (settings.trueDamageAffectedByFinalDamage()) {
            commonMultiplier *= TalentCombatEffects.trueDamageFinalDamageMultiplier(
                    owner,
                    data,
                    source
            );
        }
        if (settings.trueDamageAffectedByRoyalSacredFlame()) {
            commonMultiplier *= royalSacredFlameMultiplier(owner, data);
        }
        commonMultiplier *= FocusedShot.arrowDamageMultiplier(owner, data, source);

        double convertedMain = 0.0D;
        if (fullConversion) {
            double baseDamage = capture.rawAmount();
            // Commander's addition is explicitly applied before damage calculation,
            // so it is part of the base that is converted rather than a Damage Bonus.
            if (source.getEntity() == owner && data.owns(PERK_COMMANDER)) {
                baseDamage += owner.getMaxHealth()
                        * stat(PERK_COMMANDER, MAX_HEALTH_TO_BASE_DAMAGE);
            }
            convertedMain = safeDamage(baseDamage * commonMultiplier);
        }
        double princessBonus = safeDamage(princessBase * commonMultiplier);
        double desiredTotal = safeDamage(convertedMain + princessBonus);

        boolean hasPostDamage = desiredTotal > 0.0D;
        if (hasPostDamage) {
            PENDING.get().put(target, new PendingTrueDamage(
                    source,
                    owner,
                    fullConversion,
                    convertedMain,
                    princessBonus,
                    capture.absorptionBefore()
            ));
        }
        float replacement = fullConversion
                ? (float) desiredTotal
                : Float.NaN;
        return new HurtPlan(fullConversion, replacement, hasPostDamage);
    }

    /** Keeps the vanilla pipeline alive when only a post-mitigation bonus remains. */
    static float ensureLivingDamageStage(float amount, HurtPlan plan) {
        if (amount > 0.0F || !plan.hasPostDamage()) {
            return amount;
        }
        return 1.0E-4F;
    }

    static PostDamage applyPostMitigation(LivingEntity target, DamageSource source,
                                          float normalHealthDamage) {
        PendingTrueDamage pending = PENDING.get().remove(target);
        if (pending == null || pending.source() != source) {
            return new PostDamage(null, normalHealthDamage);
        }

        double healthDamage;
        if (pending.fullConversion()) {
            double total = pending.convertedMain() + pending.princessBonus();
            double absorption = Math.max(0.0D, pending.absorptionBefore());
            double absorbed = Math.min(absorption, total);
            target.setAbsorptionAmount((float) Math.max(0.0D, absorption - absorbed));
            healthDamage = total - absorbed;
        } else {
            double bonus = pending.princessBonus();
            double absorption = Math.max(0.0D, target.getAbsorptionAmount());
            double absorbed = Math.min(absorption, bonus);
            target.setAbsorptionAmount((float) Math.max(0.0D, absorption - absorbed));
            healthDamage = Math.max(0.0D, normalHealthDamage) + bonus - absorbed;
        }
        return new PostDamage(
                pending.owner(),
                (float) Math.min(Float.MAX_VALUE, Math.max(0.0D, healthDamage))
        );
    }

    static void clear(LivingEntity target, DamageSource source) {
        HurtCapture capture = CAPTURES.get().get(target);
        if (capture != null && capture.source() == source) {
            CAPTURES.get().remove(target);
        }
        PendingTrueDamage pending = PENDING.get().get(target);
        if (pending != null && pending.source() == source) {
            PENDING.get().remove(target);
        }
    }

    private static double princessTrueDamageBase(ServerPlayer owner,
                                                  PlayerPerkData data,
                                                  DamageSource source) {
        if (source.getEntity() != owner || !data.owns(PERK_PRINCESS_OF_EGRET)) {
            return 0.0D;
        }
        Perk princess = requiredPerk(PERK_PRINCESS_OF_EGRET);
        if (owner.getRandom().nextDouble() >= Mth.clamp(
                princess.stat(BONUS_TRUE_DAMAGE_CHANCE), 0.0D, 1.0D
        )) {
            return 0.0D;
        }
        return Math.max(0.0D,
                GeneralServerMethods.getAttributeValue(owner, Attributes.ATTACK_DAMAGE)
                        * princess.stat(BONUS_TRUE_DAMAGE_ATTACK_MULTIPLIER));
    }

    private static double royalSacredFlameMultiplier(ServerPlayer owner,
                                                       PlayerPerkData data) {
        if (!data.owns(PERK_SEVEN_COLORED_MAGICIAN)
                || !data.isTalentEnabled(PERK_SEVEN_COLORED_MAGICIAN)) {
            return 1.0D;
        }

        double doubleChance = stat(PERK_SEVEN_COLORED_MAGICIAN, DOUBLE_DAMAGE_CHANCE);
        double doubleMultiplier = stat(
                PERK_SEVEN_COLORED_MAGICIAN,
                DOUBLE_DAMAGE_MULTIPLIER
        );
        double tripleChance = stat(PERK_SEVEN_COLORED_MAGICIAN, TRIPLE_DAMAGE_CHANCE);
        double tripleMultiplier = stat(
                PERK_SEVEN_COLORED_MAGICIAN,
                TRIPLE_DAMAGE_MULTIPLIER
        );
        boolean maripatchy = data.hasActiveSoulLink(SOUL_MARIPATCHY_GROUP);
        if (maripatchy) {
            doubleChance = bonusStat(SOUL_MARIPATCHY_GROUP, DOUBLE_DAMAGE_CHANCE);
            doubleMultiplier = bonusStat(SOUL_MARIPATCHY_GROUP, DOUBLE_DAMAGE_MULTIPLIER);
            tripleChance = bonusStat(SOUL_MARIPATCHY_GROUP, TRIPLE_DAMAGE_CHANCE);
            tripleMultiplier = bonusStat(SOUL_MARIPATCHY_GROUP, TRIPLE_DAMAGE_MULTIPLIER);
        }

        double roll = owner.getRandom().nextDouble();
        double selected = 1.0D;
        double clampedDoubleChance = Mth.clamp(doubleChance, 0.0D, 1.0D);
        if (roll < clampedDoubleChance) {
            selected = Math.max(0.0D, doubleMultiplier);
        } else if (roll < Math.min(1.0D,
                clampedDoubleChance + Mth.clamp(tripleChance, 0.0D, 1.0D))) {
            selected = Math.max(0.0D, tripleMultiplier);
        }
        if (maripatchy && selected > 1.0D) {
            selected += bonusStat(SOUL_MARIPATCHY_GROUP, ROYAL_FLAME_MULTIPLIER_BONUS);
        }
        return Math.max(0.0D, selected);
    }

    private static double safeDamage(double value) {
        if (!Double.isFinite(value)) {
            return value > 0.0D ? Float.MAX_VALUE : 0.0D;
        }
        return Math.min(Float.MAX_VALUE, Math.max(0.0D, value));
    }

    record HurtPlan(boolean fullConversion, float replacementAmount,
                    boolean hasPostDamage) {
        private static final HurtPlan NONE = new HurtPlan(false, Float.NaN, false);
    }

    record PostDamage(ServerPlayer owner, float amount) {
    }

    private record HurtCapture(DamageSource source, float rawAmount,
                               float absorptionBefore) {
    }

    private record PendingTrueDamage(DamageSource source, ServerPlayer owner,
                                     boolean fullConversion, double convertedMain,
                                     double princessBonus, float absorptionBefore) {
    }
}
