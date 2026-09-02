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
//            "perk_magic_conversion"
//    );

    public enum Tier {
        R,
        SR,
        SSR
    }

    private static final Gson GSON = new Gson();
    private static final int MAX_CATALOG_ENTRIES = 512;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    /** Talents contributed by dependent mods, by owning mod id, in registration order. */
    private static final Map<String, List<TalentJson>> REGISTERED_TALENTS =
            new LinkedHashMap<>();
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static volatile Catalog effectiveCatalog = buildEffectiveCatalog();
    private static volatile CatalogSnapshot localSnapshot = buildSnapshot(effectiveCatalog);
    private static volatile CatalogSnapshot activeSnapshot = localSnapshot;

    private static CatalogSnapshot buildSnapshot(Catalog catalog) {
        return buildSnapshot(catalog, false);
    }

    private static CatalogSnapshot buildSnapshot(Catalog catalog, boolean trustServerAvailability) {
        Objects.requireNonNull(catalog.perks, "Missing perks");
        Objects.requireNonNull(catalog.rarityWeights, "Missing rarity_weights");
        Objects.requireNonNull(
                catalog.apothicAttributeMappings,
                "Missing apothic_attribute_mappings"
        );
        if (catalog.perks.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException("Too many talents: " + catalog.perks.size());
        }

        List<Perk> values = new ArrayList<>();
        Map<String, Perk> byId = new LinkedHashMap<>();
        for (TalentJson talent : catalog.perks) {
            Objects.requireNonNull(talent, "Null talent entry");
//            if (RETIRED_TALENT_IDS.contains(talent.id)) {
//                continue;
//            }
            String id = requireWireId(talent.id, "Talent id");
            if (talent.maxRank < 1) {
                throw new IllegalStateException("Talent " + id + " has invalid max_rank");
            }
            Perk perk = new Perk(
                    id,
                    Tier.valueOf(requireText(talent.tier, id + " tier")),
                    requireText(talent.name, id + " name"),
                    requireText(talent.description, id + " description"),
                    requireLocation(requireText(talent.icon, id + " icon")),
                    validateStats(talent.stats, id + " stats", false),
                    talent.primaryStatMultipliers == null
                            ? Map.of()
                            : talent.primaryStatMultipliers,
                    talent.maxRank,
                    talent.manualToggle,
                    talent.poolRequiredPerks != null
                            ? validateIds(talent.poolRequiredPerks, id + " pool_required_perks")
                            : defaultPoolRequiredPerks(talent.id),
                    talent.poolRequiredSoulLinks != null
                            ? validateIds(talent.poolRequiredSoulLinks, id + " pool_required_soul_links")
                            : defaultPoolRequiredSoulLinks(talent.id),
                    requiredMods(talent),
                    !trustServerAvailability || Boolean.TRUE.equals(talent.serverAvailable),
                    talent.randomRewardEligible == null
                            ? !PERK_SUSPENSION_OF_DISBELIEF.equals(talent.id)
                            : talent.randomRewardEligible,
                    talent.sourceRow
            );
            if (byId.put(perk.id, perk) != null) {
                throw new IllegalStateException("Duplicate talent id: " + perk.id);
            }
            values.add(perk);
        }

        Map<String, SoulLink> soulLinksById = new LinkedHashMap<>();
        for (SoulLink soulLink : SoulLinkCatalog.values()) {
            soulLinksById.put(soulLink.id(), soulLink);
            for (String perkId : soulLink.requirements()) {
                if (!byId.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing talent " + perkId
                    );
                }
            }
            for (String perkId : soulLink.rankPerks()) {
                if (!byId.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing rank talent " + perkId
                    );
                }
            }
        }
        for (Perk perk : values) {
            for (String perkId : perk.poolRequiredPerks) {
                if (!byId.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Talent " + perk.id + " requires missing pool talent " + perkId
                    );
                }
            }
            for (String soulLinkId : perk.poolRequiredSoulLinks) {
                if (!soulLinksById.containsKey(soulLinkId)) {
                    throw new IllegalStateException(
                            "Talent " + perk.id + " requires missing Soul Link " + soulLinkId
                    );
                }
            }
        }

        List<ApothicAttributeMapping> attributeMappings = new ArrayList<>();
        for (AttributeMappingJson mapping : catalog.apothicAttributeMappings) {
            Objects.requireNonNull(mapping, "Null Apothic attribute mapping");
            String customStat = requireWireId(
                    mapping.customStat,
                    "Apothic attribute mapping custom_stat"
            );
            if (!Double.isFinite(mapping.scale)) {
                throw new IllegalStateException(
                        "Invalid scale for Apothic custom stat: " + customStat
                );
            }
            List<String> excludedPerks = validateIds(
                    mapping.excludedPerks,
                    "Apothic attribute mapping excluded_perks"
            );
            for (String perkId : excludedPerks) {
                if (!byId.containsKey(perkId)) {
                    throw new IllegalStateException(
                            "Apothic custom stat " + customStat
                                    + " excludes missing talent " + perkId
                    );
                }
            }
            attributeMappings.add(new ApothicAttributeMapping(
                    customStat,
                    requireLocation(mapping.attribute),
                    requireOperation(mapping.operation),
                    mapping.scale,
                    mapping.enabled,
                    List.copyOf(excludedPerks)
            ));
        }

        EnumMap<Tier, Integer> weights = new EnumMap<>(Tier.class);
        int rWeight = requireWeight(catalog.rarityWeights.r, "R");
        int srWeight = requireWeight(catalog.rarityWeights.sr, "SR");
        int ssrWeight = requireWeight(catalog.rarityWeights.ssr, "SSR");
        if ((long) rWeight + srWeight + ssrWeight <= 0L) {
            throw new IllegalStateException("Talent rarity weights must have a positive total");
        }
        weights.put(Tier.R, rWeight);
        weights.put(Tier.SR, srWeight);
        weights.put(Tier.SSR, ssrWeight);
        return new CatalogSnapshot(
                List.copyOf(values),
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(weights),
                List.copyOf(attributeMappings)
        );
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
    private final boolean authorityAvailable;
    private final boolean randomRewardEligible;
    private final int sourceRow;

    private Perk(String id, Tier tier, String nameKey, String descriptionKey,
                 ResourceLocation iconTexture, Map<String, Double> stats,
                 Map<String, Double> primaryStatMultipliers,
                 int maxRank, boolean manuallyToggleable,
                 List<String> poolRequiredPerks,
                 List<String> poolRequiredSoulLinks,
                 List<String> requiredMods,
                 boolean authorityAvailable,
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
            if (multiplier == null || !Double.isFinite(multiplier)
                    || multiplier < 0.0D) {
                throw new IllegalStateException(
                        "Invalid primary-stat multiplier for talent " + id
                                + ": " + primaryStatId + "=" + multiplier
                );
            }
            effectivePrimaryStatMultipliers.put(
                    requireWireId(primaryStatId, "Talent " + id + " primary-stat id"),
                    multiplier
            );
        });
        this.primaryStatMultipliers = Collections.unmodifiableMap(
                effectivePrimaryStatMultipliers
        );
        this.maxRank = maxRank;
        this.manuallyToggleable = manuallyToggleable;
        this.poolRequiredPerks = List.copyOf(poolRequiredPerks);
        this.poolRequiredSoulLinks = List.copyOf(poolRequiredSoulLinks);
        this.requiredMods = List.copyOf(requiredMods);
        this.authorityAvailable = authorityAvailable;
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
        if (id.equals(PERK_MYSTERIOUS_DOLL)) {
            return MysteriousDoll.description();
        }
        if (id.equals(PERK_SHRINE_MAIDEN_DANCE)) {
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
        return authorityAvailable
                && requiredMods.stream().allMatch(modId -> PlatformServices.mods().isLoaded(modId));
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
        return activeSnapshot.values();
    }

    public static Optional<Perk> byId(String id) {
        return Optional.ofNullable(activeSnapshot.byId().get(id));
    }

    private static List<String> defaultPoolRequiredPerks(String id) {
        return switch (id) {
            case PERK_YOSHINO_CIALLO, PERK_SHIZURU_CIALLO,
                    PERK_NINGNING_CIALLO, PERK_NANAMI_CIALLO -> List.of(PERK_CONGYU_CIALLO);
            default -> List.of();
        };
    }

    private static List<String> defaultPoolRequiredSoulLinks(String id) {
        return id.equals(PERK_SWISS_ROLL_MOMENT)
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
        return SoulLinkCatalog.values();
    }

    public static Optional<SoulLink> soulLinkById(String id) {
        return SoulLinkCatalog.values().stream()
                .filter(link -> link.id().equals(id))
                .findFirst();
    }

    public static int rarityWeight(Tier tier) {
        return activeSnapshot.rarityWeights().getOrDefault(tier, 0);
    }

    public static List<ApothicAttributeMapping> apothicAttributeMappings() {
        return activeSnapshot.apothicAttributeMappings();
    }

    /**
     * Registers the talents a dependent mod ships at {@code assets/<modId>/talents.json}
     * inside its own jar.
     *
     * <p>Only the {@code perks} array is read. Rarity weights and attribute mappings stay
     * a whole-game balance decision and are ignored in addon files. Every id must be
     * namespaced to the registering mod, which makes collisions between addons
     * impossible, and the owning mod is added to each entry's required mods so the talent
     * disappears from selection wherever that mod is absent.</p>
     *
     * <p>Call this during mod setup. Nothing is modified unless the whole file validates,
     * so a rejected file leaves the previous catalog intact.</p>
     *
     * @return the number of talents registered for this mod
     * @throws IllegalStateException if the mod is absent, ships no talent file, declares a
     *         talent this catalog cannot accept, or registers after a server has started
     */
    public static synchronized int registerAddonTalents(String modId) {
        String namespace = requireModId(modId);
        requireRegistrationWindow(namespace);
        if (!PlatformServices.mods().isLoaded(namespace)) {
            throw new IllegalStateException(
                    "Cannot register talents for a mod that is not loaded: " + namespace
            );
        }
        String resourcePath = "assets/" + namespace + "/talents.json";
        Path file = PlatformServices.mods()
                .findModResource(namespace, resourcePath)
                .orElseThrow(() -> new IllegalStateException(
                        "Mod " + namespace + " ships no " + resourcePath
                ));
        List<TalentJson> talents = readAddonTalents(namespace, file);

        // Build against a candidate registry first: buildSnapshot is what rejects duplicate
        // ids and malformed entries, and it must do so before any state is replaced.
        Map<String, List<TalentJson>> candidate = new LinkedHashMap<>(REGISTERED_TALENTS);
        candidate.put(namespace, talents);
        Catalog effective = buildEffectiveCatalog(candidate);
        CatalogSnapshot rebuilt;
        try {
            rebuilt = buildSnapshot(effective);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Talents from mod " + namespace + " were rejected: "
                            + exception.getMessage(),
                    exception
            );
        }

        REGISTERED_TALENTS.clear();
        REGISTERED_TALENTS.putAll(candidate);
        boolean localActive = activeSnapshot == localSnapshot;
        effectiveCatalog = effective;
        localSnapshot = rebuilt;
        if (localActive) {
            activeSnapshot = rebuilt;
        }
        return talents.size();
    }

    /** Mod ids that have registered talents, in registration order. */
    public static List<String> registeredAddonMods() {
        synchronized (Perk.class) {
            return List.copyOf(REGISTERED_TALENTS.keySet());
        }
    }

    /**
     * Rejects registrations that arrive too late to be safe.
     *
     * <p>Talent ranks are held as {@code Map<Perk, Integer>} and {@link Perk} uses identity
     * equality, so rebuilding the catalog once player data exists would leave every stored
     * rank pointing at a discarded instance and silently read back as zero. Registration
     * therefore has to finish during mod setup, before any server exists.</p>
     */
    private static void requireRegistrationWindow(String modId) {
        if (PlatformServices.server().currentServer() != null) {
            throw new IllegalStateException(
                    "Mod " + modId + " registered talents after the server started."
                            + " Register during mod setup: rebuilding the catalog now would"
                            + " reset every loaded player's talent ranks to zero."
            );
        }
    }

    private static List<TalentJson> readAddonTalents(String namespace, Path file) {
        AddonCatalog catalog;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            catalog = GSON.fromJson(reader, AddonCatalog.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not read talents for mod " + namespace + " from " + file,
                    exception
            );
        }
        if (catalog == null || catalog.perks == null || catalog.perks.isEmpty()) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared no talents in " + file
            );
        }
        List<TalentJson> talents = new ArrayList<>(catalog.perks.size());
        for (TalentJson talent : catalog.perks) {
            talents.add(prepareAddonTalent(namespace, talent));
        }
        return List.copyOf(talents);
    }

    /** Enforces the id namespace and makes the owning mod a load requirement. */
    private static TalentJson prepareAddonTalent(String namespace, TalentJson talent) {
        if (talent == null || talent.id == null || talent.id.isBlank()) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared a talent without an id"
            );
        }
        ResourceLocation talentId = PlatformServices.resources().tryParse(talent.id);
        if (talentId == null || !namespace.equals(talentId.getNamespace())
                || talent.id.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(
                    "Talent id " + talent.id + " from mod " + namespace
                            + " must be a valid namespaced id under " + namespace + ":"
            );
        }
        requireAddonField(namespace, talent.id, "tier", talent.tier);
        try {
            Tier.valueOf(talent.tier);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Talent " + talent.id + " from mod " + namespace
                            + " has unknown tier " + talent.tier
                            + "; expected one of R, SR, SSR"
            );
        }
        requireAddonField(namespace, talent.id, "name", talent.name);
        requireAddonField(namespace, talent.id, "description", talent.description);
        requireAddonField(namespace, talent.id, "icon", talent.icon);

        List<String> requiredMods = new ArrayList<>();
        if (talent.requiredMods != null) {
            requiredMods.addAll(talent.requiredMods);
        }
        requiredMods.add(namespace);
        talent.requiredMods = requiredMods;
        return talent;
    }

    private static void requireAddonField(String namespace, String talentId,
                                          String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Talent " + talentId + " from mod " + namespace
                            + " is missing required field \"" + field + "\""
            );
        }
    }

    private static Catalog buildEffectiveCatalog() {
        return buildEffectiveCatalog(REGISTERED_TALENTS);
    }

    /** This installation's own talents followed by every registered mod's, in order. */
    private static Catalog buildEffectiveCatalog(Map<String, List<TalentJson>> registered) {
        Catalog effective = new Catalog();
        effective.rarityWeights = LOCAL_CATALOG.rarityWeights;
        effective.apothicAttributeMappings = LOCAL_CATALOG.apothicAttributeMappings;
        List<TalentJson> perks = new ArrayList<>(LOCAL_CATALOG.perks);
        registered.values().forEach(perks::addAll);
        effective.perks = List.copyOf(perks);
        return effective;
    }

    /**
     * Serializes the server's effective, validated catalog for the login snapshot.
     *
     * <p>This must stay the effective catalog rather than the parsed file: registered
     * addon talents are part of what the server rolls from, and a client that never
     * receives them cannot render the talents it is being offered.</p>
     */
    public static String exportCatalogJson() {
        Catalog exported = GSON.fromJson(GSON.toJson(effectiveCatalog), Catalog.class);
        for (TalentJson talent : exported.perks) {
            if (talent != null) {
                talent.serverAvailable = requiredMods(talent).stream()
                        .allMatch(modId -> PlatformServices.mods().isLoaded(modId));
            }
        }
        return GSON.toJson(exported);
    }

    /** Installs a server-authoritative catalog in client memory without touching local files. */
    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized talent catalog was empty"
        );
        Objects.requireNonNull(catalog.rarityWeights, "Missing rarity_weights");
        Objects.requireNonNull(catalog.perks, "Missing perks");
        Objects.requireNonNull(
                catalog.apothicAttributeMappings,
                "Missing apothic_attribute_mappings"
        );
        // The server already filtered this catalog according to its installed mods. A
        // remote client's optional-mod set must not veto an offer the server considers valid.
        for (TalentJson talent : catalog.perks) {
            if (talent != null) {
                talent.requiresMod = null;
                talent.requiredMods = List.of();
            }
        }
        activeSnapshot = buildSnapshot(catalog, true);
    }

    /** Restores this installation's own catalog after leaving a remote server. */
    public static void resetSyncedCatalog() {
        activeSnapshot = localSnapshot;
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

            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                Catalog catalog = GSON.fromJson(reader, Catalog.class);
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
            }
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static ResourceLocation requireLocation(String value) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException("Invalid talent resource location: " + value);
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

    private static List<String> validateIds(List<String> ids, String field) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(id -> requireWireId(id, field)).toList();
    }

    private static Map<String, Double> validateStats(
            Map<String, Double> stats,
            String field,
            boolean requireNonNegative
    ) {
        if (stats == null) {
            return Map.of();
        }
        Map<String, Double> validated = new LinkedHashMap<>();
        stats.forEach((key, value) -> {
            String statId = requireWireId(key, field + " key");
            if (value == null || !Double.isFinite(value)
                    || (requireNonNegative && value < 0.0D)) {
                throw new IllegalStateException("Invalid " + field + " entry: " + key + "=" + value);
            }
            validated.put(statId, value);
        });
        return validated;
    }

    private static int requireWeight(int value, String tier) {
        if (value < 0) {
            throw new IllegalStateException("Negative talent rarity weight for " + tier);
        }
        return value;
    }

    private static Catalog loadBundledCatalog() throws Exception {
        return Objects.requireNonNull(
                GSON.fromJson(loadBundledCatalogJson(), Catalog.class),
                "Bundled talent catalog was empty"
        );
    }

    private static com.google.gson.JsonObject loadBundledCatalogJson() throws Exception {
        try (var stream = Perk.class.getResourceAsStream(
                "/assets/aegis_ascension/talents.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing default assets/aegis_ascension/talents.json"
                );
            }
            try (var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
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

    /** The narrow shape a dependent mod may ship: talents only. */
    private static final class AddonCatalog {
        private List<TalentJson> perks;
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
        @SerializedName("server_available")
        private Boolean serverAvailable;
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
        @SerializedName("excluded_perks")
        private List<String> excludedPerks;
    }

    private record CatalogSnapshot(
            List<Perk> values,
            Map<String, Perk> byId,
            Map<Tier, Integer> rarityWeights,
            List<ApothicAttributeMapping> apothicAttributeMappings
    ) {
    }
}
