package com.whatever.aegis_ascension.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.whatever.aegis_ascension.client.screen.ACGInventoryScreen;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.menu.ModMenus;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Forge-free client initialization, key mappings, and session cleanup. */
public final class ClientLifecycle {
    public static final KeyMapping DEVOUR_HELD_ITEM = new KeyMapping(
            "key.aegis_ascension.devour_item",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.aegis_ascension"
    );
    public static final KeyMapping OPEN_DEVOUR_SCREEN = new KeyMapping(
            "key.aegis_ascension.open_devour_screen",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.aegis_ascension"
    );
    public static final KeyMapping OPEN_ACG_SCREEN = new KeyMapping(
            "key.aegis_ascension.open_acg_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.aegis_ascension"
    );
    /** Banks the main-hand stack, or the hovered E-inventory stack, into virtual storage. */
    public static final KeyMapping PUT_INTO_STORAGE_UI = new KeyMapping(
            "key.aegis_ascension.put_into_storage_ui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.aegis_ascension"
    );
    /** Advances the hovered offer card, or the world HUD Quest Tracker, to its next page. */
    public static final KeyMapping ADVANCE_CARD_PAGE = new KeyMapping(
            "key.aegis_ascension.advance_card_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            "key.categories.aegis_ascension"
    );
    /** Shows or hides the accepted-quest tracker HUD. */
    public static final KeyMapping TOGGLE_QUEST_TRACKER = new KeyMapping(
            "key.aegis_ascension.toggle_quest_tracker",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.aegis_ascension"
    );

    private static final List<KeyMapping> KEY_MAPPINGS = Collections.unmodifiableList(
            Arrays.asList(
                    DEVOUR_HELD_ITEM,
                    OPEN_DEVOUR_SCREEN,
                    OPEN_ACG_SCREEN,
                    PUT_INTO_STORAGE_UI,
                    ADVANCE_CARD_PAGE,
                    TOGGLE_QUEST_TRACKER
            )
    );

    private ClientLifecycle() {
    }

    /** Runs once from the active loader's client-setup work queue. */
    public static void initialize() {
        ClientSettings.get();
        MiscLocalSettings.get();
        DevourClientSettings.get();
        MenuScreens.register(ModMenus.acgInventory(), ACGInventoryScreen::new);
    }

    public static List<KeyMapping> keyMappings() {
        return KEY_MAPPINGS;
    }

    /** Clears world-specific client mirrors when leaving a server or save. */
    public static void clearSessionState() {
        // Restore local definitions first so ClientPerkState's default-primary reset stores
        // an object from the restored catalog rather than the disconnected server snapshot.
        Perk.resetSyncedCatalog();
        Aegis.resetSyncedCatalog();
        SkillEnhancement.resetSyncedCatalog();
        VirtualItems.resetSyncedCatalog();
        ClientPerkState.clear();
        ClientShopState.clear();
        ClientStorageState.clear();
        ClientQuestState.clear();
        QuestTrackerOverlay.clear();
    }
}
