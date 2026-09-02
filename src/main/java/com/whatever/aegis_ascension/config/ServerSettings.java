package com.whatever.aegis_ascension.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * General server-authoritative gameplay settings loaded from
 * {@code config/aegis_ascension/serversetting.json}.
 *
 * <p>The bundled JSON is copied on first run and becomes the editable source of truth,
 * following the same copy-then-read pattern as {@code aegises.json}. This class is the
 * home for settings that apply across mechanics rather than belonging to one talent or
 * Aegis.</p>
 */
public final class ServerSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("serversetting.json");

    private static ServerSettings instance;

    @SerializedName("primary_stat_multipliers")
    private Map<String, Double> primaryStatMultipliers = defaultPrimaryStatMultipliers();

    @SerializedName("maximum_effective_damage_reduction")
    private Double maximumEffectiveDamageReduction = 0.70D;

    @SerializedName("maximum_effective_dodge_chance")
    private Double maximumEffectiveDodgeChance = 0.70D;

    @SerializedName("true_damage_affected_by_critical_damage")
    private Boolean trueDamageAffectedByCriticalDamage = true;

    @SerializedName("true_damage_affected_by_lucky_strike")
    private Boolean trueDamageAffectedByLuckyStrike = true;

    @SerializedName("true_damage_affected_by_final_damage")
    private Boolean trueDamageAffectedByFinalDamage = true;

    @SerializedName("true_damage_affected_by_royal_sacred_flame")
    private Boolean trueDamageAffectedByRoyalSacredFlame = true;

    @SerializedName("true_damage_affected_by_skill_damage")
    private Boolean trueDamageAffectedBySkillDamage = true;

    @SerializedName("true_damage_affected_by_damage_bonus")
    private Boolean trueDamageAffectedByDamageBonus = false;

    @SerializedName("true_damage_affected_by_physical_amplification")
    private Boolean trueDamageAffectedByPhysicalAmplification = false;

    @SerializedName("true_damage_affected_by_magic_amplification")
    private Boolean trueDamageAffectedByMagicAmplification = false;

    @SerializedName("true_damage_affected_by_attack_amplification")
    private Boolean trueDamageAffectedByAttackAmplification = false;

    @SerializedName("true_damage_affected_by_distance_bonus")
    private Boolean trueDamageAffectedByDistanceBonus = false;

    @SerializedName("outcome_banner_fade_in_ticks")
    private Integer outcomeBannerFadeInTicks = 10;

    @SerializedName("outcome_banner_stay_ticks")
    private Integer outcomeBannerStayTicks = 70;

    @SerializedName("outcome_banner_fade_out_ticks")
    private Integer outcomeBannerFadeOutTicks = 20;

    private ServerSettings() {
    }

    public static ServerSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Drops the cached settings so the next {@link #get()} reads the file again. */
    public static void reload() {
        instance = null;
    }

    /**
     * Fallback multiplier for shield grants whose source does not define its own
     * {@code primary_stat_multipliers} map. An unlisted attribute uses the JSON
     * {@code default} entry, then {@code 1.0}.
     */
    public double primaryStatMultiplier(String primaryStatId) {
        Double specific = primaryStatMultipliers.get(primaryStatId);
        if (specific != null) {
            return specific;
        }
        return primaryStatMultipliers.getOrDefault("default", 1.0D);
    }

    public Map<String, Double> primaryStatMultipliers() {
        return Map.copyOf(primaryStatMultipliers);
    }

    /**
     * Maximum positive Damage Reduction used by incoming-damage calculation.
     * Accumulated Damage Reduction remains uncapped so later penalties can still
     * offset bonuses that exceed this effective ceiling.
     */
    public double maximumEffectiveDamageReduction() {
        return maximumEffectiveDamageReduction;
    }

    /**
     * Maximum Dodge Chance this mod's own dodge roll may use. Accumulated Dodge Chance
     * remains uncapped so Clear Mind State still converts the discarded part, and so a
     * later penalty can offset a bonus that exceeds this ceiling.
     *
     * <p>Ignored while Apothic Attributes owns the {@code dodge_chance} attribute: the
     * roll is then that mod's, and this mod only contributes its share of the chance.</p>
     */
    public double maximumEffectiveDodgeChance() {
        return maximumEffectiveDodgeChance;
    }

    /** Whether a successful critical hit multiplies converted True Damage. */
    public boolean trueDamageAffectedByCriticalDamage() {
        return !Boolean.FALSE.equals(trueDamageAffectedByCriticalDamage);
    }

    /** Whether the attacker's Lucky Strike multiplier applies to True Damage. */
    public boolean trueDamageAffectedByLuckyStrike() {
        return !Boolean.FALSE.equals(trueDamageAffectedByLuckyStrike);
    }

    /** Whether the attacker's Final Damage bucket applies to True Damage. */
    public boolean trueDamageAffectedByFinalDamage() {
        return !Boolean.FALSE.equals(trueDamageAffectedByFinalDamage);
    }

    /** Whether Seven-Colored Magician's Royal Sacred Flame can multiply True Damage. */
    public boolean trueDamageAffectedByRoyalSacredFlame() {
        return !Boolean.FALSE.equals(trueDamageAffectedByRoyalSacredFlame);
    }

    /**
     * Whether Skill Damage still applies once a spell is converted to True Damage.
     *
     * <p>Conversion replaces the whole outgoing pipeline, so without this a caster who
     * converts loses the one stat their build scales on.</p>
     */
    public boolean trueDamageAffectedBySkillDamage() {
        return !Boolean.FALSE.equals(trueDamageAffectedBySkillDamage);
    }

    /**
     * The remaining outgoing multipliers, off by default: conversion has always dropped
     * these, so enabling one is an opt-in balance change rather than a bug fix. Each is
     * gated on the same damage type as the normal path, so converted melee never starts
     * scaling on a spell-only bucket.
     */
    public boolean trueDamageAffectedByDamageBonus() {
        return Boolean.TRUE.equals(trueDamageAffectedByDamageBonus);
    }

    public boolean trueDamageAffectedByPhysicalAmplification() {
        return Boolean.TRUE.equals(trueDamageAffectedByPhysicalAmplification);
    }

    public boolean trueDamageAffectedByMagicAmplification() {
        return Boolean.TRUE.equals(trueDamageAffectedByMagicAmplification);
    }

    public boolean trueDamageAffectedByAttackAmplification() {
        return Boolean.TRUE.equals(trueDamageAffectedByAttackAmplification);
    }

    public boolean trueDamageAffectedByDistanceBonus() {
        return Boolean.TRUE.equals(trueDamageAffectedByDistanceBonus);
    }

    /**
     * Timing for the fading banner the gacha talents show the player who rolled. Shared
     * by every such talent, so the catalogs stay pure outcome lists. 20 ticks = 1 second.
     */
    public int outcomeBannerFadeInTicks() {
        return clampTicks(outcomeBannerFadeInTicks, 10);
    }

    public int outcomeBannerStayTicks() {
        return clampTicks(outcomeBannerStayTicks, 70);
    }

    public int outcomeBannerFadeOutTicks() {
        return clampTicks(outcomeBannerFadeOutTicks, 20);
    }

    private static int clampTicks(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, Math.min(12_000, value));
    }

    private static ServerSettings load() {
        ServerSettings settings = new ServerSettings();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = ServerSettings.class.getResourceAsStream(
                        "/assets/aegis_ascension/serversetting.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/serversetting.json"
                        );
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                ServerSettings loaded = GSON.fromJson(reader, ServerSettings.class);
                if (loaded != null) {
                    settings = loaded;
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error(
                    "Failed to read {}, using built-in server defaults",
                    FILE,
                    exception
            );
            settings = new ServerSettings();
        }
        settings.sanitize();
        return settings;
    }

    private void sanitize() {
        Map<String, Double> sanitized = new LinkedHashMap<>();
        if (primaryStatMultipliers != null) {
            primaryStatMultipliers.forEach((id, multiplier) -> {
                if (id != null && !id.isBlank() && multiplier != null
                        && Double.isFinite(multiplier) && multiplier >= 0.0D) {
                    sanitized.put(id, multiplier);
                } else {
                    AegisAscensionMod.getLogger().warn(
                            "Ignoring invalid shield Primary Attribute multiplier {}={} in {}",
                            id,
                            multiplier,
                            FILE
                    );
                }
            });
        }
        sanitized.putIfAbsent("default", 1.0D);
        primaryStatMultipliers = sanitized;

        if (maximumEffectiveDamageReduction == null
                || !Double.isFinite(maximumEffectiveDamageReduction)) {
            AegisAscensionMod.getLogger().warn(
                    "Invalid maximum effective Damage Reduction {} in {}; using 0.7",
                    maximumEffectiveDamageReduction,
                    FILE
            );
            maximumEffectiveDamageReduction = 0.70D;
        } else {
            maximumEffectiveDamageReduction = Math.max(
                    0.0D,
                    Math.min(1.0D, maximumEffectiveDamageReduction)
            );
        }

        if (maximumEffectiveDodgeChance == null
                || !Double.isFinite(maximumEffectiveDodgeChance)) {
            AegisAscensionMod.getLogger().warn(
                    "Invalid maximum effective Dodge Chance {} in {}; using 0.7",
                    maximumEffectiveDodgeChance,
                    FILE
            );
            maximumEffectiveDodgeChance = 0.70D;
        } else {
            maximumEffectiveDodgeChance = Math.max(
                    0.0D,
                    Math.min(1.0D, maximumEffectiveDodgeChance)
            );
        }

        if (trueDamageAffectedByCriticalDamage == null) {
            trueDamageAffectedByCriticalDamage = true;
        }
        if (trueDamageAffectedByLuckyStrike == null) {
            trueDamageAffectedByLuckyStrike = true;
        }
        if (trueDamageAffectedByFinalDamage == null) {
            trueDamageAffectedByFinalDamage = true;
        }
        if (trueDamageAffectedByRoyalSacredFlame == null) {
            trueDamageAffectedByRoyalSacredFlame = true;
        }
    }

    private static Map<String, Double> defaultPrimaryStatMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("attack_damage", 0.5D);
        defaults.put("armor", 0.6D);
        defaults.put("default", 1.0D);
        return defaults;
    }
}
