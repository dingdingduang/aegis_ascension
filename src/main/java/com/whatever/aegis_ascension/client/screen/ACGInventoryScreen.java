package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;

import com.whatever.aegis_ascension.client.ClientLifecycle;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.ClientStorageState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ClippableWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGInventoryStyle;
import com.whatever.aegis_ascension.client.screen.acg.ACGStorageRowWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.menu.ACGInventoryMenu;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.StorageActionPacket;
import com.whatever.aegis_ascension.network.StoreInventorySlotPacket;
import com.whatever.aegis_ascension.storage.StoredItem;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.ArrayList;
import java.util.function.IntUnaryOperator;

/**
 * The second ACG inventory mode: real vanilla player slots and a vanilla-recipe 3x3
 * crafting grid alongside a compact view of PlayerStorage.
 */
public final class ACGInventoryScreen extends AbstractContainerScreen<ACGInventoryMenu> {
    private static final int DEFAULT_PANEL_WIDTH = 480;
    private static final int DEFAULT_PANEL_HEIGHT = 226;
    private static final int CONTENT_MARGIN = 10;
    private static final int TOP_MARGIN = 12;
    private static final int BOTTOM_MARGIN = 12;
    private static final int BASE_DIVIDER_X = 260;
    private static final int MIN_INVENTORY_PANE_WIDTH = 194;
    /** Last Curios slot ends near x=241; this keeps storage safely to its right. */
    private static final int MIN_INVENTORY_PANE_WIDTH_WITH_CURIOS = 244;
    private static final int MIN_STORAGE_PANE_WIDTH = 160;
    private static final int CURIOS_GRID_X = 205;
    private static final int CURIOS_GRID_Y = 35;
    private static final int CURIOS_ROWS_PER_PAGE = 7;
    private static final int CURIOS_COLUMNS = 2;
    private static final int CURIOS_PAGE_BUTTON_Y =
            CURIOS_GRID_Y + CURIOS_ROWS_PER_PAGE * 18 + 3;
    private static final int CURIOS_PAGE_LABEL_Y = CURIOS_PAGE_BUTTON_Y + 20;
    private static final int STORAGE_PANE_GAP = 8;
    private static final int STORAGE_INNER_MARGIN = 0;
    private static final int STORAGE_CARD_GAP = 8;
    private static final int STORAGE_CARD_WIDTH = 72;
    private static final int STORAGE_CARD_HEIGHT = 72;
    private static final int STORAGE_GRID_TOP = 32;
    private static final int STORAGE_ACTION_HEIGHT = 20;
    private static final int STORAGE_ACTION_GAP = 8;
    private static final int STORAGE_COMPACT_ACTION_THRESHOLD = 300;
    private static final int MAX_STORAGE_COLUMNS = 10;
    private static final String INVENTORY_SCROLL_MODE_TAB = "INVENTORY_AND_CRAFTING";
    private static final int STORAGE_SCROLL_STEP = 40;
    private static final int STORAGE_DRAG_SCROLL_EDGE = 26;
    private static final int STORAGE_DRAG_SCROLL_STEP = 7;
    private static final double STORAGE_DRAG_THRESHOLD = 4.0D;
    private static final float STORAGE_DRAG_PREVIEW_Z = 400.0F;

    private int storagePage;
    private int curioPage;
    private int storageSelection = -1;
    private int contentDividerX = BASE_DIVIDER_X;
    private int storageX = BASE_DIVIDER_X + STORAGE_PANE_GAP;
    private int storageWidth = DEFAULT_PANEL_WIDTH - storageX - STORAGE_INNER_MARGIN;
    private int storageColumns = 1;
    private int storageRows = 1;
    private int storagePageSize = 1;
    private int storageScroll;
    private int storageMaxScroll;
    private int storageViewportTop = STORAGE_GRID_TOP;
    private int storageViewportBottom = DEFAULT_PANEL_HEIGHT - 36;
    private int storageGridStartX;
    private int storageActionY = DEFAULT_PANEL_HEIGHT - 28;
    private boolean compactStorageActions;
    private EditBox storageSearchBox;
    private String storageSearch = "";
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private int dragSourceStorageIndex = -1;
    private double dragOriginX;
    private double dragOriginY;
    private boolean draggingStorageRow;
    private ACGButton previousCurioPage;
    private ACGButton nextCurioPage;
    private final ACGDrawer drawer = new ACGDrawer();

    public ACGInventoryScreen(ACGInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = DEFAULT_PANEL_WIDTH;
        imageHeight = DEFAULT_PANEL_HEIGHT;
        inventoryLabelX = 29;
        inventoryLabelY = 108;
        titleLabelX = 29;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        int contentX = ACGDrawer.WIDTH + CONTENT_MARGIN;
        int contentTop = ACGPerkSelectionScreen.TOP_BAR_HEIGHT + TOP_MARGIN;
        imageWidth = Math.max(1, width - contentX - BOTTOM_MARGIN);
        imageHeight = Math.max(1, height - contentTop - BOTTOM_MARGIN);
        super.init();
        leftPos = contentX;
        topPos = contentTop;
        layoutResponsiveContent();
        curioPage = menu.setCurioPage(curioPage);
        drawer.init(height,
                widget -> addRenderableWidget(widget),
                () -> ACGDrawer.Destination.INVENTORY_AND_CRAFTING,
                this::navigateFromDrawer,
                () -> menu.getCarried().isEmpty());
        addCurioPaginationButtons();
        rebuildStorageWidgets();
        ACGCursorState.restoreIfPending(minecraft);
    }

    private void addCurioPaginationButtons() {
        previousCurioPage = null;
        nextCurioPage = null;
        if (menu.curioPageCount() <= 1) {
            return;
        }

        previousCurioPage = new ACGButton(
                leftPos + CURIOS_GRID_X,
                topPos + CURIOS_PAGE_BUTTON_Y,
                17,
                18,
                GeneralTextMethods.getLiteralString("<"),
                button -> changeCurioPage(-1)
        ).style(ACGButton.Style.PLAIN);
        addRenderableWidget(previousCurioPage);

        nextCurioPage = new ACGButton(
                leftPos + CURIOS_GRID_X + 19,
                topPos + CURIOS_PAGE_BUTTON_Y,
                17,
                18,
                GeneralTextMethods.getLiteralString(">"),
                button -> changeCurioPage(1)
        ).style(ACGButton.Style.PLAIN);
        addRenderableWidget(nextCurioPage);
        updateCurioPageButtonState();
    }

    private void changeCurioPage(int delta) {
        curioPage = menu.setCurioPage(curioPage + delta);
        hoveredSlot = null;
        updateCurioPageButtonState();
    }

    private void updateCurioPageButtonState() {
        int pageCount = menu.curioPageCount();
        if (previousCurioPage != null) {
            previousCurioPage.active = curioPage > 0;
        }
        if (nextCurioPage != null) {
            nextCurioPage.active = curioPage + 1 < pageCount;
        }
    }

    private void layoutResponsiveContent() {
        // The vanilla slots reach x=191, while the optional two-column Curios grid
        // reaches x=241. Reserve the actual required width before donating the
        // remainder to storage; storage may reduce its column count at narrow scales.
        int requiredInventoryWidth = menu.hasCurios()
                ? MIN_INVENTORY_PANE_WIDTH_WITH_CURIOS
                : MIN_INVENTORY_PANE_WIDTH;
        int dividerUpperBound = Math.max(requiredInventoryWidth,
                imageWidth - MIN_STORAGE_PANE_WIDTH - STORAGE_PANE_GAP
                        - STORAGE_INNER_MARGIN);
        contentDividerX = Math.min(BASE_DIVIDER_X, dividerUpperBound);
        contentDividerX = Math.max(requiredInventoryWidth, contentDividerX);
        storageX = contentDividerX + STORAGE_PANE_GAP;
        storageWidth = Math.max(STORAGE_CARD_WIDTH,
                imageWidth - storageX - STORAGE_INNER_MARGIN);

        storageColumns = Math.max(1, Math.min(MAX_STORAGE_COLUMNS,
                (storageWidth + STORAGE_CARD_GAP)
                        / (STORAGE_CARD_WIDTH + STORAGE_CARD_GAP)));
        // ACGStoragePage left-aligns its variable-length storage grid with the search box.
        // Use the same anchor here instead of centring a short final row.
        storageGridStartX = storageX;

        compactStorageActions = storageWidth < STORAGE_COMPACT_ACTION_THRESHOLD;
        int actionColumns = compactStorageActions ? 2 : 5;
        int actionRows = (5 + actionColumns - 1) / actionColumns;
        int actionBlockHeight = compactStorageActions
                ? STORAGE_ACTION_HEIGHT * actionRows
                        + STORAGE_ACTION_GAP * (actionRows - 1)
                : STORAGE_ACTION_HEIGHT;
        storageActionY = Math.max(STORAGE_GRID_TOP + STORAGE_CARD_HEIGHT
                        + STORAGE_ACTION_GAP,
                imageHeight - actionBlockHeight - 8);
        storageViewportTop = STORAGE_GRID_TOP;
        storageViewportBottom = Math.max(storageViewportTop + STORAGE_CARD_HEIGHT,
                storageActionY - STORAGE_ACTION_GAP);
        int gridHeight = Math.max(STORAGE_CARD_HEIGHT,
                storageActionY - STORAGE_GRID_TOP - STORAGE_ACTION_GAP);
        storageRows = Math.max(1,
                (gridHeight + STORAGE_CARD_GAP)
                        / (STORAGE_CARD_HEIGHT + STORAGE_CARD_GAP));
        storagePageSize = Math.max(1, storageColumns * storageRows);
    }

    private void rebuildStorageWidgets() {
        List<StoredItem> rows = ClientStorageState.getSortedItems();
        List<Integer> visible = visibleStorageIndices(rows);
        boolean scrollMode = isStorageScrollMode();
        int pageCount = scrollMode ? 1 : Math.max(1,
                (visible.size() + storagePageSize - 1) / storagePageSize);
        if (scrollMode) {
            storagePage = 0;
            int step = STORAGE_CARD_HEIGHT + STORAGE_CARD_GAP;
            int totalRows = (visible.size() + storageColumns - 1) / storageColumns;
            int viewportHeight = Math.max(1,
                    storageViewportBottom - storageViewportTop);
            storageMaxScroll = Math.max(0, totalRows * step - viewportHeight);
            storageScroll = Math.max(0,
                    Math.min(storageScroll, storageMaxScroll));
        } else {
            storageMaxScroll = 0;
            storageScroll = 0;
            storagePage = Math.max(0, Math.min(storagePage, pageCount - 1));
        }

        int storageRight = leftPos + storageX + storageWidth;
        boolean showPageButtons = !scrollMode && pageCount > 1;
        int searchWidth = Math.max(1, storageWidth - (showPageButtons ? 62 : 0));
        if (storageSearchBox == null) {
            storageSearchBox = new EditBox(
                    font,
                    leftPos + storageX,
                    topPos + 8,
                    searchWidth,
                    20,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.storage.search")
            );
            storageSearchBox.setMaxLength(256);
            storageSearchBox.setHint(GeneralTextMethods.getTranslatableString(
                    "screen.aegis_ascension.acg.storage.search_hint"));
            storageSearchBox.setValue(storageSearch);
            storageSearchBox.setResponder(this::onStorageSearchChanged);
        } else {
            storageSearchBox.setX(leftPos + storageX);
            storageSearchBox.setY(topPos + 8);
            storageSearchBox.setWidth(searchWidth);
        }
        addRenderableWidget(storageSearchBox);

        if (showPageButtons) {
            ACGButton previous = new ACGButton(
                    storageRight - 54, topPos + 8, 24, 20, GeneralTextMethods.getLiteralString("<"),
                    button -> changeStoragePage(-1)
            ).style(ACGButton.Style.PLAIN);
            previous.active = storagePage > 0;
            addRenderableWidget(previous);

            ACGButton next = new ACGButton(
                    storageRight - 24, topPos + 8, 24, 20, GeneralTextMethods.getLiteralString(">"),
                    button -> changeStoragePage(1)
            ).style(ACGButton.Style.PLAIN);
            next.active = storagePage + 1 < pageCount;
            addRenderableWidget(next);
        }

        int first = scrollMode ? 0 : storagePage * storagePageSize;
        int last = scrollMode
                ? visible.size()
                : Math.min(visible.size(), first + storagePageSize);
        List<Integer> currentPage = visible.subList(first, last);
        if (!currentPage.contains(storageSelection)) {
            storageSelection = currentPage.isEmpty() ? -1 : currentPage.get(0);
        }
        for (int visibleIndex = first; visibleIndex < last; visibleIndex++) {
            int storageIndex = visible.get(visibleIndex);
            int local = visibleIndex - first;
            int x = leftPos + storageGridStartX
                    + (local % storageColumns)
                    * (STORAGE_CARD_WIDTH + STORAGE_CARD_GAP);
            int y = topPos + STORAGE_GRID_TOP
                    + (local / storageColumns)
                    * (STORAGE_CARD_HEIGHT + STORAGE_CARD_GAP)
                    - (scrollMode ? storageScroll : 0);
            ACGStorageRowWidget card = new ACGStorageRowWidget(
                    x, y,
                    STORAGE_CARD_WIDTH, STORAGE_CARD_HEIGHT,
                    storageIndex, rows.get(storageIndex),
                    storageIndex == storageSelection,
                    this::selectStorageRow
            );
            if (scrollMode) {
                int viewportTop = topPos + storageViewportTop;
                int viewportBottom = topPos + storageViewportBottom;
                boolean fits = y + STORAGE_CARD_HEIGHT > viewportTop
                        && y < viewportBottom;
                card.setClipBounds(viewportTop, viewportBottom);
                card.visible = fits;
                card.active = fits;
            } else {
                card.setClipBounds(ClippableWidget.NO_CLIP_TOP,
                        ClippableWidget.NO_CLIP_BOTTOM);
            }
            addRenderableWidget(card);
        }

        StoredItem selected = ClientStorageState.getRow(storageSelection);
        boolean virtual = selected != null && selected.isVirtual();
        boolean confirmationRequired = virtual
                && requiresUseConfirmation(selected.virtualId());
        Component primaryLabel = GeneralTextMethods.getTranslatableString(confirmationRequired
                ? "screen.aegis_ascension.acg.inventory_mode.manage_original"
                : virtual
                ? "screen.aegis_ascension.acg.storage.use"
                : "screen.aegis_ascension.acg.storage.extract");
        int actionColumns = compactStorageActions ? 2 : 5;
        int actionWidth = Math.max(1,
                (storageWidth - STORAGE_ACTION_GAP * (actionColumns - 1))
                        / actionColumns);
        int actionY = topPos + storageActionY;
        int actionX = leftPos + storageX;
        IntUnaryOperator actionButtonX = index -> actionX
                + (index % actionColumns) * (actionWidth + STORAGE_ACTION_GAP);
        IntUnaryOperator actionButtonY = index -> actionY
                + (index / actionColumns) * (STORAGE_ACTION_HEIGHT + STORAGE_ACTION_GAP);
        ACGButton primary = new ACGButton(
                actionButtonX.applyAsInt(0), actionButtonY.applyAsInt(0),
                actionWidth, STORAGE_ACTION_HEIGHT, primaryLabel,
                button -> {
                    if (confirmationRequired) {
                        openOriginalMode();
                    } else {
                        performStorageAction(virtual
                                ? StorageActionPacket.Action.USE
                                : StorageActionPacket.Action.EXTRACT);
                    }
                }
        ).style(ACGButton.Style.CTA);
        primary.active = selected != null;
        addRenderableWidget(primary);

        ACGButton sorting = new ACGButton(
                actionButtonX.applyAsInt(1), actionButtonY.applyAsInt(1),
                actionWidth, STORAGE_ACTION_HEIGHT,
                GeneralTextMethods.getTranslatableString(switch (ClientStorageState.getSortMode()) {
                    case NAME_DESC ->
                            "screen.aegis_ascension.acg.storage.sort_descending";
                    case RARITY ->
                            "screen.aegis_ascension.acg.storage.sort_rarity";
                    case MANUAL ->
                            "screen.aegis_ascension.acg.storage.sort_manual";
                    default ->
                            "screen.aegis_ascension.acg.storage.sort_ascending";
                }),
                button -> cycleStorageSort()
        ).style(ACGButton.Style.PLAIN);
        sorting.active = !rows.isEmpty();
        addRenderableWidget(sorting);

        ACGButton view = new ACGButton(
                actionButtonX.applyAsInt(2), actionButtonY.applyAsInt(2),
                actionWidth,
                STORAGE_ACTION_HEIGHT,
                GeneralTextMethods.getTranslatableString(scrollMode
                        ? "screen.aegis_ascension.acg.storage.view_scroll"
                        : "screen.aegis_ascension.acg.storage.view_paged"),
                button -> toggleStorageView()
        ).style(ACGButton.Style.PLAIN);
        addRenderableWidget(view);

        ACGButton sell = new ACGButton(
                actionButtonX.applyAsInt(3), actionButtonY.applyAsInt(3),
                actionWidth, STORAGE_ACTION_HEIGHT,
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.storage.sell"),
                button -> performStorageAction(StorageActionPacket.Action.SELL)
        ).style(ACGButton.Style.PLAIN);
        sell.active = selected != null && !virtual;
        addRenderableWidget(sell);

        ACGButton discard = new ACGButton(
                actionButtonX.applyAsInt(4), actionButtonY.applyAsInt(4),
                actionWidth, STORAGE_ACTION_HEIGHT,
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.storage.discard"),
                button -> performStorageAction(StorageActionPacket.Action.DISCARD)
        ).style(ACGButton.Style.TEAL);
        discard.active = selected != null;
        addRenderableWidget(discard);
    }

    private void cycleStorageSort() {
        ClientStorageState.cycleSortMode();
        storagePage = 0;
        storageScroll = 0;
        storageSelection = -1;
        clearWidgets();
        init();
    }

    private boolean isStorageScrollMode() {
        return ClientSettings.get().scrollModeTabs.contains(INVENTORY_SCROLL_MODE_TAB);
    }

    private void toggleStorageView() {
        ClientSettings settings = ClientSettings.get();
        if (!settings.scrollModeTabs.remove(INVENTORY_SCROLL_MODE_TAB)) {
            settings.scrollModeTabs.add(INVENTORY_SCROLL_MODE_TAB);
        }
        settings.save();
        storagePage = 0;
        storageScroll = 0;
        storageSelection = -1;
        clearWidgets();
        init();
    }

    private List<Integer> visibleStorageIndices(List<StoredItem> rows) {
        String query = storageSearch.trim().toLowerCase(java.util.Locale.ROOT);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (ACGStoragePage.matchesStorageSearch(rows.get(i), query)) {
                visible.add(i);
            }
        }
        return visible;
    }

    private void onStorageSearchChanged(String value) {
        if (value.equals(storageSearch)) {
            return;
        }
        storageSearch = value;
        storagePage = 0;
        storageScroll = 0;
        storageSelection = -1;
        clearWidgets();
        init();
        setFocused(storageSearchBox);
    }

    private static boolean requiresUseConfirmation(String virtualId) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        return definition != null && definition.requiresConfirmation;
    }

    private void selectStorageRow(int index) {
        storageSelection = index;
        clearWidgets();
        init();
    }

    private void changeStoragePage(int delta) {
        if (isStorageScrollMode()) {
            return;
        }
        storagePage += delta;
        storageScroll = 0;
        storageSelection = -1;
        clearWidgets();
        init();
    }

    private void performStorageAction(StorageActionPacket.Action action) {
        performStorageAction(action, ClientStorageState.getRow(storageSelection));
    }

    private void performStorageAction(StorageActionPacket.Action action, StoredItem row) {
        if (row == null) {
            return;
        }
        long amount = switch (action) {
            case EXTRACT -> Math.min(row.count(), row.prototype().getMaxStackSize());
            case USE, SELL, DISCARD -> 1L;
        };
        ModNetworking.sendToServer(new StorageActionPacket(action, row, amount));
    }

    /** Called after the authoritative server storage sync lands. */
    public void refreshStorage() {
        clearWidgets();
        init();
    }

    private void openOriginalMode() {
        navigateFromDrawer(ACGDrawer.Destination.STORAGE);
    }

    private void navigateFromDrawer(ACGDrawer.Destination destination) {
        if (destination == ACGDrawer.Destination.INVENTORY_AND_CRAFTING
                || !menu.getCarried().isEmpty() || minecraft == null) {
            return;
        }
        ACGPerkSelectionScreen.UIMode target = destination.pageMode();
        if (target == null) {
            return;
        }
        ClientSettings settings = ClientSettings.get();
        if (destination == ACGDrawer.Destination.STORAGE) {
            settings.inventoryMode = ClientSettings.InventoryMode.ORIGINAL;
        }
        if (settings.rememberLastPosition) {
            settings.lastMode = target.name();
        }
        settings.save();
        ACGCursorState.remember(lastMouseX, lastMouseY);
        onClose();
        minecraft.setScreen(new ACGPerkSelectionScreen(target));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0) {
            dragSourceStorageIndex = storageRowAt(mouseX, mouseY);
            dragOriginX = mouseX;
            dragOriginY = mouseY;
            draggingStorageRow = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchFocused = getFocused() == storageSearchBox;
        if (!searchFocused
                && ClientLifecycle.PUT_INTO_STORAGE_UI.matches(keyCode, scanCode)
                && menu.getCarried().isEmpty()) {
            // In the storage half of this combined screen the same key is the inverse
            // operation: withdraw the real item under the cursor. Addressing the row by
            // identity keeps this safe even if the client display is sorted or changes
            // before the server handles the packet.
            for (var child : children()) {
                if (child instanceof ACGStorageRowWidget card && card.isHoveredNow()) {
                    if (!card.row().isVirtual()) {
                        storageSelection = card.storageIndex();
                        performStorageAction(StorageActionPacket.Action.EXTRACT, card.row());
                    }
                    // Virtual storage entries cannot become ItemStacks, but still consume
                    // the key press so it cannot fall through to another inventory action.
                    return true;
                }
            }

            // In the vanilla inventory half the key retains its existing deposit action.
            Slot hovered = getSlotUnderMouse();
            if (hovered != null && hovered.hasItem()
                    && minecraft != null && minecraft.player != null
                    && hovered.container == minecraft.player.getInventory()) {
                int menuSlot = menu.slots.indexOf(hovered);
                if (menuSlot >= 0) {
                    ModNetworking.sendToServer(new StoreInventorySlotPacket(
                            menu.containerId,
                            menuSlot
                    ));
                    return true;
                }
            }
        }
        if (!searchFocused
                && ClientLifecycle.OPEN_ACG_SCREEN.matches(keyCode, scanCode)) {
            while (ClientLifecycle.OPEN_ACG_SCREEN.consumeClick()) {
                // Prevent ClientEvents' tick handler from reopening ACG immediately.
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Returns the full sorted-list index of the storage card under the cursor. */
    private int storageRowAt(double mouseX, double mouseY) {
        for (var child : children()) {
            if (child instanceof ACGStorageRowWidget card
                    && card.visible && card.isMouseOver(mouseX, mouseY)) {
                return card.storageIndex();
            }
        }
        return -1;
    }

    /**
     * Finds the nearest visible card when a drag is released in a gap between cards. The
     * returned values are full display indices, which is the coordinate system expected by
     * {@link ClientStorageState#moveInManualOrder(int, int)}.
     */
    private int storageDropTarget(double mouseX, double mouseY) {
        int exact = storageRowAt(mouseX, mouseY);
        if (exact >= 0) {
            return exact;
        }
        int gridLeft = leftPos + storageX;
        int gridRight = gridLeft + storageWidth;
        int gridTop = topPos + STORAGE_GRID_TOP;
        int gridBottom = topPos + storageActionY;
        if (mouseX < gridLeft || mouseX > gridRight
                || mouseY < gridTop || mouseY > gridBottom) {
            return -1;
        }

        int firstIndex = -1;
        int lastIndex = -1;
        int topEdge = Integer.MAX_VALUE;
        int bottomEdge = Integer.MIN_VALUE;
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (var child : children()) {
            if (!(child instanceof ACGStorageRowWidget card) || !card.visible) {
                continue;
            }
            if (firstIndex < 0 || card.storageIndex() < firstIndex) {
                firstIndex = card.storageIndex();
            }
            if (card.storageIndex() > lastIndex) {
                lastIndex = card.storageIndex();
            }
            topEdge = Math.min(topEdge, card.getY());
            bottomEdge = Math.max(bottomEdge, card.getY() + card.getHeight());
            double dx = mouseX - (card.getX() + card.getWidth() / 2.0D);
            double dy = mouseY - (card.getY() + card.getHeight() / 2.0D);
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = card.storageIndex();
            }
        }
        if (firstIndex < 0) {
            return -1;
        }
        if (mouseY < topEdge) {
            return firstIndex;
        }
        if (mouseY > bottomEdge) {
            return lastIndex;
        }
        return nearest;
    }

    /** Draws the same cursor-following icon preview used by the original Storage tab. */
    private void renderStorageDragPreview(GuiGraphics graphics) {
        if (!draggingStorageRow || dragSourceStorageIndex < 0) {
            return;
        }
        StoredItem dragged = ClientStorageState.getRow(dragSourceStorageIndex);
        if (dragged == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, STORAGE_DRAG_PREVIEW_Z);
        ACGStorageRowWidget.drawRowIcon(
                graphics, dragged,
                (int) lastMouseX - 16,
                (int) lastMouseY - 16,
                32
        );
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0 && dragSourceStorageIndex >= 0 && !draggingStorageRow
                && (Math.abs(mouseX - dragOriginX) > STORAGE_DRAG_THRESHOLD
                || Math.abs(mouseY - dragOriginY) > STORAGE_DRAG_THRESHOLD)) {
            draggingStorageRow = true;
        }
        if (draggingStorageRow) {
            updateStorageDragScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingStorageRow && dragSourceStorageIndex >= 0) {
            int target = storageDropTarget(mouseX, mouseY);
            int source = dragSourceStorageIndex;
            draggingStorageRow = false;
            dragSourceStorageIndex = -1;
            if (target >= 0 && target != source) {
                ClientStorageState.moveInManualOrder(source, target);
                storageSelection = target;
                clearWidgets();
                init();
            }
            return true;
        }
        dragSourceStorageIndex = -1;
        draggingStorageRow = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        ACGTheme.drawVignetteBackground(
                graphics,
                width,
                height,
                (float) ClientSettings.get().backgroundOpacity
        );
        renderTopBar(graphics);
        drawer.render(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderStorageDragPreview(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        renderStorageTooltip(graphics, mouseX, mouseY);
    }

    private void renderTopBar(GuiGraphics graphics) {
        graphics.fill(0, 0, width, ACGPerkSelectionScreen.TOP_BAR_HEIGHT, 0xE6121016);
        graphics.fill(0, ACGPerkSelectionScreen.TOP_BAR_HEIGHT - 1,
                width, ACGPerkSelectionScreen.TOP_BAR_HEIGHT, ACGTheme.GOLD_DIM);
        drawCenteredString(graphics, font,
                ACGTheme.asHeader(GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.title")),
                width / 2, (ACGPerkSelectionScreen.TOP_BAR_HEIGHT - 9) / 2,
                ACGTheme.TEXT_PRIMARY);
        if (minecraft != null && minecraft.player != null) {
            Component level = ACGPerkSelectionScreen.progressionLabel();
            graphics.drawString(font, level, width - font.width(level) - 12,
                    (ACGPerkSelectionScreen.TOP_BAR_HEIGHT - 8) / 2,
                    ACGTheme.CYAN_ACCENT, false);
        }
    }

    private void renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (var child : children()) {
            if (!(child instanceof ACGStorageRowWidget card) || !card.isHoveredNow()) {
                continue;
            }
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(card.row().displayComponent());
            if (card.row().isVirtual()) {
                VirtualItems.Definition definition = VirtualItems.byId(card.row().virtualId());
                if (definition != null) {
                    tooltip.add(definition.descriptionComponent());
                }
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty() || hoveredSlot == null
                || hoveredSlot.hasItem() || !menu.isCuriosSlot(hoveredSlot.index)) {
            return;
        }
        ACGInventoryMenu.CurioSlotInfo info = menu.curioSlotInfo(hoveredSlot.index);
        if (info == null || info.pageIndex() != curioPage) {
            return;
        }

        String translationKey = "curios.identifier." + info.identifier();
        Component slotType = I18n.exists(translationKey)
                ? GeneralTextMethods.getTranslatableString(translationKey)
                : GeneralTextMethods.getLiteralString(humanizeIdentifier(info.identifier()));
        graphics.renderComponentTooltip(
                font,
                List.of(GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.inventory_workbench.curio_slot",
                        slotType,
                        info.handlerIndex() + 1
                )),
                mouseX,
                mouseY
        );
    }

    private static String humanizeIdentifier(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim()
                .replace('_', ' ')
                .replace('-', ' ');
        if (normalized.isEmpty()) {
            return "Curio";
        }
        return Character.toUpperCase(normalized.charAt(0))
                + normalized.substring(1);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Vanilla slots still receive vanilla click semantics; these plates only skin them
        // to match the ACG interface.
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            ACGInventoryStyle.box(graphics, x, y, 18, 18,
                    0xCC18151A, ACGTheme.GOLD_DIM);
        }
        // Crafting arrow between the 3x3 grid and result slot.
        graphics.drawString(font, GeneralTextMethods.getLiteralString("→"),
                leftPos + 128, topPos + 57, ACGTheme.GOLD_BRIGHT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font,
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.inventory_workbench.storage",
                        ClientStorageState.getRowCount(), ClientStorageState.getMaxItemTypes()),
                storageX, -10, ACGTheme.GOLD_BRIGHT, false);
        graphics.drawString(font,
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.inventory_workbench.crafting"),
                49, 22, ACGTheme.GOLD_BRIGHT, false);
        if (menu.hasCurios()) {
            graphics.drawString(font,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.inventory_workbench.curios"),
                    CURIOS_GRID_X, 22, ACGTheme.GOLD_BRIGHT, false);
            if (menu.curioPageCount() > 1) {
                Component page = GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.inventory_workbench.curios_page",
                        curioPage + 1,
                        menu.curioPageCount()
                );
                drawCenteredString(
                        graphics,
                        font,
                        page,
                        CURIOS_GRID_X + CURIOS_COLUMNS * 9,
                        CURIOS_PAGE_LABEL_Y,
                        ACGTheme.TEXT_MUTED
                );
            }
        }
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, ACGTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, title,
                titleLabelX, titleLabelY, ACGTheme.TEXT_PRIMARY, false);

        List<Integer> visible = visibleStorageIndices(
                ClientStorageState.getSortedItems());
        int pages = isStorageScrollMode() ? 1 : Math.max(1,
                (visible.size() + storagePageSize - 1)
                        / storagePageSize);
        Component pageLabel = GeneralTextMethods.getTranslatableString(
                "screen.aegis_ascension.acg.inventory_workbench.page",
                storagePage + 1,
                pages
        );
        graphics.drawString(font, pageLabel,
                storageX + storageWidth - font.width(pageLabel), -10,
                ACGTheme.TEXT_MUTED, false);

        if (visible.isEmpty()) {
            Component emptyLabel = GeneralTextMethods.getTranslatableString(
                    ClientStorageState.getRowCount() > 0
                            ? "screen.aegis_ascension.acg.storage.no_matches"
                            : "screen.aegis_ascension.acg.storage.empty");
            drawCenteredString(graphics, font, emptyLabel,
                    storageX + storageWidth / 2,
                    STORAGE_GRID_TOP + Math.max(0,
                            (storageActionY - STORAGE_GRID_TOP) / 2),
                    ACGTheme.TEXT_MUTED);
        }
    }

    /**
     * AbstractContainerScreen#tick is final in 1.20.1. Update drag scrolling from the
     * mouse-drag callback instead, which also avoids rebuilding the container every tick
     * when the cursor is not being dragged.
     */
    private void updateStorageDragScroll() {
        if (!draggingStorageRow || !isStorageScrollMode() || storageMaxScroll <= 0) {
            return;
        }
        int distanceFromTop = (int) lastMouseY - (topPos + storageViewportTop);
        int distanceFromBottom = (topPos + storageViewportBottom) - (int) lastMouseY;
        int delta = 0;
        if (distanceFromTop < STORAGE_DRAG_SCROLL_EDGE) {
            delta = distanceFromTop < STORAGE_DRAG_SCROLL_EDGE / 2
                    ? -STORAGE_DRAG_SCROLL_STEP * 2
                    : -STORAGE_DRAG_SCROLL_STEP;
        } else if (distanceFromBottom < STORAGE_DRAG_SCROLL_EDGE) {
            delta = distanceFromBottom < STORAGE_DRAG_SCROLL_EDGE / 2
                    ? STORAGE_DRAG_SCROLL_STEP * 2
                    : STORAGE_DRAG_SCROLL_STEP;
        }
        if (delta != 0) {
            int updated = Math.max(0,
                    Math.min(storageMaxScroll, storageScroll + delta));
            if (updated != storageScroll) {
                storageScroll = updated;
                clearWidgets();
                init();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (drawer.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (isStorageScrollMode()
                && storageMaxScroll > 0
                && mouseX >= leftPos + storageX
                && mouseX <= leftPos + storageX + storageWidth
                && mouseY >= topPos + storageViewportTop
                && mouseY <= topPos + storageViewportBottom
                && Math.abs(delta) > 1.0E-9D) {
            storageScroll = Math.max(0, Math.min(storageMaxScroll,
                    storageScroll + (int) Math.round(
                            (delta < 0.0D ? 1 : -1) * STORAGE_SCROLL_STEP)));
            clearWidgets();
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
