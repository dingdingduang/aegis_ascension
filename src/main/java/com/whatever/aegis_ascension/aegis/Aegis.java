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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A unique, data-driven Aegis loaded from config/aegis_ascension/aegises.json. */
public final class Aegis {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CATALOG_ENTRIES = 128;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    /** Aegises contributed by dependent mods, by owning mod id, in registration order. */
    private static final Map<String, List<AegisJson>> REGISTERED_AEGISES =
            new LinkedHashMap<>();
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static volatile Catalog effectiveCatalog = buildEffectiveCatalog();
    private static volatile CatalogSnapshot localSnapshot = buildSnapshot(effectiveCatalog);
    private static volatile CatalogSnapshot activeSnapshot = localSnapshot;

    private static CatalogSnapshot buildSnapshot(Catalog catalog) {
        return buildSnapshot(catalog, false);
    }

    private static CatalogSnapshot buildSnapshot(Catalog catalog, boolean trustServerAvailability) {
        Objects.requireNonNull(catalog.aegises, "Missing aegises");
        if (catalog.aegises.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException("Too many Aegises: " + catalog.aegises.size());
        }
        List<Aegis> values = new ArrayList<>();
        Map<String, Aegis> byId = new LinkedHashMap<>();
        for (AegisJson definition : catalog.aegises) {
            Objects.requireNonNull(definition, "Null Aegis entry");
            String id = requireWireId(definition.id, "Aegis id");
            Map<String, Double> stats = validateStats(definition.stats, id + " stats");
            Aegis aegis = new Aegis(
                    id,
                    requireText(definition.name, id + " name"),
                    requireText(definition.description, id + " description"),
                    requireLocation(requireText(definition.icon, id + " icon")),
                    stats,
                    definition.primaryStatMultipliers == null
                            ? Map.of()
                            : definition.primaryStatMultipliers,
                    definition.extraCastExcludedSpells == null
                            ? List.of()
                            : definition.extraCastExcludedSpells,
                    definition.enabled,
                    definition.initialSelectionAllowed,
                    requiredMods(definition),
                    !trustServerAvailability || Boolean.TRUE.equals(definition.serverAvailable),
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
        return new CatalogSnapshot(
                List.copyOf(values),
                Collections.unmodifiableMap(byId)
        );
    }

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final ResourceLocation iconTexture;
    private final Map<String, Double> stats;
    private final Map<String, Double> primaryStatMultipliers;
    private final Set<ResourceLocation> extraCastExcludedSpells;
    private final boolean enabled;
    private final boolean initialSelectionAllowed;
    private final List<String> requiredMods;
    private final boolean authorityAvailable;
    private final boolean manuallyToggleable;

    private Aegis(String id, String nameKey, String descriptionKey,
                  ResourceLocation iconTexture, Map<String, Double> stats,
                  Map<String, Double> primaryStatMultipliers,
                  List<String> extraCastExcludedSpells,
                  boolean enabled, boolean initialSelectionAllowed, List<String> requiredMods,
                  boolean authorityAvailable,
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
            if (multiplier == null || !Double.isFinite(multiplier)
                    || multiplier < 0.0D) {
                throw new IllegalStateException(
                        "Invalid primary-stat multiplier for Aegis " + id
                                + ": " + primaryStatId + "=" + multiplier
                );
            }
            effectivePrimaryStatMultipliers.put(
                    requireWireId(primaryStatId, "Aegis " + id + " primary-stat id"),
                    multiplier
            );
        });
        this.primaryStatMultipliers = Collections.unmodifiableMap(
                effectivePrimaryStatMultipliers
        );
        LinkedHashSet<ResourceLocation> effectiveExtraCastExclusions = new LinkedHashSet<>();
        for (String spellId : extraCastExcludedSpells) {
            ResourceLocation location = spellId == null
                    ? null
                    : PlatformServices.resources().tryParse(spellId);
            if (location == null) {
                throw new IllegalStateException(
                        "Invalid extra-cast spell exclusion for Aegis " + id + ": " + spellId
                );
            }
            effectiveExtraCastExclusions.add(location);
        }
        this.extraCastExcludedSpells = Collections.unmodifiableSet(
                effectiveExtraCastExclusions
        );
        this.enabled = enabled;
        this.initialSelectionAllowed = initialSelectionAllowed;
        this.requiredMods = List.copyOf(requiredMods);
        this.authorityAvailable = authorityAvailable;
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

    /** Returns whether this Aegis must not repeat the given spell. */
    public boolean excludesExtraCast(String spellId) {
        ResourceLocation location = spellId == null
                ? null
                : PlatformServices.resources().tryParse(spellId);
        return location != null && extraCastExcludedSpells.contains(location);
    }

    public boolean manuallyToggleable() {
        return manuallyToggleable;
    }

    public boolean canOffer(boolean initialSelection) {
        return enabled
                && (!initialSelection || initialSelectionAllowed)
                && authorityAvailable
                && requiredMods.stream().allMatch(modId -> PlatformServices.mods().isLoaded(modId));
    }

    public List<String> requiredMods() {
        return requiredMods;
    }

    public static List<Aegis> values() {
        return activeSnapshot.values();
    }

    public static Optional<Aegis> byId(String id) {
        return Optional.ofNullable(activeSnapshot.byId().get(id));
    }

    /**
     * Registers the Aegises a dependent mod ships at
     * {@code assets/<modId>/aegises.json} inside its own jar.
     *
     * <p>Every id must be namespaced to the registering mod. The owning mod is added to
     * each entry's required mods, registrations are accepted only before a server starts,
     * and the complete candidate catalog is validated before any live state is replaced.</p>
     *
     * @return the number of Aegises registered for this mod
     */
    public static synchronized int registerAddonAegises(String modId) {
        String namespace = requireModId(modId);
        requireRegistrationWindow(namespace);
        if (!PlatformServices.mods().isLoaded(namespace)) {
            throw new IllegalStateException(
                    "Cannot register Aegises for a mod that is not loaded: " + namespace
            );
        }
        String resourcePath = "assets/" + namespace + "/aegises.json";
        Path file = PlatformServices.mods()
                .findModResource(namespace, resourcePath)
                .orElseThrow(() -> new IllegalStateException(
                        "Mod " + namespace + " ships no " + resourcePath
                ));
        List<AegisJson> aegises = readAddonAegises(namespace, file);

        Map<String, List<AegisJson>> candidate = new LinkedHashMap<>(REGISTERED_AEGISES);
        candidate.put(namespace, aegises);
        Catalog effective = buildEffectiveCatalog(candidate);
        CatalogSnapshot rebuilt;
        try {
            rebuilt = buildSnapshot(effective);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Aegises from mod " + namespace + " were rejected: "
                            + exception.getMessage(),
                    exception
            );
        }

        REGISTERED_AEGISES.clear();
        REGISTERED_AEGISES.putAll(candidate);
        boolean localActive = activeSnapshot == localSnapshot;
        effectiveCatalog = effective;
        localSnapshot = rebuilt;
        if (localActive) {
            activeSnapshot = rebuilt;
        }
        return aegises.size();
    }

    /** Mod ids that have registered Aegises, in registration order. */
    public static List<String> registeredAddonMods() {
        synchronized (Aegis.class) {
            return List.copyOf(REGISTERED_AEGISES.keySet());
        }
    }

    /** Serializes the server's effective, validated catalog for the login snapshot. */
    public static String exportCatalogJson() {
        Catalog exported = GSON.fromJson(GSON.toJson(effectiveCatalog), Catalog.class);
        for (AegisJson definition : exported.aegises) {
            if (definition != null) {
                definition.serverAvailable = requiredMods(definition).stream()
                        .allMatch(modId -> PlatformServices.mods().isLoaded(modId));
            }
        }
        return GSON.toJson(exported);
    }

    /** Installs a server-authoritative catalog in client memory without touching local files. */
    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized Aegis catalog was empty"
        );
        Objects.requireNonNull(catalog.aegises, "Missing aegises");
        // Server availability is authoritative. Do not close or hide a valid offer merely
        // because this remote client does not have the server's optional integration mod.
        for (AegisJson definition : catalog.aegises) {
            if (definition != null) {
                definition.requiresMod = "";
                definition.requiredMods = List.of();
            }
        }
        activeSnapshot = buildSnapshot(catalog, true);
    }

    /** Restores this installation's own catalog after leaving a remote server. */
    public static void resetSyncedCatalog() {
        activeSnapshot = localSnapshot;
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

    private static void requireRegistrationWindow(String modId) {
        if (PlatformServices.server().currentServer() != null) {
            throw new IllegalStateException(
                    "Mod " + modId + " registered Aegises after the server started."
                            + " Register during mod setup: rebuilding the catalog now would"
                            + " invalidate loaded Aegis object identities."
            );
        }
    }

    private static List<AegisJson> readAddonAegises(String namespace, Path file) {
        AddonCatalog catalog;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            catalog = GSON.fromJson(reader, AddonCatalog.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not read Aegises for mod " + namespace + " from " + file,
                    exception
            );
        }
        if (catalog == null || catalog.aegises == null || catalog.aegises.isEmpty()) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared no Aegises in " + file
            );
        }
        List<AegisJson> aegises = new ArrayList<>(catalog.aegises.size());
        for (AegisJson definition : catalog.aegises) {
            aegises.add(prepareAddonAegis(namespace, definition));
        }
        return List.copyOf(aegises);
    }

    private static AegisJson prepareAddonAegis(String namespace, AegisJson definition) {
        if (definition == null) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared a null Aegis entry"
            );
        }
        requireAddonId(namespace, definition.id, "Aegis");
        requireAddonField(namespace, definition.id, "name", definition.name);
        requireAddonField(namespace, definition.id, "description", definition.description);
        requireAddonField(namespace, definition.id, "icon", definition.icon);

        List<String> requiredMods = new ArrayList<>(requiredMods(definition));
        requiredMods.add(namespace);
        definition.requiresMod = "";
        definition.requiredMods = List.copyOf(new LinkedHashSet<>(requiredMods));
        return definition;
    }

    private static void requireAddonId(String namespace, String value, String kind) {
        ResourceLocation location = value == null
                ? null
                : PlatformServices.resources().tryParse(value);
        if (location == null || !namespace.equals(location.getNamespace())
                || value.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(
                    kind + " id " + value + " from mod " + namespace
                            + " must be a valid namespaced id under " + namespace + ":"
            );
        }
    }

    private static void requireAddonField(String namespace, String entryId,
                                          String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Aegis " + entryId + " from mod " + namespace
                            + " is missing required field \"" + field + "\""
            );
        }
    }

    private static Catalog buildEffectiveCatalog() {
        return buildEffectiveCatalog(REGISTERED_AEGISES);
    }

    private static Catalog buildEffectiveCatalog(
            Map<String, List<AegisJson>> registered
    ) {
        Catalog effective = new Catalog();
        List<AegisJson> aegises = new ArrayList<>(LOCAL_CATALOG.aegises);
        registered.values().forEach(aegises::addAll);
        effective.aegises = List.copyOf(aegises);
        return effective;
    }

    private static ResourceLocation requireLocation(String value) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException("Invalid Aegis resource location: " + value);
        }
        return location;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + field);
        }
        return value;
    }

    private static String requireWireId(String value, String field) {
        String id = requireText(value, field);
        if (id.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(field + " exceeds " + MAX_WIRE_ID_LENGTH + " characters");
        }
        return id;
    }

    private static Map<String, Double> validateStats(Map<String, Double> stats, String field) {
        if (stats == null) {
            return Map.of();
        }
        Map<String, Double> validated = new LinkedHashMap<>();
        stats.forEach((key, value) -> {
            String statId = requireWireId(key, field + " key");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException("Invalid " + field + " entry: " + key + "=" + value);
            }
            validated.put(statId, value);
        });
        return validated;
    }

    private static String requireModId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String modId = value.trim();
        if (!modId.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalStateException("Invalid required mod id: " + value);
        }
        return modId;
    }

    private static List<String> requiredMods(AegisJson definition) {
        LinkedHashSet<String> modIds = new LinkedHashSet<>();
        if (definition.requiresMod != null && !definition.requiresMod.isBlank()) {
            modIds.add(requireModId(definition.requiresMod));
        }
        if (definition.requiredMods != null) {
            for (String modId : definition.requiredMods) {
                if (modId != null && !modId.isBlank()) {
                    modIds.add(requireModId(modId));
                }
            }
        }
        return List.copyOf(modIds);
    }

    private static final class Catalog {
        private List<AegisJson> aegises = List.of();
    }

    /** The narrow shape a dependent mod may ship: Aegises only. */
    private static final class AddonCatalog {
        private List<AegisJson> aegises;
    }

    private static final class AegisJson {
        private String id;
        private String name;
        private String description;
        private String icon;
        private Map<String, Double> stats = Map.of();
        @SerializedName("primary_stat_multipliers")
        private Map<String, Double> primaryStatMultipliers = Map.of();
        @SerializedName("extra_cast_excluded_spells")
        private List<String> extraCastExcludedSpells = List.of();
        private boolean enabled = true;
        @SerializedName("initial_selection_allowed")
        private boolean initialSelectionAllowed = true;
        @SerializedName("requires_mod")
        private String requiresMod = "";
        @SerializedName("required_mods")
        private List<String> requiredMods = List.of();
        @SerializedName("server_available")
        private Boolean serverAvailable;
        @SerializedName("manual_toggle")
        private Boolean manualToggle;
    }

    private record CatalogSnapshot(List<Aegis> values, Map<String, Aegis> byId) {
    }
}
