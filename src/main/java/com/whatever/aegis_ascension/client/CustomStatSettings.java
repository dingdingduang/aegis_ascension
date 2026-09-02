package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.perk.SoulLink;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-only icon overrides for the Custom Stats tab.
 *
 * <p>Entirely local, for the same reason {@link DevourClientSettings} is: which picture sits
 * beside a stat is a reading of that stat, not a property the server stores or acts on.
 * Nothing here changes a value, so this file never has to agree with the server or with
 * another player's copy.</p>
 *
 * <p>Persisted at {@code config/aegis_ascension/custom_stat_setting.json}, seeded on first
 * run from the bundled asset of the same name. Only listed stats change; everything else
 * keeps the icon built into the tab.</p>
 */
public final class CustomStatSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("custom_stat_setting.json");

    private static CustomStatSettings instance;

    /** Stat id to icon override. Absent stats keep their built-in icon. */
    public Map<String, IconOverride> overrides = new LinkedHashMap<>();

    private CustomStatSettings() {
    }

    public static CustomStatSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * The icon to draw for a stat, or empty to keep the built-in one.
     *
     * <p>A bad id or texture path is a warning rather than a throw. The hardcoded
     * definitions can afford to fail loudly because a broken one is a build error; this
     * file is hand-edited at runtime, and a typo must not take the screen down with it.</p>
     */
    public Optional<ResourceLocation> icon(String statKey) {
        IconOverride override = overrides.get(statKey);
        if (override == null) {
            return Optional.empty();
        }
        // icon wins when it parses; otherwise fall through to source, which is the entry's
        // default. Only when neither resolves does the built-in icon stand.
        if (override.icon != null && !override.icon.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(override.icon);
            if (parsed != null) {
                return Optional.of(parsed);
            }
            warn(statKey, "icon is not a valid texture path, falling back to source: "
                    + override.icon);
        }
        if (override.source == null || override.source.isBlank()) {
            return Optional.empty();
        }
        Optional<ResourceLocation> fromSource = Perk.byId(override.source)
                .map(Perk::iconTexture)
                .or(() -> Aegis.byId(override.source).map(Aegis::iconTexture))
                .or(() -> Perk.soulLinkById(override.source).map(SoulLink::iconTexture))
                .or(() -> SkillEnhancement.byId(override.source)
                        .map(SkillEnhancement::iconTexture));
        if (fromSource.isEmpty()) {
            warn(statKey, "no talent, Aegis, Soul Link, or Skill Enhancement with id "
                    + override.source);
        }
        return fromSource;
    }

    private static void warn(String statKey, String problem) {
        AegisAscensionMod.getLogger().warn(
                "Ignoring custom stat icon override for {}: {}", statKey, problem);
    }

    private static CustomStatSettings load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = CustomStatSettings.class.getResourceAsStream(
                        "/assets/aegis_ascension/custom_stat_setting.json")) {
                    if (stream != null) {
                        Files.copy(stream, FILE);
                    }
                }
            }
            if (Files.exists(FILE)) {
                try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    CustomStatSettings loaded = GSON.fromJson(reader, CustomStatSettings.class);
                    if (loaded != null) {
                        if (loaded.overrides == null) {
                            loaded.overrides = new LinkedHashMap<>();
                        }
                        return loaded;
                    }
                }
            }
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}", FILE, exception);
        }
        return new CustomStatSettings();
    }

    /** One stat's override: borrow another entry's icon, or point at a texture directly. */
    public static final class IconOverride {
        /** A talent, Aegis, Soul Link, or Skill Enhancement id whose icon this stat uses. */
        public String source;
        /** A texture path, used instead of {@link #source} for a bespoke image. */
        public String icon;
    }
}
