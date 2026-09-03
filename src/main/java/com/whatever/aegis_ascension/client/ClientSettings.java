package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only UI preferences for the ACG screen: drawer position memory, offer-card
 * sizing, background opacity, quest tracker scale, and tracker page size. Purely cosmetic/local — never touches server state or
 * gameplay values, so it's safe to read/write freely without going through the perk
 * server data sync.
 *
 * <p>Persisted as JSON at {@code config/aegis_ascension/clientsetting.json}, seeded from the
 * bundled {@code assets/aegis_ascension/clientsetting.json} on first run exactly like
 * {@link com.whatever.aegis_ascension.aegis.Aegis}'s catalog. Unlike that catalog this file is
 * also written back by {@link #save()} whenever a setting changes, but the initial contents
 * always come from the shipped asset rather than from serialising the fields below.</p>
 */
public final class ClientSettings {
    public enum InventoryMode {
        ORIGINAL,
        INVENTORY_AND_CRAFTING
    }

    /** How the Player Custom Stat tab draws its stats. */
    public enum CustomStatView {
        /** The icon-and-breakdown card grid. */
        CARDS,
        /** The dense icon + name + value rows from a classic RPG status panel. */
        LIST
    }

    /** Screen corner a HUD element anchors to before its offset is applied. */
    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("clientsetting.json");

    /** Shared with the settings panel's sliders so the clamp range and the slider range never drift apart. */
    public static final int MIN_CARD_WIDTH = 60;
    public static final int MAX_CARD_WIDTH = 200;
    public static final int MIN_CARD_HEIGHT = 90;
    public static final int MAX_CARD_HEIGHT = 300;
    /** Logical scale for the accepted-quest HUD tracker, persisted per client. */
    public static final double MIN_QUEST_TRACKER_SCALE = 0.60D;
    public static final double MAX_QUEST_TRACKER_SCALE = 1.60D;
    public static final int MIN_QUEST_TRACKER_QUEST_LIMIT = 1;
    public static final int MAX_QUEST_TRACKER_QUEST_LIMIT = 20;

    private static ClientSettings instance;

    public boolean rememberLastPosition = true;
    public int cardWidth = 120;
    public int cardHeight = 180;
    /**
     * Opacity of the drawer's own panel plate, 0..1. Applies to the panel fill and its gold
     * border only — the nav labels keep full alpha, since fading those would make the menu
     * unreadable rather than merely see-through.
     */
    public double drawerOpacity = 1.0D;
    public double backgroundOpacity = 0.90;
    /** Only read/written when rememberLastPosition is true; an ACGPerkSelectionScreen.UIMode name. */
    public String lastMode;

    /** Gates whether selectionExpanded/collectionExpanded/miscellaneousExpanded below are restored/persisted. */
    public boolean rememberCollapsedTabs = true;
    /**
     * true: Discard in the Inventory screen deletes the whole selected row on one click.
     * false (default): it opens the quantity prompt like Sell does. Defaults off because
     * discarding is irreversible and unlike Extract there's nothing to recover afterwards.
     */
    public boolean instantDiscardAll = false;
    /**
     * Names of the {@code UIMode}s whose card grid scrolls instead of paginating; every mode
     * not listed pages. Stored per tab because the right answer differs by tab — a
     * three-card Aegis draw reads better paged, while a long Inventory is far easier to
     * drag-reorder as one continuous list.
     */
    public java.util.List<String> scrollModeTabs = new java.util.ArrayList<>();
    /**
     * How the Player Custom Stat tab presents its stats. Kept here rather than in
     * {@code custom_stat_setting.json} because that file is hand-edited and never written
     * back, while this one is exactly the place a button-toggled preference is saved.
     */
    public CustomStatView customStatView = CustomStatView.CARDS;
    /**
     * Inventory sort order, by {@code PlayerStorage.SortMode} name. Client-side because
     * ordering is presentation only — the server addresses rows by identity, so it has no
     * need to know or agree with how they're arranged on screen.
     */
    public String storageSortMode = "NAME_ASC";
    /** Remembers which storage-family drawer destination was last opened. */
    public InventoryMode inventoryMode = InventoryMode.ORIGINAL;
    /** Row keys in player-arranged order, used only by MANUAL sort. See ClientStorageState#keyOf. */
    public java.util.List<String> storageManualOrder = new java.util.ArrayList<>();
    /** Drawer group collapse state; only read/written when rememberCollapsedTabs is true. */
    public boolean selectionExpanded = true;
    public boolean collectionExpanded = true;
    public boolean miscellaneousExpanded = true;

    /** Whether to draw the shield amount HUD while the player has a shield. */
    public boolean showShieldHud = true;
    /** Whether to show the owned Gold balance in the ACG screen's top bar. */
    public boolean showGoldCurrency = true;

    /** Locked Soul Links are hidden by default so the tab shows what you actually have. */
    public boolean showUnformedSoulLinks = false;

    /**
     * Whether the Inventory and Crafting screen leaves JEI a band along the bottom. When
     * false the screen reports the whole viewport as its own and JEI finds no room, which
     * is how this screen behaved before the band existed.
     */
    public boolean showJeiOverlay = true;

    /**
     * Settings sections the player has collapsed, by section id. A list rather than a set
     * to match {@link #scrollModeTabs} and keep the saved JSON stable.
     */
    public java.util.List<String> collapsedSettingSections = new java.util.ArrayList<>();
    /** Screen corner the shield HUD anchors to. */
    public HudAnchor shieldHudAnchor = HudAnchor.TOP_RIGHT;
    /** Shield HUD horizontal offset from its anchor, in pixels (positive moves right). */
    public int shieldHudOffsetX = -5;
    /** Shield HUD vertical offset from its anchor, in pixels (positive moves down). */
    public int shieldHudOffsetY = 5;

    /** Scale applied to the accepted-quest tracker overlay. */
    public double questTrackerScale = 1.0D;
    /** Maximum accepted quests shown on one tracker page. */
    public int questTrackerQuestLimit = 8;
    /** Screen corner the accepted-quest tracker anchors to. */
    public HudAnchor questTrackerHudAnchor = HudAnchor.TOP_RIGHT;
    /** Quest Tracker horizontal offset from its anchor, in pixels. */
    public int questTrackerHudOffsetX = -8;
    /** Quest Tracker vertical offset from its anchor, in pixels. */
    public int questTrackerHudOffsetY = 8;

    private ClientSettings() {
    }

    /** Lazily loads once per client session; call {@link #save()} after mutating fields. */
    public static ClientSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ClientSettings load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = ClientSettings.class.getResourceAsStream(
                        "/assets/aegis_ascension/clientsetting.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/clientsetting.json");
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                ClientSettings loaded = GSON.fromJson(reader, ClientSettings.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException exception) {
            AegisAscensionMod.getLogger().warn("Failed to read {}, falling back to defaults", FILE, exception);
        }
        return new ClientSettings();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (var writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            AegisAscensionMod.getLogger().warn("Failed to write {}", FILE, exception);
        }
    }

    /** Clamps hand-edited/stale values back into range so a bad file can't break card layout math. */
    private void sanitize() {
        cardWidth = Math.max(MIN_CARD_WIDTH, Math.min(MAX_CARD_WIDTH, cardWidth));
        cardHeight = Math.max(MIN_CARD_HEIGHT, Math.min(MAX_CARD_HEIGHT, cardHeight));
        backgroundOpacity = Math.max(0.0D, Math.min(1.0D, backgroundOpacity));
        drawerOpacity = Math.max(0.0D, Math.min(1.0D, drawerOpacity));
        questTrackerScale = Math.max(MIN_QUEST_TRACKER_SCALE,
                Math.min(MAX_QUEST_TRACKER_SCALE, questTrackerScale));
        questTrackerQuestLimit = Math.max(MIN_QUEST_TRACKER_QUEST_LIMIT,
                Math.min(MAX_QUEST_TRACKER_QUEST_LIMIT, questTrackerQuestLimit));
        if (questTrackerHudAnchor == null) {
            questTrackerHudAnchor = HudAnchor.TOP_RIGHT;
        }
        if (shieldHudAnchor == null) {
            shieldHudAnchor = HudAnchor.TOP_RIGHT;
        }
        if (inventoryMode == null) {
            inventoryMode = InventoryMode.ORIGINAL;
        }
        if (customStatView == null) {
            customStatView = CustomStatView.CARDS;
        }
    }
}
