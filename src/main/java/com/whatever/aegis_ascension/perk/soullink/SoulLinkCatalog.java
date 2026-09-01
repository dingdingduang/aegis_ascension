package com.whatever.aegis_ascension.perk.soullink;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads the independently editable config/aegis_ascension/soul_links.json catalog. */
public final class SoulLinkCatalog {
    private static final Gson GSON = new Gson();
    private static final int MAX_CATALOG_ENTRIES = 256;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    /** Soul Links contributed by dependent mods, by owning mod id, in registration order. */
    private static final Map<String, List<SoulLinkJson>> REGISTERED_SOUL_LINKS =
            new LinkedHashMap<>();
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static volatile Catalog effectiveCatalog = buildEffectiveCatalog();
    private static volatile List<SoulLink> localValues = buildValues(effectiveCatalog);
    private static volatile List<SoulLink> activeValues = localValues;

    private SoulLinkCatalog() {
    }

    public static List<SoulLink> values() {
        return activeValues;
    }

    /**
     * Registers the Soul Links a dependent mod ships at
     * {@code assets/<modId>/soul_links.json} inside its own jar.
     *
     * <p>Every id must be namespaced to the registering mod. Call this during mod setup,
     * after registering any addon talents referenced by these Soul Links. The complete
     * candidate catalog and every talent reference are validated before live state changes.</p>
     *
     * @return the number of Soul Links registered for this mod
     */
    public static synchronized int registerAddonSoulLinks(String modId) {
        String namespace = requireModId(modId);
        requireRegistrationWindow(namespace);
        if (!PlatformServices.mods().isLoaded(namespace)) {
            throw new IllegalStateException(
                    "Cannot register Soul Links for a mod that is not loaded: " + namespace
            );
        }
        String resourcePath = "assets/" + namespace + "/soul_links.json";
        Path file = PlatformServices.mods()
                .findModResource(namespace, resourcePath)
                .orElseThrow(() -> new IllegalStateException(
                        "Mod " + namespace + " ships no " + resourcePath
                ));
        List<SoulLinkJson> soulLinks = readAddonSoulLinks(namespace, file);

        Map<String, List<SoulLinkJson>> candidate = new LinkedHashMap<>(REGISTERED_SOUL_LINKS);
        candidate.put(namespace, soulLinks);
        Catalog effective = buildEffectiveCatalog(candidate);
        List<SoulLink> rebuilt;
        try {
            rebuilt = buildValues(effective);
            validatePerkReferences(rebuilt);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Soul Links from mod " + namespace + " were rejected: "
                            + exception.getMessage(),
                    exception
            );
        }

        REGISTERED_SOUL_LINKS.clear();
        REGISTERED_SOUL_LINKS.putAll(candidate);
        boolean localActive = activeValues == localValues;
        effectiveCatalog = effective;
        localValues = rebuilt;
        if (localActive) {
            activeValues = rebuilt;
        }
        return soulLinks.size();
    }

    /** Mod ids that have registered Soul Links, in registration order. */
    public static List<String> registeredAddonMods() {
        synchronized (SoulLinkCatalog.class) {
            return List.copyOf(REGISTERED_SOUL_LINKS.keySet());
        }
    }

    /** Returns the server/local catalog used as the source of truth for login synchronization. */
    public static String exportCatalogJson() {
        return GSON.toJson(effectiveCatalog);
    }

    /** Installs the server-authoritative catalog on a remote client. */
    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synced Soul Link catalog was empty"
        );
        activeValues = buildValues(catalog);
    }

    public static void resetSyncedCatalog() {
        activeValues = localValues;
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("soul_links.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = SoulLinkCatalog.class.getResourceAsStream(
                        "/assets/aegis_ascension/soul_links.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/soul_links.json"
                        );
                    }
                    Files.copy(stream, configPath);
                }
            }

            Catalog catalog;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                catalog = GSON.fromJson(reader, Catalog.class);
            }
            return Objects.requireNonNull(catalog, "Soul Link catalog was empty");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void requireRegistrationWindow(String modId) {
        if (PlatformServices.server().currentServer() != null) {
            throw new IllegalStateException(
                    "Mod " + modId + " registered Soul Links after the server started."
                            + " Register during mod setup so progression catalogs remain stable."
            );
        }
    }

    private static List<SoulLinkJson> readAddonSoulLinks(String namespace, Path file) {
        AddonCatalog catalog;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            catalog = GSON.fromJson(reader, AddonCatalog.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not read Soul Links for mod " + namespace + " from " + file,
                    exception
            );
        }
        if (catalog == null || catalog.soulLinks == null || catalog.soulLinks.isEmpty()) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared no Soul Links in " + file
            );
        }
        List<SoulLinkJson> soulLinks = new ArrayList<>(catalog.soulLinks.size());
        for (SoulLinkJson entry : catalog.soulLinks) {
            soulLinks.add(prepareAddonSoulLink(namespace, entry));
        }
        return List.copyOf(soulLinks);
    }

    private static SoulLinkJson prepareAddonSoulLink(
            String namespace,
            SoulLinkJson entry
    ) {
        if (entry == null) {
            throw new IllegalStateException(
                    "Mod " + namespace + " declared a null Soul Link entry"
            );
        }
        requireAddonId(namespace, entry.id);
        requireText(entry.synergyName, entry.id + " synergy_name");
        requireText(entry.description, entry.id + " description");
        requireLocation(entry.icon, entry.id);
        return entry;
    }

    private static void requireAddonId(String namespace, String value) {
        ResourceLocation location = value == null
                ? null
                : PlatformServices.resources().tryParse(value);
        if (location == null || !namespace.equals(location.getNamespace())
                || value.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(
                    "Soul Link id " + value + " from mod " + namespace
                            + " must be a valid namespaced id under " + namespace + ":"
            );
        }
    }

    private static Catalog buildEffectiveCatalog() {
        return buildEffectiveCatalog(REGISTERED_SOUL_LINKS);
    }

    private static Catalog buildEffectiveCatalog(
            Map<String, List<SoulLinkJson>> registered
    ) {
        Catalog effective = new Catalog();
        List<SoulLinkJson> soulLinks = new ArrayList<>(LOCAL_CATALOG.soulLinks);
        registered.values().forEach(soulLinks::addAll);
        effective.soulLinks = List.copyOf(soulLinks);
        return effective;
    }

    private static void validatePerkReferences(List<SoulLink> soulLinks) {
        for (SoulLink soulLink : soulLinks) {
            for (String perkId : soulLink.requirements()) {
                if (Perk.byId(perkId).isEmpty()) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing talent " + perkId
                    );
                }
            }
            for (String perkId : soulLink.rankPerks()) {
                if (Perk.byId(perkId).isEmpty()) {
                    throw new IllegalStateException(
                            "Soul Link " + soulLink.id()
                                    + " references missing rank talent " + perkId
                    );
                }
            }
        }
    }

    private static List<SoulLink> buildValues(Catalog catalog) {
        Objects.requireNonNull(catalog.soulLinks, "Missing soul_links");
        if (catalog.soulLinks.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException("Too many Soul Links: " + catalog.soulLinks.size());
        }

        List<SoulLink> values = new ArrayList<>();
        Map<String, SoulLink> byId = new LinkedHashMap<>();
        for (SoulLinkJson entry : catalog.soulLinks) {
            Objects.requireNonNull(entry, "Null Soul Link entry");
            String id = requireWireId(entry.id, "Soul Link id");
            SoulLink link = new SoulLink(
                    id,
                    requireText(entry.synergyName, id + " synergy_name"),
                    requireText(entry.description, id + " description"),
                    requireLocation(entry.icon, id),
                    validateIds(entry.requiredPerks, id + " required_perks"),
                    validateIds(entry.rankPerks, id + " rank_perks"),
                    entry.bonusStats == null ? Map.of() : entry.bonusStats,
                    entry.enabled,
                    entry.sourceRow
            );
            if (byId.put(id, link) != null) {
                throw new IllegalStateException("Duplicate Soul Link id: " + id);
            }
            validateNumbers(link);
            values.add(link);
        }
        return List.copyOf(values);
    }

    private static void validateNumbers(SoulLink link) {
        link.bonusStats().forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_WIRE_ID_LENGTH
                    || value == null || !Double.isFinite(value)) {
                throw new IllegalStateException(
                        "Invalid bonus_stats entry in Soul Link " + link.id() + ": " + key
                );
            }
        });
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

    private static String requireModId(String value) {
        String modId = requireText(value, "mod id").trim();
        if (!modId.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalStateException("Invalid mod id: " + value);
        }
        return modId;
    }

    private static List<String> validateIds(List<String> ids, String field) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(id -> requireWireId(id, field)).toList();
    }

    private static ResourceLocation requireLocation(String value, String id) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException(
                    "Invalid icon resource location for Soul Link " + id + ": " + value
            );
        }
        return location;
    }

    private static final class Catalog {
        @SerializedName("soul_links")
        private List<SoulLinkJson> soulLinks = List.of();
    }

    /** The narrow shape a dependent mod may ship: Soul Links only. */
    private static final class AddonCatalog {
        @SerializedName("soul_links")
        private List<SoulLinkJson> soulLinks;
    }

    private static final class SoulLinkJson {
        private String id;
        @SerializedName("required_perks")
        private List<String> requiredPerks = List.of();
        @SerializedName("rank_perks")
        private List<String> rankPerks = List.of();
        @SerializedName("synergy_name")
        private String synergyName;
        private String description;
        private String icon;
        @SerializedName("bonus_stats")
        private Map<String, Double> bonusStats = Map.of();
        private boolean enabled;
        @SerializedName("source_row")
        private int sourceRow;
    }
}
