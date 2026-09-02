package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.mechanic.TalentStatService.*;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.talents.TalentGoldRewards;
import com.whatever.aegis_ascension.perk.talents.MysteriousDoll;
import com.whatever.aegis_ascension.perk.talents.ShrineMaidenDance;
import com.whatever.aegis_ascension.perk.soullink.MistyLake;
import com.whatever.aegis_ascension.perk.soullink.TeamRadiance;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Handles talent acquisition, paid selections, and Breakthrough progression. */
public final class TalentProgressionEffects {
    private TalentProgressionEffects() {
    }

    public static void onTalentAcquired(ServerPlayer player, PlayerPerkData data,
                                        Perk perk, int newRank,
                                        boolean triggerRewardChains) {
        recalculateAttributes(player, data);

        // Driven by the stat rather than by talent id, so a talent grants selection
        // charges on acquisition by declaring selection_charges_granted and nothing else.
        int charges = Math.max(0, integerStat(perk, SELECTION_CHARGES_GRANTED));
        if (charges > 0) {
            data.addSelectionCharges(charges);

//            triggerBreakthroughs(player, data, charges);
        }

        switch (perk.id()) {
            case PERK_YURIZONO_SEIA -> AegisExperienceSystem.grantLevels(
                    player, data, integerStat(perk, IMMEDIATE_LEVEL_GAIN));
            case PERK_BOUNDARY_OF_LIFE_AND_DEATH -> {
                // This is the talent's initial runtime state, not a permanent
                // accumulated reward. Reacquiring it after a progression reset must
                // start from the configured number of uses.
                data.setCustomStat(REVIVES_REMAINING, perk.stat(REVIVE_USES));
                data.setCustomStat(REVIVE_LUCK, 0.0D);
            }
            case PERK_MYSTERIOUS_DOLL -> {
                if (triggerRewardChains) {
                    MysteriousDoll.roll(player, data);
                }
            }
            case PERK_SHRINE_MAIDEN_DANCE -> {
                if (triggerRewardChains) {
                    ShrineMaidenDance.roll(player, data);
                }
            }
            case PERK_GOLDEN_RULE -> TalentGoldRewards.grantImmediate(data, perk);
            case PERK_WORLD_IS_MINE -> data.addSkillEnhancementCharges(Math.max(
                    0,
                    integerStat(perk, SKILL_ENHANCEMENT_CHARGES_GRANTED)
            ));
            case PERK_REINHARDT -> {
                int count = Math.max(0, integerStat(perk, RANDOM_AEGIS_COUNT));
                for (int index = 0; index < count; index++) {
                    Aegis granted = data.grantRandomUnownedAegis(player).orElse(null);
                    if (granted == null) {
                        break;
                    }
                    player.sendSystemMessage(getTranslatableString(
                            "message.aegis_ascension.reinhardt.random_aegis",
                            granted.title()
                    ));
                }
            }
            default -> {
            }
        }

        recalculateAttributes(player, data);
    }

    public static void onTalentSelected(ServerPlayer player, PlayerPerkData data) {
        if (data.owns(PERK_ALICE)) {
            Perk alice = requiredPerk(PERK_ALICE);
            if (GeneralServerMethods.getAttributeValue(player, Attributes.LUCK)
                    > alice.stat(LUCK_THRESHOLD)) {
                int triggerLimit = Math.max(0, integerStat(alice, LUCK_GAIN_TRIGGER_LIMIT));
                int triggers = Math.max(0, Mth.floor(
                        data.getCustomStat(ALICE_LUCK_TRIGGERS)
                ));
                if (triggers < triggerLimit) {
                    data.addCustomStat(ALICE_LUCK, alice.stat(LUCK_FLAT_IF_ABOVE));
                    data.setCustomStat(ALICE_LUCK_TRIGGERS, triggers + 1.0D);
                    recalculateAttributes(player, data);
                }
            } else if (data.getCustomStat(ALICE_OFFER_BONUS) == 0.0D) {
                double optionBonus = alice.stat(TALENT_OPTION_BONUS_IF_BELOW);
                data.setCustomStat(ALICE_OFFER_BONUS, optionBonus);
                data.addCustomStat(OFFER_BONUS, optionBonus);
            }
        }
    }

    public static void triggerBreakthroughs(ServerPlayer player, PlayerPerkData data, int count) {
        for (int index = 0; index < count; index++) {
            triggerBreakthrough(player, data);
            if (data.owns(PERK_RIPPLES_OF_THE_PAST)
                    && player.getRandom().nextDouble() < Mth.clamp(
                    stat(PERK_RIPPLES_OF_THE_PAST, ADDITIONAL_BREAKTHROUGH_CHANCE),
                    0.0D,
                    1.0D
            )) {
                // Authority of Time may add exactly one Breakthrough for each original
                // trigger. The added Breakthrough does not roll Authority again.
                triggerBreakthrough(player, data);
            }
        }
    }

    private static void triggerBreakthrough(ServerPlayer player, PlayerPerkData data) {
        if (PlatformServices.config().resetTalentRefreshOnBreakthrough()) {
            data.resetPerkRefreshCharges();
        }
        data.grantBreakthroughPerkRefreshCharges();
        data.addCustomStat(BREAKTHROUGH_COUNT, 1.0D);
        data.setCustomStat(BLAZING_REVIVE_USED, 0.0D);
        data.setCustomStat(BLAZING_BREAKTHROUGH_DAMAGE, 0.0D);

        double multiplier = 1.0D
                + sumOwnedStat(data, BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS)
                + data.getCustomStat(BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS)
                + TeamRadiance.breakthroughEffectBonus(data);
        if (data.hasActiveSoulLink(SOUL_MARIPATCHY_GROUP)) {
            multiplier += bonusStat(
                    SOUL_MARIPATCHY_GROUP, BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS
            );
        }
        if (data.isAegisEnabled(AegisConstants.FROST_MOON)) {
            multiplier += aegisStat(
                    AegisConstants.FROST_MOON,
                    BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS
            );
        }
        if (data.owns(PERK_STARLIGHT_INTERTWINED_BENEDICTION)) {
            Perk starlight = requiredPerk(PERK_STARLIGHT_INTERTWINED_BENEDICTION);
            multiplier *= player.getRandom().nextDouble()
                    < starlight.stat(DOUBLE_BREAKTHROUGH_CHANCE)
                    ? starlight.stat(DOUBLE_BREAKTHROUGH_MULTIPLIER)
                    : starlight.stat(TRIPLE_BREAKTHROUGH_MULTIPLIER);
        }
        if (data.owns(PERK_ENIGMA)) {
            Perk enigma = requiredPerk(PERK_ENIGMA);
            boolean triggered = data.isAegisEnabled(AegisConstants.BLISS)
                    || player.getRandom().nextDouble() < Mth.clamp(
                            enigma.stat(ENIGMA_TRIGGER_CHANCE),
                            0.0D,
                            1.0D
                    );
            double enigmaMultiplier = Math.max(0.0D, enigma.stat(
                    triggered
                            ? ENIGMA_TRIGGER_MULTIPLIER
                            : ENIGMA_FAILURE_MULTIPLIER
            ));
            boolean failureNegated = !triggered
                    && data.owns(PERK_LAW_OF_THE_CYCLE)
                    && stat(PERK_LAW_OF_THE_CYCLE, IGNORE_NEGATIVE_EFFECTS) > 0.0D;
            if (failureNegated) {
                enigmaMultiplier = 1.0D;
            }
            multiplier *= enigmaMultiplier;
            if (triggered) {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.enigma.triggered",
                        compact(enigmaMultiplier)
                ));
            } else if (failureNegated) {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.enigma.failure_negated"
                ));
            } else {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.enigma.failed",
                        compact(enigmaMultiplier)
                ));
            }
        }

        addIfOwned(data, PERK_HALF_HUMAN_HALF_PHANTOM_GARDENER,
                GARDENER_CRITICAL_DAMAGE,
                BREAKTHROUGH_CRITICAL_DAMAGE, multiplier);
        addIfOwned(data, PERK_HAYASE_YUKA, INDEPENDENT_SKILL_DAMAGE,
                BREAKTHROUGH_INDEPENDENT_SKILL_DAMAGE, multiplier);
        addIfOwned(data, PERK_OTOGI_NOAH, INDEPENDENT_SKILL_AREA,
                BREAKTHROUGH_INDEPENDENT_SKILL_AREA, multiplier);
        addIfOwned(data, PERK_SKILL_DAMAGE_CONVERSION, TRUE_DAMAGE,
                BREAKTHROUGH_TRUE_DAMAGE, multiplier);
        addIfOwned(data, PERK_NOELLE, SUMMON_POWER, BREAKTHROUGH_SUMMON_POWER, multiplier);
        addIfOwned(data, PERK_KARYLS_BLESSING, MAGIC_DAMAGE_AMPLIFICATION,
                BREAKTHROUGH_MAGIC_DAMAGE_AMPLIFICATION, multiplier);
        addIfOwned(data, PERK_KOKKOROS_BLESSING, PHYSICAL_DAMAGE_AMPLIFICATION,
                BREAKTHROUGH_PHYSICAL_DAMAGE_AMPLIFICATION, multiplier);
        addIfOwned(data, PERK_MASTER_SPARK, TRUE_DAMAGE, BREAKTHROUGH_TRUE_DAMAGE, multiplier);
        addIfOwned(data, PERK_WIND_ARROW, BREAKTHROUGH_ATTACK_RANGE,
                BREAKTHROUGH_ATTACK_RANGE_FLAT, multiplier);
        addIfOwned(data, PERK_SEVEN_COLORED_MAGICIAN, TRUE_DAMAGE,
                BREAKTHROUGH_TRUE_DAMAGE, multiplier);
        addIfOwned(data, PERK_BLESSING_OF_THE_WORLD_TREE, HEALING_POWER,
                BREAKTHROUGH_HEALING_POWER, multiplier);
        addIfOwned(data, PERK_PLATEAU_WITCH, SUMMON_POWER,
                BREAKTHROUGH_SUMMON_POWER, multiplier);

        // Ciallo gains are fixed per Breakthrough and deliberately ignore every global
        // Breakthrough multiplier, including Starlight. Only Yuzusoft Fan Level scales
        // their accumulated totals live.
        addIfOwned(data, PERK_CONGYU_CIALLO, CIALLO_MAX_HEALTH_MULTIPLIER,
                BREAKTHROUGH_MAX_HEALTH_MULTIPLIER, 1.0D);
        addIfOwned(data, PERK_CONGYU_CIALLO, CIALLO_PHYSICAL_DAMAGE_AMPLIFICATION,
                BREAKTHROUGH_PHYSICAL_DAMAGE_AMPLIFICATION, 1.0D);
        addIfOwned(data, PERK_CONGYU_CIALLO, CIALLO_MAGIC_DAMAGE_AMPLIFICATION,
                BREAKTHROUGH_MAGIC_DAMAGE_AMPLIFICATION, 1.0D);
        addIfOwned(data, PERK_YOSHINO_CIALLO, CIALLO_ATTACK_MULTIPLIER,
                BREAKTHROUGH_ATTACK_MULTIPLIER, 1.0D);
        addIfOwned(data, PERK_SHIZURU_CIALLO, CIALLO_FINAL_DAMAGE,
                BREAKTHROUGH_FINAL_DAMAGE, 1.0D);
        addIfOwned(data, PERK_NINGNING_CIALLO, CIALLO_COOLDOWN_REDUCTION,
                BREAKTHROUGH_COOLDOWN_REDUCTION, 1.0D);
        addIfOwned(data, PERK_NANAMI_CIALLO, CIALLO_LUCK,
                BREAKTHROUGH_LUCK_FLAT, 1.0D);

        addIfOwned(data, PERK_ILLUSION_BUBBLE, PRIMARY_FLAT,
                BREAKTHROUGH_PRIMARY_ATTRIBUTE_FLAT, multiplier);
        if (data.owns(PERK_CIRNO)) {
            data.addCustomStat(
                    PRIMARY_FLAT,
                    (stat(PERK_CIRNO, BREAKTHROUGH_PRIMARY_ATTRIBUTE_FLAT)
                            + MistyLake.cirnoPrimaryStatBonus(data)) * multiplier
            );
        }

        if (data.owns(PERK_PLANA)) {
            double allSkillEnhancementGain = stat(
                    PERK_PLANA,
                    BREAKTHROUGH_ALL_SKILL_ENHANCEMENT_ATTRIBUTE
            );
            if (data.hasActiveSoulLink(SOUL_SHITTIM_CHEST)) {
                allSkillEnhancementGain *= 1.0D + bonusStat(
                        SOUL_SHITTIM_CHEST,
                        PLANA_LOGIC_CORRECTION_MULTIPLIER_BONUS
                );
            }
            data.addAttributedCustomStat(
                    PERK_PLANA,
                    ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                    allSkillEnhancementGain * multiplier
            );
        }
        // addIfOwned reads the perk's breakthrough_* stat and writes the attribute it
        // feeds; passing them the other way round reads a stat Zephyr's Care does not
        // declare and banks the result under a key nothing consumes.
        addIfOwned(
                data,
                PERK_ZEPHYRS_CARE,
                ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                BREAKTHROUGH_ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                multiplier
        );

        if (data.owns(PERK_ARONA)) {
            double primaryGain = aronaBreakthroughPrimaryGain(data);
            data.addCustomStat(ARONA_PRIMARY_FLAT, primaryGain * multiplier);
        }
        if (data.owns(PERK_YURIZONO_SEIA)) {
            AegisExperienceSystem.grantLevels(player, data, Math.max(1, (int) Math.round(
                    stat(PERK_YURIZONO_SEIA, BREAKTHROUGH_LEVEL_GAIN) * multiplier
            )));
        }
        if (data.owns(PERK_FLOWER_FAIRY)) {
            // Flower Fairy's Skill Enhancement reward is fixed per Breakthrough and
            // deliberately ignores every Breakthrough multiplier.
            data.addSkillEnhancementCharges(Math.max(0, integerStat(
                    requiredPerk(PERK_FLOWER_FAIRY),
                    BREAKTHROUGH_SKILL_ENHANCEMENT_CHARGES
            )));
        }
        if (data.owns(PERK_BUTTERFLYS_GENTLE_TOUCH)) {
            // Butterfly's reward is deliberately fixed per Breakthrough and does not use
            // the Breakthrough multiplier from Plana, soul links, Aegis, or Starlight.
            data.addSelectionCharges(Math.max(0, integerStat(
                    requiredPerk(PERK_BUTTERFLYS_GENTLE_TOUCH),
                    BREAKTHROUGH_SELECTION_CHARGES
            )));
        }
        if (data.owns(PERK_ZEPHYRS_CARE)) {
            AegisExperienceSystem.grantExperience(player, data, Math.max(1L, Math.round(
                    stat(PERK_ZEPHYRS_CARE, BREAKTHROUGH_EXPERIENCE) * multiplier
            )));
        }
        if (data.owns(PERK_WORLD_IS_MINE)) {
            AegisExperienceSystem.grantExperience(player, data, Math.max(0L, Math.round(
                    stat(PERK_WORLD_IS_MINE, BREAKTHROUGH_EXPERIENCE) * multiplier
            )));
        }
        TalentGoldRewards.grantBreakthrough(player, data, multiplier);
        if (data.owns(PERK_TEACHER_FOX)) {
            data.addCustomStat(TEACHER_HEALTH_MULTIPLIER,
                    stat(PERK_TEACHER_FOX, BREAKTHROUGH_MAX_HEALTH_MULTIPLIER) * multiplier);
        }
        if (data.hasActiveSoulLink(SOUL_LOGISTICS_COMBO)) {
            data.addAttributedCustomStat(SOUL_LOGISTICS_COMBO, INDEPENDENT_SKILL_AREA,
                    bonusStat(
                            SOUL_LOGISTICS_COMBO,
                            BREAKTHROUGH_INDEPENDENT_SKILL_AREA
                    ) * multiplier);
        }
        if (data.hasActiveSoulLink(SOUL_MILLENNIUM_ECHO)) {
            data.addAttributedCustomStat(SOUL_MILLENNIUM_ECHO, CRITICAL_CHANCE,
                    bonusStat(SOUL_MILLENNIUM_ECHO, BREAKTHROUGH_CRITICAL_CHANCE) * multiplier);
        }
        if (data.hasActiveSoulLink(SOUL_TRINITY_TEA_PARTY)) {
            grantTrinitySwissRolls(player, data, multiplier);
        }
        if (data.hasActiveSoulLink(SOUL_LOVE_AS_ETERNAL_AS_THIS_MOMENT)) {
            data.addAttributedCustomStat(
                    SOUL_LOVE_AS_ETERNAL_AS_THIS_MOMENT,
                    INDEPENDENT_DAMAGE_AMPLIFICATION,
                    bonusStat(
                            SOUL_LOVE_AS_ETERNAL_AS_THIS_MOMENT,
                            BREAKTHROUGH_INDEPENDENT_DAMAGE_AMPLIFICATION
                    ) * multiplier);
        }
        if (data.isAegisEnabled(AegisConstants.WATER)) {
            data.addCustomStat(
                    AEGIS_MAX_HEALTH_MULTIPLIER,
                    aegisStat(
                            AegisConstants.WATER,
                            AegisConstants.BREAKTHROUGH_MAX_HEALTH_MULTIPLIER
                    ) * multiplier
            );
        }
        if (data.isAegisEnabled(AegisConstants.HEALING)) {
            data.addAttributedCustomStat(
                    AegisConstants.HEALING,
                    HEALTH_REGENERATION,
                    aegisStat(
                            AegisConstants.HEALING,
                            AegisConstants.BREAKTHROUGH_HEALTH_REGENERATION
                    ) * multiplier
            );
        }
        if (data.isAegisEnabled(AegisConstants.WISDOM)) {
            data.addAttributedCustomStat(
                    AegisConstants.WISDOM,
                    SKILL_DAMAGE,
                    aegisStat(AegisConstants.WISDOM, AegisConstants.BREAKTHROUGH_SKILL_DAMAGE)
                            * multiplier
            );
            data.addCustomStat(
                    AegisConstants.SKILL_AREA,
                    aegisStat(AegisConstants.WISDOM, AegisConstants.BREAKTHROUGH_SKILL_AREA)
                            * multiplier
            );
        }
        if (data.isAegisEnabled(AegisConstants.ARCANE)) {
            // Arcane Aegis (1): each Breakthrough permanently raises barrage missile
            // speed, damage, and area. ArcaneAegis turns these totals into multipliers.
            data.addAttributedCustomStat(
                    AegisConstants.ARCANE,
                    AegisConstants.BARRAGE_MISSILE_SPEED,
                    aegisStat(AegisConstants.ARCANE, AegisConstants.BARRAGE_MISSILE_SPEED)
                            * multiplier
            );
            data.addAttributedCustomStat(
                    AegisConstants.ARCANE,
                    AegisConstants.BARRAGE_DAMAGE,
                    aegisStat(AegisConstants.ARCANE, AegisConstants.BARRAGE_DAMAGE)
                            * multiplier
            );
            data.addAttributedCustomStat(
                    AegisConstants.ARCANE,
                    AegisConstants.BARRAGE_AREA,
                    aegisStat(AegisConstants.ARCANE, AegisConstants.BARRAGE_AREA)
                            * multiplier
            );
        }
        if (data.isAegisEnabled(AegisConstants.BLESSING)) {
            data.addCustomStat(
                    AEGIS_ATTACK_MULTIPLIER,
                    aegisStat(
                            AegisConstants.BLESSING,
                            AegisConstants.BREAKTHROUGH_ATTACK_MULTIPLIER
                    ) * multiplier
            );
            data.addCustomStat(
                    AEGIS_ATTACK_SPEED_MULTIPLIER,
                    aegisStat(
                            AegisConstants.BLESSING,
                            AegisConstants.BREAKTHROUGH_ATTACK_SPEED_MULTIPLIER
                    ) * multiplier
            );
            data.addCustomStat(
                    AEGIS_ATTACK_RANGE,
                    aegisStat(AegisConstants.BLESSING, AegisConstants.BREAKTHROUGH_ATTACK_RANGE)
                            * multiplier
            );
            SummonCompat.refreshOwnedSummons(player, data);
        }
        if (data.isAegisEnabled(AegisConstants.LUCKY)) {
            // Lucky counts the actual Breakthrough trigger exactly once. Its progress
            // deliberately ignores the Breakthrough effect multiplier above, including
            // Starlight's double-or-triple result.
            int interval = Math.max(1, (int) Math.round(aegisStat(
                    AegisConstants.LUCKY,
                    AegisConstants.SOUL_LINK_BREAKTHROUGH_INTERVAL
            )));
            int progress = Math.max(0, Mth.floor(data.addCustomStat(
                    AegisConstants.LUCKY_SOUL_LINK_BREAKTHROUGH_PROGRESS,
                    1.0D
            )));
            if (progress >= interval) {
                data.setCustomStat(
                        AegisConstants.LUCKY_SOUL_LINK_BREAKTHROUGH_PROGRESS,
                        progress % interval
                );
                data.grantRandomInactiveSoulLinkSet(player);
            }
        }

        recalculateAttributes(player, data);
    }

    /**
     * Converts Trinity Tea Party's formerly display-only Swiss Roll counter into real
     * consumables in virtual storage. Swiss Roll Moment rolls once per Breakthrough: its
     * configured branches are mutually exclusive, while any unused probability means no
     * extra roll.
     */
    private static void grantTrinitySwissRolls(ServerPlayer player, PlayerPerkData data,
                                               double breakthroughMultiplier) {
        long amount = Math.max(0L, Math.round(
                bonusStat(SOUL_TRINITY_TEA_PARTY, BREAKTHROUGH_SWISS_ROLLS)
                        * breakthroughMultiplier
        ));
        if (amount <= 0L) {
            return;
        }

        if (data.owns(PERK_SWISS_ROLL_MOMENT)) {
            Perk moment = requiredPerk(PERK_SWISS_ROLL_MOMENT);
            double oneChance = Mth.clamp(moment.stat(ONE_EXTRA_ROLL_CHANCE), 0.0D, 1.0D);
            double twoChance = Mth.clamp(moment.stat(TWO_EXTRA_ROLL_CHANCE), 0.0D, 1.0D);
            double roll = player.getRandom().nextDouble();
            if (roll < oneChance) {
                amount += Math.max(0, integerStat(moment, ONE_EXTRA_ROLL_COUNT));
            } else if (roll < Math.min(1.0D, oneChance + twoChance)) {
                amount += Math.max(0, integerStat(moment, TWO_EXTRA_ROLL_COUNT));
            }
        }

        if (!data.getStorage().addVirtual(VirtualItems.SWISS_ROLL, amount)) {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.trinity_tea_party.storage_full",
                    data.getStorage().getMaxTypes()
            ), false);
            return;
        }

        // Kept as a lifetime-earned counter for Custom Stats/save compatibility; the
        // usable rolls themselves now live in PlayerStorage.
        data.addCustomStat(SWISS_ROLLS, amount);
        player.displayClientMessage(getTranslatableString(
                "message.aegis_ascension.trinity_tea_party.swiss_rolls",
                amount
        ), false);
        ModNetworking.syncStorageTo(player);
    }

    private static double aronaBreakthroughPrimaryGain(PlayerPerkData data) {
        Perk arona = requiredPerk(PERK_ARONA);
        double primaryGain = arona.stat(BREAKTHROUGH_PRIMARY_ATTRIBUTE_FLAT);
        if (data.hasActiveSoulLink(SOUL_SHITTIM_CHEST)) {
            primaryGain += bonusStat(
                    SOUL_SHITTIM_CHEST,
                    ARONA_BREAKTHROUGH_PRIMARY_ATTRIBUTE_FLAT_BONUS
            );
        }
        return primaryGain;
    }

    private static void addIfOwned(PlayerPerkData data, String perkId, String customStat,
                                   String perkStat, double multiplier) {
        if (data.owns(perkId)) {
            data.addAttributedCustomStat(perkId, customStat, stat(perkId, perkStat) * multiplier);
        }
    }
}
