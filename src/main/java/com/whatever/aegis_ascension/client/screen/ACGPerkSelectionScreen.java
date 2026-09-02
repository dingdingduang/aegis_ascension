package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.client.ClientLifecycle;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ClippableWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGInventoryStyle;
import com.whatever.aegis_ascension.client.screen.acg.ACGShopSlotWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGStorageRowWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGStatSourceBreakdown;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Breakdown;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Definition;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.network.OpenACGInventoryPacket;
import com.whatever.aegis_ascension.network.RequestStorageDataPacket;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RequestPerkDataPacket;
import com.whatever.aegis_ascension.network.RequestSkillEnhancementOffersPacket;
import com.whatever.aegis_ascension.network.RequestQuestDataPacket;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Unified ARPG-styled hub for every Perk/Aegis interaction: selecting new offers,
 * choosing Skill Enhancements, and reviewing everything already owned.
 *
 * <p><b>Layout.</b> Every mode shares a far-left category drawer and a top bar
 * (Panel 1). {@link UIMode#OWNED_AEGIS} and {@link UIMode#OWNED_PERKS} use the full
 * three-panel layout from the reference composition: drawer, a center showcase with a
 * circular portrait (Panel 2), and a paginated grid of owned/locked items on the right
 * (Panel 3). The remaining modes use a two-panel layout (drawer + a single paginated
 * content panel) because their underlying data is a flat list rather than a
 * "selected item vs. inventory" relationship.</p>
 *
 * <p><b>Data flow.</b> The screen is a shell: each {@link UIMode} delegates its
 * widgets, rendering, input, and page-local state to an {@link ACGPage}. Actions remain
 * server-authoritative and use the same network packets as before. Aegis and Perk offer
 * state belongs to {@link ACGAegisSelectionPage} and {@link ACGPerkSelectionPage};
 * {@link #setAegisOffers} and {@link #setPerkOffers} forward server rolls to those pages.</p>
 *
 * <p><b>Rendering passes.</b> {@link #render} always draws, in order: the vignette
 * background, the top bar, the {@link ACGDrawer}, the active page, the widget layer via
 * {@code super.render}, and finally shared overlays such as pagination and tooltips.</p>
 */
public final class ACGPerkSelectionScreen extends Screen {
    public enum UIMode {
        AEGIS_SELECTION,
        PERK_SELECTION,
        SKILL_ENHANCEMENT,
        OWNED_AEGIS,
        OWNED_PERKS,
        OWNED_SOUL_LINKS,
        DEVOURED,
        PLAYER_CUSTOM_STAT,
        CUSTOM_SHOP,
        QUEST_CENTER,
        STORAGE,
        SERVER_SETTINGS,
        CLIENT_SETTINGS
    }

    static final int TOP_BAR_HEIGHT = 28;
    private static final int CARD_GAP = 8;

    private UIMode mode;
    private boolean initializedOnce;
    private boolean openingIntegratedInventory;

    /** One clockwise revolution of the showcase's middle ring, in milliseconds. */
    private static final long SHOWCASE_SPIN_PERIOD_MS = 24_000L;

    /** Whether the current tab's grid scrolls rather than paginates. */
    boolean isGridScrollMode() {
        return ClientSettings.get().scrollModeTabs.contains(mode.name());
    }

    void toggleGridScrollMode() {
        ClientSettings settings = ClientSettings.get();
        if (!settings.scrollModeTabs.remove(mode.name())) {
            settings.scrollModeTabs.add(mode.name());
        }
        settings.save();
        // Both views start at the top; carrying a stale offset or page across the switch
        // would land the player somewhere arbitrary.
        gridScroll = 0;
        page = 0;
        rebuildContent();
    }

    /** Pixels scrolled per wheel notch in a card grid. */
    private static final double GRID_SCROLL_STEP = 40.0D;

    /** Shared scroll state for whichever card grid the current mode is showing. */
    int gridScroll;
    int gridMaxScroll;
    int gridViewportTop;
    int gridViewportBottom;

    int contentX;
    int contentWidth;
    int contentTop;
    int contentBottom;

    int page;
    int pageCount = 1;
    /** Where addPaginationButtons last placed the < / > pair; the page label follows it. */
    private int paginationCenterX;
    private int paginationY;
    private int pageSize = 1;
    private int statSyncTicks;
    /** Safety net: how long an awaiting-server flag has been set without a resync clearing it. */
    private int awaitingTicks;

    private String hoveredStatKey;
    private int statSourceScroll;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    private final ACGScreenContext pageContext = new ACGScreenContext(this);
    private final ACGDrawer drawer = new ACGDrawer();
    private final ACGPage storagePage = new ACGStoragePage();
    private final ACGPage devouredPage = new ACGDevouredPage();
    private final ACGPage clientSettingsPage = new ACGClientSettingsPage();
    private final ACGPage serverSettingsPage = new ACGServerSettingsPage();
    private final ACGShopPage shopPage = new ACGShopPage();
    private final ACGAegisSelectionPage aegisSelectionPage =
            new ACGAegisSelectionPage();
    private final ACGPerkSelectionPage perkSelectionPage =
            new ACGPerkSelectionPage();
    private final ACGSkillEnhancementPage skillEnhancementPage =
            new ACGSkillEnhancementPage();
    private final ACGOwnedAegisPage ownedAegisPage = new ACGOwnedAegisPage();
    private final ACGOwnedTalentsPage ownedTalentsPage = new ACGOwnedTalentsPage();
    private final ACGPage soulLinksPage = new ACGSoulLinksPage();
    private final ACGPage customStatsPage = new ACGCustomStatsPage();
    private final ACGPage questPage = new ACGQuestCenterPage();

    public ACGPerkSelectionScreen() {
        this(initialModeFromSettings());
    }

    /** Feature 1 ("Remember Last Position"): resume the last-viewed tab if it's still valid. */
    private static UIMode initialModeFromSettings() {
        ClientSettings settings = ClientSettings.get();
        if (settings.rememberLastPosition && settings.lastMode != null) {
            try {
                return UIMode.valueOf(settings.lastMode);
            } catch (IllegalArgumentException ignored) {
                // Stale value from an older build with different mode names; fall through.
            }
        }
        return UIMode.AEGIS_SELECTION;
    }

    public ACGPerkSelectionScreen(UIMode initialMode) {
        super(ACGTheme.asHeader(getTranslatableString("screen.aegis_ascension.acg.title")));
        this.mode = initialMode;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        if (mode == UIMode.STORAGE
                && ClientSettings.get().inventoryMode
                == ClientSettings.InventoryMode.INVENTORY_AND_CRAFTING) {
            openIntegratedInventory();
            return;
        }
        layoutMetrics();
        drawer.init(pageContext);
        if (!initializedOnce) {
            initializedOnce = true;
            requestDataForMode(mode);
        }
        initModeWidgets();
        cullScrolledCards();
        ACGCursorState.restoreIfPending(minecraft);
    }

    private void layoutMetrics() {
        contentX = ACGDrawer.WIDTH + 10;
        contentWidth = Math.max(140, width - contentX - 12);
        contentTop = TOP_BAR_HEIGHT + 12;
        contentBottom = height - 12;
    }

    void rebuildContent() {
        clearWidgets();
        init();
    }

    <T extends AbstractWidget> T addPageWidget(T widget) {
        return addRenderableWidget(widget);
    }

    void focusPageWidget(net.minecraft.client.gui.components.events.GuiEventListener listener) {
        setFocused(listener);
    }

    net.minecraft.client.gui.components.events.GuiEventListener focusedPageWidget() {
        return getFocused();
    }

    List<? extends net.minecraft.client.gui.components.events.GuiEventListener> pageChildren() {
        return children();
    }

    net.minecraft.client.gui.Font pageFont() {
        return font;
    }

    Minecraft pageMinecraft() {
        return minecraft;
    }

    UIMode currentMode() {
        return mode;
    }

    @Override
    public void tick() {
        super.tick();
        skillEnhancementPage.tick(pageContext);
        ownedAegisPage.tick(pageContext);
        ownedTalentsPage.tick(pageContext);
        if (mode == UIMode.STORAGE) {
            storagePage.tick(pageContext);
        }
        if (mode == UIMode.PLAYER_CUSTOM_STAT
                && ClientPerkState.isLiveCustomStatsRefreshAllowed()
                && ++statSyncTicks >= 20) {
            statSyncTicks = 0;
            ModNetworking.sendToServer(new RequestPerkDataPacket(true, true));
        }
        if (isAwaitingServer()) {
            // Normally cleared within a tick or two by refreshFromServer() once the
            // server's resync packet arrives. If that resync is ever lost (dropped
            // packet, a screen swap mid-flight, etc.), this stops the offer cards from
            // staying permanently disabled until the player closes and reopens the
            // screen — self-heal after ~3s instead of requiring a manual workaround.
            if (++awaitingTicks > 60) {
                awaitingTicks = 0;
                clearAwaitingFlags();
                rebuildContent();
            }
        } else {
            awaitingTicks = 0;
        }
    }

    private boolean isAwaitingServer() {
        return aegisSelectionPage.isAwaitingServer()
                || perkSelectionPage.isAwaitingServer()
                || skillEnhancementPage.isAwaitingServer();
    }

    private void clearAwaitingFlags() {
        aegisSelectionPage.clearAwaiting();
        perkSelectionPage.clearAwaiting();
        skillEnhancementPage.clearAwaiting();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == UIMode.OWNED_PERKS
                && ownedTalentsPage.keyPressed(
                pageContext, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (mode == UIMode.STORAGE
                && storagePage.keyPressed(pageContext, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (consumeMatchingOpenKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        // A card's description can be longer than fits in its box (e.g. Mysterious Doll's
        // perk text); ACGCardWidget#renderBig pages it instead of clipping it, and this is
        // the "next page" input — ADVANCE_CARD_PAGE (down-arrow by default, rebindable via
        // Controls) while hovering the card, wrapping back to the first page after the
        // last. A no-op on any card whose description fits on one page.
        if (ClientLifecycle.ADVANCE_CARD_PAGE.matches(keyCode, scanCode)) {
            for (var child : children()) {
                if (child instanceof ACGCardWidget card && card.isHoveredNow() && card.hasMultipleDescriptionPages()) {
                    card.advanceDescriptionPage();
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean consumeMatchingOpenKey(int keyCode, int scanCode) {
        if (ClientLifecycle.OPEN_ACG_SCREEN.matches(keyCode, scanCode)) {
            while (ClientLifecycle.OPEN_ACG_SCREEN.consumeClick()) {
                // Prevent the tick handler from immediately reopening the screen.
            }
            return true;
        }

        return false;
    }

    @Override
    public void onClose() {
        ClientPerkState.endOfferSession();
        ClientPerkState.endAegisOfferSession();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Called by {@code ClientPacketHandler} whenever fresh player data arrives
     * while this screen is open.
     */
    public void refreshFromServer() {
        clearAwaitingFlags();
        aegisSelectionPage.onServerSync(pageContext);
        perkSelectionPage.onServerSync(pageContext);
        skillEnhancementPage.onServerSync(pageContext);
        if (mode == UIMode.STORAGE) {
            storagePage.onServerSync(pageContext);
        } else if (mode == UIMode.DEVOURED) {
            devouredPage.onServerSync(pageContext);
        } else if (mode == UIMode.QUEST_CENTER) {
            questPage.onServerSync(pageContext);
        }
        rebuildContent();
    }

    /** Quest progress is independent of the other pages; avoid rebuilding offer cards while it ticks. */
    public void refreshQuestFromServer() {
        if (mode == UIMode.QUEST_CENTER) {
            questPage.onServerSync(pageContext);
            rebuildContent();
        } else if (mode == UIMode.SERVER_SETTINGS) {
            serverSettingsPage.onServerSync(pageContext);
            rebuildContent();
        }
    }

    /** Called by {@code ClientPacketHandler} when the server pushes a new Aegis offer roll. */
    public void setAegisOffers(List<Aegis> offers) {
        aegisSelectionPage.setOffers(pageContext, offers);
        if (mode == UIMode.AEGIS_SELECTION && minecraft != null) {
            rebuildContent();
        }
    }

    /** Called by {@code ClientPacketHandler} when the server pushes a new Perk offer roll. */
    public void setPerkOffers(List<Perk> offers) {
        perkSelectionPage.setOffers(pageContext, offers);
        if (mode == UIMode.PERK_SELECTION && minecraft != null) {
            rebuildContent();
        }
    }

    private void requestDataForMode(UIMode target) {
        switch (target) {
            case AEGIS_SELECTION -> {
                if (!aegisSelectionPage.hasOffers()
                        && ClientPerkState.getAegisSelectionCharges() > 0) {
                    aegisSelectionPage.requestOffers();
                }
            }
            case PERK_SELECTION -> {
                if (!perkSelectionPage.hasOffers()
                        && ClientPerkState.getSelectionCharges() > 0) {
                    perkSelectionPage.requestOffers();
                }
            }
            case SKILL_ENHANCEMENT ->
                    ModNetworking.sendToServer(new RequestSkillEnhancementOffersPacket());
            // Only Custom Stats renders the per-source records, so the other tabs
            // share this request but ask the server to leave them out.
            case PLAYER_CUSTOM_STAT ->
                    ModNetworking.sendToServer(new RequestPerkDataPacket(false, true));
            case OWNED_AEGIS, OWNED_PERKS, OWNED_SOUL_LINKS, DEVOURED ->
                    ModNetworking.sendToServer(new RequestPerkDataPacket(false, false));
            case CUSTOM_SHOP -> shopPage.requestSelectedShop();
            case QUEST_CENTER -> ModNetworking.sendToServer(new RequestQuestDataPacket());
            case STORAGE -> ModNetworking.sendToServer(new RequestStorageDataPacket());
            case SERVER_SETTINGS -> ModNetworking.sendToServer(new RequestQuestDataPacket());
            case CLIENT_SETTINGS -> {
                // Purely client-side; nothing to request from the server.
            }
        }
    }

    void switchMode(UIMode newMode) {
        if (newMode == UIMode.STORAGE
                && ClientSettings.get().inventoryMode
                == ClientSettings.InventoryMode.INVENTORY_AND_CRAFTING) {
            openIntegratedInventory();
            return;
        }
        if (mode == newMode) {
            return;
        }
        if (mode == UIMode.STORAGE) {
            storagePage.onDeactivated(pageContext);
        } else if (mode == UIMode.OWNED_PERKS) {
            ownedTalentsPage.onDeactivated(pageContext);
        }
        mode = newMode;
        page = 0;
        // Per-tab views: an offset from the previous tab means nothing here, and a large
        // one would open this tab scrolled past its own content.
        gridScroll = 0;
        skillEnhancementPage.onDeactivated(pageContext);
        statSyncTicks = 0;
        drawer.onModeChanged();
        ClientSettings settings = ClientSettings.get();
        if (settings.rememberLastPosition) {
            settings.lastMode = mode.name();
            settings.save();
        }
        requestDataForMode(mode);
        rebuildContent();
    }

    void openIntegratedInventory() {
        if (openingIntegratedInventory) {
            return;
        }
        openingIntegratedInventory = true;
        ClientSettings settings = ClientSettings.get();
        settings.inventoryMode = ClientSettings.InventoryMode.INVENTORY_AND_CRAFTING;
        if (settings.rememberLastPosition) {
            settings.lastMode = UIMode.STORAGE.name();
        }
        settings.save();
        ACGCursorState.remember(lastMouseX, lastMouseY);
        ModNetworking.sendToServer(new OpenACGInventoryPacket());
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount - 1, page + delta));
        rebuildContent();
    }

    // ------------------------------------------------------------------
    // Widget construction (one method per mode)
    // ------------------------------------------------------------------

    private void initModeWidgets() {
        // The Devoured tab can vanish between opens (a remembered lastMode, or the Aegis
        // being reset away), which would otherwise leave the screen on a tab with no drawer
        // row to leave it by.
        if (mode == UIMode.DEVOURED && !ACGDevouredPage.isAvailable()) {
            mode = UIMode.OWNED_AEGIS;
        }
        // Reset so a mode with no grid (Client Side Setting, an empty offer list) reports
        // pageCount=1 and both the pagination buttons and the "Page X / Y" label stay
        // hidden, instead of the previous mode's leftover value lingering — pageCount is
        // only ever recomputed by computeGrid(), which not every mode's init calls.
        pageCount = 1;
        switch (mode) {
            case AEGIS_SELECTION -> aegisSelectionPage.init(pageContext);
            case PERK_SELECTION -> perkSelectionPage.init(pageContext);
            case SKILL_ENHANCEMENT -> skillEnhancementPage.init(pageContext);
            case OWNED_AEGIS -> ownedAegisPage.init(pageContext);
            case OWNED_PERKS -> ownedTalentsPage.init(pageContext);
            case OWNED_SOUL_LINKS -> soulLinksPage.init(pageContext);
            case DEVOURED -> devouredPage.init(pageContext);
            case PLAYER_CUSTOM_STAT -> customStatsPage.init(pageContext);
            case CUSTOM_SHOP -> shopPage.init(pageContext);
            case QUEST_CENTER -> questPage.init(pageContext);
            case STORAGE -> storagePage.init(pageContext);
            case SERVER_SETTINGS -> serverSettingsPage.init(pageContext);
            case CLIENT_SETTINGS -> clientSettingsPage.init(pageContext);
        }
    }

    // ------------------------------------------------------------------
    // Rendering (one pass per mode, all delegated from render())
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        // Note: the load-bearing blend-enable lives in GeneralClientMethods#bindAndBlit, right
        // before each textured blit, because the cards draw late (inside super.render())
        // after intervening GuiGraphics draws that reset the blend state. See the comment
        // there for the full explanation of the "hard black rectangle" artifact.
        ACGTheme.drawVignetteBackground(graphics, width, height, (float) ClientSettings.get().backgroundOpacity);
        renderTopBar(graphics);
        // A remembered Inventory & Crafting mode redirects from init() before any ACG
        // page or drawer widgets are constructed. The server opens the container on the
        // following network turn, during which Minecraft may render this outgoing screen
        // once. Do not ask an uninitialized drawer/page to render during that hand-off.
        if (openingIntegratedInventory) {
            return;
        }
        drawer.render(graphics);

        switch (mode) {
            case AEGIS_SELECTION -> aegisSelectionPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case PERK_SELECTION -> perkSelectionPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case SKILL_ENHANCEMENT -> skillEnhancementPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case OWNED_AEGIS -> ownedAegisPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case OWNED_PERKS -> ownedTalentsPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case OWNED_SOUL_LINKS -> soulLinksPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case DEVOURED -> devouredPage.render(pageContext, graphics, mouseX, mouseY, partialTick);
            case PLAYER_CUSTOM_STAT -> customStatsPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case CUSTOM_SHOP -> shopPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case QUEST_CENTER -> questPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case STORAGE -> storagePage.render(pageContext, graphics, mouseX, mouseY, partialTick);
            case SERVER_SETTINGS -> serverSettingsPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
            case CLIENT_SETTINGS -> clientSettingsPage.render(
                    pageContext, graphics, mouseX, mouseY, partialTick);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderGridScrollbar(graphics);
        renderPageLabel(graphics);
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderTopBar(GuiGraphics graphics) {
        graphics.fill(0, 0, width, TOP_BAR_HEIGHT, 0xE6121016);
        graphics.fill(0, TOP_BAR_HEIGHT - 1, width, TOP_BAR_HEIGHT, ACGTheme.GOLD_DIM);
        drawCenteredString(graphics, font, ACGTheme.asHeader(title), width / 2, (TOP_BAR_HEIGHT - 9) / 2, ACGTheme.TEXT_PRIMARY);
        if (minecraft != null && minecraft.player != null) {
            ClientSettings settings = ClientSettings.get();
            if (settings.showGoldCurrency) {
                final int iconSize = 16;
                final int iconX = 10;
                final int iconY = (TOP_BAR_HEIGHT - iconSize) / 2;
                GeneralClientMethods.blitFittedTexture(graphics, GoldCurrency.ICON,
                        iconX, iconY, iconSize, iconSize, 128);
                Component gold = getTranslatableString(
                        "screen.aegis_ascension.acg.owned_gold",
                        ClientPerkState.getGoldCurrency());
                graphics.drawString(font, gold, iconX + iconSize + 4,
                        (TOP_BAR_HEIGHT - 8) / 2, ACGTheme.GOLD_BRIGHT, false);
            }
            Component level = progressionLabel();
            graphics.drawString(font, level, width - font.width(level) - 12, (TOP_BAR_HEIGHT - 8) / 2, ACGTheme.CYAN_ACCENT, false);
        }
    }

    static Component progressionLabel() {
        if (ClientPerkState.usesMinecraftDefaultLevel()) {
            return getTranslatableString("screen.aegis_ascension.acg.level",
                    Minecraft.getInstance().player == null
                            ? ClientPerkState.getProgressionLevel()
                            : Minecraft.getInstance().player.experienceLevel);
        }
        int rank = ClientPerkState.getAegisAscensionRank();
        long current = ClientPerkState.getAegisAscensionExperience();
        long needed = ClientPerkState.getAegisAscensionExperienceToNextRank();
        if (needed <= 0L || rank >= ClientPerkState.getAegisAscensionMaximumRank()) {
            return getTranslatableString("screen.aegis_ascension.acg.aegis_rank_max", rank);
        }
        return getTranslatableString("screen.aegis_ascension.acg.aegis_rank_progress",
                rank, current, needed);
    }

    /** Scroll thumb for whichever grid is currently scrollable; mirrors the drawer's. */
    private void renderGridScrollbar(GuiGraphics graphics) {
        if (gridMaxScroll <= 0) {
            return;
        }
        int trackHeight = gridViewportBottom - gridViewportTop;
        int contentHeight = trackHeight + gridMaxScroll;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        int thumbY = gridViewportTop + Math.round(
                (trackHeight - thumbHeight) * (gridScroll / (float) gridMaxScroll));
        int thumbX = contentX + contentWidth - 4;
        graphics.fill(thumbX, thumbY, thumbX + 2, thumbY + thumbHeight, ACGTheme.GOLD_DIM);
    }

    private void renderPageLabel(GuiGraphics graphics) {
        if (pageCount <= 1) {
            return;
        }
        // Centred in the gap between the < and > buttons, at their own recorded anchor.
        drawCenteredString(graphics, font,
                getTranslatableString("screen.aegis_ascension.collection.page", page + 1, pageCount),
                paginationCenterX, paginationY + (20 - 8) / 2, ACGTheme.TEXT_MUTED);
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Shop slots carry real item stacks, so they show the game's own tooltip rather
        // than a card's text.
        if (mode == UIMode.CUSTOM_SHOP
                && shopPage.renderHoveredTooltip(font, graphics, mouseX, mouseY)) {
            hoveredStatKey = null;
            return;
        }
        for (var child : children()) {
            if (!(child instanceof ACGCardWidget card) || !card.isHoveredNow()) {
                continue;
            }
            if (card.statKey() != null) {
                if (!card.statKey().equals(hoveredStatKey)) {
                    hoveredStatKey = card.statKey();
                    statSourceScroll = 0;
                }
                renderStatSourceBreakdown(graphics, card.statKey(), mouseX, mouseY);
                return;
            }
            if (card.tooltipText() != null) {
                hoveredStatKey = null;
                int tooltipWidth = Math.min(300, width - 24);
                graphics.renderTooltip(font, font.split(card.tooltipText(), tooltipWidth), mouseX, mouseY);
                return;
            }
        }
        hoveredStatKey = null;
    }

    /**
     * Hover panel for a Player Custom Stat card: which owned Perk, active Soul Link,
     * enabled Aegis, or chosen Skill Enhancement contributed how much to this stat, on
     * top of the flat/percentage/final values the card itself already shows.
     */
    private void renderStatSourceBreakdown(GuiGraphics graphics, String statKey, int mouseX, int mouseY) {
        Definition definition = CustomStats.definition(statKey);
        if (definition == null) {
            return;
        }
        Breakdown breakdown = CustomStats.breakdown(definition);
        Component statValue = getLiteralString(breakdown.finalText(definition));
        List<ACGStatSourceBreakdown.Source> sources = ACGStatSourceBreakdown.sources(statKey);
        statSourceScroll = ACGStatSourceBreakdown.renderPanel(graphics, font,
                getTranslatableString(definition.translationKey()), statValue, sources,
                mouseX, mouseY, width, height, statSourceScroll);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (mode == UIMode.STORAGE
                && storagePage.mouseDragged(pageContext, mouseX, mouseY,
                button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mode == UIMode.STORAGE
                && storagePage.mouseReleased(pageContext, mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hoveredStatKey != null && Math.abs(delta) > 1.0E-9D) {
            statSourceScroll = ACGStatSourceBreakdown.adjustScroll(statSourceScroll, delta);
            return true;
        }
        if (drawer.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (mode == UIMode.STORAGE
                && storagePage.mouseScrolled(pageContext, mouseX, mouseY, delta)) {
            return true;
        }
        if (mode == UIMode.DEVOURED
                && devouredPage.mouseScrolled(pageContext, mouseX, mouseY, delta)) {
            return true;
        }
        if (mode == UIMode.QUEST_CENTER
                && questPage.mouseScrolled(pageContext, mouseX, mouseY, delta)) {
            return true;
        }
        // Any mode whose grid overflows, not just the Inventory — gridMaxScroll is only
        // non-zero when the active grid actually scrolls, so this needs no mode list.
        if (mode != UIMode.STORAGE && gridMaxScroll > 0
                && mouseX >= contentX && mouseY > TOP_BAR_HEIGHT
                && Math.abs(delta) > 1.0E-9D) {
            gridScroll = Math.max(0, Math.min(gridMaxScroll,
                    gridScroll + (int) Math.round((delta < 0.0D ? 1 : -1) * GRID_SCROLL_STEP)));
            rebuildContent();
            return true;
        }
        if (mode == UIMode.CLIENT_SETTINGS
                && clientSettingsPage.mouseScrolled(
                pageContext, mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * TEMPORARY diagnostic logging for the "cards stop reacting after a selection"
     * report — a disabled ({@code active=false}) widget's {@code onPress()} never fires,
     * so without this override a click on a stuck card leaves zero trace in the log.
     * Safe to remove once that's root-caused; every click on this screen logs one line.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mode == UIMode.STORAGE
                && storagePage.mouseClicked(pageContext, mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Vanilla only moves focus when some child accepts the click, so clicking empty
        // space leaves the search box focused and still blinking. Clearing it here makes a
        // click-away behave the way every text field is expected to.
        if (!handled && getFocused() != null) {
            setFocused(null);
        }
        return handled;
    }

    /**
     * One "next milestone at level N" line plus its progress bar.
     *
     * @param maxAwards how many awards this track can ever receive from levelling. Past that
     *                  point no further milestone is granted, so continuing to advertise a
     *                  next level would promise a charge that will never arrive — the line
     *                  switches to a completed message and a full bar instead.
     * @param maxKey    lang key for that completed message.
     * @param y         top of the line, so several tracks can stack.
     */
    void drawLevelProgress(GuiGraphics graphics, String progressKey, String maxKey,
                           String highestLevelKey, int interval, int maxAwards, int y) {
        drawLevelProgress(graphics, progressKey, maxKey, highestLevelKey, interval,
                maxAwards, y, contentX + contentWidth / 2, 180);
    }

    /**
     * @param centerX  horizontal centre of this track, so two can sit side by side.
     * @param barWidth width of the progress bar; the label is clipped to it, which is what
     *                 stops two side-by-side tracks running into each other on a narrow pane.
     */
    void drawLevelProgress(GuiGraphics graphics, String progressKey, String maxKey,
                           String highestLevelKey, int interval, int maxAwards, int y,
                           int centerX, int barWidth) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        int level = ClientPerkState.usesMinecraftDefaultLevel()
                ? minecraft.player.experienceLevel
                : ClientPerkState.getProgressionLevel();
        int step = Math.max(1, interval);
        // Milestones land at step, 2*step, ... so the last one this track ever grants is
        // maxAwards * step.
        long finalMilestone = (long) Math.max(0, maxAwards) * step;

        // Awards are gated on the highest level ever reached, not the current one. After a
        // level drop the next award still sits above that mark, so counting from the
        // current level would promise a charge the server will never grant. Falling back
        // to the current level keeps the old reading when the stat has not arrived yet.
        int highestLevel = Math.max(level,
                (int) Math.round(ClientPerkState.getDisplayStat(highestLevelKey)));
        long nextMilestone = ((long) (highestLevel / step) + 1L) * step;

        if (nextMilestone > finalMilestone) {
            drawCenteredString(graphics, font, getLiteralString(font.plainSubstrByWidth(
                            getTranslatableString(maxKey, maxAwards).getString(), barWidth)),
                    centerX, y, ACGTheme.TEXT_MUTED);
            ACGTheme.drawProgressBar(graphics, centerX - barWidth / 2, y + 11, barWidth, 6, 1.0F,
                    0xFF241F1A, ACGTheme.GOLD_DIM);
            return;
        }

        long previousMilestone = nextMilestone - step;
        float progress = Math.max(0.0F, Math.min(1.0F,
                (level - previousMilestone) / (float) step));
        drawCenteredString(graphics, font, getLiteralString(font.plainSubstrByWidth(
                        getTranslatableString(progressKey, level, nextMilestone).getString(),
                        barWidth)),
                centerX, y, ACGTheme.TEXT_SECONDARY);
        ACGTheme.drawProgressBar(graphics, centerX - barWidth / 2, y + 11, barWidth, 6, progress,
                0xFF241F1A, ACGTheme.ORANGE_ACTION);
    }

    // ------------------------------------------------------------------
    // Shared grid layout helper
    // ------------------------------------------------------------------

    static record GridLayout(int columns, int cardWidth, int cardHeight, int startX, int startY,
                             int firstIndex, int lastIndex) {
    }

    private GridLayout computeGrid(int itemCount, int areaX, int areaWidth, int top, int bottom,
                                   int minCardWidth, int maxCardWidth, int cardHeight, int maxColumns) {
        return computeGrid(itemCount, areaX, areaWidth, top, bottom,
                minCardWidth, maxCardWidth, cardHeight, maxColumns, false);
    }

    /**
     * @param leftAlign true packs the cards against {@code areaX} instead of centring them.
     *                  Centring reads well for a fixed offer spread, but in a
     *                  variable-length list it makes a two-item page float in the middle of
     *                  the panel and shift sideways as the count changes.
     */
    GridLayout computeGrid(int itemCount, int areaX, int areaWidth, int top, int bottom,
                           int minCardWidth, int maxCardWidth, int cardHeight, int maxColumns,
                           boolean leftAlign) {
        int columns = Math.max(1, Math.min(maxColumns, Math.max(1, areaWidth / (minCardWidth + CARD_GAP))));
        columns = Math.max(1, Math.min(columns, Math.max(1, itemCount)));
        int rows = Math.max(1, (bottom - top) / (cardHeight + CARD_GAP));
        int pageSizeLocal = Math.max(1, columns * rows);
        int pageCountLocal = Math.max(1, (itemCount + pageSizeLocal - 1) / pageSizeLocal);
        page = Math.max(0, Math.min(page, pageCountLocal - 1));
        pageCount = pageCountLocal;
        pageSize = pageSizeLocal;
        int availableWidth = Math.max(minCardWidth, areaWidth - CARD_GAP * (columns - 1));
        int cw = Math.max(minCardWidth, Math.min(maxCardWidth, availableWidth / columns));
        int gridWidth = cw * columns + CARD_GAP * (columns - 1);
        int startX = leftAlign ? areaX : areaX + Math.max(0, (areaWidth - gridWidth) / 2);

        // Scroll mode is handled here rather than in each mode's init, so every grid in the
        // screen inherits it without changing its own layout loop: returning the full index
        // range with a scrolled startY makes the existing
        // "startY + row * step" arithmetic lay out one continuous stack. Cards that end up
        // outside the viewport are hidden centrally by cullScrolledCards().
        gridViewportTop = top;
        gridViewportBottom = bottom;
        if (isGridScrollMode()) {
            int step = cardHeight + CARD_GAP;
            int gridRows = (Math.max(0, itemCount) + columns - 1) / columns;
            // True viewport height, not floored: partial rows are allowed to render, so
            // the last row still needs to be scrollable all the way into a short pane.
            gridMaxScroll = Math.max(0, gridRows * step - Math.max(1, bottom - top));
            gridScroll = Math.max(0, Math.min(gridMaxScroll, gridScroll));
            // Pagination is bypassed entirely; without this renderPageLabel would keep
            // drawing "Page 1 / N" over a view that shows everything at once.
            pageCount = 1;
            page = 0;
            return new GridLayout(columns, cw, cardHeight, startX, top - gridScroll,
                    0, itemCount);
        }

        gridMaxScroll = 0;
        gridScroll = 0;
        int firstIndex = page * pageSizeLocal;
        int lastIndex = Math.min(itemCount, firstIndex + pageSizeLocal);
        return new GridLayout(columns, cw, cardHeight, startX, top, firstIndex, lastIndex);
    }

    /**
     * Hides card widgets that scrolled outside the grid viewport.
     *
     * <p>Done once after the mode has built its widgets rather than inside each init, so a
     * grid only has to lay its cards out — it never has to know about scrolling. Widgets are
     * hidden rather than clipped because a half-visible card would still take clicks along
     * its exposed edge.</p>
     */
    private void cullScrolledCards() {
        for (var child : children()) {
            if (!(child instanceof AbstractWidget widget) || !isGridCard(widget)) {
                continue;
            }
            if (gridMaxScroll <= 0) {
                // Paged view: nothing overflows, so leave the card unclipped. Cleared
                // explicitly because widgets outlive a single mode's layout.
                ((ClippableWidget) widget).setClipBounds(
                        ClippableWidget.NO_CLIP_TOP, ClippableWidget.NO_CLIP_BOTTOM);
                continue;
            }
            // Clipped to the grid band so a partially scrolled card stops at the viewport
            // edge instead of drawing over the header, the action row, or the title bar.
            ((ClippableWidget) widget).setClipBounds(gridViewportTop, gridViewportBottom);
            // Intersects the viewport, rather than sits entirely inside it. The strict
            // test came from the drawer, whose 24px rows always fit; a 180px card in a
            // pane only 96px tall (a small window, once the header stack is subtracted)
            // can never satisfy it, so every card was culled and the grid rendered blank.
            boolean fits = widget.getY() + widget.getHeight() > gridViewportTop
                    && widget.getY() < gridViewportBottom;
            widget.visible = fits;
            widget.active = fits && widget.active;
        }
    }

    /** The card widget types a grid lays out; everything else (buttons, boxes) is left alone. */
    private static boolean isGridCard(AbstractWidget widget) {
        return widget instanceof ACGCardWidget
                || widget instanceof ACGShopSlotWidget
                || widget instanceof ACGStorageRowWidget;
    }

    private void addPaginationButtons(int centerX, int y) {
        addPaginationButtons(centerX, y, true);
    }

    /**
     * @param includeViewToggle false for a tab that places the Pages/Scroll button in its
     *                          own action row instead, so the two don't both appear.
     */
    void addPaginationButtons(int centerX, int y, boolean includeViewToggle) {
        // Recorded even when the buttons are suppressed, so renderPageLabel never has to
        // re-derive a position that four separate modes each anchor differently.
        paginationCenterX = centerX;
        paginationY = y;

        // Added here because every grid mode calls this method: one definition places the
        // toggle correctly in all of them, including the tabs (Soul Link, Player Custom
        // Stat) that have no action row of their own to host a button. Placed before the
        // page-count check so it stays reachable in scroll mode, where there are no pages.
        if (includeViewToggle) {
            addRenderableWidget(ACGButton.builder(
                        getTranslatableString(isGridScrollMode()
                                ? "screen.aegis_ascension.acg.storage.view_scroll"
                                : "screen.aegis_ascension.acg.storage.view_paged"),
                            button -> toggleGridScrollMode())
                    .bounds(contentX + contentWidth - 62, y, 62, 20)
                    .build()
                    .style(ACGButton.Style.PLAIN));
        }
        if (pageCount <= 1) {
            return;
        }
        ACGButton previous = ACGButton.builder(getLiteralString("<"), button -> changePage(-1))
                .bounds(centerX - 74, y, 24, 20)
                .build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        ACGButton next = ACGButton.builder(getLiteralString(">"), button -> changePage(1))
                .bounds(centerX + 50, y, 24, 20)
                .build();
        next.active = page + 1 < pageCount;
        addRenderableWidget(next);
    }

    /**
     * The arcane-circle showcase backdrop — disc, concentric rings, tick marks, glyphs and
     * sparkles — shared by Owned Aegis, Owned Perks, and the Inventory so all three read as
     * the same screen family.
     *
     * parameter pulse 0..1 breathing value; only the glow scales with it, leaving the rings and
     *              glyphs stable so the backdrop doesn't visibly throb.
     */
    void drawShowcaseBackdrop(GuiGraphics graphics, int centerX, int centerY) {
        // Driven off the wall clock rather than tickCount + partialTick: renderStorage has
        // no partialTick to pass, and sourcing the animation here means all three showcases
        // breathe and spin in step without threading a parameter through each of them. Also
        // frame-rate independent, where a per-frame accumulator would not be.
        long now = System.currentTimeMillis();
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 1000.0D);
        float spin = (now % SHOWCASE_SPIN_PERIOD_MS) / (float) SHOWCASE_SPIN_PERIOD_MS * 360.0F;

        float radius = Math.min(58.0F, Math.max(34.0F, showcaseWidth() * 0.30F));
        ACGInventoryStyle.drawArcaneCircle(graphics, centerX, centerY, radius, pulse, spin);
        // Brightness carries the breathing, not size. A size-only swing moved the glow's
        // edge by ~8px over a 6-second cycle on a soft radial gradient, which is below the
        // threshold of noticing; alpha on the same gradient reads clearly while staying
        // subtle enough not to distract from the item.
        int glowAlpha = 0x9A + Math.round(pulse * 0x65);
        ACGInventoryStyle.texSquareTinted(graphics, ACGInventoryStyle.GLOW, centerX, centerY,
                radius * (1.40F + pulse * 0.34F), ACGInventoryStyle.GLOW_SIZE,
                (glowAlpha << 24) | 0x00FFFFFF);
        // Drawn last so the motes read as floating in front of the disc and rings.
        ACGInventoryStyle.drawSparkles(graphics, centerX, centerY, radius, now);
    }

    int showcaseWidth() {
        return Math.min(260, Math.max(160, contentWidth * 2 / 5));
    }

}
