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
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p>The same file also describes the tab's list view — how many columns it uses, how tall
 * a row is, and which stats are grouped under which heading. A config written before the
 * list view existed simply has no {@code list_view} block, and {@link #listView()} hands
 * back defaults that show every stat in one ungrouped column pair.</p>
 */
public final class CustomStatSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("custom_stat_setting.json");

    private static CustomStatSettings instance;

    /** Stat id to icon override. Absent stats keep their built-in icon. */
    public Map<String, IconOverride> overrides = new LinkedHashMap<>();

    /** Layout of the tab's list view. Absent means {@link ListView#DEFAULTS}. */
    @SerializedName("list_view")
    public ListView listView;

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

    /**
     * The icon to draw beside a stat in the list view, or empty to fall back to the card
     * icon. Separate from {@link #icon} because the two are read at very different sizes:
     * a 28px portrait that reads clearly on a card turns to mush on a 16px row, so the
     * shipped file points list rows at small, flat vanilla item textures instead.
     */
    public Optional<ListIcon> listIcon(String statKey) {
        IconOverride override = overrides.get(statKey);
        if (override == null || override.listIcon == null || override.listIcon.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation parsed = ResourceLocation.tryParse(override.listIcon);
        if (parsed == null) {
            warn(statKey, "list_icon is not a valid texture path: " + override.listIcon);
            return Optional.empty();
        }
        int size = override.listIconSize > 0 ? override.listIconSize : 16;
        return Optional.of(new ListIcon(parsed, size));
    }

    /** The list view's layout, with hand-edited values clamped back into range. */
    public ListView listView() {
        ListView configured = listView;
        if (configured == null) {
            return ListView.DEFAULTS;
        }
        configured.columns = Math.max(1, Math.min(4, configured.columns));
        configured.rowHeight = Math.max(12, Math.min(32, configured.rowHeight));
        if (configured.groups == null) {
            configured.groups = new ArrayList<>();
        }
        return configured;
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
        /** A texture path drawn beside this stat in the list view. Any namespace, so a
         * vanilla item texture such as {@code minecraft:textures/item/iron_sword.png}
         * works as well as one of this mod's. */
        @SerializedName("list_icon")
        public String listIcon;
        /**
         * Edge length of the {@link #listIcon} file in pixels, so it can be scaled down
         * whole rather than cropped. 16 (a vanilla item texture) when unset.
         */
        @SerializedName("list_icon_size")
        public int listIconSize;
    }

    /** A resolved list-view icon and the size of the file it came from. */
    public record ListIcon(ResourceLocation texture, int textureSize) {
    }

    /** How the list view arranges its rows. */
    public static final class ListView {
        static final ListView DEFAULTS = new ListView();

        /** Side-by-side stat columns; 1-4. */
        public int columns = 2;
        /** Height of one stat row in pixels; 12-32. */
        @SerializedName("row_height")
        public int rowHeight = 18;
        /** Whether each group draws its {@code title_key} above its rows. */
        @SerializedName("show_group_headers")
        public boolean showGroupHeaders = true;
        /**
         * Ordered groups. Stats listed here appear in this order; any stat the file leaves
         * out is appended afterwards, so a stat added by a mod update still shows up in a
         * config written before it existed.
         */
        public List<Group> groups = new ArrayList<>();
    }

    /** One titled run of stats in the list view. */
    public static final class Group {
        /** Lang key for the heading above this group. */
        @SerializedName("title_key")
        public String titleKey;
        /** Stat ids, in the order they should be listed. */
        public List<String> stats = new ArrayList<>();
    }
}
