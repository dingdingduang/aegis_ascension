package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_RANK;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.aegis.AuthorityAegis;
import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ApothicAttributesCompat;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import com.whatever.aegis_ascension.compat.ManaCompat;
import com.whatever.aegis_ascension.config.ServerSettings;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.perk.talents.Arona;
import com.whatever.aegis_ascension.perk.talents.FairTrade;
import com.whatever.aegis_ascension.perk.talents.PerfectAndElegantServant;
import com.whatever.aegis_ascension.perk.talents.TeamStar;
import com.whatever.aegis_ascension.perk.soullink.MadokaWithHomura;
import com.whatever.aegis_ascension.perk.soullink.MakeUpWorkClub;
import com.whatever.aegis_ascension.perk.soullink.MistyLake;
import com.whatever.aegis_ascension.perk.soullink.TeamRadiance;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.util.AegisModifiers;
import com.whatever.aegis_ascension.util.DisplayStatScope;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.util.StatAttribution;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared source of truth for talent-derived attributes, combat statistics,
 * compatibility modifiers, and synchronized Custom Stats values.
 */
public final class TalentStatService {
    private static final UUID ATTACK_FLAT_ID = AegisModifiers.adopt("34da31ad-36dc-4b7e-a886-c2cd640cb844");
    private static final UUID ATTACK_MULTIPLIER_ID = AegisModifiers.adopt("dffbdf80-d85f-4087-bf2d-191b0169c34f");
    private static final UUID PRIMARY_SELECTED_FLAT_ID =
            AegisModifiers.adopt("71f2bd98-b69b-4ca8-8a7c-35e278d353cf");
    private static final UUID PRIMARY_SELECTED_MULTIPLIER_ID =
            AegisModifiers.adopt("8c5f019a-15ab-4ca6-bd45-41410659dd80");
    private static final UUID HEALTH_FLAT_ID = AegisModifiers.adopt("675f47f5-97e0-4bca-85cd-057f9a386e6b");
    private static final UUID HEALTH_MULTIPLIER_ID = AegisModifiers.adopt("ec9a8473-cc62-4184-9a5b-a9ebd7706043");
    private static final UUID MAGIC_CONVERSION_HEALTH_ID =
            AegisModifiers.adopt("7fdd5555-44ad-47ea-88f9-c009b451fa26");
    private static final UUID MOVEMENT_MULTIPLIER_ID = AegisModifiers.adopt("3d118a4f-1668-4cf1-b9a7-b3fe82c3ebd6");
    private static final UUID ATTACK_SPEED_MULTIPLIER_ID = AegisModifiers.adopt("076e967c-a078-4775-a1ae-124a50cbcc4c");
    private static final UUID ATTACK_SPEED_FLAT_ID =
            AegisModifiers.adopt("4f9386ea-2d38-4ff9-bd88-bfc9064683d8");
    private static final UUID ATTACK_RANGE_ID =
            AegisModifiers.adopt("174f34a7-5201-4526-a18c-166327aecf76");
    private static final UUID LUCK_FLAT_ID = AegisModifiers.adopt("b937a6e2-9e1c-4fe8-ad1f-a06389568a7b");
    private static final UUID AEGIS_LUCK_MULTIPLIER_ID =
            AegisModifiers.adopt("1a404ff9-a81e-4d66-b345-f02f54a980d7");
    private static final UUID LEGACY_MOVEMENT_ID = AegisModifiers.adopt("79f5c26b-b99e-4fc8-a628-2a8d8cbbd944");
    private static final UUID LEGACY_HEALTH_ID = AegisModifiers.adopt("f2a78cb5-a53b-4cc7-a798-ce723ed69d4f");
    private static final UUID LEGACY_ATTACK_ID = AegisModifiers.adopt("edbe3297-7765-4769-87d4-f3c2156d391b");
    private static final UUID LEGACY_ARMOR_ID = AegisModifiers.adopt("fa30a8bb-dba4-4cab-9248-96e183f4a7bc");
    private static final UUID LEGACY_ATTACK_SPEED_ID = AegisModifiers.adopt("a913dd5f-ae7f-4b66-925a-76a851516802");
    private static final UUID LEGACY_LUCK_ID = AegisModifiers.adopt("ac839dc5-550d-49e1-8114-7ca954d23cbb");
    private static final UUID LEGACY_KNOCKBACK_ID = AegisModifiers.adopt("79729f8d-bfd0-486e-9dc0-7d634794d0f8");
    private static final UUID QUEST_HEALTH_PENALTY_ID = AegisModifiers.adopt("8f5f0b7d-cf6c-4cc6-9c71-24bdb3e4b1a1");
    private static final UUID QUEST_MOVEMENT_PENALTY_ID = AegisModifiers.adopt("f2f5c654-1b16-45de-80d9-2c18da8cb7a6");
    private static final UUID QUEST_ARMOR_PENALTY_ID = AegisModifiers.adopt("2c16cb5b-a8d6-4d0a-8a68-3d0f56a39116");
    private static final UUID QUEST_PRIMARY_PENALTY_ID = AegisModifiers.adopt("f694a0ba-5f0b-4c95-9a3d-2aa116af37a4");

    private TalentStatService() {
    }

    public static void recalculateAttributes(Player player, PlayerPerkData data) {
        clampCappedTriggerProgress(data);
        if (player instanceof ServerPlayer serverPlayer) {
            data.setCustomStat(
                    TEAM_DAMAGE_BONUS_ACTIVE,
                    TeamStar.damageBonus(serverPlayer)
            );
        }
        // Frieren's Soul Link reads the live Max Mana attribute. Publish Magic
        // Conversion first so its newest value is included in the same recalculation.
        IronSpellsCompat.updateAttributeModifiers(player, data);
        data.setCustomStat(
                MAGICIAN_PRIMARY_ATTRIBUTE_FLAT,
                magicianPrimaryAttributeFlat(player, data)
        );
        AttributeTotals totals = new AttributeTotals();
        boolean ignoreNegatives = data.owns(PERK_LAW_OF_THE_CYCLE)
                && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
        double magicConversionHealth = magicConversionMaximumHealth(player, data);

        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            int rank = entry.getValue();
            totals.attackMultiplier += perk.stat(ATTACK_MULTIPLIER) * rank;
            totals.primaryMultiplier += perk.stat(PRIMARY_ATTRIBUTE_MULTIPLIER) * rank;
            totals.attackSpeedMultiplier += perk.stat(ATTACK_SPEED_MULTIPLIER) * rank;
            totals.attackSpeedFlat += perk.stat(ATTACK_SPEED_FLAT) * rank;
            totals.movementMultiplier += perk.stat(MOVEMENT_SPEED_MULTIPLIER) * rank;
            // Luck and Lucky Strike are separate systems. Only explicit Luck stats
            // modify Attributes.LUCK; Lucky Strike is calculated for damage below.
            totals.luckFlat += perk.stat(LUCK_FLAT) * rank;

            double healthMultiplier = perk.stat(MAX_HEALTH_MULTIPLIER) * rank;
            if (healthMultiplier >= 0.0D || !ignoreNegatives) {
                totals.healthMultiplier += healthMultiplier;
            } else {
                totals.healthMultiplier += MadokaWithHomura.convertPercentagePenalty(
                        data, healthMultiplier
                );
            }
            if (perk.stats().containsKey(FIXED_MAX_HEALTH)) {
                if (!ignoreNegatives) {
                    totals.fixedMaxHealth = perk.stat(FIXED_MAX_HEALTH);
                } else {
                    totals.healthMultiplier +=
                            MadokaWithHomura.fixedMaxHealthMultiplierBonus(data);
                }
            }

            if (perk.id().equals(PERK_GREAT_FAIRY)) {
                totals.attackMultiplier += perk.stat(ATTACK_MULTIPLIER_PER_OWNED_TALENT)
                        * data.getUniqueTalentCount()
                        * MistyLake.greatFairyMultiplier(data);
            }
            if (perk.id().equals(PERK_SHIROKO)) {
                int chargesPerStack = Math.max(1, (int) Math.round(perk.stat(
                        UNSPENT_SKILL_ENHANCEMENT_CHARGES_PER_STACK
                )));
                int stacks = data.getSkillEnhancementCharges() / chargesPerStack;
                totals.attackMultiplier += stacks
                        * perk.stat(ATTACK_DAMAGE_PER_STACK)
                        * rank;
            }
        }

        double yuzusoftMultiplier = yuzusoftFanMultiplier(data);
        totals.primaryMultiplier += data.getCustomStat(PRIMARY_ATTRIBUTE_MULTIPLIER);
        totals.attackMultiplier += data.getCustomStat(CIALLO_ATTACK_MULTIPLIER)
                * yuzusoftMultiplier;
        totals.healthMultiplier += data.getCustomStat(CIALLO_MAX_HEALTH_MULTIPLIER)
                * yuzusoftMultiplier;
        totals.luckFlat += data.getCustomStat(CIALLO_LUCK) * yuzusoftMultiplier;

        if (data.hasActiveSoulLink(SOUL_COMBO_TECHNIQUE)) {
            totals.attackMultiplier += bonusStat(SOUL_COMBO_TECHNIQUE, ATTACK_MULTIPLIER);
        }
        if (data.hasActiveSoulLink(SOUL_MARIPATCHY_GROUP)) {
            totals.movementMultiplier += bonusStat(
                    SOUL_MARIPATCHY_GROUP, MOVEMENT_SPEED_MULTIPLIER
            );
        }

        totals.attackMultiplier += data.getCustomStat(AEGIS_ATTACK_MULTIPLIER);
        totals.healthFlat += data.getCustomStat(LAEVATEIN_HEALTH);
        totals.healthFlat += VirtualItems.bonus(data, VirtualItems.Effect.MAX_HEALTH);
        totals.healthMultiplier += data.getCustomStat(TEACHER_HEALTH_MULTIPLIER)
                + data.getCustomStat(AEGIS_MAX_HEALTH_MULTIPLIER)
                + VirtualItems.statBonus(data, VirtualItems.MAX_HEALTH_MULTIPLIER);
        totals.attackSpeedMultiplier += data.getCustomStat(AEGIS_ATTACK_SPEED_MULTIPLIER)
                + VirtualItems.statBonus(data, VirtualItems.ATTACK_SPEED_MULTIPLIER);
        totals.attackSpeedFlat += data.getCustomStat(KNIGHT_ATTACK_SPEED_FLAT);
        totals.luckFlat += data.getCustomStat(ALICE_LUCK);

        if (data.isAegisEnabled(AegisConstants.WATER)) {
            totals.healthMultiplier += aegisStat(
                    AegisConstants.WATER,
                    MAX_HEALTH_MULTIPLIER
            );
        }
        if (data.isAegisEnabled(AegisConstants.LUCKY)) {
            totals.luckFlat += aegisStat(AegisConstants.LUCKY, LUCK_FLAT);
        }
        AttributeInstance health = GeneralServerMethods.getAttributeInstance(player, Attributes.MAX_HEALTH);
        float oldMaxHealth = player.getMaxHealth();
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_FLAT_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MULTIPLIER_ID);
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_FLAT_ID);
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_MULTIPLIER_ID);
        removeModifier(player, Attributes.MAX_HEALTH, MAGIC_CONVERSION_HEALTH_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MULTIPLIER_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MULTIPLIER_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_FLAT_ID);
        removeModifier(player, GeneralServerMethods.getEntityReachAttribute(), ATTACK_RANGE_ID);
        removeModifier(player, Attributes.LUCK, LUCK_FLAT_ID);
        removeModifier(player, Attributes.LUCK, AEGIS_LUCK_MULTIPLIER_ID);
        // Remove modifiers from the original seven-example-perk build during migration.
        removeModifier(player, Attributes.MOVEMENT_SPEED, LEGACY_MOVEMENT_ID);
        removeModifier(player, Attributes.MAX_HEALTH, LEGACY_HEALTH_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, LEGACY_ATTACK_ID);
        removeModifier(player, Attributes.ARMOR, LEGACY_ARMOR_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, LEGACY_ATTACK_SPEED_ID);
        removeModifier(player, Attributes.LUCK, LEGACY_LUCK_ID);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, LEGACY_KNOCKBACK_ID);
        removeModifier(player, Attributes.MAX_HEALTH, QUEST_HEALTH_PENALTY_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, QUEST_MOVEMENT_PENALTY_ID);
        removeModifier(player, Attributes.ARMOR, QUEST_ARMOR_PENALTY_ID);
        for (SkillEnhancement enhancement : SkillEnhancement.values()) {
            enhancement.attribute().ifPresent(attribute ->
            {
                removeModifier(player, attribute, enhancement.modifierId());
                removeModifier(player, attribute,
                        enhancement.allSkillEnhancementAttributeModifierId());
                removeModifier(player, attribute, PRIMARY_SELECTED_FLAT_ID);
                removeModifier(player, attribute, PRIMARY_SELECTED_MULTIPLIER_ID);
            }
            );
        }

        if (totals.fixedMaxHealth >= 0.0D && health != null) {
            // Keep fixed-health talents fixed after the separate mana-derived modifier.
            totals.healthFlat = totals.fixedMaxHealth
                    - GeneralServerMethods.getAttributeBaseValue(
                    player, Attributes.MAX_HEALTH, 0.0D
            )
                    - magicConversionHealth;
            totals.healthMultiplier = 0.0D;
        }

        addModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MULTIPLIER_ID,
                "aegis_ascension:attack_multiplier", totals.attackMultiplier,
                AttributeOperation.MULTIPLY_TOTAL);
        // ATTACK_FLAT_ID has always been cleared at the top of this pass but never applied;
        // the attack stat book is its first user. Added before the multiplier's
        // MULTIPLY_TOTAL takes effect, so book points scale with attack% like base damage.
        addModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_FLAT_ID,
                "aegis_ascension:attack_flat",
                VirtualItems.bonus(data, VirtualItems.Effect.ATTACK_DAMAGE)
                        + VirtualItems.statBonus(data, VirtualItems.ATTACK_DAMAGE),
                AttributeOperation.ADDITION);
        addModifier(player, Attributes.MAX_HEALTH, HEALTH_FLAT_ID,
                "aegis_ascension:health_flat", totals.healthFlat, AttributeOperation.ADDITION);
        addModifier(player, Attributes.MAX_HEALTH, MAGIC_CONVERSION_HEALTH_ID,
                "aegis_ascension:magic_conversion_health", magicConversionHealth,
                AttributeOperation.ADDITION);
        addModifier(player, Attributes.MAX_HEALTH, HEALTH_MULTIPLIER_ID,
                "aegis_ascension:health_multiplier", totals.healthMultiplier,
                AttributeOperation.MULTIPLY_TOTAL);
        addModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MULTIPLIER_ID,
                "aegis_ascension:movement_multiplier", totals.movementMultiplier,
                AttributeOperation.MULTIPLY_TOTAL);
        addModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MULTIPLIER_ID,
                "aegis_ascension:attack_speed_multiplier", totals.attackSpeedMultiplier,
                AttributeOperation.MULTIPLY_TOTAL);
        addModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_FLAT_ID,
                "aegis_ascension:attack_speed_flat", totals.attackSpeedFlat,
                AttributeOperation.ADDITION);
        addModifier(player, GeneralServerMethods.getEntityReachAttribute(), ATTACK_RANGE_ID,
                "aegis_ascension:attack_range", attackRangeBonus(data),
                AttributeOperation.ADDITION);
        addModifier(player, Attributes.LUCK, LUCK_FLAT_ID,
                "aegis_ascension:luck", totals.luckFlat, AttributeOperation.ADDITION);
        if (data.isAegisEnabled(AegisConstants.FORTUNE)) {
            addModifier(
                    player,
                    Attributes.LUCK,
                    AEGIS_LUCK_MULTIPLIER_ID,
                    "aegis_ascension:aegis/fortune_luck",
                    aegisStat(AegisConstants.FORTUNE, AegisConstants.LUCK_MULTIPLIER),
                    AttributeOperation.MULTIPLY_TOTAL
            );
        }
        data.getSkillEnhancementRanks().forEach((enhancement, rank) ->
                enhancement.attribute().ifPresent(attribute -> {
                    if (attribute != Attributes.MAX_HEALTH || totals.fixedMaxHealth < 0.0D) {
                        addModifier(
                                player,
                                attribute,
                                enhancement.modifierId(),
                                "aegis_ascension:skill_enhancement/" + enhancement.id(),
                                enhancement.amount() * rank,
                                enhancement.operation()
                        );
                    }
                })
        );

        double allSkillEnhancementAttribute = allSkillEnhancementAttribute(data);
        for (SkillEnhancement enhancement : SkillEnhancement.values()) {
            if (!enhancement.affectedByAllSkillEnhancementAttribute()) {
                continue;
            }
            enhancement.attribute().ifPresent(attribute -> {
                if (attribute != Attributes.MAX_HEALTH || totals.fixedMaxHealth < 0.0D) {
                    addModifier(
                            player,
                            attribute,
                            enhancement.allSkillEnhancementAttributeModifierId(),
                            "aegis_ascension:all_skill_enhancement_attribute/"
                                    + enhancement.id(),
                            allSkillEnhancementAttribute,
                            AttributeOperation.MULTIPLY_TOTAL
                    );
                }
            });
        }

        if (data.hasChosenPrimarySkillEnhancement()) {
            SkillEnhancement primary = data.getPrimarySkillEnhancement();
            primary.attribute().ifPresent(attribute -> {
                if (attribute == Attributes.MAX_HEALTH
                        && totals.fixedMaxHealth >= 0.0D) {
                    return;
                }
                addModifier(
                        player,
                        attribute,
                        PRIMARY_SELECTED_FLAT_ID,
                        "aegis_ascension:primary_skill_enhancement_flat",
                        totalPrimaryAttributeFlat(data) * primary.amount(),
                        primary.operation()
                );
                addModifier(
                        player,
                        attribute,
                        PRIMARY_SELECTED_MULTIPLIER_ID,
                        "aegis_ascension:primary_skill_enhancement_multiplier",
                        totals.primaryMultiplier,
                        AttributeOperation.MULTIPLY_TOTAL
                );
            });
        }
        if (data.isChallengePenaltyActive()) {
            addModifier(player, Attributes.MAX_HEALTH, QUEST_HEALTH_PENALTY_ID,
                    "aegis_ascension:quest_challenge_health_penalty", -0.50D,
                    AttributeOperation.MULTIPLY_TOTAL);
            addModifier(player, Attributes.MOVEMENT_SPEED, QUEST_MOVEMENT_PENALTY_ID,
                    "aegis_ascension:quest_challenge_movement_penalty", -0.50D,
                    AttributeOperation.MULTIPLY_TOTAL);
            addModifier(player, Attributes.ARMOR, QUEST_ARMOR_PENALTY_ID,
                    "aegis_ascension:quest_challenge_armor_penalty", -0.50D,
                    AttributeOperation.MULTIPLY_TOTAL);
            if (data.hasChosenPrimarySkillEnhancement()) {
                data.getPrimarySkillEnhancement().attribute().ifPresent(attribute ->
                        addModifier(player, attribute, QUEST_PRIMARY_PENALTY_ID,
                                "aegis_ascension:quest_challenge_primary_penalty", -0.50D,
                                AttributeOperation.MULTIPLY_TOTAL));
            }
        }

        // Apothic Attributes exposes mapped custom stats in its Attributes GUI.
        // Attack Damage and Luck above are already vanilla attributes, so the
        // same GUI sees them too. Millennium Echo uses the final Apothic crit
        // chance, including equipment and modifiers from other mods.
        double overflowChanceStep = 0.0D;
        double damagePerOverflowStep = 0.0D;
        if (data.hasActiveSoulLink(SOUL_MILLENNIUM_ECHO)) {
            overflowChanceStep = bonusStat(
                    SOUL_MILLENNIUM_ECHO, OVERFLOW_CRITICAL_CHANCE_STEP
            );
            damagePerOverflowStep = bonusStat(
                    SOUL_MILLENNIUM_ECHO, CRITICAL_DAMAGE_PER_OVERFLOW_STEP
            );
        }
        double talentCriticalChance = criticalChance(data);
        double talentCriticalDamage = criticalDamageBonus(data);
        ApothicAttributesCompat.updateAttributeModifiers(
                player,
                data,
                talentCriticalChance,
                talentCriticalDamage,
                flameCriticalDamagePerCriticalChance(data),
                overflowChanceStep,
                damagePerOverflowStep
        );
        IronSpellsCompat.updateAttributeModifiers(player, data);
        if (player instanceof ServerPlayer serverPlayer) {
            DevourAegis.applyModifiers(serverPlayer, data);
        }

        float newMaxHealth = player.getMaxHealth();
        if (newMaxHealth > oldMaxHealth) {
            player.setHealth(Math.min(newMaxHealth, player.getHealth() + newMaxHealth - oldMaxHealth));
        } else if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }
    }

    private static void removeModifier(Player player, Attribute attribute, UUID id) {
        GeneralServerMethods.removeAttributeModifier(player, attribute, id);
    }

    private static void addModifier(Player player, Attribute attribute, UUID id, String name,
                                    double amount, AttributeOperation operation) {
        GeneralServerMethods.addAttributeModifier(player, attribute, id, name, amount, operation);
    }

    public static double magicConversionMaximumMana(PlayerPerkData data) {
        return data.owns(PERK_MAGIC_CONVERSION)
                ? Math.max(0.0D, data.getCustomStat(MAGIC_CONVERSION_MAX_MANA))
                : 0.0D;
    }

    /** Flat Max Health granted by R Magic Conversion from the effective live mana pool. */
    public static double magicConversionMaximumHealth(Player player, PlayerPerkData data) {
        return data.owns(PERK_MAGIC_CONVERSION)
                ? ManaCompat.maximumMana(player, data)
                * Math.max(0.0D, stat(PERK_MAGIC_CONVERSION, MAX_HEALTH_PER_MAX_MANA))
                : 0.0D;
    }

    /** Lets the one-second player tick avoid rebuilding every attribute unnecessarily. */
    static boolean isMagicConversionHealthCurrent(Player player, PlayerPerkData data) {
        AttributeInstance health = GeneralServerMethods.getAttributeInstance(player, Attributes.MAX_HEALTH);
        if (health == null) {
            return true;
        }
        double expected = magicConversionMaximumHealth(player, data);
        AttributeModifier current = GeneralServerMethods.getAttributeModifier(
                player, Attributes.MAX_HEALTH, MAGIC_CONVERSION_HEALTH_ID
        );
        if (Math.abs(expected) < 1.0E-9D) {
            return current == null;
        }
        return current != null
                && GeneralServerMethods.getAttributeOperation(current) == AttributeOperation.ADDITION
                && Math.abs(current.getAmount() - expected) < 1.0E-9D;
    }

    public static double frierenMaximumMana(PlayerPerkData data) {
        return data.owns(PERK_FRIEREN)
                ? Math.max(0.0D, data.getCustomStat(PRIMARY_FLAT))
                * stat(PERK_FRIEREN, MANA_PER_PRIMARY_STAT)
                : 0.0D;
    }

    static boolean consumeCappedTrigger(PlayerPerkData data, Perk perk,
                                                String counterKey,
                                                double inferredTriggers) {
        return consumeCappedTrigger(
                data,
                perk,
                counterKey,
                inferredTriggers,
                MAX_TRIGGER_COUNT
        );
    }

    static boolean consumeCappedTrigger(PlayerPerkData data, Perk perk,
                                        String counterKey,
                                        double inferredTriggers,
                                        String maximumStatKey) {
        int maximum = maximumTriggerCount(perk, maximumStatKey);
        int persisted = Math.max(0, Mth.floor(data.getCustomStat(counterKey)));
        int inferred = Math.max(0, Mth.floor(inferredTriggers + 1.0E-7D));
        int current = data.getCustomStats().containsKey(counterKey)
                ? persisted
                : inferred;
        if (current >= maximum) {
            if (persisted != current) {
                data.setCustomStat(counterKey, current);
            }
            return false;
        }
        data.setCustomStat(counterKey, current + 1.0D);
        return true;
    }

    static double inferredTriggerCount(double accumulated, double perTrigger) {
        if (Math.abs(perTrigger) <= 1.0E-9D) {
            return 0.0D;
        }
        return Math.abs(accumulated / perTrigger);
    }

    private static void clampCappedTriggerProgress(PlayerPerkData data) {
        if (data.owns(PERK_LAEVATEIN)) {
            Perk perk = requiredPerk(PERK_LAEVATEIN);
            int maximum = maximumTriggerCount(perk);
            clampTriggerCounter(data, LAEVATEIN_TRIGGER_COUNT, maximum);
            clampAccumulatedValue(
                    data,
                    LAEVATEIN_HEALTH,
                    perk.stat(MAX_HEALTH_FLAT_PER_DAMAGE),
                    maximum
            );
        }
        if (data.owns(PERK_MAGIC_CONVERSION)) {
            Perk perk = requiredPerk(PERK_MAGIC_CONVERSION);
            int maximum = maximumTriggerCount(perk);
            clampTriggerCounter(data, MAGIC_CONVERSION_TRIGGER_COUNT, maximum);
            clampAccumulatedValue(
                    data,
                    MAGIC_CONVERSION_MAX_MANA,
                    perk.stat(MAX_MANA_FLAT_PER_DAMAGE),
                    maximum
            );
        }
        if (data.owns(PERK_I_SHALL_INTERPRET_THE_RADIANCE)) {
            Perk perk = requiredPerk(PERK_I_SHALL_INTERPRET_THE_RADIANCE);
            int maximum = maximumTriggerCount(perk);
            clampTriggerCounter(data, RADIANCE_TRIGGER_COUNT, maximum);
            clampAccumulatedValue(
                    data,
                    FROSTBITE_DAMAGE,
                    perk.stat(DAMAGE_BONUS_PER_KILL),
                    maximum
            );
            clampAccumulatedValue(
                    data,
                    FROSTBITE_DAMAGE_TAKEN,
                    -perk.stat(DAMAGE_REDUCTION_PER_KILL),
                    maximum
            );
        }
        if (data.owns(PERK_INNATE_DREAM)) {
            Perk perk = requiredPerk(PERK_INNATE_DREAM);
            clampCappedTriggerProgress(
                    data,
                    perk,
                    INNATE_DAMAGE_TRIGGER_COUNT,
                    INNATE_DAMAGE,
                    DAMAGE_BONUS_PER_TRIGGER,
                    DAMAGE_BONUS_MAX_TRIGGER_COUNT
            );
            clampCappedTriggerProgress(
                    data,
                    perk,
                    INNATE_SKILL_DAMAGE_TRIGGER_COUNT,
                    INNATE_SKILL_DAMAGE,
                    SKILL_DAMAGE_PER_TRIGGER,
                    SKILL_DAMAGE_MAX_TRIGGER_COUNT
            );
            clampCappedTriggerProgress(
                    data,
                    perk,
                    INNATE_CRITICAL_DAMAGE_TRIGGER_COUNT,
                    INNATE_CRITICAL_DAMAGE,
                    CRITICAL_DAMAGE_PER_TRIGGER,
                    CRITICAL_DAMAGE_MAX_TRIGGER_COUNT
            );
        }
    }

    private static int maximumTriggerCount(Perk perk) {
        return maximumTriggerCount(perk, MAX_TRIGGER_COUNT);
    }

    private static int maximumTriggerCount(Perk perk, String maximumStatKey) {
        return Math.max(0, integerStat(perk, maximumStatKey));
    }

    private static void clampCappedTriggerProgress(PlayerPerkData data, Perk perk,
                                                   String counterKey,
                                                   String accumulatedStatKey,
                                                   String perTriggerStatKey,
                                                   String maximumStatKey) {
        int maximum = maximumTriggerCount(perk, maximumStatKey);
        clampTriggerCounter(data, counterKey, maximum);
        clampAccumulatedValue(
                data,
                accumulatedStatKey,
                perk.stat(perTriggerStatKey),
                maximum
        );
    }

    private static void clampTriggerCounter(PlayerPerkData data, String counterKey,
                                            int maximum) {
        if (data.getCustomStats().containsKey(counterKey)
                && data.getCustomStat(counterKey) > maximum) {
            data.setCustomStat(counterKey, maximum);
        }
    }

    private static void clampAccumulatedValue(PlayerPerkData data, String customStatKey,
                                              double perTrigger, int maximum) {
        double limit = perTrigger * maximum;
        double current = data.getCustomStat(customStatKey);
        if ((perTrigger >= 0.0D && current > limit)
                || (perTrigger < 0.0D && current < limit)) {
            data.setCustomStat(customStatKey, limit);
        }
    }

    static double magicDamageBonus(PlayerPerkData data) {
        return data.getCustomStat(MAGIC_DAMAGE)
                + sumOwnedStat(data, MAGIC_DAMAGE)
                + allSkillEnhancementCustomStatBonus(data, MAGIC_DAMAGE)
                + primaryCustomStatBonus(data, MAGIC_DAMAGE);
    }

    static double magicAmplification(PlayerPerkData data) {
        double amplification = data.getCustomStat(MAGIC_DAMAGE_AMPLIFICATION)
                + data.getCustomStat(LUNAR_DAMAGE)
                + data.getCustomStat(CIALLO_MAGIC_DAMAGE_AMPLIFICATION)
                * yuzusoftFanMultiplier(data)
                + sumOwnedStat(data, MAGIC_DAMAGE_AMPLIFICATION);
        if (data.owns(PERK_COLLECTOR)) {
            amplification += stat(
                    PERK_COLLECTOR,
                    MAGIC_DAMAGE_AMPLIFICATION_PER_SOUL_LINK
            ) * data.getActiveSoulLinks().size()
                    * MakeUpWorkClub.collectorMultiplier(data);
        }
        return amplification;
    }

    static double skillDamageBonus(PlayerPerkData data,
                                           double currentLuckyStrike) {
        double skillDamage = data.getCustomStat(SKILL_DAMAGE)
                + data.getCustomStat(INNATE_SKILL_DAMAGE)
                + sumOwnedStat(data, SKILL_DAMAGE)
                + allSkillEnhancementCustomStatBonus(data, SKILL_DAMAGE)
                + primaryCustomStatBonus(data, SKILL_DAMAGE);
        if (data.owns(PERK_CLEAR_MIND_STATE)) {
            skillDamage += sumOwnedStat(data, EVASION_FLAT)
                    * stat(PERK_CLEAR_MIND_STATE, SKILL_DAMAGE_PER_EVASION);
        }
        if (data.owns(PERK_METEOR_SPARKLE)) {
            skillDamage += currentLuckyStrike
                    * stat(PERK_METEOR_SPARKLE, SKILL_DAMAGE_PER_LUCKY_STRIKE);
        }
        if (data.owns(PERK_GREAT_FAIRY)) {
            skillDamage += stat(PERK_GREAT_FAIRY, SKILL_DAMAGE_PER_OWNED_TALENT)
                    * data.getUniqueTalentCount()
                    * MistyLake.greatFairyMultiplier(data);
        }
        return skillDamage;
    }

    /** Public spell-addon API value; all recognized spell damage uses this calculation. */
    public static double skillDamageMultiplier(Player player, PlayerPerkData data) {
        return TalentDamageCalculations.damageSkillCalculation(player, data);
    }

    private static double summonCount(PlayerPerkData data) {
        return data.getCustomStat(SUMMON_COUNT) + sumOwnedStat(data, SUMMON_COUNT);
    }

    private static double attackRangeBonus(PlayerPerkData data) {
        double summonCount = summonCount(data);
        double attackRange = data.getCustomStat(BREAKTHROUGH_ATTACK_RANGE)
                + data.getCustomStat(ATTACK_RANGE)
                + data.getCustomStat(AEGIS_ATTACK_RANGE)
                + sumOwnedStat(data, ATTACK_RANGE_FLAT);
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            if (isActive(data, entry.getKey())) {
                attackRange += entry.getKey().stat(ATTACK_RANGE_PER_SUMMON)
                        * summonCount * entry.getValue();
            }
        }
        return attackRange;
    }

    /**
     * Display values screens other than Custom Stats depend on.
     *
     * <p>These ride along with every routine progression sync, because the screens that
     * read them are opened by a server push and so cannot ask first. Everything else in
     * the display map is fetched by the Collection screen when it needs it.</p>
     */
    private static final Set<String> ESSENTIAL_DISPLAY_STATS = Set.of(
            TEAM_RADIANCE_RANK,
            StatAttribution.CUSTOM_STAT_PREFIX + AegisConstants.AUTHORITY_SELECT_ALL_USES
    );

    /**
     * @param scope how much of the map the receiver needs. Building the whole thing and
     *              filtering keeps one code path, so a stat can never appear under one
     *              scope and silently vanish under another.
     */
    public static Map<String, Double> buildDisplayStats(Player player, PlayerPerkData data,
                                                        DisplayStatScope scope) {
        Map<String, Double> complete = buildCompleteDisplayStats(
                player, data, scope.includesAttribution()
        );
        if (scope != DisplayStatScope.ESSENTIAL) {
            return complete;
        }
        Map<String, Double> essential = new LinkedHashMap<>();
        ESSENTIAL_DISPLAY_STATS.forEach(key -> {
            Double value = complete.get(key);
            if (value != null) {
                essential.put(key, value);
            }
        });
        return Map.copyOf(essential);
    }

    private static Map<String, Double> buildCompleteDisplayStats(
            Player player, PlayerPerkData data, boolean includeAttribution) {
        Map<String, Double> stats = new LinkedHashMap<>();

        double activeSoulLinks = data.getActiveSoulLinks().size();
        double summonCount = summonCount(data);
        double luck = GeneralServerMethods.getAttributeValue(player, Attributes.LUCK);
        double luckyStrike = luckyStrike(player, data);
        double breakthroughEffect = 1.0D
                + sumOwnedStat(data, BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS)
                + data.getCustomStat(BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS)
                + TeamRadiance.breakthroughEffectBonus(data);
        if (data.hasActiveSoulLink(SOUL_MARIPATCHY_GROUP)) {
            breakthroughEffect += bonusStat(
                    SOUL_MARIPATCHY_GROUP, BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS
            );
        }
        if (data.isAegisEnabled(AegisConstants.FROST_MOON)) {
            breakthroughEffect += aegisStat(
                    AegisConstants.FROST_MOON,
                    BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS
            );
        }
        if (data.isAegisEnabled(AegisConstants.FOX_GOD)) {
            breakthroughEffect += aegisStat(
                    AegisConstants.FOX_GOD,
                    AegisConstants.BREAKTHROUGH_EFFECT_PER_CONSTELLATION
            ) * Math.max(0, com.whatever.aegis_ascension.aegis.FoxAegis.constellationCount(data));
        }
        double yuzusoftMultiplier = yuzusoftFanMultiplier(data);
        double finalDamage = data.getCustomStat(FINAL_DAMAGE)
                + data.getCustomStat(BLAZING_BREAKTHROUGH_DAMAGE)
                + data.getCustomStat(CIALLO_FINAL_DAMAGE) * yuzusoftMultiplier
                + sumOwnedStat(data, FINAL_DAMAGE)
                + PerfectAndElegantServant.finalDamage(data)
                + TeamRadiance.finalDamageBonus(data)
                + VirtualItems.statBonus(data, VirtualItems.FINAL_DAMAGE);
        double pecorineFinalDamage = data.owns(PERK_PECORINES_BLESSING)
                && player.getHealth() >= player.getMaxHealth()
                ? stat(PERK_PECORINES_BLESSING, FULL_HEALTH_FINAL_DAMAGE)
                : 0.0D;
        finalDamage += pecorineFinalDamage;
        if (data.owns(PERK_KOKONA)) {
            finalDamage += stat(PERK_KOKONA, FINAL_DAMAGE_PER_OWNED_TALENT)
                    * data.getUniqueTalentCount();
        }
        if (data.owns(PERK_FIREFLY_FLAME) && luckyStrike
                > stat(PERK_FIREFLY_FLAME, LUCKY_STRIKE_THRESHOLD)) {
            finalDamage += stat(PERK_FIREFLY_FLAME, FINAL_DAMAGE_ABOVE_THRESHOLD);
        }
        if (data.isAegisEnabled(AegisConstants.HARMONY)) {
            finalDamage += aegisStat(AegisConstants.HARMONY, FINAL_DAMAGE)
                    * harmonyScalingFactor(data);
        }
        if (data.isAegisEnabled(AegisConstants.DESTRUCTION)) {
            finalDamage += Math.max(0.0D, -rawDamageResistance(data))
                    * aegisStat(
                            AegisConstants.DESTRUCTION,
                            AegisConstants.FINAL_DAMAGE_PER_NEGATIVE_DAMAGE_REDUCTION
                    );
        }

        double damageBonus = data.getCustomStat(WALK_DAMAGE)
                + data.getCustomStat(FROSTBITE_DAMAGE)
                + data.getCustomStat(INNATE_DAMAGE)
                + data.getCustomStat(TOP_DAMAGE)
                + data.getCustomStat(AegisConstants.ARCANE_BARRAGE_DAMAGE_BONUS)
                + data.getCustomStat(DOMINUS_SHIELD_DAMAGE_BONUS)
                + FairTrade.damageBonus(data)
                + TeamStar.damageBonus(player)
                + sumOwnedStat(data, DAMAGE_BONUS)
                + VirtualItems.statBonus(data, VirtualItems.DAMAGE_BONUS);

        double physicalAmplification = data.getCustomStat(
                PHYSICAL_DAMAGE_AMPLIFICATION
        ) + data.getCustomStat(LUNAR_DAMAGE)
                + data.getCustomStat(CIALLO_PHYSICAL_DAMAGE_AMPLIFICATION)
                * yuzusoftMultiplier
                + sumOwnedStat(data, PHYSICAL_DAMAGE_AMPLIFICATION);
        double magicAmplification = magicAmplification(data);
        if (data.owns(PERK_COLLECTOR)) {
            physicalAmplification += stat(
                    PERK_COLLECTOR, PHYSICAL_DAMAGE_AMPLIFICATION_PER_SOUL_LINK
            ) * activeSoulLinks * MakeUpWorkClub.collectorMultiplier(data);
        }

        double skillDamage = skillDamageBonus(data, luckyStrike);
        double magicDamage = magicDamageBonus(data);
        double attackDamageAmplification = sumOwnedStat(
                data,
                ATTACK_DAMAGE_AMPLIFICATION
        );
        if (data.owns(PERK_RIGHTEOUS_KNIGHT)) {
            attackDamageAmplification += data.getCustomStat(KNIGHT_DAMAGE);
        }
        if (data.hasActiveSoulLink(SOUL_COMBO_TECHNIQUE)) {
            attackDamageAmplification += bonusStat(
                    SOUL_COMBO_TECHNIQUE,
                    ATTACK_DAMAGE_AMPLIFICATION
            );
        }

        double summonPower = data.getCustomStat(SUMMON_POWER)
                + sumOwnedStat(data, SUMMON_POWER);
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            if (isActive(data, entry.getKey())) {
                summonPower += entry.getKey().stat(SUMMON_POWER_PER_SUMMON)
                        * summonCount * entry.getValue();
            }
        }

        double attackRange = attackRangeBonus(data);

        double talentCriticalChance = criticalChance(data);
        double talentCriticalDamage = criticalDamageBonus(data)
                + flameCriticalDamage(data, talentCriticalChance)
                + millenniumOverflowCriticalDamage(data, talentCriticalChance);

        stats.put(BREAKTHROUGH_EFFECT, breakthroughEffect);
        putAttributeDisplayStats(stats, ATTACK_DAMAGE, player, Attributes.ATTACK_DAMAGE);
        putAttributeDisplayStats(stats, ARMOR, player, Attributes.ARMOR);
        putAttributeDisplayStats(stats, ATTACK_SPEED, player, Attributes.ATTACK_SPEED);
        // Kept in the synchronized display map for the Attack Damage source panel.
        // These are not separate cards.
        stats.put(AEGIS_ATTACK_MULTIPLIER, data.getCustomStat(AEGIS_ATTACK_MULTIPLIER));
        stats.put(FINAL_DAMAGE, finalDamage);
        stats.put(
                INDEPENDENT_DAMAGE_AMPLIFICATION,
                data.getCustomStat(INDEPENDENT_DAMAGE_AMPLIFICATION)
                        + sumOwnedStat(data, INDEPENDENT_DAMAGE_AMPLIFICATION)
        );
        stats.put(DAMAGE_BONUS, damageBonus);
        stats.put(ATTACK_DAMAGE_AMPLIFICATION, attackDamageAmplification);
        stats.put(MAGIC_DAMAGE, magicDamage);
        stats.put(PHYSICAL_DAMAGE_AMPLIFICATION, physicalAmplification);
        stats.put(MAGIC_DAMAGE_AMPLIFICATION, magicAmplification);
        stats.put(SKILL_DAMAGE, skillDamage);
        stats.put(AegisConstants.SKILL_AREA, data.getCustomStat(AegisConstants.SKILL_AREA));
        stats.put(AegisConstants.BARRAGE_MISSILE_SPEED,
                data.getCustomStat(AegisConstants.BARRAGE_MISSILE_SPEED));
        stats.put(AegisConstants.BARRAGE_DAMAGE,
                data.getCustomStat(AegisConstants.BARRAGE_DAMAGE));
        stats.put(AegisConstants.BARRAGE_AREA,
                data.getCustomStat(AegisConstants.BARRAGE_AREA));
        stats.put(TRUE_DAMAGE, data.getCustomStat(TRUE_DAMAGE)
                + sumOwnedStat(data, TRUE_DAMAGE));
        stats.put(HEALTH_REGENERATION, data.getCustomStat(HEALTH_REGENERATION)
                + sumOwnedStat(data, HEALTH_RESTORE_PER_SECOND));
        stats.put(MANA_REGENERATION, data.getCustomStat(MANA_REGENERATION)
                + sumOwnedStat(data, MANA_RESTORE_PER_SECOND)
                + manaRegenerationMultiplier(data));
        stats.put(HEALING_POWER, data.getCustomStat(HEALING_POWER)
                + sumOwnedStat(data, HEALING_POWER));
        stats.put(SUMMON_POWER, summonPower);
        stats.put(SUMMON_COUNT, summonCount);
        stats.put(CRITICAL_CHANCE, ApothicAttributesCompat.criticalChance(
                player, talentCriticalChance
        ));
        stats.put(CRITICAL_DAMAGE, ApothicAttributesCompat.criticalDamage(
                player, 1.5D + talentCriticalDamage
        ));
        stats.put(LUCKY_STRIKE, luckyStrike);
        putAttributeDisplayStats(stats, LUCK, player, Attributes.LUCK);
        stats.put(ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                allSkillEnhancementAttribute(data));
        stats.put(PRIMARY_ATTRIBUTE_FLAT, totalPrimaryAttributeFlat(data));
        stats.put(ATTACK_RANGE, attackRange);
        stats.put(COOLDOWN_REDUCTION, cooldownReduction(data));
        stats.put(ATTACK_SPEED_FLAT,
                data.getCustomStat(KNIGHT_ATTACK_SPEED_FLAT)
                        + sumOwnedStat(data, ATTACK_SPEED_FLAT));
        stats.put(INDEPENDENT_SKILL_DAMAGE,
                data.getCustomStat(INDEPENDENT_SKILL_DAMAGE));
        stats.put(INDEPENDENT_SKILL_AREA,
                data.getCustomStat(INDEPENDENT_SKILL_AREA));
        double accumulatedDamageReduction = damageResistance(data);
        stats.put(DAMAGE_REDUCTION, effectiveDamageResistance(data));
        // Mod-only: nothing outside Aegis Ascension feeds this, so the "other" halves
        // are zero and the accumulated total is entirely ours.
        stats.put(DISPLAY_FLAT_PREFIX + DAMAGE_REDUCTION, 0.0D);
        stats.put(DISPLAY_PERCENT_PREFIX + DAMAGE_REDUCTION,
                accumulatedDamageReduction);
        stats.put(DISPLAY_OTHER_FLAT_PREFIX + DAMAGE_REDUCTION, 0.0D);
        stats.put(DISPLAY_OTHER_PERCENT_PREFIX + DAMAGE_REDUCTION, 0.0D);
        stats.put(SHIELD_GAIN, shieldGain(player, data));
        stats.put(REVIVES_REMAINING, data.getCustomStat(REVIVES_REMAINING));
        stats.put(TALENT_OPTION_BONUS, talentOptionBonus(player, data));
        stats.put(TEAM_RADIANCE_RANK, (double) TeamRadiance.rank(data));
        // Internal accumulated values let the client separate direct talent bonuses
        // from bonuses earned over time. Keys beginning with this prefix are never
        // rendered as their own Custom Stats cards.
        data.getCustomStats().forEach((key, value) -> {
            if (!includeAttribution && StatAttribution.isRecord(key)) {
                return;
            }
            stats.put(StatAttribution.CUSTOM_STAT_PREFIX + key, value);
        });
        stats.put(
                "__custom." + PECORINE_ACTIVE_FINAL_DAMAGE,
                pecorineFinalDamage
        );
        return Map.copyOf(stats);
    }

    /**
     * Splits a live Minecraft attribute into this mod's share and everything else.
     *
     * <p>These four stats are not mod-only: Attack Damage carries the held weapon, Armor
     * the worn pieces, and either may carry a potion or another mod. Ownership is settled
     * by modifier identity rather than by arithmetic, because a diamond sword's {@code +7}
     * is additive in exactly the way a talent's flat bonus is — only the id tells them
     * apart.</p>
     *
     * <p>The four published components reconstruct the real value exactly:</p>
     * <pre>final = (flat + otherFlat) * (1 + percentage) * (1 + otherPercentage)</pre>
     *
     * <p>{@code flat} and {@code percentage} are this mod's own, measured directly from
     * the modifiers it owns. {@code otherFlat} is the vanilla base value plus every
     * additive modifier we do not own. {@code otherPercentage} is then whatever remains,
     * which is why the equation closes.</p>
     *
     * <p>The two percentages multiply rather than sum, because this mod expresses its
     * own percentages as {@code MULTIPLY_TOTAL}, which Minecraft applies in turn. Adding
     * them would understate the result whenever anything outside also contributes a
     * multiplier.</p>
     *
     * <p>{@code otherPercentage} is taken as a remainder so the identity holds by
     * construction. Today it also equals the outside contribution measured directly,
     * because Minecraft sums {@code MULTIPLY_BASE} into one shared factor and this mod
     * contributes none of that operation to these attributes — so the two sides
     * separate cleanly with no cross-term. Applying a {@code MULTIPLY_BASE} modifier to
     * one of them would introduce one; the identity would still hold, but the
     * interaction would then be charged to the "other" half rather than being zero.</p>
     */
    private static void putAttributeDisplayStats(Map<String, Double> stats,
                                                 String statKey,
                                                 Player player,
                                                 Attribute attribute) {
        if (GeneralServerMethods.getAttributeInstance(player, attribute) == null) {
            stats.put(statKey, 0.0D);
            stats.put(DISPLAY_FLAT_PREFIX + statKey, 0.0D);
            stats.put(DISPLAY_PERCENT_PREFIX + statKey, 0.0D);
            stats.put(DISPLAY_OTHER_FLAT_PREFIX + statKey, 0.0D);
            stats.put(DISPLAY_OTHER_PERCENT_PREFIX + statKey, 0.0D);
            return;
        }
        double flat = 0.0D;
        double multiplyBase = 0.0D;
        double multiplyTotal = 1.0D;
        double otherFlat = GeneralServerMethods.getAttributeBaseValue(player, attribute, 0.0D);
        for (AttributeModifier modifier : GeneralServerMethods.getAttributeModifiers(
                player, attribute
        )) {
            double amount = modifier.getAmount();
            if (!Double.isFinite(amount)) {
                continue;
            }
            boolean ours = AegisModifiers.isOurs(modifier.getId());
            switch (GeneralServerMethods.getAttributeOperation(modifier)) {
                case ADDITION -> {
                    if (ours) {
                        flat += amount;
                    } else {
                        otherFlat += amount;
                    }
                }
                case MULTIPLY_BASE -> {
                    if (ours) {
                        multiplyBase += amount;
                    }
                }
                case MULTIPLY_TOTAL -> {
                    if (ours) {
                        multiplyTotal *= 1.0D + amount;
                    }
                }
            }
        }
        double percentage = (1.0D + multiplyBase) * multiplyTotal - 1.0D;
        double finalValue = GeneralServerMethods.getAttributeValue(player, attribute);
        double accountedFor = (flat + otherFlat) * (1.0D + percentage);
        double otherPercentage = Math.abs(accountedFor) > 1.0E-9D
                ? finalValue / accountedFor - 1.0D
                : 0.0D;

        stats.put(statKey, finalValue);
        stats.put(DISPLAY_FLAT_PREFIX + statKey, flat);
        stats.put(DISPLAY_PERCENT_PREFIX + statKey, percentage);
        stats.put(DISPLAY_OTHER_FLAT_PREFIX + statKey, otherFlat);
        stats.put(DISPLAY_OTHER_PERCENT_PREFIX + statKey, otherPercentage);
    }

    public static double cooldownReduction(PlayerPerkData data) {
        double total = data.getCustomStat(COOLDOWN_REDUCTION)
                + data.getCustomStat(CIALLO_COOLDOWN_REDUCTION) * yuzusoftFanMultiplier(data)
                + sumOwnedStat(data, COOLDOWN_REDUCTION);
        if (data.isAegisEnabled(AegisConstants.HARMONY)) {
            total += aegisStat(AegisConstants.HARMONY, COOLDOWN_REDUCTION)
                    * harmonyScalingFactor(data);
        }
        return total;
    }

    public static double shieldGain(Player player, PlayerPerkData data) {
        return shieldGainExcludingPerk(player, data, null);
    }

    public static double shieldGainExcludingPerk(Player player, PlayerPerkData data,
                                                  String excludedPerkId) {
        int level = AegisExperienceSystem.effectiveLevel(player, data);
        return data.getCustomStat(SHIELD_GAIN)
                + sumOwnedStatExcludingPerk(data, SHIELD_GAIN, excludedPerkId)
                + sumOwnedStatExcludingPerk(data, SHIELD_GAIN_PER_LEVEL, excludedPerkId)
                * level;
    }

    private static double sumOwnedStatExcludingPerk(PlayerPerkData data, String statKey,
                                                     String excludedPerkId) {
        double total = 0.0D;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (isActive(data, perk)
                    && (excludedPerkId == null || !perk.id().equals(excludedPerkId))) {
                total += perk.stat(statKey) * entry.getValue();
            }
        }
        return total;
    }

    public static double shieldGainMultiplier(PlayerPerkData data) {
        double multiplier = 1.0D;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!isActive(data, perk)) {
                continue;
            }
            double value = perk.stat(SHIELD_GAIN_MULTIPLIER);
            if (value > 0.0D) {
                // Ranks of the same talent stack multiplicatively, as do separate talents.
                multiplier *= Math.pow(value, Math.max(1, entry.getValue()));
            }
        }
        return multiplier;
    }

    /** Additive experience-gain bonus used only when Apothic does not own the stat. */
    public static double experienceGainBonus(PlayerPerkData data) {
        return sumOwnedStat(data, EXPERIENCE_GAINED);
    }

    /** Optional mana mods consume this as an additive regeneration multiplier. */
    public static double manaRegenerationMultiplier(PlayerPerkData data) {
        return sumOwnedStat(data, MANA_REGENERATION_MULTIPLIER);
    }

    static double sumOwnedStat(PlayerPerkData data, String statKey) {
        double total = 0.0D;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            if (isActive(data, entry.getKey())) {
                total += entry.getKey().stat(statKey) * entry.getValue();
            }
        }
        return total;
    }

    public static double luckyStrike(Player player, PlayerPerkData data) {
        double value = GeneralServerMethods.getAttributeValue(player, Attributes.LUCK) * 0.1D
                + data.getCustomStat(LUCKY_STRIKE)
                + data.getCustomStat(REVIVE_LUCK)
                + sumOwnedStat(data, LUCKY_STRIKE)
                + data.getActiveSoulLinks().stream()
                .mapToDouble(link -> link.bonusStat(LUCKY_STRIKE))
                .sum();
        boolean uncapped = data.isAegisEnabled(AegisConstants.STELLAR)
                && aegisStat(AegisConstants.STELLAR, AegisConstants.UNCAPPED_LUCKY_STRIKE)
                > 0.0D;
        if (data.isAegisEnabled(AegisConstants.STELLAR)) {
            value += aegisStat(AegisConstants.STELLAR, LUCKY_STRIKE);
            value += aegisStat(
                    AegisConstants.STELLAR,
                    AegisConstants.LUCKY_STRIKE_PER_SOUL_LINK
            ) * data.getActiveSoulLinks().size();
        }
        value = Math.max(0.0D, value);
        double cap = Aegis.byId(AegisConstants.STELLAR)
                .filter(aegis -> aegis.stats().containsKey(AegisConstants.LUCKY_STRIKE_CAP))
                .map(aegis -> Math.max(0.0D, aegis.stat(AegisConstants.LUCKY_STRIKE_CAP)))
                .orElse(3.0D);
        return uncapped ? value : Math.min(cap, value);
    }

    public static double luckyStrikeMultiplier(Player player, PlayerPerkData data) {
        return 1.0D + luckyStrike(player, data);
    }

    static boolean isActive(PlayerPerkData data, Perk perk) {
        return !perk.manuallyToggleable() || data.isTalentEnabled(perk.id());
    }

    static double criticalChance(PlayerPerkData data) {
        double chance = data.getCustomStat(CRITICAL_CHANCE)
                + skillEnhancementCustomStat(data, CRITICAL_CHANCE)
                + allSkillEnhancementCustomStatBonus(data, CRITICAL_CHANCE)
                + primaryCustomStatBonus(data, CRITICAL_CHANCE);
        chance += PerfectAndElegantServant.criticalChance(data);
        boolean ignoreNegatives = data.owns(PERK_LAW_OF_THE_CYCLE)
                && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.id().equals(PERK_BLAZING_FEATHER_STARWEAVER)
                    && (!perk.manuallyToggleable() || data.isTalentEnabled(perk.id()))) {
                double contribution = perk.stat(CRITICAL_CHANCE) * entry.getValue();
                boolean koharuNegated = perk.id().equals(PERK_KOHARUS_BLESSING)
                        && MakeUpWorkClub.negatesKoharuPenalty(data);
                if (contribution >= 0.0D) {
                    chance += contribution;
                } else if (ignoreNegatives && MadokaWithHomura.isActive(data)) {
                    chance += MadokaWithHomura.convertPercentagePenalty(
                            data, contribution
                    );
                } else if (!ignoreNegatives && !koharuNegated) {
                    chance += contribution;
                }
            }
        }
        chance += data.getActiveSoulLinks().stream()
                .mapToDouble(link -> link.bonusStat(CRITICAL_CHANCE))
                .sum();
        if (data.owns(PERK_BLAZING_FEATHER_STARWEAVER)) {
            chance += stat(PERK_BLAZING_FEATHER_STARWEAVER, CRITICAL_CHANCE);
        }
        if (data.isAegisEnabled(AegisConstants.FLAME)) {
            chance += aegisStat(AegisConstants.FLAME, CRITICAL_CHANCE);
        }
        return chance;
    }

    static double flameCriticalDamage(PlayerPerkData data,
                                              double totalCriticalChance) {
        return Math.max(0.0D, totalCriticalChance)
                * flameCriticalDamagePerCriticalChance(data);
    }

    static double flameCriticalDamagePerCriticalChance(PlayerPerkData data) {
        return data.isAegisEnabled(AegisConstants.FLAME)
                ? aegisStat(
                        AegisConstants.FLAME,
                        AegisConstants.CRITICAL_DAMAGE_PER_CRITICAL_CHANCE
                )
                : 0.0D;
    }

    static double rawDamageResistance(PlayerPerkData data) {
        double value = sumOwnedStat(data, DAMAGE_REDUCTION)
                + data.getCustomStat(DAMAGE_REDUCTION)
                - data.getCustomStat(FROSTBITE_DAMAGE_TAKEN);
        if (data.isAegisEnabled(AegisConstants.DESTRUCTION)) {
            value += aegisStat(AegisConstants.DESTRUCTION, DAMAGE_REDUCTION);
        }
        return value;
    }

    static double damageResistance(PlayerPerkData data) {
        boolean lawOfCycle = data.owns(PERK_LAW_OF_THE_CYCLE)
                && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
        if (!lawOfCycle) {
            return rawDamageResistance(data);
        }

        double value = 0.0D;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            double contribution = entry.getKey().stat(DAMAGE_REDUCTION)
                    * entry.getValue();
            value += contribution >= 0.0D
                    ? contribution
                    : MadokaWithHomura.convertDamageReductionPenalty(data, contribution);
        }
        double accumulated = data.getCustomStat(DAMAGE_REDUCTION);
        value += accumulated >= 0.0D
                ? accumulated
                : MadokaWithHomura.convertDamageReductionPenalty(data, accumulated);
        value += MadokaWithHomura.convertDamageReductionPenalty(
                data, data.getCustomStat(FROSTBITE_DAMAGE_TAKEN)
        );
        if (data.isAegisEnabled(AegisConstants.DESTRUCTION)) {
            double destruction = aegisStat(AegisConstants.DESTRUCTION, DAMAGE_REDUCTION);
            value += destruction >= 0.0D
                    ? destruction
                    : MadokaWithHomura.convertDamageReductionPenalty(data, destruction);
        }
        return value;
    }

    static double effectiveDamageResistance(PlayerPerkData data) {
        return Math.min(
                ServerSettings.get().maximumEffectiveDamageReduction(),
                damageResistance(data)
        );
    }

    static double harmonyScalingFactor(PlayerPerkData data) {
        if (!data.isAegisEnabled(AegisConstants.HARMONY)) {
            return 0.0D;
        }
        long r = data.getPerkRanks().keySet().stream()
                .filter(perk -> perk.tier() == Perk.Tier.R).count();
        long sr = data.getPerkRanks().keySet().stream()
                .filter(perk -> perk.tier() == Perk.Tier.SR).count();
        long ssr = data.getPerkRanks().keySet().stream()
                .filter(perk -> perk.tier() == Perk.Tier.SSR).count();
        return 1.0D
                + r * aegisStat(AegisConstants.HARMONY, AegisConstants.PERK_R_TALENT_SCALING)
                + sr * aegisStat(AegisConstants.HARMONY, AegisConstants.PERK_SR_TALENT_SCALING)
                + ssr * aegisStat(AegisConstants.HARMONY, AegisConstants.PERK_SSR_TALENT_SCALING);
    }

    static double criticalDamageBonus(PlayerPerkData data) {
        double bonus = data.getCustomStat(CRITICAL_DAMAGE)
                + data.getCustomStat(GARDENER_CRITICAL_DAMAGE)
                + data.getCustomStat(INNATE_CRITICAL_DAMAGE)
                + data.getCustomStat(TOP_CRITICAL_DAMAGE)
                + skillEnhancementCustomStat(data, CRITICAL_DAMAGE)
                + allSkillEnhancementCustomStatBonus(data, CRITICAL_DAMAGE)
                + primaryCustomStatBonus(data, CRITICAL_DAMAGE);
        bonus += PerfectAndElegantServant.criticalDamage(data);
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.manuallyToggleable() || data.isTalentEnabled(perk.id())) {
                bonus += perk.stat(CRITICAL_DAMAGE) * entry.getValue();
            }
        }
        bonus += data.getActiveSoulLinks().stream()
                .mapToDouble(link -> link.bonusStat(CRITICAL_DAMAGE))
                .sum();
        return bonus;
    }

    private static double skillEnhancementCustomStat(PlayerPerkData data, String statKey) {
        double value = 0.0D;
        for (Map.Entry<SkillEnhancement, Integer> entry
                : data.getSkillEnhancementRanks().entrySet()) {
            if (entry.getKey().customStat().filter(statKey::equals).isPresent()) {
                value += entry.getKey().amount() * entry.getValue();
            }
        }
        return value;
    }

    private static double allSkillEnhancementCustomStatBonus(PlayerPerkData data,
                                                              String statKey) {
        boolean affected = SkillEnhancement.values().stream().anyMatch(enhancement ->
                enhancement.affectedByAllSkillEnhancementAttribute()
                        && enhancement.customStat().filter(statKey::equals).isPresent()
        );
        return affected ? allSkillEnhancementAttribute(data) : 0.0D;
    }

    private static double allSkillEnhancementAttribute(PlayerPerkData data) {
        double accumulated = data.getCustomStat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE);
        boolean lawOfCycle = data.owns(PERK_LAW_OF_THE_CYCLE)
                && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
        double value = accumulated < 0.0D && lawOfCycle
                ? MadokaWithHomura.convertPercentagePenalty(data, accumulated)
                : accumulated;
        value += TeamRadiance.allSkillEnhancementBonus(data);
        value += VirtualItems.statBonus(
                data,
                VirtualItems.ALL_SKILL_ENHANCEMENT_ATTRIBUTE
        );
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            if (isActive(data, entry.getKey())) {
                value += entry.getKey().stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE)
                        * entry.getValue();
            }
        }
        if (data.isAegisEnabled(AegisConstants.HARMONY)) {
            double harmonyValue = aegisStat(
                    AegisConstants.HARMONY,
                    ALL_SKILL_ENHANCEMENT_ATTRIBUTE
            );
            value += harmonyValue * harmonyScalingFactor(data);
        }
        if (data.isChallengePenaltyActive()) {
            value -= 0.25D;
        }
        return value;
    }

    public static double talentOptionBonus(Player player, PlayerPerkData data) {
        double value = data.getCustomStat(OFFER_BONUS)
                + TeamRadiance.talentOptionBonus(data);
        if (data.owns(PERK_FLOWER_FAIRY)) {
            double flowerFairy = stat(PERK_FLOWER_FAIRY, TALENT_OPTION_BONUS);
            boolean lawOfCycle = data.owns(PERK_LAW_OF_THE_CYCLE)
                    && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
            if (flowerFairy >= 0.0D || !lawOfCycle) {
                value += flowerFairy;
            } else {
                value += MadokaWithHomura.convertFlatPenalty(data, flowerFairy);
            }
        }
        return value;
    }

    private static double primaryCustomStatBonus(PlayerPerkData data, String statKey) {
        if (!data.hasChosenPrimarySkillEnhancement()) {
            return 0.0D;
        }
        SkillEnhancement primary = data.getPrimarySkillEnhancement();
        if (primary.customStat().filter(statKey::equals).isEmpty()) {
            return 0.0D;
        }
        return totalPrimaryAttributeFlat(data) * primary.amount()
                + primaryAttributeMultiplier(data);
    }

    private static double totalPrimaryAttributeFlat(PlayerPerkData data) {
        return data.getCustomStat(PRIMARY_FLAT)
                + Arona.effectiveAccumulatedPrimaryStat(data)
                + AuthorityAegis.effectiveAccumulatedPrimaryStat(data)
                + data.getCustomStat(MAGICIAN_PRIMARY_ATTRIBUTE_FLAT)
                + VirtualItems.bonus(data, VirtualItems.Effect.PRIMARY_STAT)
                + VirtualItems.statBonus(data, VirtualItems.PRIMARY_ATTRIBUTE_FLAT);
    }

    static double magicianPrimaryAttributeFlat(Player player, PlayerPerkData data) {
        if (!data.hasActiveSoulLink(SOUL_MAGICIAN_MASTER_AND_APPRENTICE)) {
            return 0.0D;
        }
        double manaPerStep = bonusStat(
                SOUL_MAGICIAN_MASTER_AND_APPRENTICE,
                FRIEREN_MAX_MANA_PER_STEP
        );
        if (manaPerStep <= 0.0D) {
            return 0.0D;
        }
        double primaryPerStep = bonusStat(
                SOUL_MAGICIAN_MASTER_AND_APPRENTICE,
                FRIEREN_PRIMARY_ATTRIBUTE_FLAT_PER_STEP
        );
        return Math.floor(ManaCompat.maximumMana(player, data) / manaPerStep)
                * primaryPerStep;
    }

    private static double primaryAttributeMultiplier(PlayerPerkData data) {
        double value = data.getCustomStat(PRIMARY_ATTRIBUTE_MULTIPLIER);
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            if (isActive(data, entry.getKey())) {
                value += entry.getKey().stat(PRIMARY_ATTRIBUTE_MULTIPLIER)
                        * entry.getValue();
            }
        }
        return value;
    }

    static double millenniumOverflowCriticalDamage(PlayerPerkData data,
                                                            double totalCriticalChance) {
        if (!data.hasActiveSoulLink(SOUL_MILLENNIUM_ECHO)) {
            return 0.0D;
        }
        double step = bonusStat(
                SOUL_MILLENNIUM_ECHO, OVERFLOW_CRITICAL_CHANCE_STEP
        );
        if (step <= 0.0D) {
            return 0.0D;
        }
        double damagePerStep = bonusStat(
                SOUL_MILLENNIUM_ECHO, CRITICAL_DAMAGE_PER_OVERFLOW_STEP
        );
        double overflow = Math.max(0.0D, totalCriticalChance - 1.0D);
        double steps = Math.floor((overflow + 1.0E-9D) / step);
        return steps * damagePerStep;
    }

    static Perk requiredPerk(String perkId) {
        return Perk.byId(perkId).orElseThrow(() ->
                new IllegalStateException("Missing configured perk: " + perkId));
    }

    static double stat(String perkId, String statKey) {
        return requiredPerk(perkId).stat(statKey);
    }

    static int integerStat(Perk perk, String statKey) {
        return (int) Math.round(perk.stat(statKey));
    }

    static double bonusStat(String soulLinkId, String statKey) {
        return Perk.soulLinkById(soulLinkId)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing configured combination: " + soulLinkId
                ))
                .bonusStat(statKey);
    }

    static double yuzusoftFanMultiplier(PlayerPerkData data) {
        SoulLink soulLink = Perk.soulLinkById(SOUL_YUZUSOFT_FAN_LEVEL)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing configured combination: " + SOUL_YUZUSOFT_FAN_LEVEL
                ));
        return soulLink.rank(data) <= 0 ? 0.0D : 1.0D + soulLink.rankBonus(data);
    }

    static double aegisStat(String aegisId, String statKey) {
        return Aegis.byId(aegisId)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing configured Aegis: " + aegisId
                ))
                .stat(statKey);
    }

    private static double firstNonZero(Perk perk, String... keys) {
        for (String key : keys) {
            double value = perk.stat(key);
            if (Math.abs(value) >= 1.0E-9D) {
                return value;
            }
        }
        return 0.0D;
    }

    private static final class AttributeTotals {
        private double attackMultiplier;
        private double primaryMultiplier;
        private double healthFlat;
        private double healthMultiplier;
        private double movementMultiplier;
        private double attackSpeedMultiplier;
        private double attackSpeedFlat;
        private double luckFlat;
        private double fixedMaxHealth = -1.0D;
    }
}
