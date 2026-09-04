package com.whatever.aegis_ascension.client;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How Skill Enhancements are presented, kept entirely on the client.
 *
 * <p>skill_enhancement_serverside.json holds what an enhancement does and belongs to the server:
 * it is synced to every client on join, so anything in it is the server owner's choice.
 * A title, a description, and an icon change nothing the server acts on, so they live
 * here instead, where they stay yours whether you are offline or connected.</p>
 *
 * <p>Persisted at {@code config/aegis_ascension/skill_enhancement_clientside.json},
 * seeded on first run from the bundled asset of the same name.</p>
 */
public final class SkillEnhancementClientSettings {
    private static final ResourceLocation FALLBACK_ICON = ResourceLocation.tryParse("minecraft:textures/item/book.png");
    private static final int FALLBACK_ICON_SIZE = 16;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("skill_enhancement_clientside.json");

    private static SkillEnhancementClientSettings instance;

    public Map<String, Entry> entries = new LinkedHashMap<>();

    private SkillEnhancementClientSettings() {
    }

    public static SkillEnhancementClientSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** The enhancement's title, or its raw id when this file does not describe it. */
    public static Component title(SkillEnhancement enhancement) {
        Entry entry = get().entries.get(enhancement.id());
        if (entry == null || entry.name == null || entry.name.isBlank()) {
            return getLiteralString(enhancement.id());
        }
        return getTranslatableString(entry.name);
    }

    /**
     * The enhancement's description, with the per-rank amount written in. The value is
     * the catalogue's; only how it is written is decided here.
     */
    public static Component description(SkillEnhancement enhancement) {
        Entry entry = get().entries.get(enhancement.id());
        String amount = formattedAmount(enhancement);
        if (entry == null || entry.description == null || entry.description.isBlank()) {
            return getLiteralString(amount);
        }
        return getTranslatableString(entry.description, amount);
    }

    /**
     * The per-rank amount as text. A percent entry writes {@code 0.02} as {@code 2%}; a
     * number entry writes it as-is. Trailing zeroes are trimmed either way.
     *
     * <p>Editing an entry's {@code display_format} changes only this string. The amount
     * an enhancement actually grants comes from skill_enhancement_serverside.json and is untouched
     * by anything in this file.</p>
     */
    public static String formattedAmount(SkillEnhancement enhancement) {
        Entry entry = get().entries.get(enhancement.id());
        boolean percent = entry != null && "percent".equals(entry.displayFormat);
        double displayed = percent ? enhancement.amount() * 100.0D : enhancement.amount();
        String suffix = percent ? "%" : "";
        if (Math.abs(displayed - Math.rint(displayed)) < 1.0E-9D) {
            return String.format(Locale.ROOT, "%.0f%s", displayed, suffix);
        }
        return String.format(Locale.ROOT, "%.2f%s", displayed, suffix)
                .replaceAll("0+(%?)$", "$1")
                .replaceAll("\\.(%?)$", "$1");
    }

    /** The texture to draw, falling back to a neutral one for an undescribed entry. */
    public static ResourceLocation icon(SkillEnhancement enhancement) {
        Entry entry = get().entries.get(enhancement.id());
        if (entry == null || entry.icon == null || entry.icon.isBlank()) {
            return FALLBACK_ICON;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(entry.icon);
        if (parsed == null) {
            warn(enhancement.id(), "icon is not a valid texture path: " + entry.icon);
            return FALLBACK_ICON;
        }
        return parsed;
    }

    /** The square size that texture is authored at. */
    public static int iconSize(SkillEnhancement enhancement) {
        Entry entry = get().entries.get(enhancement.id());
        if (entry == null || entry.iconTextureSize <= 0) {
            return FALLBACK_ICON_SIZE;
        }
        return entry.iconTextureSize;
    }

    private static void warn(String enhancementId, String problem) {
        AegisAscensionMod.getLogger().warn(
                "Ignoring Skill Enhancement presentation for {}: {}", enhancementId, problem);
    }

    private static SkillEnhancementClientSettings load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = SkillEnhancementClientSettings.class.getResourceAsStream(
                        "/assets/aegis_ascension/skill_enhancement_clientside.json")) {
                    if (stream != null) {
                        Files.copy(stream, FILE);
                    }
                }
            }
            if (Files.exists(FILE)) {
                try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    SkillEnhancementClientSettings loaded =
                            GSON.fromJson(reader, SkillEnhancementClientSettings.class);
                    if (loaded != null) {
                        if (loaded.entries == null) {
                            loaded.entries = new LinkedHashMap<>();
                        }
                        return loaded;
                    }
                }
            }
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}", FILE, exception);
        }
        return new SkillEnhancementClientSettings();
    }

    public static final class Entry {
        public String name;
        public String description;
        public String icon;
        @SerializedName("icon_texture_size")
        public int iconTextureSize = 16;
        @SerializedName("display_format")
        public String displayFormat = "number";
    }
}
