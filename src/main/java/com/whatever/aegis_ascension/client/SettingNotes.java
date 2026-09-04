package com.whatever.aegis_ascension.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingNotes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("general_setting_text_clientside.json");

    private static SettingNotes instance;

    public Map<String, String> clientSettings = new LinkedHashMap<>();
    public Map<String, String> serverSettings = new LinkedHashMap<>();

    public static String client(String key) {
        return lookup(get().clientSettings, key);
    }

    public static String server(String key) {
        return lookup(get().serverSettings, key);
    }

    private static String lookup(Map<String, String> notes, String key) {
        String text = notes.get(key);
        return text == null || text.isBlank() ? null : text;
    }

    private static SettingNotes get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static SettingNotes load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = SettingNotes.class.getResourceAsStream(
                        "/assets/aegis_ascension/general_setting_text_clientside.json")) {
                    if (stream != null) {
                        Files.copy(stream, FILE);
                    }
                }
            }
            if (Files.exists(FILE)) {
                try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    SettingNotes loaded = GSON.fromJson(reader, SettingNotes.class);
                    if (loaded != null) {
                        if (loaded.clientSettings == null) {
                            loaded.clientSettings = new LinkedHashMap<>();
                        }
                        if (loaded.serverSettings == null) {
                            loaded.serverSettings = new LinkedHashMap<>();
                        }
                        return loaded;
                    }
                }
            }
        } catch (IOException | JsonSyntaxException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}", FILE, exception);
        }
        return new SettingNotes();
    }
}
