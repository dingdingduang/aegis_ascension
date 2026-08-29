package com.whatever.aegis_ascension.aegis;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fox God's Aegis ward casting settings, read from {@code config/mwisa_aegis_ascension_fox_aegis_wards.json}.
 *
 * <p>That file is owned and seeded by the ward addon, which also reads the ward behavior
 * fields from it (model, lifespan, action cadence, damage/heal scaling). This mod reads
 * only the casting fields it needs — which spell summons each ward, at what level, how
 * many, and the re-summon cycle — so one file configures wards for both mods with no
 * cross-mod class dependency.</p>
 *
 * <p>The wards themselves come from the addon, so when it is not installed the file will
 * not exist; the built-in defaults below keep this class working and Fox God's Aegis
 * simply has no ward spells to cast.</p>
 */
public final class FoxAegisWards {
    /** Shared with the ward addon, which seeds this file. */
    private static final String FILE_NAME = "mwisa_aegis_ascension_fox_aegis_wards.json";

    private static final Gson GSON = new Gson();
    private static volatile Config config;

    private FoxAegisWards() {
    }

    public static double durationSeconds() {
        return get().wardDurationSeconds;
    }

    public static WardType wardI() {
        return get().wardTypeI;
    }

    public static WardType wardII() {
        return get().wardTypeII;
    }

    public static WardType wardIII() {
        return get().wardTypeIII;
    }

    /** Drops the cache so the next read re-parses the file from disk. */
    public static void reload() {
        synchronized (FoxAegisWards.class) {
            config = null;
        }
    }

    private static Config get() {
        Config loaded = config;
        if (loaded == null) {
            synchronized (FoxAegisWards.class) {
                if (config == null) {
                    config = load();
                }
                loaded = config;
            }
        }
        return loaded;
    }

    /**
     * Reads the addon's config if present. Unlike this mod's own configs, the file is not
     * seeded here — the addon owns it — so a missing file is normal and falls back to
     * defaults rather than failing.
     */
    private static Config load() {
        Path path = PlatformServices.paths().configDirectory().resolve(FILE_NAME);
        try {
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Config loaded = GSON.fromJson(reader, Config.class);
                    if (loaded != null) {
                        return loaded.sanitized();
                    }
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error(
                    "Failed to read {}; Fox God's Aegis is using ward defaults", FILE_NAME, exception);
        }
        return new Config().sanitized();
    }

    /**
     * One ward type's cast settings. The same JSON object also carries the ward behavior
     * fields the addon reads; they are simply ignored here.
     */
    public static final class WardType {
        @SerializedName("spell_id")
        private String spellId = "";
        @SerializedName("spell_level")
        private int spellLevel = 1;
        private int count = 1;

        public String spellId() {
            return spellId == null ? "" : spellId;
        }

        public int spellLevel() {
            return Math.max(1, spellLevel);
        }

        public int count() {
            return Math.max(1, count);
        }
    }

    private static final class Config {
        @SerializedName("ward_duration_seconds")
        private double wardDurationSeconds = 30.0D;
        @SerializedName("ward_type_i")
        private WardType wardTypeI = new WardType();
        @SerializedName("ward_type_ii")
        private WardType wardTypeII = new WardType();
        @SerializedName("ward_type_iii")
        private WardType wardTypeIII = new WardType();

        private Config sanitized() {
            if (wardDurationSeconds <= 0.0D) {
                wardDurationSeconds = 30.0D;
            }
            if (wardTypeI == null) {
                wardTypeI = new WardType();
            }
            if (wardTypeII == null) {
                wardTypeII = new WardType();
            }
            if (wardTypeIII == null) {
                wardTypeIII = new WardType();
            }
            return this;
        }
    }
}
