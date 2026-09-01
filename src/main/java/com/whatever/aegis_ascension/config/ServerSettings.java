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

    @SerializedName("true_damage_affected_by_critical_damage")
    private Boolean trueDamageAffectedByCriticalDamage = true;

    @SerializedName("true_damage_affected_by_lucky_strike")
    private Boolean trueDamageAffectedByLuckyStrike = true;

    @SerializedName("true_damage_affected_by_final_damage")
    private Boolean trueDamageAffectedByFinalDamage = true;

    @SerializedName("true_damage_affected_by_royal_sacred_flame")
    private Boolean trueDamageAffectedByRoyalSacredFlame = true;

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
