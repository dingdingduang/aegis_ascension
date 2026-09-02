package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.screen.ACGInventoryScreen;
import com.whatever.aegis_ascension.menu.ACGInventoryMenu;
import com.whatever.aegis_ascension.menu.ModMenus;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Confines JEI's ingredient list to the empty region inside the ACG inventory screen,
 * under the inventory slots and left of the storage pane.
 *
 * <p>JEI builds the list's area as {@code screenRect.cropLeft(guiRight)}, where the screen
 * rectangle comes from {@code getScreenWidth}/{@code getScreenHeight} on this very
 * interface. Those are reported values, not the real window, so three of the four edges are
 * ours to set: the left through {@code guiRight}, the right through the reported width, and
 * the bottom through the reported height. Only the top cannot be moved, and an exclusion
 * covers it.</p>
 *
 * <p>Exclusions alone could never have done this: {@code IngredientGrid.calculateBounds}
 * sizes and aligns the grid from the available area with no knowledge of them, and they are
 * applied afterwards only to hide individual slots. The area itself has to be the region.
 * The screen's own layout is never resized for any of this, so the storage pane keeps its
 * full size.</p>
 *
 * <p>With the overlay switched off the screen claims the whole viewport again and JEI
 * finds no room, which is how it behaved before. This class is only loaded by JEI when JEI
 * is installed; the rest of the mod never references JEI classes.</p>
 */
@JeiPlugin
public final class AegisJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID =
            GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "jei");

    /** Slot 0 is the crafting result, so the 3x3 grid starts at 1. */
    private static final int CRAFTING_GRID_FIRST_SLOT = 1;
    private static final int CRAFTING_GRID_SLOT_COUNT = 9;
    /** Three inventory rows then the hotbar, before armor, offhand and Curios. */
    private static final int INVENTORY_FIRST_SLOT = 10;
    private static final int INVENTORY_SLOT_COUNT = 36;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    /**
     * Enables JEI's "+" transfer button on this screen. Without a handler JEI cannot know
     * which of a custom menu's slots are the crafting grid, so the button is hidden - which
     * is why it appears in the vanilla inventory and not here.
     *
     * <p>The slot layout matches what the built-in crafting handler expects: the result at
     * 0, the 3x3 grid at 1-9, then the inventory rows and hotbar at 10-45. Armor, offhand
     * and Curios are appended after those, so they stay outside the range JEI fills from.</p>
     */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                ACGInventoryMenu.class,
                ModMenus.acgInventory(),
                RecipeTypes.CRAFTING,
                CRAFTING_GRID_FIRST_SLOT,
                CRAFTING_GRID_SLOT_COUNT,
                INVENTORY_FIRST_SLOT,
                INVENTORY_SLOT_COUNT
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(ACGInventoryScreen.class,
                AegisJeiPlugin::propertiesFor);
        registration.addGuiContainerHandler(ACGInventoryScreen.class,
                new InventoryAreas());
    }

    private static IGuiProperties propertiesFor(ACGInventoryScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        Rect2i free = screen.jeiFreeArea();
        if (free.getWidth() <= 0) {
            // A GUI reaching the right edge leaves cropLeft nothing, which is how the
            // overlay stays hidden on this screen when it is switched off.
            return new ClaimedArea(screenWidth, screenHeight, 0, screenWidth);
        }
        int reportedWidth = free.getX() + free.getWidth();
        int reportedHeight = free.getY() + free.getHeight();
        return new ClaimedArea(reportedWidth, reportedHeight, 0, free.getX());
    }




    /** Everything in the freed strip that is not the empty region JEI should occupy. */
    private static final class InventoryAreas implements IGuiContainerHandler<ACGInventoryScreen> {
        @Override
        public List<Rect2i> getGuiExtraAreas(ACGInventoryScreen screen) {
            Rect2i free = screen.jeiFreeArea();
            if (free.getWidth() <= 0) {
                return List.of();
            }
            // The reported screen rectangle already bounds the right and bottom edges, so
            // only the rows above the region remain to be hidden. Slots the grid lays out
            // there are dropped by calculateAvailableSlotCount, leaving the region.
            if (free.getY() <= 0) {
                return List.of();
            }
            return List.of(new Rect2i(free.getX(), 0, free.getWidth(), free.getY()));
        }
    }

    /** Reports the part of the viewport the ACG inventory claims for itself. */
    private record ClaimedArea(int width, int height, int claimedLeft, int claimedWidth)
            implements IGuiProperties {
        @Override
        public Class<? extends Screen> getScreenClass() {
            return ACGInventoryScreen.class;
        }

        @Override
        public int getGuiLeft() {
            return claimedLeft;
        }

        @Override
        public int getGuiTop() {
            return 0;
        }

        @Override
        public int getGuiXSize() {
            return claimedWidth;
        }

        @Override
        public int getGuiYSize() {
            return height;
        }

        @Override
        public int getScreenWidth() {
            return width;
        }

        @Override
        public int getScreenHeight() {
            return height;
        }
    }
}
