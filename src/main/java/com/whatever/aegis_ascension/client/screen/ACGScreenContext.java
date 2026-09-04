package com.whatever.aegis_ascension.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

/**
 * Narrow host API shared by ACG pages. Page implementations own their local state while
 * this context supplies the common drawer/content geometry and reusable layout helpers.
 */
final class ACGScreenContext {
    private final ACGPerkSelectionScreen screen;

    ACGScreenContext(ACGPerkSelectionScreen screen) {
        this.screen = screen;
    }

    int contentX() { return screen.contentX; }
    int contentWidth() { return screen.contentWidth; }
    int contentTop() { return screen.contentTop; }
    int contentBottom() { return screen.contentBottom; }
    int screenWidth() { return screen.width; }
    int screenHeight() { return screen.height; }
    ACGPerkSelectionScreen.UIMode mode() { return screen.currentMode(); }
    Font font() { return screen.pageFont(); }
    Minecraft minecraft() { return screen.pageMinecraft(); }

    <T extends AbstractWidget> T add(T widget) {
        return screen.addPageWidget(widget);
    }

    ACGCollapsedSections collapsedSections() { return screen.collapsedSections; }

    void rebuild() { screen.rebuildContent(); }
    void focus(GuiEventListener listener) { screen.focusPageWidget(listener); }
    GuiEventListener focused() { return screen.focusedPageWidget(); }
    List<? extends GuiEventListener> children() { return screen.pageChildren(); }

    boolean gridScrollMode() { return screen.isGridScrollMode(); }
    void toggleGridScrollMode() { screen.toggleGridScrollMode(); }
    int gridScroll() { return screen.gridScroll; }
    void gridScroll(int value) { screen.gridScroll = value; }
    int gridMaxScroll() { return screen.gridMaxScroll; }
    void gridMaxScroll(int value) { screen.gridMaxScroll = value; }
    int gridViewportTop() { return screen.gridViewportTop; }
    void gridViewportTop(int value) { screen.gridViewportTop = value; }
    int gridViewportBottom() { return screen.gridViewportBottom; }
    void gridViewportBottom(int value) { screen.gridViewportBottom = value; }
    int page() { return screen.page; }
    void page(int value) { screen.page = value; }
    int pageCount() { return screen.pageCount; }
    void pageCount(int value) { screen.pageCount = value; }

    ACGPerkSelectionScreen.GridLayout computeGrid(
            int itemCount, int areaX, int areaWidth, int top, int bottom,
            int minCardWidth, int maxCardWidth, int cardHeight, int maxColumns,
            boolean leftAlign) {
        return screen.computeGrid(itemCount, areaX, areaWidth, top, bottom,
                minCardWidth, maxCardWidth, cardHeight, maxColumns, leftAlign);
    }

    ACGPerkSelectionScreen.GridLayout computeGrid(
            int itemCount, int areaX, int areaWidth, int top, int bottom,
            int minCardWidth, int maxCardWidth, int cardHeight, int maxColumns,
            boolean leftAlign, int gap) {
        return screen.computeGrid(itemCount, areaX, areaWidth, top, bottom,
                minCardWidth, maxCardWidth, cardHeight, maxColumns, leftAlign, gap);
    }

    void addPaginationButtons(int centerX, int y, boolean includeViewToggle) {
        screen.addPaginationButtons(centerX, y, includeViewToggle);
    }

    int showcaseWidth() { return screen.showcaseWidth(); }
    void drawShowcaseBackdrop(GuiGraphics graphics, int centerX, int centerY) {
        screen.drawShowcaseBackdrop(graphics, centerX, centerY);
    }
    void openIntegratedInventory() { screen.openIntegratedInventory(); }
    void switchMode(ACGPerkSelectionScreen.UIMode mode) { screen.switchMode(mode); }

    void drawLevelProgress(GuiGraphics graphics, String progressKey, String maxKey,
                           String highestLevelKey, int interval, int maxAwards, int y) {
        screen.drawLevelProgress(
                graphics, progressKey, maxKey, highestLevelKey, interval, maxAwards, y);
    }

    void drawLevelProgress(GuiGraphics graphics, String progressKey, String maxKey,
                           String highestLevelKey, int interval, int maxAwards, int y,
                           int centerX, int barWidth) {
        screen.drawLevelProgress(graphics, progressKey, maxKey, highestLevelKey,
                interval, maxAwards, y, centerX, barWidth);
    }
}
