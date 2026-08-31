package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.mechanic.TalentStatService.*;
import static com.whatever.aegis_ascension.mechanic.TalentDamageCalculations.*;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ApothicAttributesCompat;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import com.whatever.aegis_ascension.compat.ManaCompat;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.talents.FocusedShot;
import com.whatever.aegis_ascension.perk.talents.DominusLapidis;
import com.whatever.aegis_ascension.perk.talents.PerfectAndElegantServant;
import com.whatever.aegis_ascension.perk.talents.TeamStar;
import com.whatever.aegis_ascension.perk.soullink.MakeUpWorkClub;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Handles active combat, healing, revival, kill, and combat-tick talent effects. */
public final class TalentCombatEffects {
    private static final ThreadLocal<Boolean> DESTRUCTION_HEALING =
            ThreadLocal.withInitial(() -> false);

    private TalentCombatEffects() {
    }

    /** Captures the unmodified LivingHurt amount before Apothic's critical handler. */
    public static void captureLivingHurt(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        TrueDamageMechanic.captureRawHurt(target, source, amount);
    }

    public static void onPlayerTick(ServerPlayer player, PlayerPerkData data) {
        PerfectAndElegantServant.tick(player, data);
        DominusLapidis.tick(player, data);
        TeamStar.tick(player, data);
        if (player.tickCount % 20 == 0) {
            IronSpellsCompat.updateAttributeModifiers(player, data);
            double current = data.getCustomStat(MAGICIAN_PRIMARY_ATTRIBUTE_FLAT);
            double updated = magicianPrimaryAttributeFlat(player, data);
            if (Math.abs(current - updated) > 1.0E-9D
                    || !isMagicConversionHealthCurrent(player, data)) {
                recalculateAttributes(player, data);
            }
        }
        double overflowChanceStep = 0.0D;
        double damagePerOverflowStep = 0.0D;
        if (data.hasActiveSoulLink(SOUL_MILLENNIUM_ECHO)) {
            overflowChanceStep = bonusStat(
                    SOUL_MILLENNIUM_ECHO,
                    OVERFLOW_CRITICAL_CHANCE_STEP
            );
            damagePerOverflowStep = bonusStat(
                    SOUL_MILLENNIUM_ECHO,
                    CRITICAL_DAMAGE_PER_OVERFLOW_STEP
            );
        }
        ApothicAttributesCompat.refreshDynamicCriticalDamageModifiers(
                player,
                flameCriticalDamagePerCriticalChance(data),
                overflowChanceStep,
                damagePerOverflowStep
        );

        // Angel's Aegis shields are owned by AngelsAegisShieldHandler.

        if (data.isAegisEnabled(AegisConstants.HEALING)) {
            int interval = Math.max(1, (int) Math.round(
                    aegisStat(AegisConstants.HEALING, AegisConstants.HEALING_INTERVAL_SECONDS)
                            * 20.0D
            ));
            if (player.tickCount % interval == 0) {
                double amount = player.getMaxHealth() * aegisStat(
                        AegisConstants.HEALING,
                        AegisConstants.HEALING_MAX_HEALTH_FRACTION
                );
                double negativeResistance = Math.max(0.0D, -rawDamageResistance(data));
                amount *= 1.0D + negativeResistance;
                if (player.getRandom().nextDouble() < aegisStat(
                        AegisConstants.HEALING,
                        AegisConstants.HEALING_FIXED_CRITICAL_CHANCE
                )) {
                    amount *= aegisStat(
                            AegisConstants.HEALING,
                            AegisConstants.HEALING_FIXED_CRITICAL_DAMAGE
                    );
                }
                player.heal((float) Math.min(Float.MAX_VALUE, amount));
            }
        }

        if (data.isAegisEnabled(AegisConstants.DESTRUCTION) && player.tickCount % 20 == 0) {
            healFromDestruction(
                    player,
                    (float) (player.getMaxHealth() * aegisStat(
                            AegisConstants.DESTRUCTION,
                            AegisConstants.HEALTH_RESTORE_PER_SECOND
                    ))
            );
        }

        Perk healingMagic = requiredPerk(PERK_HEALING_MAGIC);
        int healingInterval = Math.max(1, (int) Math.round(
                healingMagic.stat(INTERVAL_SECONDS) * 20.0D
        ));
        if (data.owns(healingMagic.id()) && player.tickCount % healingInterval == 0) {
            player.heal(player.getMaxHealth()
                    * (float) healingMagic.stat(HEALTH_RESTORE_PER_SECOND));
            ManaCompat.restoreFraction(
                    player,
                    data,
                    healingMagic.stat(MANA_RESTORE_PER_SECOND)
            );
        }

        if (data.owns(PERK_MUNDANE_STROLL)) {
            Perk mundaneStroll = requiredPerk(PERK_MUNDANE_STROLL);
            double lastX = data.getCustomStat(WALK_LAST_X);
            double lastY = data.getCustomStat(WALK_LAST_Y);
            double lastZ = data.getCustomStat(WALK_LAST_Z);
            if (data.getCustomStat(WALK_INITIALIZED) > 0.0D) {
                double distance = Math.sqrt(
                        Math.pow(player.getX() - lastX, 2.0D)
                                + Math.pow(player.getY() - lastY, 2.0D)
                                + Math.pow(player.getZ() - lastZ, 2.0D)
                );
                if (distance < 50.0D) {
                    double progress = data.addCustomStat(WALK_DISTANCE, distance);
                    double bonus = data.getCustomStat(WALK_DAMAGE);
                    double distancePerStack = mundaneStroll.stat(DISTANCE_PER_STACK);
                    double damagePerStack = mundaneStroll.stat(DAMAGE_MULTIPLIER_PER_STACK);
                    double damageCap = mundaneStroll.stat(DAMAGE_MULTIPLIER_CAP);
                    while (progress >= distancePerStack && bonus < damageCap) {
                        progress -= distancePerStack;
                        bonus = Math.min(damageCap, bonus + damagePerStack);
                    }
                    data.setCustomStat(WALK_DISTANCE, progress);
                    data.setCustomStat(WALK_DAMAGE, bonus);
                }
            }
            data.setCustomStat(WALK_INITIALIZED, 1.0D);
            data.setCustomStat(WALK_LAST_X, player.getX());
            data.setCustomStat(WALK_LAST_Y, player.getY());
            data.setCustomStat(WALK_LAST_Z, player.getZ());
        }
    }

    /** Returns the LivingHurt-stage amount after all common outgoing and incoming effects. */
    public static float onLivingHurt(
            LivingEntity target,
            DamageSource source,
            float originalAmount
    ) {
        TrueDamageMechanic.HurtPlan trueDamage = TrueDamageMechanic.prepare(
                target,
                source,
                originalAmount
        );
        if (trueDamage.fullConversion()) {
            return trueDamage.replacementAmount();
        }

        float amount = SummonCompat.applyBlessingDamage(source, originalAmount);
        if (source.getEntity() instanceof ServerPlayer attacker) {
            float currentAmount = amount;
            amount = PerkData.get(attacker)
                    .map(data -> modifyOutgoingHurt(
                            attacker, data, target, source, currentAmount
                    ))
                    .orElse(currentAmount);
        }

        // Independent Damage is deliberately outside the normal outgoing-damage
        // bucket. Applying it after the complete attacker calculation makes it a
        // separate multiplier and lets it scale every attributable source, including
        // projectiles, spells, and supported Iron's Spells/Ars Nouveau summons.
        ServerPlayer damageOwner = SummonCompat.findDamageOwner(source);
        if (damageOwner != null) {
            float currentAmount = amount;
            amount = PerkData.get(damageOwner)
                    .map(data -> (float) Math.min(
                            Float.MAX_VALUE,
                            Math.max(0.0D,
                                    currentAmount * damageIndependentCalculation(data))
                    ))
                    .orElse(currentAmount);
        }

        if (target instanceof ServerPlayer victim) {
            float currentAmount = amount;
            amount = PerkData.get(victim)
                    .map(data -> modifyIncomingHurt(victim, data, currentAmount))
                    .orElse(currentAmount);
        }
        return TrueDamageMechanic.ensureLivingDamageStage(amount, trueDamage);
    }

    private static float modifyOutgoingHurt(
            ServerPlayer attacker,
            PlayerPerkData data,
            LivingEntity target,
            DamageSource source,
            float originalAmount
    ) {
        DamageCalculationContext context = DamageCalculationContext.create(
                attacker,
                target,
                source
        );
        double situationalFinalDamage = fernFinalDamageOnTrigger(
                attacker,
                data,
                context.magicDamage()
        );
        if (data.owns(PERK_PECORINES_BLESSING)
                && attacker.getHealth() >= attacker.getMaxHealth()) {
            situationalFinalDamage += stat(
                    PERK_PECORINES_BLESSING,
                    FULL_HEALTH_FINAL_DAMAGE
            );
        }
        double baseDamage = originalAmount;
        if (data.owns(PERK_COMMANDER)) {
            baseDamage += attacker.getMaxHealth()
                    * stat(PERK_COMMANDER, MAX_HEALTH_TO_BASE_DAMAGE);
        }
        double amount = baseDamage * damageCommonCalculation(
                attacker, data, situationalFinalDamage
        );
        if (context.physicalDamage()) {
            amount *= damagePhysicalCalculation(data);
        }
        if (context.magicDamage()) {
            amount *= damageMagicCalculation(data);
        }
        if (context.spellDamage()) {
            amount *= damageSkillCalculation(attacker, data);
        }
        if (context.directMeleeAttack()) {
            amount *= damageAttackAmplificationCalculation(data);
        }
        amount *= FocusedShot.arrowDamageMultiplier(attacker, data, source);
        if (data.owns(PERK_GANYUS_BLESSING)) {
            Perk ganyu = requiredPerk(PERK_GANYUS_BLESSING);
            double distance = attacker.distanceTo(target);
            if (distance >= ganyu.stat(MINIMUM_DAMAGE_DISTANCE)) {
                amount *= Math.max(0.0D, 1.0D
                        + (distance - ganyu.stat(DISTANCE_DAMAGE_OFFSET))
                        * ganyu.stat(DAMAGE_MULTIPLIER_PER_DISTANCE));
            }
        }
        double armorIgnore = 0.0D;
        if (context.physicalDamage() && data.owns(PERK_HANAKOS_BLESSING)) {
            armorIgnore += stat(PERK_HANAKOS_BLESSING, PHYSICAL_ARMOR_IGNORE)
                    * MakeUpWorkClub.hanakoMultiplier(data);
        }
        if (data.owns(PERK_ET_OMNIA_VANITAS)
                && !ApothicAttributesCompat.handlesMappedAttribute(attacker, ARMOR_SHRED)) {
            armorIgnore += stat(PERK_ET_OMNIA_VANITAS, ARMOR_SHRED);
        }
        amount = compensateForArmorIgnore(amount, target, source, armorIgnore);
        return (float) Math.min(Float.MAX_VALUE, amount);
    }

    private static float modifyIncomingHurt(
            ServerPlayer victim,
            PlayerPerkData data,
            float originalAmount
    ) {
        if (data.owns(PERK_TSUKIYUKI_MIYAKO) && victim.getRandom().nextDouble()
                < stat(PERK_TSUKIYUKI_MIYAKO, IGNORE_DAMAGE_CHANCE)) {
            return 0.0F;
        }
        double multiplier = 1.0D - effectiveDamageResistance(data);
        double amount = Math.max(0.0D, originalAmount * multiplier);
        if (data.isAegisEnabled(AegisConstants.DESTRUCTION)) {
            amount = Math.min(
                    amount,
                    victim.getMaxHealth() * aegisStat(
                            AegisConstants.DESTRUCTION,
                            AegisConstants.MAX_DAMAGE_MAX_HEALTH_FRACTION
                    )
            );
        }
        // /kill deals Float.MAX_VALUE, so clamp multipliers before converting to float.
        return (float) Math.min(Float.MAX_VALUE, amount);
    }

    /** Returns whether an externally initiated heal should be canceled. */
    public static boolean shouldCancelLivingHeal(LivingEntity target) {
        if (DESTRUCTION_HEALING.get() || !(target instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player).map(data -> {
            Aegis destruction = Aegis.byId(AegisConstants.DESTRUCTION).orElseThrow();
            boolean blocksExternalHealing = !destruction.stats().containsKey(
                    AegisConstants.BLOCKS_EXTERNAL_HEALING
            ) || destruction.stat(AegisConstants.BLOCKS_EXTERNAL_HEALING) > 0.0D;
            return data.isAegisEnabled(AegisConstants.DESTRUCTION)
                    && blocksExternalHealing;
        }).orElse(false);
    }

    private static void healFromDestruction(ServerPlayer player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        DESTRUCTION_HEALING.set(true);
        try {
            player.heal(amount);
        } finally {
            DESTRUCTION_HEALING.remove();
        }
    }

    /** Applies post-mitigation hit effects and returns the resulting health-damage amount. */
    public static float onLivingDamage(
            LivingEntity target,
            DamageSource source,
            float originalAmount
    ) {
        TrueDamageMechanic.PostDamage postDamage =
                TrueDamageMechanic.applyPostMitigation(target, source, originalAmount);
        ServerPlayer attacker = postDamage.owner();
        if (attacker == null && source.getEntity() instanceof ServerPlayer directAttacker) {
            attacker = directAttacker;
        }
        if (attacker == null) {
            return postDamage.amount();
        }
        ServerPlayer finalAttacker = attacker;
        return PerkData.get(attacker)
                .map(data -> applyLivingDamage(
                        finalAttacker,
                        data,
                        target,
                        source,
                        postDamage.amount()
                ))
                .orElse(postDamage.amount());
    }

    /** Clears transient conversion state when another handler cancels or zeroes a hit. */
    public static void clearLivingHurt(LivingEntity target, DamageSource source) {
        TrueDamageMechanic.clear(target, source);
    }

    private static float applyLivingDamage(
            ServerPlayer attacker,
            PlayerPerkData data,
            LivingEntity target,
            DamageSource source,
            float originalAmount
    ) {
            float amount = originalAmount;
            if (data.owns(PERK_CRIMSON_YOUNG_MOON)) {
                float missingHealth = attacker.getMaxHealth() - attacker.getHealth();
                attacker.heal(missingHealth
                        * (float) stat(PERK_CRIMSON_YOUNG_MOON, MISSING_HEALTH_RESTORE));
            }
            if (data.owns(PERK_LAEVATEIN)) {
                Perk laevatein = requiredPerk(PERK_LAEVATEIN);
                double healthPerTrigger = laevatein.stat(MAX_HEALTH_FLAT_PER_DAMAGE);
                if (consumeCappedTrigger(
                        data,
                        laevatein,
                        LAEVATEIN_TRIGGER_COUNT,
                        inferredTriggerCount(
                                data.getCustomStat(LAEVATEIN_HEALTH),
                                healthPerTrigger
                        )
                )) {
                    data.addCustomStat(LAEVATEIN_HEALTH, healthPerTrigger);
                    recalculateAttributes(attacker, data);
                }
            }
            if (data.owns(PERK_MAGIC_CONVERSION)) {
                Perk magicConversion = requiredPerk(PERK_MAGIC_CONVERSION);
                double manaPerTrigger = magicConversion.stat(MAX_MANA_FLAT_PER_DAMAGE);
                if (consumeCappedTrigger(
                        data,
                        magicConversion,
                        MAGIC_CONVERSION_TRIGGER_COUNT,
                        inferredTriggerCount(
                                data.getCustomStat(MAGIC_CONVERSION_MAX_MANA),
                                manaPerTrigger
                        )
                )) {
                    data.addCustomStat(MAGIC_CONVERSION_MAX_MANA, manaPerTrigger);
                    recalculateAttributes(attacker, data);
                }
            }
            if (data.owns(PERK_RIGHTEOUS_KNIGHT)
                    && DamageCalculationContext.create(
                            attacker,
                            target,
                            source
                    ).directMeleeAttack()) {
                double attacks = data.addCustomStat(KNIGHT_ATTACKS, 1.0D);
                Perk knight = requiredPerk(PERK_RIGHTEOUS_KNIGHT);
                long attacksPerStack = Math.max(1L, Math.round(knight.stat(ATTACKS_PER_STACK)));
                if (((long) attacks) % attacksPerStack == 0L) {
                    boolean attributesChanged = false;
                    double damageCap = knight.stat(ATTACK_DAMAGE_AMPLIFICATION_CAP);
                    double currentDamage = data.getCustomStat(KNIGHT_DAMAGE);
                    if (currentDamage < damageCap) {
                        data.setCustomStat(KNIGHT_DAMAGE, Math.min(
                                damageCap,
                                currentDamage
                                        + knight.stat(ATTACK_DAMAGE_AMPLIFICATION_PER_STACK)
                        ));
                    }
                    double speedCap = knight.stat(ATTACK_SPEED_FLAT_CAP);
                    double currentSpeed = data.getCustomStat(KNIGHT_ATTACK_SPEED_FLAT);
                    if (currentSpeed < speedCap) {
                        data.setCustomStat(KNIGHT_ATTACK_SPEED_FLAT, Math.min(
                                speedCap,
                                currentSpeed + knight.stat(ATTACK_SPEED_FLAT_PER_STACK)
                        ));
                        attributesChanged = true;
                    }
                    if (attributesChanged) {
                        recalculateAttributes(attacker, data);
                    }
                }
            }
            if (data.owns(PERK_INNATE_DREAM)) {
                Perk innateDream = requiredPerk(PERK_INNATE_DREAM);
                double chance = Math.min(1.0D,
                        innateDream.stat(BASE_TRIGGER_CHANCE)
                                + innateDream.stat(TRIGGER_CHANCE_PER_BREAKTHROUGH)
                                * data.getCustomStat(BREAKTHROUGH_COUNT));
                if (attacker.getRandom().nextDouble() < chance) {
                    int randomEffectCount = Math.max(1,
                            integerStat(innateDream, RANDOM_EFFECT_COUNT));
                    switch (attacker.getRandom().nextInt(randomEffectCount)) {
                        case 0 -> {
                            data.addCustomStat(PRIMARY_FLAT,
                                    innateDream.stat(PRIMARY_ATTRIBUTE_FLAT_PER_TRIGGER));
                            recalculateAttributes(attacker, data);
                        }
                        case 1 -> {
                            double perTrigger = innateDream.stat(DAMAGE_BONUS_PER_TRIGGER);
                            if (consumeCappedTrigger(
                                    data,
                                    innateDream,
                                    INNATE_DAMAGE_TRIGGER_COUNT,
                                    inferredTriggerCount(
                                            data.getCustomStat(INNATE_DAMAGE),
                                            perTrigger
                                    ),
                                    DAMAGE_BONUS_MAX_TRIGGER_COUNT
                            )) {
                                data.addCustomStat(INNATE_DAMAGE, perTrigger);
                            }
                        }
                        case 2 -> {
                            double perTrigger = innateDream.stat(SKILL_DAMAGE_PER_TRIGGER);
                            if (consumeCappedTrigger(
                                    data,
                                    innateDream,
                                    INNATE_SKILL_DAMAGE_TRIGGER_COUNT,
                                    inferredTriggerCount(
                                            data.getCustomStat(INNATE_SKILL_DAMAGE),
                                            perTrigger
                                    ),
                                    SKILL_DAMAGE_MAX_TRIGGER_COUNT
                            )) {
                                data.addCustomStat(INNATE_SKILL_DAMAGE, perTrigger);
                            }
                        }
                        default -> {
                            double perTrigger = innateDream.stat(CRITICAL_DAMAGE_PER_TRIGGER);
                            if (consumeCappedTrigger(
                                    data,
                                    innateDream,
                                    INNATE_CRITICAL_DAMAGE_TRIGGER_COUNT,
                                    inferredTriggerCount(
                                            data.getCustomStat(INNATE_CRITICAL_DAMAGE),
                                            perTrigger
                                    ),
                                    CRITICAL_DAMAGE_MAX_TRIGGER_COUNT
                            )) {
                                data.addCustomStat(INNATE_CRITICAL_DAMAGE, perTrigger);
                                recalculateAttributes(attacker, data);
                            }
                        }
                    }
                }
            }
            if (data.owns(PERK_NECROMANCER)) {
                Perk necromancer = requiredPerk(PERK_NECROMANCER);
                double threshold = target.getMaxHealth()
                        >= necromancer.stat(ELITE_MAX_HEALTH_THRESHOLD)
                        ? necromancer.stat(ELITE_EXECUTE_HEALTH_FRACTION)
                        : necromancer.stat(EXECUTE_HEALTH_FRACTION);
                if (target.getHealth() - amount <= target.getMaxHealth() * threshold) {
                    amount = Math.max(amount, target.getHealth() + 1.0F);
                }
            }
            return amount;
    }

    /** Runs revival and kill effects; true means the loader should cancel the death. */
    public static boolean onLivingDeath(LivingEntity target, DamageSource source) {
        if (target instanceof ServerPlayer player) {
//            AegisAscensionMod.LOGGER.info(
//                    "[ReviveDebug] LivingDeathEvent entered: player={}, source={}, sourceEntity={}, "
//                            + "health={}, maxHealth={}, removed={}, removalReason={}, deadOrDying={}, "
//                            + "deathTime={}, hurtTime={}, invulnerableTime={}, alreadyCanceled={}",
//                    player.getGameProfile().getName(),
//                    event.getSource().getMsgId(),
//                    event.getSource().getEntity() == null
//                            ? "none"
//                            : event.getSource().getEntity().getType().toString(),
//                    player.getHealth(),
//                    player.getMaxHealth(),
//                    player.isRemoved(),
//                    player.getRemovalReason(),
//                    player.isDeadOrDying(),
//                    player.deathTime,
//                    player.hurtTime,
//                    player.invulnerableTime,
//                    event.isCanceled()
//            );
            boolean revived = PerkData.get(player).map(data -> {
                if (data.owns(PERK_BOUNDARY_OF_LIFE_AND_DEATH)
                        && data.getCustomStat(REVIVES_REMAINING) > 0.0D) {
                    double revivesBefore = data.getCustomStat(REVIVES_REMAINING);
                    data.addCustomStat(REVIVES_REMAINING, -1.0D);
                    Perk boundary = requiredPerk(PERK_BOUNDARY_OF_LIFE_AND_DEATH);
//                    AegisAscensionMod.LOGGER.info(
//                            "[ReviveDebug] Selecting revive: player={}, perk={}, usesBefore={}, usesAfter={}",
//                            player.getGameProfile().getName(),
//                            boundary.id(),
//                            revivesBefore,
//                            data.getCustomStat(REVIVES_REMAINING)
//                    );
                    data.addCustomStat(REVIVE_LUCK, boundary.stat(LUCKY_STRIKE_PER_REVIVE));
                    revive(player, boundary);
                    recalculateAttributes(player, data);
//                    logPostRecalculationState(player, boundary);
                    return true;
                }
                if (data.owns(PERK_BLAZING_FEATHER_STARWEAVER)
                        && data.getCustomStat(BLAZING_REVIVE_USED) == 0.0D) {
                    data.setCustomStat(BLAZING_REVIVE_USED, 1.0D);
                    Perk blazing = requiredPerk(PERK_BLAZING_FEATHER_STARWEAVER);
//                    AegisAscensionMod.LOGGER.info(
//                            "[ReviveDebug] Selecting revive: player={}, perk={}, blazingReviveUsed={}",
//                            player.getGameProfile().getName(),
//                            blazing.id(),
//                            data.getCustomStat(BLAZING_REVIVE_USED)
//                    );
                    data.setCustomStat(BLAZING_BREAKTHROUGH_DAMAGE,
                            blazing.stat(FINAL_DAMAGE_AFTER_REVIVE));
                    revive(player, blazing);
                    return true;
                }
                return false;
            }).orElse(false);
            if (revived) {
//                AegisAscensionMod.LOGGER.info(
//                        "[ReviveDebug] LivingDeathEvent canceled after revive: player={}, health={}, "
//                                + "maxHealth={}, removed={}, removalReason={}, deadOrDying={}, "
//                                + "deathTime={}, hurtTime={}, invulnerableTime={}",
//                        player.getGameProfile().getName(),
//                        player.getHealth(),
//                        player.getMaxHealth(),
//                        player.isRemoved(),
//                        player.getRemovalReason(),
//                        player.isDeadOrDying(),
//                        player.deathTime,
//                        player.hurtTime,
//                        player.invulnerableTime
//                );
                return true;
            }
//            AegisAscensionMod.LOGGER.info(
//                    "[ReviveDebug] No revive was available: player={}, health={}, removed={}, "
//                            + "removalReason={}, deadOrDying={}",
//                    player.getGameProfile().getName(),
//                    player.getHealth(),
//                    player.isRemoved(),
//                    player.getRemovalReason(),
//                    player.isDeadOrDying()
//            );
        }

        if (!(source.getEntity() instanceof ServerPlayer killer)) {
            return false;
        }
        PerkData.get(killer).ifPresent(data -> {
            int killEffectRuns = 1;
            if (data.hasActiveSoulLink(SOUL_DEATH_GODS_AUTHORITY)
                    && killer.getRandom().nextDouble() < bonusStat(
                    SOUL_DEATH_GODS_AUTHORITY, ADDITIONAL_TRIGGER_CHANCE)) {
                killEffectRuns += (int) Math.round(bonusStat(
                        SOUL_DEATH_GODS_AUTHORITY, ADDITIONAL_TRIGGER_COUNT));
            }
            for (int run = 0; run < killEffectRuns; run++) {
                if (data.owns(PERK_LUNAR_GODDESSS_BLESSING)
                        && killer.getRandom().nextDouble() < stat(
                        PERK_LUNAR_GODDESSS_BLESSING, KILL_TRIGGER_CHANCE)) {
                    data.addCustomStat(LUNAR_DAMAGE, stat(
                            PERK_LUNAR_GODDESSS_BLESSING,
                            PHYSICAL_DAMAGE_AMPLIFICATION_PER_TRIGGER
                    ));
                }
                if (data.owns(PERK_I_SHALL_INTERPRET_THE_RADIANCE)) {
                    Perk radiance = requiredPerk(PERK_I_SHALL_INTERPRET_THE_RADIANCE);
                    double damagePerTrigger = radiance.stat(DAMAGE_BONUS_PER_KILL);
                    double damageTakenPerTrigger = -radiance.stat(
                            DAMAGE_REDUCTION_PER_KILL
                    );
                    double inferredTriggers = Math.max(
                            inferredTriggerCount(
                                    data.getCustomStat(FROSTBITE_DAMAGE),
                                    damagePerTrigger
                            ),
                            inferredTriggerCount(
                                    data.getCustomStat(FROSTBITE_DAMAGE_TAKEN),
                                    damageTakenPerTrigger
                            )
                    );
                    if (consumeCappedTrigger(
                            data,
                            radiance,
                            RADIANCE_TRIGGER_COUNT,
                            inferredTriggers
                    )) {
                        data.addCustomStat(FROSTBITE_DAMAGE, damagePerTrigger);
                        data.addCustomStat(
                                FROSTBITE_DAMAGE_TAKEN,
                                damageTakenPerTrigger
                        );
                    }
                }
                Perk topPlayer = requiredPerk(PERK_TOP_PLAYER);
                if (data.owns(topPlayer.id()) && target.getMaxHealth()
                        >= topPlayer.stat(ELITE_MAX_HEALTH_THRESHOLD)) {
                    killer.giveExperiencePoints(integerStat(
                            topPlayer, EXPERIENCE_PER_ELITE_KILL
                    ));
                    data.addCustomStat(PRIMARY_FLAT,
                            topPlayer.stat(PRIMARY_ATTRIBUTE_FLAT_PER_ELITE_KILL));
                    data.addCustomStat(TOP_DAMAGE,
                            topPlayer.stat(DAMAGE_BONUS_PER_ELITE_KILL));
                    data.addCustomStat(TOP_CRITICAL_DAMAGE,
                            topPlayer.stat(CRITICAL_DAMAGE_PER_ELITE_KILL));
                    recalculateAttributes(killer, data);
                }
            }
        });
        return false;
    }

    /** Observes health damage after shield absorption and other amount modifiers. */
    public static void onFinalLivingDamage(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        if (target instanceof ServerPlayer victim) {
            PerkData.get(victim).ifPresent(data ->
                    PerfectAndElegantServant.onDamageReceived(victim, data, amount)
            );
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            PerkData.get(attacker).ifPresent(data ->
                    PerfectAndElegantServant.onDamageDealt(attacker, data, amount)
            );
        }
        ServerPlayer damageOwner = SummonCompat.findDamageOwner(source);
        if (damageOwner != null && damageOwner != target) {
            PerkData.get(damageOwner).ifPresent(data ->
                    MakeUpWorkClub.onDamageDealt(data, target)
            );
        }
    }

    private static void logMaxHealthModifiers(String stage, ServerPlayer player) {
        AttributeInstance instance = GeneralServerMethods.getAttributeInstance(player, Attributes.MAX_HEALTH);
        if (instance == null) {
//            AegisAscensionMod.LOGGER.warn("[ReviveDebug] {}: MAX_HEALTH attribute is null for {}",
//                    stage, player.getGameProfile().getName());
            return;
        }
        StringBuilder modifiers = new StringBuilder();
        for (AttributeModifier modifier : GeneralServerMethods.getAttributeModifiers(
                player, Attributes.MAX_HEALTH
        )) {
            modifiers.append(" [").append(modifier.getName())
                    .append(' ').append(GeneralServerMethods.getAttributeOperation(modifier))
                    .append(' ').append(modifier.getAmount()).append(']');
        }
//        AegisAscensionMod.LOGGER.info(
//                "[ReviveDebug] {}: MAX_HEALTH base={} value={} finiteValue={} modifiers:{}",
//                stage,
//                instance.getBaseValue(),
//                instance.getValue(),
//                Double.isFinite(instance.getValue()),
//                modifiers.length() == 0 ? " none" : modifiers.toString()
//        );
    }

    private static void revive(ServerPlayer player, Perk sourcePerk) {
        logMaxHealthModifiers("Before revive()", player);
        double reviveFraction = sourcePerk.stat(REVIVE_HEALTH_FRACTION);
        float requestedHealth = Math.max(1.0F, player.getMaxHealth() * (float) reviveFraction);
        int requestedInvulnerability = integerStat(sourcePerk, INVULNERABILITY_TICKS);
//        AegisAscensionMod.LOGGER.info(
//                "[ReviveDebug] Before revive(): player={}, perk={}, fraction={}, requestedHealth={}, "
//                        + "requestedInvulnerability={}, health={}, maxHealth={}, removed={}, "
//                        + "removalReason={}, deadOrDying={}, deathTime={}, hurtTime={}",
//                player.getGameProfile().getName(),
//                sourcePerk.id(),
//                reviveFraction,
//                requestedHealth,
//                requestedInvulnerability,
//                player.getHealth(),
//                player.getMaxHealth(),
//                player.isRemoved(),
//                player.getRemovalReason(),
//                player.isDeadOrDying(),
//                player.deathTime,
//                player.hurtTime
//        );
        player.setHealth(requestedHealth);
        player.invulnerableTime = requestedInvulnerability;
        player.clearFire();
//        AegisAscensionMod.LOGGER.info(
//                "[ReviveDebug] After revive(): player={}, perk={}, health={}, maxHealth={}, "
//                        + "removed={}, removalReason={}, deadOrDying={}, deathTime={}, hurtTime={}, "
//                        + "invulnerableTime={}",
//                player.getGameProfile().getName(),
//                sourcePerk.id(),
//                player.getHealth(),
//                player.getMaxHealth(),
//                player.isRemoved(),
//                player.getRemovalReason(),
//                player.isDeadOrDying(),
//                player.deathTime,
//                player.hurtTime,
//                player.invulnerableTime
//        );
    }

    /** Returns only the Final Damage bucket used by converted True Damage. */
    static double trueDamageFinalDamageMultiplier(ServerPlayer player,
                                                   PlayerPerkData data,
                                                   DamageSource source) {
        boolean magicDamage = DamageCalculationContext.isMagicDamage(source);
        double situationalFinalDamage = fernFinalDamageOnTrigger(
                player,
                data,
                magicDamage
        );
        if (data.owns(PERK_PECORINES_BLESSING)
                && player.getHealth() >= player.getMaxHealth()) {
            situationalFinalDamage += stat(
                    PERK_PECORINES_BLESSING,
                    FULL_HEALTH_FINAL_DAMAGE
            );
        }
        return damageFinalCalculation(player, data, situationalFinalDamage);
    }

    /**
     * LivingHurt runs before vanilla armor absorption. Scale its input so the later
     * full-armor calculation produces the same result as a reduced armor value.
     */
    private static double compensateForArmorIgnore(double amount, LivingEntity target,
                                                    DamageSource source,
                                                    double ignoredFraction) {
        double ignored = Mth.clamp(ignoredFraction, 0.0D, 1.0D);
        if (ignored <= 0.0D || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return amount;
        }
        float armor = (float) Math.max(
                0.0D,
                GeneralServerMethods.getAttributeValue(target, Attributes.ARMOR)
        );
        float toughness = (float) Math.max(
                0.0D,
                GeneralServerMethods.getAttributeValue(target, Attributes.ARMOR_TOUGHNESS)
        );
        float originalAfterArmor = CombatRules.getDamageAfterAbsorb(
                (float) Math.min(Float.MAX_VALUE, amount),
                armor,
                toughness
        );
        float ignoredAfterArmor = CombatRules.getDamageAfterAbsorb(
                (float) Math.min(Float.MAX_VALUE, amount),
                (float) (armor * (1.0D - ignored)),
                toughness
        );
        if (originalAfterArmor <= 1.0E-6F) {
            return amount;
        }
        return amount * ignoredAfterArmor / originalAfterArmor;
    }

    private static double fernFinalDamageOnTrigger(ServerPlayer player,
                                                    PlayerPerkData data,
                                                    boolean magicDamage) {
        if (!magicDamage || !data.owns(PERK_FERN)) {
            return 0.0D;
        }
        double chance = stat(PERK_FERN, MAGIC_DAMAGE_TRIGGER_CHANCE);
        if (data.hasActiveSoulLink(SOUL_MAGICIAN_MASTER_AND_APPRENTICE)) {
            chance += bonusStat(
                    SOUL_MAGICIAN_MASTER_AND_APPRENTICE,
                    FERN_TRIGGER_CHANCE_BONUS
            );
        }
        return player.getRandom().nextDouble() < Mth.clamp(chance, 0.0D, 1.0D)
                ? stat(PERK_FERN, FINAL_DAMAGE_ON_TRIGGER)
                : 0.0D;
    }
}
