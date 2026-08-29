package com.whatever.aegis_ascension.perk;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.soullink.SoulLinkCatalog;
import com.whatever.aegis_ascension.perk.talents.MysteriousDoll;
import com.whatever.aegis_ascension.perk.talents.ShrineMaidenDance;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.ConfigDescription;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** A data-driven talent loaded from config/aegis_ascension/talents.json. */
public final class Perk {
    //TODO: remove from nbt when retire a talent
//    private static final List<String> RETIRED_TALENT_IDS = List.of(
//            "sr_magic_conversion"
//    );

    public enum Tier {
        R,
        SR,
        SSR
    }

    private static final Gson GSON = new Gson();
    private static final Catalog CATALOG = loadCatalog();
    private static final List<Perk> VALUES;
    private static final Map<String, Perk> BY_ID;
    private static final List<SoulLink> SOUL_LINKS;
    private static final Map<Tier, Integer> RARITY_WEIGHTS;
    private static final List<ApothicAttributeMapping> APOTHIC_ATTRIBUTE_MAPPINGS;

    static {
        List<Perk> values = new ArrayList<>();
        Map<String, Perk> byId = new LinkedHashMap<>();
        for (TalentJson talent : CATALOG.perks) {
//            if (RETIRED_TALENT_IDS.contains(talent.id)) {
//                continue;
//            }
            Perk perk = new Perk(
                    talent.id,
                    Tier.valueOf(talent.tier),
                    talent.name,
                    talent.description,
                    requireLocation(talent.icon),
                    talent.stats == null ? Map.of() : talent.stats,
                    talent.primaryStatMultipliers == null
                            ? Map.of()
                            : talent.primaryStatMultipliers,
                    Math.max(1, talent.maxRank),
                    talent.manualToggle,
                    talent.poolRequiredPerks != null
                            ? talent.poolRequiredPerks
                            : defaultPoolRequiredPerks(talent.id),
                    talent.poolRequiredSoulLinks != null
                            ? talent.poolRequiredSoulLinks
                            : defaultPoolRequiredSoulLinks(talent.id),
                    requiredMods(talent),
                    talent.randomRewardEligible == null
                            ? !R_SUSPENSION_OF_DISBELIEF.equals(talent.id)
                            : talent.randomRewardEligible,
                    talent.sourceRow
            );
            if (byId.put(perk.id, perk) != null) {
                throw new IllegalStateException("Duplicate talent id: " + perk.id);
            }
            values.add(perk);
        }
        VALUES = List.copyOf(values);
        BY_ID = Collections.unmodifiableMap(byId);

        SOUL_LINKS = SoulLinkCatalog.values();
        for (SoulLink soulLink : SOUL_LINKS) {
            for (String perkId : soulLink.requirements()) {
                if (!BY_ID.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing talent " + perkId
                    );
                }
            }
            for (String perkId : soulLink.rankPerks()) {
                if (!BY_ID.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing rank talent " + perkId
                    );
                }
            }
        }

        List<ApothicAttributeMapping> attributeMappings = new ArrayList<>();
        for (AttributeMappingJson mapping : CATALOG.apothicAttributeMappings) {
            if (mapping.customStat == null || mapping.customStat.isBlank()) {
                throw new IllegalStateException("Apothic attribute mapping has a blank custom_stat");
            }
            if (!Double.isFinite(mapping.scale)) {
                throw new IllegalStateException(
                        "Invalid scale for Apothic custom stat: " + mapping.customStat
                );
            }
            attributeMappings.add(new ApothicAttributeMapping(
                    mapping.customStat,
                    requireLocation(mapping.attribute),
                    requireOperation(mapping.operation),
                    mapping.scale,
                    mapping.enabled
            ));
        }
        APOTHIC_ATTRIBUTE_MAPPINGS = List.copyOf(attributeMappings);

        EnumMap<Tier, Integer> weights = new EnumMap<>(Tier.class);
        weights.put(Tier.R, CATALOG.rarityWeights.r);
        weights.put(Tier.SR, CATALOG.rarityWeights.sr);
        weights.put(Tier.SSR, CATALOG.rarityWeights.ssr);
        RARITY_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    private final String id;
    private final Tier tier;
    private final String nameKey;
    private final String descriptionKey;
    private final ResourceLocation iconTexture;
    private final Map<String, Double> stats;
    private final Map<String, Double> primaryStatMultipliers;
    private final int maxRank;
    private final boolean manuallyToggleable;
    private final List<String> poolRequiredPerks;
    private final List<String> poolRequiredSoulLinks;
    private final List<String> requiredMods;
    private final boolean randomRewardEligible;
    private final int sourceRow;

    private Perk(String id, Tier tier, String nameKey, String descriptionKey,
                 ResourceLocation iconTexture, Map<String, Double> stats,
                 Map<String, Double> primaryStatMultipliers,
                 int maxRank, boolean manuallyToggleable,
                 List<String> poolRequiredPerks,
                 List<String> poolRequiredSoulLinks,
                 List<String> requiredMods,
                 boolean randomRewardEligible,
                 int sourceRow) {
        this.id = id;
        this.tier = tier;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.iconTexture = iconTexture;
        Map<String, Double> effectiveStats = new LinkedHashMap<>(stats);
        this.stats = Collections.unmodifiableMap(effectiveStats);
        Map<String, Double> effectivePrimaryStatMultipliers = new LinkedHashMap<>();
        primaryStatMultipliers.forEach((primaryStatId, multiplier) -> {
            if (primaryStatId == null || primaryStatId.isBlank()
                    || multiplier == null || !Double.isFinite(multiplier)
                    || multiplier < 0.0D) {
                throw new IllegalStateException(
                        "Invalid primary-stat multiplier for talent " + id
                                + ": " + primaryStatId + "=" + multiplier
                );
            }
            effectivePrimaryStatMultipliers.put(primaryStatId, multiplier);
        });
        this.primaryStatMultipliers = Collections.unmodifiableMap(
                effectivePrimaryStatMultipliers
        );
        this.maxRank = maxRank;
        this.manuallyToggleable = manuallyToggleable;
        this.poolRequiredPerks = List.copyOf(poolRequiredPerks);
        this.poolRequiredSoulLinks = List.copyOf(poolRequiredSoulLinks);
        this.requiredMods = List.copyOf(requiredMods);
        this.randomRewardEligible = randomRewardEligible;
        this.sourceRow = sourceRow;
    }

    public String id() {
        return id;
    }

    public Tier tier() {
        return tier;
    }

    public Component title() {
        return getTranslatableString(nameKey);
    }

    public Component description() {
        if (id.equals(R_MYSTERIOUS_DOLL)) {
            return MysteriousDoll.description();
        }
        if (id.equals(R_SHRINE_MAIDEN_DANCE)) {
            return ShrineMaidenDance.description();
        }
        Map<String, Double> descriptionValues = new LinkedHashMap<>(stats);
        primaryStatMultipliers.forEach((primaryStatId, multiplier) ->
                descriptionValues.put(
                        "primary_stat_multiplier_" + primaryStatId,
                        multiplier
                )
        );
        descriptionValues.put("max_rank", (double) maxRank);
        return ConfigDescription.render(descriptionKey, descriptionValues);
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

    /** Multiplier for this talent's Primary reward when applied to a destination stat. */
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

    public int maxRank() {
        return maxRank;
    }

    public boolean repeatable() {
        return maxRank > 1;
    }

    public boolean manuallyToggleable() {
        return manuallyToggleable;
    }

    public boolean canAcquire(int currentRank) {
        return areRequiredModsLoaded() && currentRank < maxRank;
    }

    public List<String> poolRequiredPerks() {
        return poolRequiredPerks;
    }

    public List<String> poolRequiredSoulLinks() {
        return poolRequiredSoulLinks;
    }

    public List<String> requiredMods() {
        return requiredMods;
    }

    /** Whether random-talent rewards may grant this entry. Manual offers are unaffected. */
    public boolean randomRewardEligible() {
        return randomRewardEligible;
    }

    public boolean areRequiredModsLoaded() {
        return requiredMods.stream().allMatch(modId -> PlatformServices.mods().isLoaded(modId));
    }

    public boolean isUnlockedForPool(PlayerPerkData data) {
        return isUnlockedForPool(data::owns, data::hasActiveSoulLink);
    }

    public boolean isUnlockedForPool(Predicate<String> ownsPerk,
                                     Predicate<String> hasActiveSoulLink) {
        return areRequiredModsLoaded()
                && poolRequiredPerks.stream().allMatch(ownsPerk)
                && poolRequiredSoulLinks.stream().allMatch(hasActiveSoulLink);
    }

    public int sourceRow() {
        return sourceRow;
    }

    public static List<Perk> values() {
        return VALUES;
    }

    public static Optional<Perk> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static List<String> defaultPoolRequiredPerks(String id) {
        return switch (id) {
            case R_YOSHINO_CIALLO, R_SHIZURU_CIALLO,
                    R_NINGNING_CIALLO, R_NANAMI_CIALLO -> List.of(R_CONGYU_CIALLO);
            default -> List.of();
        };
    }

    private static List<String> defaultPoolRequiredSoulLinks(String id) {
        return id.equals(SR_SWISS_ROLL_MOMENT)
                ? List.of(SOUL_TRINITY_TEA_PARTY)
                : List.of();
    }

    private static List<String> requiredMods(TalentJson talent) {
        java.util.LinkedHashSet<String> modIds = new java.util.LinkedHashSet<>();
        if (talent.requiresMod != null && !talent.requiresMod.isBlank()) {
            modIds.add(requireModId(talent.requiresMod));
        }
        if (talent.requiredMods != null) {
            for (String modId : talent.requiredMods) {
                if (modId != null && !modId.isBlank()) {
                    modIds.add(requireModId(modId));
                }
            }
        }
        return List.copyOf(modIds);
    }

    private static String requireModId(String value) {
        String modId = value.trim();
        if (!modId.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalStateException("Invalid required mod id: " + value);
        }
        return modId;
    }

    public static List<SoulLink> soulLinks() {
        return SOUL_LINKS;
    }

    public static Optional<SoulLink> soulLinkById(String id) {
        return SOUL_LINKS.stream().filter(link -> link.id().equals(id)).findFirst();
    }

    public static int rarityWeight(Tier tier) {
        return RARITY_WEIGHTS.getOrDefault(tier, 0);
    }

    public static List<ApothicAttributeMapping> apothicAttributeMappings() {
        return APOTHIC_ATTRIBUTE_MAPPINGS;
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("talents.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = Perk.class.getResourceAsStream(
                        "/assets/aegis_ascension/talents.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/talents.json"
                        );
                    }
                    Files.copy(stream, configPath);
                }
            }

            JsonObject configuredRoot;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                configuredRoot = JsonParser.parseReader(reader).getAsJsonObject();
            }

            JsonObject bundledRoot = loadBundledCatalogJson();
            inheritBundledModRequirements(configuredRoot, bundledRoot);

            Catalog catalog = GSON.fromJson(configuredRoot, Catalog.class);
            Objects.requireNonNull(catalog, "Talent catalog was empty");
            Objects.requireNonNull(catalog.rarityWeights, "Missing rarity_weights");
            Objects.requireNonNull(catalog.perks, "Missing perks");
            if (catalog.apothicAttributeMappings == null) {
                Catalog bundled = loadBundledCatalog();
                catalog.apothicAttributeMappings = Objects.requireNonNull(
                        bundled.apothicAttributeMappings,
                        "Bundled catalog is missing apothic_attribute_mappings"
                );
            }
            return catalog;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static boolean appendBundledEntryIfMissing(
            JsonObject configuredRoot,
            JsonObject bundledRoot,
            String arrayName,
            String entryId) {
        JsonArray configuredEntries = Objects.requireNonNull(
                configuredRoot.getAsJsonArray(arrayName),
                "Missing " + arrayName
        );
        if (findEntry(configuredEntries, entryId) != null) {
            return false;
        }
        JsonObject bundledEntry = Objects.requireNonNull(
                findEntry(bundledRoot.getAsJsonArray(arrayName), entryId),
                "Bundled catalog is missing " + entryId
        );
        configuredEntries.add(bundledEntry.deepCopy());
        return true;
    }

    /**
     * Makes newly bundled dependency metadata effective for an existing config.
     * An explicit empty requires_mod or required_mods value in the config opts out.
     */
    private static void inheritBundledModRequirements(
            JsonObject configuredRoot,
            JsonObject bundledRoot) {
        JsonArray configuredPerks = configuredRoot.getAsJsonArray("perks");
        JsonArray bundledPerks = bundledRoot.getAsJsonArray("perks");
        if (configuredPerks == null || bundledPerks == null) {
            return;
        }
        for (var configuredElement : configuredPerks) {
            if (!configuredElement.isJsonObject()) {
                continue;
            }
            JsonObject configured = configuredElement.getAsJsonObject();
            if (configured.has("requires_mod") || configured.has("required_mods")
                    || !configured.has("id")) {
                continue;
            }
            JsonObject bundled = findEntry(
                    bundledPerks,
                    configured.get("id").getAsString()
            );
            if (bundled == null) {
                continue;
            }
            if (bundled.has("requires_mod")) {
                configured.add("requires_mod", bundled.get("requires_mod").deepCopy());
            }
            if (bundled.has("required_mods")) {
                configured.add("required_mods", bundled.get("required_mods").deepCopy());
            }
        }
    }

    private static JsonObject findEntry(JsonArray entries, String entryId) {
        if (entries == null) {
            return null;
        }
        for (var entry : entries) {
            if (entry.isJsonObject()
                    && entry.getAsJsonObject().has("id")
                    && entryId.equals(entry.getAsJsonObject().get("id").getAsString())) {
                return entry.getAsJsonObject();
            }
        }
        return null;
    }

    private static ResourceLocation requireLocation(String value) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException("Invalid talent resource location: " + value);
        }
        return location;
    }

    private static Catalog loadBundledCatalog() throws Exception {
        return Objects.requireNonNull(
                GSON.fromJson(loadBundledCatalogJson(), Catalog.class),
                "Bundled talent catalog was empty"
        );
    }

    private static JsonObject loadBundledCatalogJson() throws Exception {
        try (var stream = Perk.class.getResourceAsStream(
                "/assets/aegis_ascension/talents.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing default assets/aegis_ascension/talents.json"
                );
            }
            try (var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static AttributeOperation requireOperation(String value) {
        return switch (Objects.requireNonNull(value, "Missing attribute operation")) {
            case "addition" -> AttributeOperation.ADDITION;
            case "multiply_base" -> AttributeOperation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeOperation.MULTIPLY_TOTAL;
            default -> throw new IllegalStateException(
                    "Unknown attribute mapping operation: " + value
            );
        };
    }

    private static final class Catalog {
        @SerializedName("rarity_weights")
        private RarityWeights rarityWeights;
        @SerializedName("apothic_attribute_mappings")
        private List<AttributeMappingJson> apothicAttributeMappings;
        private List<TalentJson> perks = List.of();
    }

    private static final class RarityWeights {
        @SerializedName("R")
        private int r;
        @SerializedName("SR")
        private int sr;
        @SerializedName("SSR")
        private int ssr;
    }

    private static final class TalentJson {
        private String id;
        private String tier;
        private String name;
        private String description;
        private String icon;
        private Map<String, Double> stats = Map.of();
        @SerializedName("primary_stat_multipliers")
        private Map<String, Double> primaryStatMultipliers = Map.of();
        private int maxRank;
        private boolean manualToggle;
        @SerializedName("pool_required_perks")
        private List<String> poolRequiredPerks;
        @SerializedName("pool_required_soul_links")
        private List<String> poolRequiredSoulLinks;
        @SerializedName("requires_mod")
        private String requiresMod;
        @SerializedName("required_mods")
        private List<String> requiredMods;
        @SerializedName("random_reward_eligible")
        private Boolean randomRewardEligible;
        @SerializedName("source_row")
        private int sourceRow;
    }

    private static final class AttributeMappingJson {
        @SerializedName("custom_stat")
        private String customStat;
        private String attribute;
        private String operation;
        private double scale = 1.0D;
        private boolean enabled = true;
    }
}
