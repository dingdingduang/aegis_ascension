package com.whatever.aegis_ascension.client.screen.collectiontabs;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Data model for the Talent Collection's Custom Stats tab.
 *
 * <p>Live Minecraft attributes receive an exact server-calculated split. Other
 * custom stats currently have one native unit: percentage stats use the
 * percentage component, while counts and ordinary numbers use the flat
 * component. Keeping that rule here makes every card expose the same three
 * values without pretending that an unsupported modifier exists.</p>
 */
public final class CustomStats {
    private static final List<Definition> DEFINITIONS = List.of(
            skillStat(ATTACK_DAMAGE, "attack_damage", Format.NUMBER),
            skillStat(ARMOR, "armor", Format.NUMBER),
            skillStat(ATTACK_SPEED, "attack_speed", Format.NUMBER),
            stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE, PERK_NOELLE, Format.PERCENT),
            stat(BREAKTHROUGH_EFFECT, PERK_PLANA, Format.PERCENT),
            stat(FINAL_DAMAGE, PERK_KOKONA, Format.PERCENT),
            soulStat(
                    INDEPENDENT_DAMAGE_AMPLIFICATION,
                    SOUL_LOVE_AS_ETERNAL_AS_THIS_MOMENT,
                    Format.PERCENT
            ),
            stat(DAMAGE_BONUS, PERK_BECAUSE_YOU_EXIST_AS_I_WRITE, Format.PERCENT),
            stat(ATTACK_DAMAGE_AMPLIFICATION, PERK_RIGHTEOUS_KNIGHT, Format.PERCENT),
            stat(MAGIC_DAMAGE, PERK_MAGIC_BLADE, Format.PERCENT),
            stat(PHYSICAL_DAMAGE_AMPLIFICATION, PERK_FLICKERING_LIGHT, Format.PERCENT),
            stat(MAGIC_DAMAGE_AMPLIFICATION, PERK_MASTER_SPARK, Format.PERCENT),
            stat(SKILL_DAMAGE, PERK_SKILL_DAMAGE_CONVERSION, Format.PERCENT),
            stat(TRUE_DAMAGE, PERK_SEVEN_COLORED_MAGICIAN, Format.PERCENT),
            stat(HEALTH_REGENERATION, PERK_HEALING_MAGIC, Format.PERCENT),
            stat(MANA_REGENERATION, PERK_MAGIC_BLADE, Format.PERCENT),
            stat(HEALING_POWER, PERK_BLESSING_OF_THE_WORLD_TREE, Format.PERCENT),
            stat(SUMMON_POWER, PERK_NOELLE, Format.PERCENT),
            stat(SUMMON_COUNT, PERK_PLATEAU_WITCH, Format.INTEGER),
            stat(CRITICAL_CHANCE, PERK_INK_DYED_SAKURA, Format.PERCENT),
            stat(CRITICAL_DAMAGE, PERK_HALF_HUMAN_HALF_PHANTOM_GARDENER, Format.PERCENT),
            stat(LUCK, PERK_ALICE, Format.NUMBER),
            stat(LUCKY_STRIKE, PERK_LUCKY_ARROW, Format.PERCENT),
            stat(PRIMARY_ATTRIBUTE_FLAT, PERK_ARONA, Format.NUMBER),
            stat(ATTACK_RANGE, PERK_WIND_ARROW, Format.NUMBER),
            stat(COOLDOWN_REDUCTION, PERK_NINGNING_CIALLO, Format.PERCENT),
            stat(INDEPENDENT_SKILL_DAMAGE, PERK_HAYASE_YUKA, Format.PERCENT),
            stat(INDEPENDENT_SKILL_AREA, PERK_OTOGI_NOAH, Format.PERCENT),
            stat(DAMAGE_REDUCTION, PERK_BEATER, Format.PERCENT),
            stat(SHIELD_GAIN, PERK_KOHARU_SPRITE, Format.PERCENT),
            stat(REVIVES_REMAINING, PERK_BOUNDARY_OF_LIFE_AND_DEATH, Format.INTEGER),
            stat(TALENT_OPTION_BONUS, PERK_FLOWER_FAIRY, Format.INTEGER),
            aegisStat(AegisConstants.BARRAGE_MISSILE_SPEED,
                    AegisConstants.ARCANE, Format.PERCENT),
            aegisStat(AegisConstants.BARRAGE_DAMAGE,
                    AegisConstants.ARCANE, Format.PERCENT),
            aegisStat(AegisConstants.BARRAGE_AREA,
                    AegisConstants.ARCANE, Format.PERCENT)
    );

    private CustomStats() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static List<TalentCollectionCard> cards() {
        return DEFINITIONS.stream().map(definition -> {
            Breakdown breakdown = breakdown(definition);
            double value = breakdown.finalValue();
            Component valueText = getLiteralString(
                    breakdown.finalText(definition)
            );
            Component tooltip = getTranslatableString(definition.translationKey())
                    .append("\n\n")
                    .append(getTranslatableString(definition.descriptionKey()))
                    .append("\n\n")
                    .append(getTranslatableString(
                            "screen.aegis_ascension.collection.stat.flat_value",
                            breakdown.flatText()
                    ))
                    .append("\n")
                    .append(getTranslatableString(
                            "screen.aegis_ascension.collection.stat.percentage_value",
                            breakdown.percentageText()
                    ))
                    .append("\n")
                    .append(getTranslatableString(
                            "screen.aegis_ascension.collection.stat.final_value",
                            valueText
                    ));
            int valueColor = value > 1.0E-9D
                    ? 0xFF72E39A
                    : value < -1.0E-9D ? 0xFFE07A7A : 0xFFAAAAAA;
            return new TalentCollectionCard(
                    definition.icon(),
                    28,
                    getTranslatableString(definition.translationKey()),
                    getTranslatableString(definition.descriptionKey()),
                    valueText,
                    tooltip,
                    0xFF55C7E8,
                    valueColor,
                    true,
                    null,
                    null,
                    null,
                    definition.key()
            );
        }).toList();
    }

    public static Definition definition(String key) {
        return DEFINITIONS.stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    public static Format format(String key) {
        Definition definition = definition(key);
        return definition == null ? Format.NUMBER : definition.format();
    }

    public static Breakdown breakdown(Definition definition) {
        String flatKey = DISPLAY_FLAT_PREFIX + definition.key();
        String percentageKey = DISPLAY_PERCENT_PREFIX + definition.key();
        double finalValue = ClientPerkState.getDisplayStat(definition.key());
        if (ClientPerkState.hasDisplayStat(flatKey)
                || ClientPerkState.hasDisplayStat(percentageKey)) {
            return new Breakdown(
                    ClientPerkState.getDisplayStat(flatKey),
                    ClientPerkState.getDisplayStat(percentageKey),
                    finalValue
            );
        }
        if (definition.format().percentageBased()) {
            return new Breakdown(0.0D, finalValue, finalValue);
        }
        return new Breakdown(finalValue, 0.0D, finalValue);
    }

    private static Definition stat(String key, String iconPerkId, Format format) {
        ResourceLocation icon = Perk.byId(iconPerkId)
                .map(Perk::iconTexture)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing stat icon talent: " + iconPerkId
                ));
        return new Definition(key, icon, format);
    }

    private static Definition skillStat(String key, String enhancementId, Format format) {
        ResourceLocation icon = SkillEnhancement.byId(enhancementId)
                .map(SkillEnhancement::iconTexture)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing stat icon enhancement: " + enhancementId
                ));
        return new Definition(key, icon, format);
    }

    private static Definition aegisStat(String key, String aegisId, Format format) {
        ResourceLocation icon = Aegis.byId(aegisId)
                .map(Aegis::iconTexture)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing stat icon Aegis: " + aegisId
                ));
        return new Definition(key, icon, format);
    }

    private static Definition soulStat(String key, String soulLinkId, Format format) {
        ResourceLocation icon = Perk.soulLinkById(soulLinkId)
                .map(SoulLink::iconTexture)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing stat icon Soul Link: " + soulLinkId
                ));
        return new Definition(key, icon, format);
    }

    public enum Format {
        PERCENT(true) {
            @Override
            public String format(double value) {
                return compact(value * 100.0D) + "%";
            }
        },
        PERCENT_PER_SECOND(true) {
            @Override
            public String format(double value) {
                return compact(value * 100.0D) + "%/s";
            }
        },
        NUMBER(false) {
            @Override
            public String format(double value) {
                return compact(value);
            }
        },
        INTEGER(false) {
            @Override
            public String format(double value) {
                return Long.toString(Math.round(value));
            }
        };

        private final boolean percentageBased;

        Format(boolean percentageBased) {
            this.percentageBased = percentageBased;
        }

        public boolean percentageBased() {
            return percentageBased;
        }

        public abstract String format(double value);

        private static String compact(double value) {
            if (Math.abs(value) < 0.0000005D) {
                value = 0.0D;
            }
            return BigDecimal.valueOf(value)
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
    }

    public record Definition(String key, ResourceLocation icon, Format format) {
        public String translationKey() {
            return "screen.aegis_ascension.collection.stat." + key;
        }

        public String descriptionKey() {
            return translationKey() + ".description";
        }
    }

    public record Breakdown(double flat, double percentage, double finalValue) {
        public String flatText() {
            return Format.NUMBER.format(flat);
        }

        public String percentageText() {
            return Format.PERCENT.format(percentage);
        }

        public String finalText(Definition definition) {
            return definition.format().format(finalValue);
        }
    }
}
