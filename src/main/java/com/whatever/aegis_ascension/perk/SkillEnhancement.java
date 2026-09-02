package com.whatever.aegis_ascension.perk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.util.AegisModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/** A repeatable, data-driven skill enhancement. */
public final class SkillEnhancement {
    public static final String DEFAULT_PRIMARY_ID = "attack_damage";
    private static final Gson GSON = new Gson();
    private static final int MAX_CATALOG_ENTRIES = 256;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static final CatalogSnapshot LOCAL_SNAPSHOT = buildSnapshot(LOCAL_CATALOG);
    private static volatile CatalogSnapshot activeSnapshot = LOCAL_SNAPSHOT;
    private static final String SKILL_ENHANCEMENT = "skill_enhancement";


    private static CatalogSnapshot buildSnapshot(Catalog catalog) {
        Objects.requireNonNull(catalog.enhancements, "Missing enhancements");
        if (catalog.enhancements.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException(
                    "Too many skill enhancements: " + catalog.enhancements.size()
            );
        }
        List<SkillEnhancement> values = new ArrayList<>();
        Map<String, SkillEnhancement> byId = new LinkedHashMap<>();
        for (EnhancementJson definition : catalog.enhancements) {
            Objects.requireNonNull(definition, "Null skill enhancement entry");
            String id = requireWireId(definition.id, "Skill enhancement id");
            ResourceLocation attributeId = blank(definition.attribute)
                    ? null : requireLocation(definition.attribute, "attribute");
            String customStat = blank(definition.customStat)
                    ? null : requireWireId(definition.customStat, id + " custom_stat");
            if ((attributeId == null) == (customStat == null)) {
                throw new IllegalStateException(
                        "Skill enhancement " + id
                                + " must define exactly one of attribute or custom_stat"
                );
            }
            if (!Double.isFinite(definition.amount) || definition.amount == 0.0D) {
                throw new IllegalStateException(
                        "Skill enhancement " + id + " has an invalid amount"
                );
            }

            SkillEnhancement enhancement = new SkillEnhancement(
                    id,
                    requireText(definition.name, id + " name"),
                    requireText(definition.description, id + " description"),
                    requireLocation(definition.icon, "icon"),
                    Math.max(1, definition.iconTextureSize),
                    attributeId,
                    customStat,
                    definition.amount,
                    attributeId == null ? AttributeOperation.ADDITION
                            : requireOperation(definition.operation),
                    DisplayFormat.fromJson(definition.displayFormat),
                    definition.affectedByAllSkillEnhancementAttribute == null
                            || definition.affectedByAllSkillEnhancementAttribute
            );
            if (byId.put(enhancement.id, enhancement) != null) {
                throw new IllegalStateException(
                        "Duplicate skill enhancement id: " + enhancement.id
                );
            }
            values.add(enhancement);
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("Skill enhancement catalog is empty");
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
    private final int iconTextureSize;
    private final ResourceLocation attributeId;
    private final String customStat;
    private final double amount;
    private final AttributeOperation operation;
    private final DisplayFormat displayFormat;
    private final boolean affectedByAllSkillEnhancementAttribute;
    private final UUID modifierId;
    private final UUID allSkillEnhancementAttributeModifierId;

    private SkillEnhancement(String id, String nameKey, String descriptionKey,
                             ResourceLocation iconTexture, int iconTextureSize,
                             ResourceLocation attributeId, String customStat, double amount,
                             AttributeOperation operation, DisplayFormat displayFormat,
                             boolean affectedByAllSkillEnhancementAttribute) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.iconTexture = iconTexture;
        this.iconTextureSize = iconTextureSize;
        this.attributeId = attributeId;
        this.customStat = customStat;
        this.amount = amount;
        this.operation = operation;
        this.displayFormat = displayFormat;
        this.affectedByAllSkillEnhancementAttribute =
                affectedByAllSkillEnhancementAttribute;
        this.modifierId = AegisModifiers.mint(SKILL_ENHANCEMENT + GeneralConstants.SLASH + id);
        this.allSkillEnhancementAttributeModifierId =
                AegisModifiers.mint(TalentConstants.ALL_SKILL_ENHANCEMENT_ATTRIBUTE + GeneralConstants.SLASH + id);
    }

    public String id() {
        return id;
    }

    public Component title() {
        return getTranslatableString(nameKey);
    }

    public Component description() {
        return getTranslatableString(descriptionKey, formattedAmount());
    }

    public ResourceLocation iconTexture() {
        return iconTexture;
    }

    public int iconTextureSize() {
        return iconTextureSize;
    }

    public Optional<Attribute> attribute() {
        if (attributeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(GeneralServerMethods.resolveAttribute(attributeId));
    }

    public Optional<String> customStat() {
        return Optional.ofNullable(customStat);
    }

    public double amount() {
        return amount;
    }

    public AttributeOperation operation() {
        return operation;
    }

    public UUID modifierId() {
        return modifierId;
    }

    public boolean affectedByAllSkillEnhancementAttribute() {
        return affectedByAllSkillEnhancementAttribute;
    }

    public UUID allSkillEnhancementAttributeModifierId() {
        return allSkillEnhancementAttributeModifierId;
    }

    public static List<SkillEnhancement> values() {
        return activeSnapshot.values();
    }

    public static Optional<SkillEnhancement> byId(String id) {
        return Optional.ofNullable(activeSnapshot.byId().get(id));
    }

    /** Serializes the server's effective, validated catalog for the login snapshot. */
    public static String exportCatalogJson() {
        return GSON.toJson(LOCAL_CATALOG);
    }

    /** Installs a server-authoritative catalog in client memory without touching local files. */
    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized skill enhancement catalog was empty"
        );
        Objects.requireNonNull(catalog.enhancements, "Missing enhancements");
        activeSnapshot = buildSnapshot(catalog);
    }

    /** Restores this installation's own catalog after leaving a remote server. */
    public static void resetSyncedCatalog() {
        activeSnapshot = LOCAL_SNAPSHOT;
    }

    public static SkillEnhancement defaultPrimary() {
        return byId(DEFAULT_PRIMARY_ID).orElseThrow(() -> new IllegalStateException(
                "Missing default primary skill enhancement: " + DEFAULT_PRIMARY_ID
        ));
    }

    private String formattedAmount() {
        double displayed = displayFormat == DisplayFormat.PERCENT ? amount * 100.0D : amount;
        if (Math.abs(displayed - Math.rint(displayed)) < 1.0E-9D) {
            return String.format(Locale.ROOT, "%.0f%s", displayed,
                    displayFormat == DisplayFormat.PERCENT ? "%" : "");
        }
        return String.format(Locale.ROOT, "%.2f%s", displayed,
                        displayFormat == DisplayFormat.PERCENT ? "%" : "")
                .replaceAll("0+(%?)$", "$1")
                .replaceAll("\\.(%?)$", "$1");
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("skill_enhancements.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = SkillEnhancement.class.getResourceAsStream(
                        "/assets/aegis_ascension/skill_enhancements.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/skill_enhancements.json"
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
            Objects.requireNonNull(catalog, "Skill enhancement catalog was empty");
            Objects.requireNonNull(catalog.enhancements, "Missing enhancements");
            return catalog;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
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

    private static void appendBundledEnhancementIfMissing(
            JsonObject configured,
            JsonObject bundled,
            String enhancementId) {
        JsonArray configuredEntries = Objects.requireNonNull(
                configured.getAsJsonArray("enhancements"),
                "Missing enhancements"
        );
        if (findEnhancement(configuredEntries, enhancementId) != null) {
            return;
        }
        JsonObject bundledEntry = Objects.requireNonNull(
                findEnhancement(bundled.getAsJsonArray("enhancements"), enhancementId),
                "Bundled catalog is missing " + enhancementId
        );
        configuredEntries.add(bundledEntry.deepCopy());
    }

    private static JsonObject findEnhancement(JsonArray entries, String enhancementId) {
        if (entries == null) {
            return null;
        }
        for (var entry : entries) {
            if (entry.isJsonObject()
                    && entry.getAsJsonObject().has("id")
                    && enhancementId.equals(
                    entry.getAsJsonObject().get("id").getAsString())) {
                return entry.getAsJsonObject();
            }
        }
        return null;
    }

    private static JsonObject loadBundledCatalogJson() throws Exception {
        try (var stream = SkillEnhancement.class.getResourceAsStream(
                "/assets/aegis_ascension/skill_enhancements.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing default assets/aegis_ascension/skill_enhancements.json"
                );
            }
            try (var reader = new java.io.InputStreamReader(
                    stream,
                    StandardCharsets.UTF_8
            )) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResourceLocation requireLocation(String value, String field) {
        ResourceLocation location = PlatformServices.resources().tryParse(value);
        if (location == null) {
            throw new IllegalStateException(
                    "Invalid skill enhancement " + field + " resource location: " + value
            );
        }
        return location;
    }

    private static AttributeOperation requireOperation(String value) {
        return switch (Objects.requireNonNull(value, "Missing attribute operation")) {
            case "addition" -> AttributeOperation.ADDITION;
            case "multiply_base" -> AttributeOperation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeOperation.MULTIPLY_TOTAL;
            default -> throw new IllegalStateException(
                    "Unknown skill enhancement attribute operation: " + value
            );
        };
    }

    private enum DisplayFormat {
        NUMBER,
        PERCENT;

        private static DisplayFormat fromJson(String value) {
            return switch (Objects.requireNonNull(value, "Missing display_format")) {
                case "number" -> NUMBER;
                case "percent" -> PERCENT;
                default -> throw new IllegalStateException(
                        "Unknown skill enhancement display_format: " + value
                );
            };
        }
    }

    private static final class Catalog {
        private List<EnhancementJson> enhancements = List.of();
    }

    private static final class EnhancementJson {
        private String id;
        private String name;
        private String description;
        private String icon;
        @SerializedName("icon_texture_size")
        private int iconTextureSize = 16;
        private String attribute;
        @SerializedName("custom_stat")
        private String customStat;
        private double amount;
        private String operation = "addition";
        @SerializedName("display_format")
        private String displayFormat = "number";
        @SerializedName("affected_by_all_skill_enhancement_attribute")
        private Boolean affectedByAllSkillEnhancementAttribute;
    }

    private record CatalogSnapshot(
            List<SkillEnhancement> values,
            Map<String, SkillEnhancement> byId
    ) {
    }
}
