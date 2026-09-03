package com.whatever.aegis_ascension.client.screen.acg;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.COLLECTOR_EFFECT_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.DAMAGE_REDUCTION_CONVERSION_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.FLAT_PENALTY_CONVERSION_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.GREAT_FAIRY_EFFECT_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.PERCENTAGE_PENALTY_CONVERSION_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_MADOKA_WITH_HOMURA;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_MAKE_UP_WORK_CLUB;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_MISTY_LAKE;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.SOUL_TEAM_RADIANCE;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_ALL_SKILL_RANK;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_BREAKTHROUGH_RANK;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_FINAL_DAMAGE_RANK;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_RANK;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.TEAM_RADIANCE_TALENT_OPTION_RANK;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Breakdown;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Definition;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Format;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.perk.talents.FairTrade;
import com.whatever.aegis_ascension.util.DodgeMath;
import com.whatever.aegis_ascension.util.GoldScalingMath;
import com.whatever.aegis_ascension.util.StatAttribution;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-source attribution for a Custom Stat: which owned Perk, active Soul Link, enabled
 * Aegis, or chosen Skill Enhancement contributed how much to a given stat key, plus the
 * hover-panel renderer that displays that list.
 *
 * <p>This contains the {@code statSources}/{@code renderSourceTooltip} machinery used by
 * {@link com.whatever.aegis_ascension.client.screen.ACGPerkSelectionScreen}'s
 * {@code PLAYER_CUSTOM_STAT} mode can offer the same source drill-down on hover that the
 * legacy collection screen already had. The attribution rules themselves
 * (which perk/soul-link/aegis affects which stat, and any perk-specific special cases)
 * are unchanged from the original; only the class they live in and the rendering entry
 * point are new.</p>
 */
public final class ACGStatSourceBreakdown {
    private ACGStatSourceBreakdown() {
    }

    public record Source(Component name, ResourceLocation icon, int iconTextureSize,
                         Component value, int color, double rawValue) {
    }

    // ------------------------------------------------------------------
    // Data: which sources feed a given stat key.
    // ------------------------------------------------------------------

    public static List<Source> sources(String statKey) {
        List<Source> sources = new ArrayList<>();
        if (ATTACK_DAMAGE.equals(statKey)) {
            for (Perk perk : ClientPerkState.getOwnedPerks()) {
                if (perk.manuallyToggleable() && !ClientPerkState.isTalentEnabled(perk)) {
                    continue;
                }
                int rank = ClientPerkState.getRank(perk);
                double multiplier = perk.stat(ATTACK_MULTIPLIER) * rank;
                if (isPrimaryStatTarget(ATTACK_DAMAGE)) {
                    multiplier += perk.stat(PRIMARY_ATTRIBUTE_MULTIPLIER) * rank;
                }
                if (perk.id().equals(PERK_GREAT_FAIRY)) {
                    multiplier += perk.stat(ATTACK_MULTIPLIER_PER_OWNED_TALENT)
                            * ClientPerkState.getUsedTalentSlots()
                            * mistyLakeGreatFairyMultiplier();
                }
                if (perk.id().equals(PERK_SHIROKO)) {
                    int chargesPerStack = Math.max(1, (int) Math.round(perk.stat(
                            UNSPENT_SKILL_ENHANCEMENT_CHARGES_PER_STACK
                    )));
                    int stacks = ClientPerkState.getSkillEnhancementCharges()
                            / chargesPerStack;
                    multiplier += stacks * perk.stat(ATTACK_DAMAGE_PER_STACK) * rank;
                    multiplier += goldBonus(perk, ATTACK_DAMAGE_PER_GOLD_STACK,
                            ATTACK_DAMAGE_GOLD_CAP) * rank;
                }
                addSource(sources, perk.title(), perk.iconTexture(), 28,
                        multiplier, Format.PERCENT);
            }

            Perk.soulLinkById(SOUL_COMBO_TECHNIQUE)
                    .filter(ClientPerkState::isSoulLinkActive)
                    .ifPresent(link -> addSource(
                            sources,
                            link.title(),
                            link.iconTexture(),
                            28,
                            link.bonusStat(ATTACK_MULTIPLIER),
                            Format.PERCENT
                    ));

            addYuzusoftAccumulatedSources(
                    sources,
                    PERK_YOSHINO_CIALLO,
                    CIALLO_ATTACK_MULTIPLIER,
                    Format.PERCENT
            );

            Aegis.byId(AegisConstants.BLESSING)
                    .filter(ClientPerkState::isAegisEnabled)
                    .ifPresent(aegis -> addSource(
                            sources,
                            aegis.title(),
                            aegis.iconTexture(),
                            128,
                            ClientPerkState.getDisplayStat(AEGIS_ATTACK_MULTIPLIER),
                            Format.PERCENT
                    ));

        }

        if (ARMOR.equals(statKey) && isPrimaryStatTarget(ARMOR)) {
            for (Perk perk : ClientPerkState.getOwnedPerks()) {
                if (perk.manuallyToggleable() && !ClientPerkState.isTalentEnabled(perk)) {
                    continue;
                }
                addSource(
                        sources,
                        perk.title(),
                        perk.iconTexture(),
                        28,
                        perk.stat(PRIMARY_ATTRIBUTE_MULTIPLIER)
                                * ClientPerkState.getRank(perk),
                        Format.PERCENT
                );
            }
        }

        if (ATTACK_SPEED.equals(statKey)) {
            for (Perk perk : ClientPerkState.getOwnedPerks()) {
                if (perk.manuallyToggleable() && !ClientPerkState.isTalentEnabled(perk)) {
                    continue;
                }
                int rank = ClientPerkState.getRank(perk);
                addSource(
                        sources,
                        perk.title(),
                        perk.iconTexture(),
                        28,
                        perk.stat(ATTACK_SPEED_FLAT) * rank,
                        Format.NUMBER
                );
                double percentage = perk.stat(ATTACK_SPEED_MULTIPLIER) * rank;
                if (isPrimaryStatTarget(ATTACK_SPEED)) {
                    percentage += perk.stat(PRIMARY_ATTRIBUTE_MULTIPLIER) * rank;
                }
                addSource(
                        sources,
                        perk.title(),
                        perk.iconTexture(),
                        28,
                        percentage,
                        Format.PERCENT
                );
            }
            for (SoulLink link : Perk.soulLinks()) {
                if (ClientPerkState.isSoulLinkActive(link)) {
                    addSource(
                            sources,
                            link.title(),
                            link.iconTexture(),
                            28,
                            link.bonusStat(ATTACK_SPEED_MULTIPLIER),
                            Format.PERCENT
                    );
                }
            }
            for (Aegis aegis : Aegis.values()) {
                if (ClientPerkState.isAegisEnabled(aegis)) {
                    addSource(
                            sources,
                            aegis.title(),
                            aegis.iconTexture(),
                            128,
                            aegis.stat(ATTACK_SPEED_MULTIPLIER),
                            Format.PERCENT
                    );
                }
            }
            Aegis.byId(AegisConstants.BLESSING)
                    .filter(ClientPerkState::isAegisEnabled)
                    .ifPresent(aegis -> addSource(
                            sources,
                            aegis.title(),
                            aegis.iconTexture(),
                            128,
                            ClientPerkState.getDisplayStat(AEGIS_ATTACK_SPEED_MULTIPLIER),
                            Format.PERCENT
                    ));
            addCustomPerkSource(
                    sources,
                    PERK_RIGHTEOUS_KNIGHT,
                    KNIGHT_ATTACK_SPEED_FLAT,
                    Format.NUMBER
            );
        }

        if (!ATTACK_DAMAGE.equals(statKey)
                && !ARMOR.equals(statKey)
                && !ATTACK_SPEED.equals(statKey)) {
            Format format = statFormat(statKey);
            for (Perk perk : ClientPerkState.getOwnedPerks()) {
                if (perk.manuallyToggleable() && !ClientPerkState.isTalentEnabled(perk)) {
                    continue;
                }
                double value = perkContribution(statKey, perk)
                        * ClientPerkState.getRank(perk);
                addSource(sources, perk.title(), perk.iconTexture(), 28, value, format);
            }
            for (SoulLink link : Perk.soulLinks()) {
                if (!ClientPerkState.isSoulLinkActive(link)) {
                    continue;
                }
                addSource(
                        sources,
                        link.title(),
                        link.iconTexture(),
                        28,
                        soulLinkContribution(statKey, link),
                        format
                );
            }
            for (Aegis aegis : Aegis.values()) {
                if (!ClientPerkState.isAegisEnabled(aegis)) {
                    continue;
                }
                addSource(
                        sources,
                        aegis.title(),
                        aegis.iconTexture(),
                        128,
                        aegisContribution(statKey, aegis),
                        format
                );
            }
            addKnownAccumulatedSources(sources, statKey, format);
        }

        addAllSkillEnhancementAttributeSources(sources, statKey);
        addMysteriousDollRewardSources(sources, statKey);
        addPrimaryFlatSource(sources, statKey);

        String enhancementId = ATTACK_DAMAGE.equals(statKey) ? "attack_damage"
                : ARMOR.equals(statKey) ? "armor"
                : ATTACK_SPEED.equals(statKey) ? "attack_speed" : "";
        SkillEnhancement.byId(enhancementId).ifPresent(enhancement -> {
            int rank = ClientPerkState.getSkillEnhancementRank(enhancement);
            if (rank > 0) {
                addSource(
                        sources,
                        enhancement.title(),
                        enhancement.iconTexture(),
                        enhancement.iconTextureSize(),
                        enhancement.amount() * rank,
                        enhancement.operation()
                                == AttributeOperation.ADDITION
                                ? Format.NUMBER : Format.PERCENT
                );
            }
        });
        if (!ATTACK_DAMAGE.equals(statKey)
                && !ARMOR.equals(statKey)
                && !ATTACK_SPEED.equals(statKey)) {
            ClientPerkState.getSkillEnhancementRanks().forEach((enhancement, rank) ->
                    enhancement.customStat()
                            .filter(statKey::equals)
                            .ifPresent(ignored -> addSource(
                                    sources,
                                    enhancement.title(),
                                    enhancement.iconTexture(),
                                    enhancement.iconTextureSize(),
                                    enhancement.amount() * rank,
                                    enhancement.operation()
                                            == AttributeOperation.ADDITION
                                            ? Format.NUMBER : Format.PERCENT
                            ))
            );
        }

        // Runs before the remainder below so attributed gains shrink it rather
        // than being double-counted alongside it.
        addAccumulatedSources(sources, statKey);

        Definition definition = CustomStats.definition(statKey);
        if (definition == null) {
            return sources;
        }
        if (definition.attributeBacked()) {
            // A live Minecraft attribute. The rows above are this mod's multipliers,
            // which share no unit with the attribute's absolute value, so no remainder
            // can be inferred here. What the server does state exactly is the half of
            // the value that belongs to nothing in this mod.
            Breakdown breakdown = CustomStats.breakdown(definition);
            addSource(
                    sources,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.other_flat_source"
                    ),
                    definition.icon(),
                    28,
                    breakdown.otherFlat(),
                    Format.NUMBER
            );
            addSource(
                    sources,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.other_percentage_source"
                    ),
                    definition.icon(),
                    28,
                    breakdown.otherPercentage(),
                    Format.PERCENT
            );
            return sources;
        }
        // Every contributor is one of ours and shares the stat's own unit, so whatever
        // the listed rows do not account for is still an unattributed mod source.
        double listed = sources.stream().mapToDouble(Source::rawValue).sum();
        double remainder = ClientPerkState.getDisplayStat(statKey) - listed;
        if (Math.abs(remainder) > 1.0E-7D) {
            addSource(
                    sources,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.other_sources"
                    ),
                    definition.icon(),
                    28,
                    remainder,
                    definition.format()
            );
        }
        return sources;
    }

    /**
     * Lists gradual gains against the talent, Aegis, or Soul Link that granted them.
     *
     * <p>A Breakthrough bonus or a Shrine Maiden Dance penalty is folded into one
     * shared custom stat, so previously only the total survived and the panel could
     * report it solely as an unattributed remainder. The server now also records each
     * grant under its source, and this reads those records back.</p>
     *
     * <p>The records are display bookkeeping: gameplay reads the shared stat, so a
     * record that is missing just falls back into the remainder exactly as before.
     * Source ids come from the catalog JSON, so a talent the server owner renamed or
     * deleted can leave one behind; rather than dropping the value it is listed as
     * coming from a talent that is no longer installed.</p>
     */
    private static void addAccumulatedSources(List<Source> sources, String statKey) {
        String prefix = StatAttribution.SYNCED_PREFIX;
        String suffix = StatAttribution.SEPARATOR + statKey;
        List<Map.Entry<String, Double>> records = ClientPerkState.getDisplayStats()
                .entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix)
                        && entry.getKey().endsWith(suffix))
                .toList();
        for (Map.Entry<String, Double> record : records) {
            String key = record.getKey();
            String sourceId = key.substring(
                    prefix.length(), key.length() - suffix.length()
            );
            if (sourceId.isEmpty()
                    || sourceId.indexOf(StatAttribution.SEPARATOR) >= 0) {
                continue;
            }
            double value = record.getValue();
            Perk perk = Perk.byId(sourceId).orElse(null);
            if (perk != null) {
                addAccumulatedSource(sources, perk.title(), perk.iconTexture(),
                        28, value, statKey);
                continue;
            }
            Aegis aegis = Aegis.byId(sourceId).orElse(null);
            if (aegis != null) {
                addAccumulatedSource(sources, aegis.title(), aegis.iconTexture(),
                        128, value, statKey);
                continue;
            }
            SoulLink soulLink = Perk.soulLinkById(sourceId).orElse(null);
            if (soulLink != null) {
                addAccumulatedSource(sources, soulLink.title(), soulLink.iconTexture(),
                        28, value, statKey);
                continue;
            }
            addSource(
                    sources,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.removed_source"
                    ),
                    null,
                    28,
                    value,
                    statFormat(statKey)
            );
        }
    }

    private static void addAccumulatedSource(List<Source> sources, Component name,
                                             ResourceLocation icon, int iconTextureSize,
                                             double value, String statKey) {
        addSource(
                sources,
                getTranslatableString(
                        "screen.aegis_ascension.collection.stat.accumulated_source", name
                ),
                icon,
                iconTextureSize,
                value,
                statFormat(statKey)
        );
    }

    private static Format statFormat(String statKey) {
        return CustomStats.definitions().stream()
                .filter(definition -> definition.key().equals(statKey))
                .map(Definition::format)
                .findFirst()
                .orElse(Format.NUMBER);
    }

    private static double perkContribution(String statKey, Perk perk) {
        double value = perk.stat(statKey);
        if (isPrimaryStatTarget(statKey)) {
            value += perk.stat(PRIMARY_ATTRIBUTE_MULTIPLIER);
        }
        if (BREAKTHROUGH_EFFECT.equals(statKey)) {
            return perk.stat(BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS);
        }
        if (HEALTH_REGENERATION.equals(statKey)) {
            return perk.stat(HEALTH_RESTORE_PER_SECOND);
        }
        if (MANA_REGENERATION.equals(statKey)) {
            return perk.stat(MANA_RESTORE_PER_SECOND)
                    + perk.stat(MANA_REGENERATION_MULTIPLIER);
        }
        if (LUCK.equals(statKey)) {
            return perk.stat(LUCK_FLAT);
        }
        if (ATTACK_RANGE.equals(statKey)) {
            return perk.stat(ATTACK_RANGE_FLAT)
                    + perk.stat(ATTACK_RANGE_PER_SUMMON)
                    * ClientPerkState.getDisplayStat(SUMMON_COUNT);
        }
        if (SUMMON_POWER.equals(statKey)) {
            return value + perk.stat(SUMMON_POWER_PER_SUMMON)
                    * ClientPerkState.getDisplayStat(SUMMON_COUNT);
        }
        if (FINAL_DAMAGE.equals(statKey)) {
            if (perk.id().equals(PERK_KOKONA)) {
                value += perk.stat(FINAL_DAMAGE_PER_OWNED_TALENT)
                        * ClientPerkState.getUsedTalentSlots();
            }
            if (perk.id().equals(PERK_FIREFLY_FLAME)
                    && ClientPerkState.getDisplayStat(LUCKY_STRIKE)
                    > perk.stat(LUCKY_STRIKE_THRESHOLD)) {
                value += perk.stat(FINAL_DAMAGE_ABOVE_THRESHOLD);
            }
            if (perk.id().equals(PERK_PERFECT_AND_ELEGANT_SERVANT)) {
                value += ClientPerkState.getDisplayStat(
                        "__custom." + PERFECTION_STACKS
                ) * perk.stat(PERFECTION_FINAL_DAMAGE_PER_STACK);
            }
            if (perk.id().equals(PERK_PECORINES_BLESSING)) {
                value += ClientPerkState.getDisplayStat(
                        "__custom." + PECORINE_ACTIVE_FINAL_DAMAGE
                );
            }
        }
        if (CRITICAL_CHANCE.equals(statKey)
                && perk.id().equals(PERK_PERFECT_AND_ELEGANT_SERVANT)) {
            value += ClientPerkState.getDisplayStat(
                    "__custom." + PERFECTION_STACKS
            ) * perk.stat(PERFECTION_CRITICAL_CHANCE_PER_STACK);
        }
        if (CRITICAL_DAMAGE.equals(statKey)
                && perk.id().equals(PERK_PERFECT_AND_ELEGANT_SERVANT)) {
            value += ClientPerkState.getDisplayStat(
                    "__custom." + PERFECTION_STACKS
            ) * perk.stat(PERFECTION_CRITICAL_DAMAGE_PER_STACK);
        }
        if (PHYSICAL_DAMAGE_AMPLIFICATION.equals(statKey)
                && perk.id().equals(PERK_COLLECTOR)) {
            value += perk.stat(PHYSICAL_DAMAGE_AMPLIFICATION_PER_SOUL_LINK)
                    * activeSoulLinkCount() * makeUpCollectorMultiplier();
        }
        if (MAGIC_DAMAGE_AMPLIFICATION.equals(statKey)
                && perk.id().equals(PERK_COLLECTOR)) {
            value += perk.stat(MAGIC_DAMAGE_AMPLIFICATION_PER_SOUL_LINK)
                    * activeSoulLinkCount() * makeUpCollectorMultiplier();
        }
        if (SKILL_DAMAGE.equals(statKey)) {
            if (perk.id().equals(PERK_CLEAR_MIND_STATE)) {
                // The two halves rebuild the uncapped Dodge Chance the server
                // converts; the shown Dodge Chance itself is already capped.
                value += DodgeMath.skillDamage(
                        ClientPerkState.getDisplayStat(
                                DISPLAY_PERCENT_PREFIX + DODGE_CHANCE)
                                + ClientPerkState.getDisplayStat(
                                        DISPLAY_OTHER_PERCENT_PREFIX + DODGE_CHANCE),
                        perk.stat(DODGE_CHANCE_STEP),
                        perk.stat(SKILL_DAMAGE_PER_DODGE_CHANCE_STEP)
                );
            }
            if (perk.id().equals(PERK_ROLLING_IN_WEALTH)) {
                value += goldBonus(perk, SKILL_DAMAGE_PER_GOLD_STACK,
                        SKILL_DAMAGE_GOLD_CAP);
            }
            if (perk.id().equals(PERK_METEOR_SPARKLE)) {
                value += ClientPerkState.getDisplayStat(LUCKY_STRIKE)
                        * perk.stat(SKILL_DAMAGE_PER_LUCKY_STRIKE);
            }
            if (perk.id().equals(PERK_GREAT_FAIRY)) {
                value += perk.stat(SKILL_DAMAGE_PER_OWNED_TALENT)
                        * ClientPerkState.getUsedTalentSlots()
                        * mistyLakeGreatFairyMultiplier();
            }
        }
        if (value < 0.0D && ClientPerkState.owns(PERK_LAW_OF_THE_CYCLE)) {
            if (ClientPerkState.isSoulLinkActive(requiredSoulLink(
                    SOUL_MADOKA_WITH_HOMURA
            ))) {
                String conversionKey = DAMAGE_REDUCTION.equals(statKey)
                        ? DAMAGE_REDUCTION_CONVERSION_MULTIPLIER
                        : TALENT_OPTION_BONUS.equals(statKey)
                        ? FLAT_PENALTY_CONVERSION_MULTIPLIER
                        : PERCENTAGE_PENALTY_CONVERSION_MULTIPLIER;
                value = Math.abs(value) * requiredSoulLink(
                        SOUL_MADOKA_WITH_HOMURA
                ).bonusStat(conversionKey);
            } else {
                value = 0.0D;
            }
        } else if (CRITICAL_CHANCE.equals(statKey)
                && perk.id().equals(PERK_KOHARUS_BLESSING)
                && ClientPerkState.isSoulLinkActive(requiredSoulLink(
                SOUL_MAKE_UP_WORK_CLUB
        ))) {
            value = 0.0D;
        }
        return value;
    }

    private static boolean isPrimaryStatTarget(String statKey) {
        if (!ClientPerkState.hasChosenPrimarySkillEnhancement()) {
            return false;
        }
        SkillEnhancement primary = ClientPerkState.getPrimarySkillEnhancement();
        if (primary.customStat().filter(statKey::equals).isPresent()) {
            return true;
        }
        return switch (primary.id()) {
            case "attack_damage" -> ATTACK_DAMAGE.equals(statKey);
            case "armor" -> ARMOR.equals(statKey);
            case "attack_speed" -> ATTACK_SPEED.equals(statKey);
            default -> false;
        };
    }

    private static void addAllSkillEnhancementAttributeSources(
            List<Source> sources,
            String statKey) {
        boolean affected = SkillEnhancement.values().stream().anyMatch(enhancement ->
                enhancement.affectedByAllSkillEnhancementAttribute()
                        && (enhancement.id().equals(statKey)
                        || enhancement.customStat().filter(statKey::equals).isPresent())
        );
        if (!affected) {
            return;
        }
        for (Perk perk : ClientPerkState.getOwnedPerks()) {
            if (perk.manuallyToggleable() && !ClientPerkState.isTalentEnabled(perk)) {
                continue;
            }
            addSource(
                    sources,
                    perk.title(),
                    perk.iconTexture(),
                    28,
                    perk.stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE)
                            * ClientPerkState.getRank(perk),
                    Format.PERCENT
            );
        }
        Perk.soulLinkById(SOUL_TEAM_RADIANCE)
                .filter(ClientPerkState::isSoulLinkActive)
                .ifPresent(link -> addSource(
                        sources,
                        link.title(),
                        link.iconTexture(),
                        28,
                        soulLinkContribution(ALL_SKILL_ENHANCEMENT_ATTRIBUTE, link),
                        Format.PERCENT
                ));
        Aegis.byId(AegisConstants.HARMONY)
                .filter(ClientPerkState::isAegisEnabled)
                .ifPresent(aegis -> addSource(
                        sources,
                        aegis.title(),
                        aegis.iconTexture(),
                        128,
                        aegis.stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE)
                                * harmonyFactor(aegis),
                        Format.PERCENT
                ));
    }

    private static void addPrimaryFlatSource(List<Source> sources, String statKey) {
        if (!isPrimaryStatTarget(statKey)) {
            return;
        }
        SkillEnhancement primary = ClientPerkState.getPrimarySkillEnhancement();
        double mysteriousRanks = ClientPerkState.getDisplayStat(
                "__custom." + MYSTERIOUS_DOLL_REWARD_SOURCE_PREFIX + PRIMARY_FLAT
        );
        double magicianRanks = ClientPerkState.getDisplayStat(
                "__custom." + MAGICIAN_PRIMARY_ATTRIBUTE_FLAT
        );
        double aronaRanks = ClientPerkState.getDisplayStat(
                "__custom." + ARONA_PRIMARY_FLAT
        );
        var arona = Perk.byId(PERK_ARONA)
                .filter(perk -> ClientPerkState.owns(perk.id()));
        double effectiveAronaRanks = arona
                .map(perk -> aronaRanks
                        * perk.primaryStatMultiplier(primary.id()))
                .orElse(0.0D);
        double authorityRanks = ClientPerkState.getDisplayStat(
                "__custom." + AegisConstants.AUTHORITY_PRIMARY_FLAT
        );
        var authority = Aegis.byId(AegisConstants.AUTHORITY)
                .filter(ClientPerkState::isAegisEnabled);
        double effectiveAuthorityRanks = authority
                .map(aegis -> authorityRanks
                        * aegis.primaryStatMultiplier(primary.id()))
                .orElse(0.0D);
        double value = (ClientPerkState.getDisplayStat(PRIMARY_ATTRIBUTE_FLAT)
                - mysteriousRanks - magicianRanks
                - effectiveAronaRanks - effectiveAuthorityRanks)
                * primary.amount();
        Format format = primary.customStat().isPresent()
                ? statFormat(statKey)
                : primary.operation()
                == AttributeOperation.ADDITION
                ? Format.NUMBER : Format.PERCENT;
        addSource(
                sources,
                getTranslatableString(
                        "screen.aegis_ascension.collection.stat.accumulated_primary"
                ),
                primary.iconTexture(),
                primary.iconTextureSize(),
                value,
                format
        );
        Perk.byId(PERK_MYSTERIOUS_DOLL).ifPresent(perk -> addSource(
                sources,
                perk.title(),
                perk.iconTexture(),
                28,
                mysteriousRanks * primary.amount(),
                format
        ));
        Perk.soulLinkById(SOUL_MAGICIAN_MASTER_AND_APPRENTICE)
                .filter(ClientPerkState::isSoulLinkActive)
                .ifPresent(link -> addSource(
                        sources,
                        link.title(),
                        link.iconTexture(),
                        28,
                        magicianRanks * primary.amount(),
                        format
                ));
        arona.ifPresent(perk -> addSource(
                sources,
                perk.title(),
                perk.iconTexture(),
                28,
                effectiveAronaRanks * primary.amount(),
                format
        ));
        authority.ifPresent(aegis -> addSource(
                        sources,
                        aegis.title(),
                        aegis.iconTexture(),
                        128,
                        effectiveAuthorityRanks * primary.amount(),
                        format
                ));
    }

    private static void addMysteriousDollRewardSources(
            List<Source> sources,
            String statKey) {
        Perk.byId(PERK_MYSTERIOUS_DOLL).ifPresent(perk -> {
            double directValue = ClientPerkState.getDisplayStat(
                    "__custom." + MYSTERIOUS_DOLL_REWARD_SOURCE_PREFIX + statKey
            );
            addSource(
                    sources,
                    perk.title(),
                    perk.iconTexture(),
                    28,
                    directValue,
                    statFormat(statKey)
            );
            if (isPrimaryStatTarget(statKey)) {
                addSource(
                        sources,
                        perk.title(),
                        perk.iconTexture(),
                        28,
                        ClientPerkState.getDisplayStat(
                                "__custom." + MYSTERIOUS_DOLL_REWARD_SOURCE_PREFIX
                                        + PRIMARY_ATTRIBUTE_MULTIPLIER
                        ),
                        Format.PERCENT
                );
            }
        });
    }

    private static long activeSoulLinkCount() {
        return Perk.soulLinks().stream().filter(ClientPerkState::isSoulLinkActive).count();
    }

    private static double soulLinkContribution(String statKey, SoulLink link) {
        if (link.id().equals(SOUL_TEAM_RADIANCE)) {
            int rank = Math.max(0, (int) Math.floor(
                    ClientPerkState.getDisplayStat(TEAM_RADIANCE_RANK)
            ));
            String unlockKey;
            if (TALENT_OPTION_BONUS.equals(statKey)) {
                unlockKey = TEAM_RADIANCE_TALENT_OPTION_RANK;
            } else if (BREAKTHROUGH_EFFECT.equals(statKey)) {
                unlockKey = TEAM_RADIANCE_BREAKTHROUGH_RANK;
            } else if (ALL_SKILL_ENHANCEMENT_ATTRIBUTE.equals(statKey)) {
                unlockKey = TEAM_RADIANCE_ALL_SKILL_RANK;
            } else if (FINAL_DAMAGE.equals(statKey)) {
                unlockKey = TEAM_RADIANCE_FINAL_DAMAGE_RANK;
            } else {
                return 0.0D;
            }
            if (rank < Math.max(1, (int) Math.round(link.bonusStat(unlockKey)))) {
                return 0.0D;
            }
        }
        return BREAKTHROUGH_EFFECT.equals(statKey)
                ? link.bonusStat(BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS)
                : link.bonusStat(statKey);
    }

    private static double mistyLakeGreatFairyMultiplier() {
        SoulLink link = Perk.soulLinkById(SOUL_MISTY_LAKE).orElse(null);
        return link != null && ClientPerkState.isSoulLinkActive(link)
                ? Math.max(0.0D, link.bonusStat(GREAT_FAIRY_EFFECT_MULTIPLIER))
                : 1.0D;
    }

    /**
     * Mirrors the server's Gold-scaling talents, including the gate that switches them
     * off entirely on a vanilla-level server.
     */
    private static double goldBonus(Perk perk, String bonusPerStackKey, String capKey) {
        if (ClientPerkState.usesMinecraftDefaultLevel()) {
            return 0.0D;
        }
        return GoldScalingMath.bonus(
                ClientPerkState.getGoldCurrency(),
                perk.stat(GOLD_PER_STACK),
                perk.stat(bonusPerStackKey),
                perk.stat(capKey)
        );
    }

    private static double makeUpCollectorMultiplier() {
        SoulLink link = Perk.soulLinkById(SOUL_MAKE_UP_WORK_CLUB).orElse(null);
        return link != null && ClientPerkState.isSoulLinkActive(link)
                ? Math.max(0.0D, link.bonusStat(COLLECTOR_EFFECT_MULTIPLIER))
                : 1.0D;
    }

    private static SoulLink requiredSoulLink(String soulLinkId) {
        return Perk.soulLinkById(soulLinkId).orElseThrow(() ->
                new IllegalStateException("Missing configured Soul Link: " + soulLinkId)
        );
    }

    private static double aegisContribution(String statKey, Aegis aegis) {
        if (BREAKTHROUGH_EFFECT.equals(statKey)
                && aegis.id().equals(AegisConstants.FROST_MOON)) {
            return aegis.stat(BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS);
        }
        if (CRITICAL_DAMAGE.equals(statKey)
                && aegis.id().equals(AegisConstants.FLAME)) {
            return ClientPerkState.getDisplayStat(CRITICAL_CHANCE)
                    * aegis.stat(AegisConstants.CRITICAL_DAMAGE_PER_CRITICAL_CHANCE);
        }
        if (LUCKY_STRIKE.equals(statKey)
                && aegis.id().equals(AegisConstants.STELLAR)) {
            return aegis.stat(LUCKY_STRIKE)
                    + aegis.stat(AegisConstants.LUCKY_STRIKE_PER_SOUL_LINK)
                    * activeSoulLinkCount();
        }
        if (LUCK.equals(statKey) && aegis.id().equals(AegisConstants.LUCKY)) {
            return aegis.stat(LUCK_FLAT);
        }
        if (FINAL_DAMAGE.equals(statKey)
                && aegis.id().equals(AegisConstants.HARMONY)) {
            return aegis.stat(FINAL_DAMAGE) * harmonyFactor(aegis);
        }
        if (ALL_SKILL_ENHANCEMENT_ATTRIBUTE.equals(statKey)
                && aegis.id().equals(AegisConstants.HARMONY)) {
            return aegis.stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE)
                    * harmonyFactor(aegis);
        }
        if (FINAL_DAMAGE.equals(statKey)
                && aegis.id().equals(AegisConstants.DESTRUCTION)) {
            return Math.max(0.0D, -ClientPerkState.getDisplayStat(DAMAGE_REDUCTION))
                    * aegis.stat(
                    AegisConstants.FINAL_DAMAGE_PER_NEGATIVE_DAMAGE_REDUCTION
            );
        }
        if (COOLDOWN_REDUCTION.equals(statKey)
                && aegis.id().equals(AegisConstants.HARMONY)) {
            return aegis.stat(COOLDOWN_REDUCTION) * harmonyFactor(aegis);
        }
        if (DAMAGE_REDUCTION.equals(statKey)
                && aegis.id().equals(AegisConstants.DESTRUCTION)) {
            return aegis.stat(DAMAGE_REDUCTION);
        }
        return aegis.stat(statKey);
    }

    private static double harmonyFactor(Aegis aegis) {
        long r = ClientPerkState.getOwnedPerks().stream()
                .filter(perk -> perk.tier() == Perk.Tier.R).count();
        long sr = ClientPerkState.getOwnedPerks().stream()
                .filter(perk -> perk.tier() == Perk.Tier.SR).count();
        long ssr = ClientPerkState.getOwnedPerks().stream()
                .filter(perk -> perk.tier() == Perk.Tier.SSR).count();
        return 1.0D
                + r * aegis.stat(AegisConstants.PERK_R_TALENT_SCALING)
                + sr * aegis.stat(AegisConstants.PERK_SR_TALENT_SCALING)
                + ssr * aegis.stat(AegisConstants.PERK_SSR_TALENT_SCALING);
    }

    private static void addKnownAccumulatedSources(List<Source> sources,
                                                    String statKey,
                                                    Format format) {
        if (DAMAGE_BONUS.equals(statKey)) {
            addCustomPerkSource(sources, PERK_MUNDANE_STROLL, WALK_DAMAGE, format);
            addCustomPerkSource(sources, PERK_I_SHALL_INTERPRET_THE_RADIANCE,
                    FROSTBITE_DAMAGE, format);
            addCustomPerkSource(sources, PERK_INNATE_DREAM, INNATE_DAMAGE, format);
            addCustomPerkSource(sources, PERK_TOP_PLAYER, TOP_DAMAGE, format);
            addCustomPerkSource(
                    sources,
                    PERK_DOMINUS_LAPIDIS,
                    DOMINUS_SHIELD_DAMAGE_BONUS,
                    format
            );
            Perk.byId(PERK_TEAM_STAR).ifPresent(perk -> addSource(
                    sources,
                    perk.title(),
                    perk.iconTexture(),
                    28,
                    ClientPerkState.getDisplayStat(
                            "__custom." + TEAM_DAMAGE_BONUS_ACTIVE
                    ),
                    format
            ));
            if (ClientPerkState.owns(PERK_FAIR_TRADE)) {
                Perk.byId(PERK_FAIR_TRADE).ifPresent(perk -> addSource(
                        sources,
                        perk.title(),
                        perk.iconTexture(),
                        28,
                        FairTrade.damageBonus(ClientPerkState.getDisplayStat(
                                "__custom." + FAIR_TRADE_SUCCESSFUL_TRADES
                        )),
                        format
                ));
            }
        }
        if (ATTACK_DAMAGE_AMPLIFICATION.equals(statKey)) {
            addCustomPerkSource(
                    sources,
                    PERK_RIGHTEOUS_KNIGHT,
                    KNIGHT_DAMAGE,
                    format
            );
        }
        if (BREAKTHROUGH_EFFECT.equals(statKey)) {
            addCustomPerkSource(
                    sources,
                    PERK_SHRINE_MAIDEN_DANCE,
                    BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS,
                    format
            );
        }
        if (SKILL_DAMAGE.equals(statKey)) {
            addCustomPerkSource(sources, PERK_INNATE_DREAM,
                    INNATE_SKILL_DAMAGE, format);
        }
        if (PHYSICAL_DAMAGE_AMPLIFICATION.equals(statKey)
                || MAGIC_DAMAGE_AMPLIFICATION.equals(statKey)) {
            addCustomPerkSource(sources, PERK_LUNAR_GODDESSS_BLESSING, LUNAR_DAMAGE, format);
            addYuzusoftAccumulatedSources(
                    sources,
                    PERK_CONGYU_CIALLO,
                    PHYSICAL_DAMAGE_AMPLIFICATION.equals(statKey)
                            ? CIALLO_PHYSICAL_DAMAGE_AMPLIFICATION
                            : CIALLO_MAGIC_DAMAGE_AMPLIFICATION,
                    format
            );
        }
        if (FINAL_DAMAGE.equals(statKey)) {
            addCustomPerkSource(sources, PERK_BLAZING_FEATHER_STARWEAVER,
                    BLAZING_BREAKTHROUGH_DAMAGE, format);
            addYuzusoftAccumulatedSources(
                    sources,
                    PERK_SHIZURU_CIALLO,
                    CIALLO_FINAL_DAMAGE,
                    format
            );
        }
        if (INDEPENDENT_DAMAGE_AMPLIFICATION.equals(statKey)) {
            addCustomSoulLinkSource(
                    sources,
                    SOUL_LOVE_AS_ETERNAL_AS_THIS_MOMENT,
                    INDEPENDENT_DAMAGE_AMPLIFICATION,
                    format
            );
        }
        if (CRITICAL_DAMAGE.equals(statKey)) {
            addCustomPerkSource(sources, PERK_HALF_HUMAN_HALF_PHANTOM_GARDENER,
                    GARDENER_CRITICAL_DAMAGE, format);
            addCustomPerkSource(sources, PERK_INNATE_DREAM,
                    INNATE_CRITICAL_DAMAGE, format);
            addCustomPerkSource(sources, PERK_TOP_PLAYER, TOP_CRITICAL_DAMAGE, format);
        }
        if (LUCKY_STRIKE.equals(statKey)) {
            addCustomPerkSource(sources, PERK_BOUNDARY_OF_LIFE_AND_DEATH, REVIVE_LUCK, format);
        }
        if (LUCK.equals(statKey)) {
            addCustomPerkSource(sources, PERK_ALICE, ALICE_LUCK, format);
            addYuzusoftAccumulatedSources(
                    sources,
                    PERK_NANAMI_CIALLO,
                    CIALLO_LUCK,
                    format
            );
        }
        if (ALL_SKILL_ENHANCEMENT_ATTRIBUTE.equals(statKey)) {
            addCustomPerkSource(
                    sources,
                    PERK_PLANA,
                    ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                    format
            );
        }
        if (DAMAGE_REDUCTION.equals(statKey)) {
            addCustomPerkSource(sources, PERK_I_SHALL_INTERPRET_THE_RADIANCE,
                    FROSTBITE_DAMAGE_TAKEN, format);
        }
        if (COOLDOWN_REDUCTION.equals(statKey)) {
            addYuzusoftAccumulatedSources(
                    sources,
                    PERK_NINGNING_CIALLO,
                    CIALLO_COOLDOWN_REDUCTION,
                    format
            );
        }
    }

    private static void addYuzusoftAccumulatedSources(
            List<Source> sources,
            String perkId,
            String customStatKey,
            Format format) {
        SoulLink soulLink = Perk.soulLinkById(SOUL_YUZUSOFT_FAN_LEVEL)
                .orElse(null);
        if (soulLink == null || soulLink.rank(ClientPerkState::owns) <= 0) {
            return;
        }
        double accumulated = ClientPerkState.getDisplayStat(
                "__custom." + customStatKey
        );
        Perk.byId(perkId).ifPresent(perk -> addSource(
                sources,
                perk.title(),
                perk.iconTexture(),
                28,
                accumulated,
                format
        ));
        addSource(
                sources,
                soulLink.title(),
                soulLink.iconTexture(),
                28,
                accumulated * soulLink.rankBonus(ClientPerkState::owns),
                format
        );
    }

    private static void addCustomPerkSource(List<Source> sources, String perkId,
                                            String customStatKey, Format format) {
        Perk.byId(perkId).ifPresent(perk -> addSource(
                sources,
                perk.title(),
                perk.iconTexture(),
                28,
                ClientPerkState.getDisplayStat("__custom." + customStatKey),
                format
        ));
    }

    private static void addCustomSoulLinkSource(
            List<Source> sources,
            String soulLinkId,
            String customStatKey,
            Format format) {
        Perk.soulLinkById(soulLinkId).ifPresent(soulLink -> addSource(
                sources,
                soulLink.title(),
                soulLink.iconTexture(),
                28,
                ClientPerkState.getDisplayStat("__custom." + customStatKey),
                format
        ));
    }

    private static void addSource(List<Source> sources, Component name,
                                  ResourceLocation icon, int iconTextureSize,
                                  double value, Format format) {
        if (Math.abs(value) <= 1.0E-9D) {
            return;
        }
        String formatted = (value > 0.0D ? "+" : "") + format.format(value);
        sources.add(new Source(
                name,
                icon,
                iconTextureSize,
                getLiteralString(formatted),
                value >= 0.0D ? 0xFF72E39A : 0xFFE07A7A,
                value
        ));
    }

    // ------------------------------------------------------------------
    // Rendering: the hover panel itself, ported from
    // Custom-stat source tooltip/rendering helpers.
    // ------------------------------------------------------------------

    /**
     * Draws the source-attribution hover panel and returns the (possibly clamped) scroll
     * offset the caller should keep for next frame. Callers own the {@code scroll} field;
     * this method never mutates caller state directly so it stays a pure function of its
     * arguments, matching the rest of this class.
     */
    public static int renderPanel(GuiGraphics graphics, Font font, Component statTitle,
                                  Component statValue, List<Source> sources,
                                  int mouseX, int mouseY, int screenWidth, int screenHeight,
                                  int scroll) {
        int panelWidth = Math.min(300, Math.max(190, screenWidth - 24));
        int visibleCount = visibleCount(sources.size(), screenHeight);
        int maximumScroll = maximumScroll(sources.size(), screenHeight);
        int clampedScroll = Math.max(0, Math.min(scroll, maximumScroll));
        int footerHeight = maximumScroll > 0 ? 15 : 5;
        int panelHeight = 28 + visibleCount * 22 + footerHeight;
        int x = mouseX + 12;
        if (x + panelWidth > screenWidth - 6) {
            x = mouseX - panelWidth - 12;
        }
        x = Math.max(6, Math.min(x, screenWidth - panelWidth - 6));
        int y = Math.max(6, Math.min(mouseY - 10, screenHeight - panelHeight - 6));

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF0101018);
        graphics.fill(x, y, x + panelWidth, y + 1, 0xFFE0E0E0);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight,
                0xFFE0E0E0);
        graphics.fill(x, y, x + 1, y + panelHeight, 0xFFE0E0E0);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight,
                0xFFE0E0E0);
        graphics.drawString(
                font,
                getTranslatableString(
                        "screen.aegis_ascension.collection.stat.source_header",
                        statTitle,
                        statValue
                ),
                x + 7,
                y + 7,
                0xFFFFFFFF,
                false
        );

        if (sources.isEmpty()) {
            graphics.drawString(
                    font,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.no_mod_sources"
                    ),
                    x + 7,
                    y + 31,
                    0xFFAAAAAA,
                    false
            );
        } else {
            int end = Math.min(sources.size(), clampedScroll + visibleCount);
            for (int index = clampedScroll; index < end; index++) {
                Source source = sources.get(index);
                int rowY = y + 27 + (index - clampedScroll) * 22;
                renderIcon(graphics, source, x + 7, rowY + 2);
                int valueWidth = font.width(source.value());
                int nameWidth = Math.max(30, panelWidth - valueWidth - 40);
                String sourceName = font.plainSubstrByWidth(
                        source.name().getString(),
                        nameWidth
                );
                graphics.drawString(font, sourceName, x + 28, rowY + 6,
                        0xFFE0E0E0, false);
                graphics.drawString(font, source.value(), x + panelWidth - valueWidth - 7,
                        rowY + 6, source.color(), false);
            }
        }

        if (maximumScroll > 0) {
            int first = clampedScroll + 1;
            int last = Math.min(sources.size(), clampedScroll + visibleCount);
            graphics.drawCenteredString(
                    font,
                    getTranslatableString(
                            "screen.aegis_ascension.collection.stat.scroll_sources",
                            first,
                            last,
                            sources.size()
                    ),
                    x + panelWidth / 2,
                    y + panelHeight - 12,
                    0xFFAAAAAA
            );
        }
        return clampedScroll;
    }

    private static void renderIcon(GuiGraphics graphics, Source source, int x, int y) {
        if (source.icon() == null) {
            // A source with no icon still deserves its row; a contribution left behind
            // by a talent that is no longer installed has nothing to draw.
            return;
        }
        int textureSize = source.iconTextureSize();
        float scale = 16.0F / textureSize;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(
                source.icon(), 0, 0, 0.0F, 0.0F,
                textureSize, textureSize, textureSize, textureSize
        );
        graphics.pose().popPose();
    }

    /** Mouse-wheel step for the panel: one row per notch, clamped to non-negative. */
    public static int adjustScroll(int scroll, double wheelDelta) {
        return Math.max(0, scroll + (wheelDelta < 0.0D ? 1 : -1));
    }

    /**
     * Whether this many sources need scrolling to be read at this screen height.
     *
     * <p>The screen asks before it swallows a scroll event on a hovered stat: a panel that
     * already shows everything has nothing to scroll, and eating the wheel there would
     * leave the grid behind it unscrollable for no gain.</p>
     */
    public static boolean isScrollable(int sourceCount, int screenHeight) {
        return maximumScroll(sourceCount, screenHeight) > 0;
    }

    /** Rows the panel can show at this screen height, ignoring how many it has. */
    private static int maximumVisible(int screenHeight) {
        return Math.max(1, Math.min(9, (screenHeight - 58) / 22));
    }

    private static int visibleCount(int sourceCount, int screenHeight) {
        return Math.min(maximumVisible(screenHeight), Math.max(1, sourceCount));
    }

    private static int maximumScroll(int sourceCount, int screenHeight) {
        return Math.max(0, sourceCount - visibleCount(sourceCount, screenHeight));
    }
}
