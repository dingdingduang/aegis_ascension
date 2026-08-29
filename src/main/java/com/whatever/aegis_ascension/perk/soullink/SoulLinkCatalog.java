package com.whatever.aegis_ascension.perk.soullink;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.AegisAscensionMod;
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
    private static final List<SoulLink> VALUES = load();

    private SoulLinkCatalog() {
    }

    public static List<SoulLink> values() {
        return VALUES;
    }

    private static List<SoulLink> load() {
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
            Objects.requireNonNull(catalog, "Soul Link catalog was empty");
            Objects.requireNonNull(catalog.soulLinks, "Missing soul_links");

            List<SoulLink> values = new ArrayList<>();
            Map<String, SoulLink> byId = new LinkedHashMap<>();
            for (SoulLinkJson entry : catalog.soulLinks) {
                String id = requireText(entry.id, "Soul Link id");
                SoulLink link = new SoulLink(
                        id,
                        requireText(entry.synergyName, id + " synergy_name"),
                        requireText(entry.description, id + " description"),
                        requireLocation(entry.icon, id),
                        entry.requiredPerks == null ? List.of() : entry.requiredPerks,
                        entry.rankPerks == null ? List.of() : entry.rankPerks,
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
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void validateNumbers(SoulLink link) {
        link.bonusStats().forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
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
