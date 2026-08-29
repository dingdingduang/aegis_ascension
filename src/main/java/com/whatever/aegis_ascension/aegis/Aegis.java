package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.ConfigDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A unique, data-driven Aegis loaded from config/aegis_ascension/aegises.json. */
public final class Aegis {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Catalog CATALOG = loadCatalog();
    private static final List<Aegis> VALUES;
    private static final Map<String, Aegis> BY_ID;

    static {
        List<Aegis> values = new ArrayList<>();
        Map<String, Aegis> byId = new LinkedHashMap<>();
        for (AegisJson definition : CATALOG.aegises) {
            Aegis aegis = new Aegis(
                    Objects.requireNonNull(definition.id, "Missing Aegis id"),
                    Objects.requireNonNull(definition.name, "Missing Aegis name"),
                    Objects.requireNonNull(definition.description, "Missing Aegis description"),
                    requireLocation(definition.icon),
                    definition.stats == null ? Map.of() : definition.stats,
                    definition.primaryStatMultipliers == null
                            ? Map.of()
                            : definition.primaryStatMultipliers,
                    definition.enabled,
                    definition.initialSelectionAllowed,
                    definition.requiresMod == null ? "" : definition.requiresMod,
                    definition.manualToggle != null
                            ? definition.manualToggle
                            : defaultManualToggle(definition.id)
            );
            if (byId.put(aegis.id, aegis) != null) {
                throw new IllegalStateException("Duplicate Aegis id: " + aegis.id);
            }
            values.add(aegis);
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("Aegis catalog is empty");
        }
        VALUES = List.copyOf(values);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final ResourceLocation iconTexture;
    private final Map<String, Double> stats;
    private final Map<String, Double> primaryStatMultipliers;
    private final boolean enabled;
    private final boolean initialSelectionAllowed;
    private final String requiresMod;
    private final boolean manuallyToggleable;

    private Aegis(String id, String nameKey, String descriptionKey,
                  ResourceLocation iconTexture, Map<String, Double> stats,
                  Map<String, Double> primaryStatMultipliers,
                  boolean enabled, boolean initialSelectionAllowed, String requiresMod,
                  boolean manuallyToggleable) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.iconTexture = iconTexture;
        Map<String, Double> effectiveStats = new LinkedHashMap<>(stats);
        if (id.equals(AegisConstants.STELLAR)) {
            effectiveStats.putIfAbsent(AegisConstants.LUCKY_STRIKE_CAP, 3.0D);
        }
        this.stats = Collections.unmodifiableMap(effectiveStats);
        Map<String, Double> effectivePrimaryStatMultipliers = new LinkedHashMap<>();
        primaryStatMultipliers.forEach((primaryStatId, multiplier) -> {
            if (primaryStatId == null || primaryStatId.isBlank()
                    || multiplier == null || !Double.isFinite(multiplier)
                    || multiplier < 0.0D) {
                throw new IllegalStateException(
                        "Invalid primary-stat multiplier for Aegis " + id
                                + ": " + primaryStatId + "=" + multiplier
                );
            }
            effectivePrimaryStatMultipliers.put(primaryStatId, multiplier);
        });
        this.primaryStatMultipliers = Collections.unmodifiableMap(
                effectivePrimaryStatMultipliers
        );
        this.enabled = enabled;
        this.initialSelectionAllowed = initialSelectionAllowed;
        this.requiresMod = requiresMod;
        this.manuallyToggleable = manuallyToggleable;
    }

    public String id() {
        return id;
    }

    public Component title() {
        return getTranslatableString(nameKey);
    }

    public Component description() {
        Map<String, Double> descriptionStats = new LinkedHashMap<>(stats);
        primaryStatMultipliers.forEach((primaryStatId, multiplier) ->
                descriptionStats.put(
                        "primary_stat_multiplier_" + primaryStatId,
                        multiplier
                )
        );
        return ConfigDescription.render(descriptionKey, descriptionStats);
    }

    public ResourceLocation iconTexture() {
        return iconTexture;
    }

    public Map<String, Double> stats() {
        return stats;
    }

    public double stat(String key) {
        return stats.getOrDefault(key, 0.0D);
    }

    /**
     * Returns this Aegis's multiplier for a chosen Primary Skill Enhancement.
     * Entries are keyed by the enhancement id used in skill_enhancements.json.
     */
    public double primaryStatMultiplier(String primaryStatId) {
        Double specific = primaryStatMultipliers.get(primaryStatId);
        if (specific != null) {
            return specific;
        }
        return primaryStatMultipliers.getOrDefault("default", 1.0D);
    }

    public Map<String, Double> primaryStatMultipliers() {
        return primaryStatMultipliers;
    }

    public boolean manuallyToggleable() {
        return manuallyToggleable;
    }

    public boolean canOffer(boolean initialSelection) {
        return enabled
                && (!initialSelection || initialSelectionAllowed)
                && (requiresMod.isBlank() || PlatformServices.mods().isLoaded(requiresMod));
    }

    public static List<Aegis> values() {
        return VALUES;
    }

    public static Optional<Aegis> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static boolean defaultManualToggle(String id) {
        return switch (id) {
            case AegisConstants.BLISS,
                    AegisConstants.ANGEL,
                    AegisConstants.HEALING,
                    AegisConstants.WISDOM,
                    AegisConstants.LUCKY,
                    AegisConstants.DESTRUCTION,
                    AegisConstants.FOX_GOD,
                    AegisConstants.ARCANE -> true;
            default -> false;
        };
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("aegises.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = Aegis.class.getResourceAsStream(
                        "/assets/aegis_ascension/aegises.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/aegises.json"
                        );
                    }
                    Files.copy(stream, configPath);
                }
            }
            JsonObject root;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }

            Catalog catalog = GSON.fromJson(root, Catalog.class);
            Objects.requireNonNull(catalog, "Aegis catalog was empty");
            Objects.requireNonNull(catalog.aegises, "Missing aegises");
            return catalog;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static ResourceLocation requireLocation(String value) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException("Invalid Aegis resource location: " + value);
        }
        return location;
    }

    private static final class Catalog {
        private List<AegisJson> aegises = List.of();
    }

    private static final class AegisJson {
        private String id;
        private String name;
        private String description;
        private String icon;
        private Map<String, Double> stats = Map.of();
        @SerializedName("primary_stat_multipliers")
        private Map<String, Double> primaryStatMultipliers = Map.of();
        private boolean enabled = true;
        @SerializedName("initial_selection_allowed")
        private boolean initialSelectionAllowed = true;
        @SerializedName("requires_mod")
        private String requiresMod = "";
        @SerializedName("manual_toggle")
        private Boolean manualToggle;
    }
}
