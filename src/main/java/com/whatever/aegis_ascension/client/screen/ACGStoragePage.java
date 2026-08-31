package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.ClientLifecycle;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.ClientStorageState;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGEditBox;
import com.whatever.aegis_ascension.client.screen.acg.ACGInventoryStyle;
import com.whatever.aegis_ascension.client.screen.acg.ACGStorageRowWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.StorageActionPacket;
import com.whatever.aegis_ascension.storage.StoredItem;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Original virtual-storage page hosted by the unified ACG screen. */
final class ACGStoragePage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int STORAGE_CARD_SIZE = 72;
    private static final float MODAL_Z = 400.0F;
    private static final float MODAL_WIDGET_Z = 410.0F;
    private static final double DRAG_THRESHOLD = 4.0D;
    private static final int DRAG_SCROLL_EDGE = 26;
    private static final int DRAG_SCROLL_STEP = 7;
    private static final double GRID_SCROLL_STEP = 40.0D;

    private int selection = -1;
    private StorageActionPacket.Action quantityPrompt;
    private String quantityInput = "";
    private EditBox quantityBox;
    private boolean confirmUsePrompt;
    private EditBox searchBox;
    private String search = "";

    private int dragSourceIndex = -1;
    private double dragOriginX;
    private double dragOriginY;
    private boolean draggingRow;
    private int dragCursorX;
    private int dragCursorY;

    @Override
    public void init(ACGScreenContext context) {
        int contentX = context.contentX();
        int contentWidth = context.contentWidth();
        int contentTop = context.contentTop();
        int contentBottom = context.contentBottom();
        List<StoredItem> rows = ClientStorageState.getSortedItems();
        if (selection >= rows.size()) {
            selection = rows.isEmpty() ? -1 : 0;
        }
        if (selection < 0 && !rows.isEmpty()) {
            selection = 0;
        }

        if (confirmUsePrompt) {
            initConfirmUsePrompt(context);
            return;
        }
        if (quantityPrompt != null) {
            initQuantityPrompt(context);
            return;
        }

        int showcaseWidth = context.showcaseWidth();
        int rightX = contentX + showcaseWidth + CARD_GAP;
        int rightWidth = contentWidth - showcaseWidth - CARD_GAP;

        if (searchBox == null) {
            searchBox = new EditBox(context.font(), rightX, contentTop + 2, rightWidth, 16,
                    getTranslatableString("screen.aegis_ascension.acg.storage.search"));
            searchBox.setMaxLength(256);
            searchBox.setHint(
                    getTranslatableString("screen.aegis_ascension.acg.storage.search_hint"));
            searchBox.setValue(search);
            searchBox.setResponder(value -> {
                if (value.equals(search)) {
                    return;
                }
                search = value;
                context.page(0);
                context.rebuild();
                context.focus(searchBox);
            });
        } else {
            searchBox.setX(rightX);
            searchBox.setY(contentTop + 2);
            searchBox.setWidth(rightWidth);
        }
        context.add(searchBox);

        List<Integer> visible = visibleStorageIndices(rows);
        if (!visible.contains(selection)) {
            selection = visible.isEmpty() ? -1 : visible.get(0);
        }

        context.gridViewportTop(contentTop + 22);
        context.gridViewportBottom(contentBottom - 34);
        boolean scrollMode = context.gridScrollMode();

        if (!visible.isEmpty()) {
            ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                    visible.size(), rightX, rightWidth,
                    context.gridViewportTop(), scrollMode ? contentBottom - 34 : contentBottom - 60,
                    STORAGE_CARD_SIZE, STORAGE_CARD_SIZE, STORAGE_CARD_SIZE, 10, true);

            int first = scrollMode ? 0 : layout.firstIndex();
            int last = scrollMode ? visible.size() : layout.lastIndex();
            int step = layout.cardHeight() + CARD_GAP;
            if (scrollMode) {
                int gridRows = (visible.size() + layout.columns() - 1) / layout.columns();
                context.gridMaxScroll(Math.max(0,
                        gridRows * step
                                - (context.gridViewportBottom() - context.gridViewportTop())));
                context.gridScroll(Math.max(0,
                        Math.min(context.gridMaxScroll(), context.gridScroll())));
                context.pageCount(1);
                context.page(0);
            } else {
                context.gridMaxScroll(0);
                context.gridScroll(0);
            }

            for (int slot = first; slot < last; slot++) {
                int index = visible.get(slot);
                int local = slot - first;
                int x = layout.startX()
                        + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
                int y = layout.startY()
                        + (local / layout.columns()) * step - context.gridScroll();
                ACGStorageRowWidget card = new ACGStorageRowWidget(
                        x, y, layout.cardWidth(), layout.cardHeight(),
                        index, rows.get(index), index == selection,
                        selected -> selectStorageRow(context, selected));
                if (scrollMode) {
                    boolean fits = y >= context.gridViewportTop()
                            && y + layout.cardHeight() <= context.gridViewportBottom();
                    card.visible = fits;
                    card.active = fits;
                }
                context.add(card);
            }
        } else {
            context.gridMaxScroll(0);
        }
        context.addPaginationButtons(rightX + rightWidth / 2, contentBottom - 52, false);

        StoredItem selected = selection >= 0 && selection < rows.size()
                ? rows.get(selection) : null;
        boolean virtual = selected != null && selected.isVirtual();
        ACGButton primary = ACGButton.builder(
                        getTranslatableString(virtual
                                ? "screen.aegis_ascension.acg.storage.use"
                                : "screen.aegis_ascension.acg.storage.extract"),
                        button -> beginStorageAction(context, virtual
                                ? StorageActionPacket.Action.USE
                                : StorageActionPacket.Action.EXTRACT))
                .bounds(contentX + (showcaseWidth - 160) / 2,
                        contentBottom - 26, 160, 20)
                .build()
                .style(ACGButton.Style.CTA);
        primary.active = selected != null;
        context.add(primary);

        int actionSlots = 4;
        int actionWidth = Math.max(40,
                (rightWidth - CARD_GAP * (actionSlots - 1)) / actionSlots);
        int actionY = contentBottom - 26;
        ACGButton sorting = ACGButton.builder(
                        getTranslatableString(switch (ClientStorageState.getSortMode()) {
                            case NAME_DESC -> "screen.aegis_ascension.acg.storage.sort_descending";
                            case RARITY -> "screen.aegis_ascension.acg.storage.sort_rarity";
                            case MANUAL -> "screen.aegis_ascension.acg.storage.sort_manual";
                            default -> "screen.aegis_ascension.acg.storage.sort_ascending";
                        }),
                        button -> {
                            ClientStorageState.cycleSortMode();
                            context.gridScroll(0);
                            context.page(0);
                            context.rebuild();
                        })
                .bounds(rightX, actionY, actionWidth, 20)
                .build()
                .style(ACGButton.Style.PLAIN);
        sorting.active = !rows.isEmpty();
        context.add(sorting);

        context.add(ACGButton.builder(
                        getTranslatableString(context.gridScrollMode()
                                ? "screen.aegis_ascension.acg.storage.view_scroll"
                                : "screen.aegis_ascension.acg.storage.view_paged"),
                        button -> context.toggleGridScrollMode())
                .bounds(rightX + actionWidth + CARD_GAP, actionY, actionWidth, 20)
                .build()
                .style(ACGButton.Style.PLAIN));

        ACGButton selling = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.storage.sell"),
                        button -> beginStorageAction(context, StorageActionPacket.Action.SELL))
                .bounds(rightX + (actionWidth + CARD_GAP) * 2,
                        actionY, actionWidth, 20)
                .build()
                .style(ACGButton.Style.PLAIN);
        selling.active = selected != null && ClientStorageState.isSellable(selection);
        context.add(selling);

        ACGButton discard = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.storage.discard"),
                        button -> beginStorageAction(context, StorageActionPacket.Action.DISCARD))
                .bounds(rightX + (actionWidth + CARD_GAP) * 3,
                        actionY, actionWidth, 20)
                .build()
                .style(ACGButton.Style.TEAL);
        discard.active = selected != null;
        context.add(discard);
    }

    private List<Integer> visibleStorageIndices(List<StoredItem> rows) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (matchesStorageSearch(rows.get(i), query)) {
                visible.add(i);
            }
        }
        return visible;
    }

    /** Shared with the integrated Inventory & Crafting storage pane. */
    static boolean matchesStorageSearch(StoredItem row, String query) {
        if (query.isBlank()) {
            return true;
        }
        String name = row.displayName().toLowerCase(Locale.ROOT);
        String namespace;
        String path;
        if (row.isVirtual()) {
            namespace = AegisAscensionMod.MOD_ID;
            path = row.virtualId().toLowerCase(Locale.ROOT);
        } else {
            var id = GeneralClientMethods.getItemKey(row.prototype().getItem());
            namespace = id == null ? "" : id.getNamespace();
            path = id == null ? "" : id.getPath();
        }
        int separator = query.indexOf(':');
        if (separator >= 0) {
            String namespaceQuery = query.substring(0, separator).trim();
            String pathQuery = query.substring(separator + 1).trim();
            if (pathQuery.startsWith("[") && pathQuery.endsWith("]")
                    && pathQuery.length() >= 2) {
                pathQuery = pathQuery.substring(1, pathQuery.length() - 1).trim();
            }
            pathQuery = pathQuery.replace(' ', '_');
            return namespace.equals(namespaceQuery) && path.contains(pathQuery);
        }
        return name.contains(query) || namespace.contains(query) || path.contains(query);
    }

    private static boolean requiresUseConfirmation(String virtualId) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        return definition != null && definition.requiresConfirmation;
    }

    private static boolean isActionVirtual(String virtualId) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        return definition != null && definition.effect.isAction();
    }

    private void selectStorageRow(ACGScreenContext context, int index) {
        selection = index;
        context.rebuild();
    }

    private void beginStorageAction(ACGScreenContext context,
                                    StorageActionPacket.Action action) {
        StoredItem row = ClientStorageState.getRow(selection);
        if (row == null) {
            return;
        }
        boolean actionBook = row.isVirtual() && isActionVirtual(row.virtualId());
        if (action == StorageActionPacket.Action.USE && row.isVirtual()
                && requiresUseConfirmation(row.virtualId())) {
            confirmUsePrompt = true;
            context.rebuild();
            return;
        }
        boolean instant = switch (action) {
            case EXTRACT -> row.count() <= 64L;
            case USE -> actionBook || row.count() <= 1L;
            case DISCARD -> ClientSettings.get().instantDiscardAll || row.count() <= 1L;
            default -> false;
        };
        if (instant) {
            ModNetworking.sendToServer(new StorageActionPacket(action, row, row.count()));
            return;
        }
        quantityPrompt = action;
        quantityInput = String.valueOf(Math.min(row.count(), 64L));
        context.rebuild();
    }

    private void initQuantityPrompt(ACGScreenContext context) {
        StoredItem row = ClientStorageState.getRow(selection);
        if (row == null) {
            quantityPrompt = null;
            return;
        }
        int panelWidth = 200;
        int panelHeight = 96;
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;

        quantityBox = new ACGEditBox(context.font(), panelX + 12, panelY + 34,
                panelWidth - 24 - 46, 18,
                getTranslatableString("screen.aegis_ascension.acg.storage.amount"),
                MODAL_WIDGET_Z);
        quantityBox.setValue(quantityInput);
        quantityBox.setResponder(value -> quantityInput = value);
        quantityBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,10}"));
        context.add(quantityBox);

        context.add(ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.storage.full"),
                        button -> {
                            quantityInput = String.valueOf(row.count());
                            quantityBox.setValue(quantityInput);
                        })
                .bounds(panelX + panelWidth - 52, panelY + 34, 40, 18)
                .build()
                .zOffset(MODAL_WIDGET_Z));

        context.add(ACGButton.builder(getTranslatableString("gui.cancel"), button -> {
                    quantityPrompt = null;
                    context.rebuild();
                })
                .bounds(panelX + 12, panelY + panelHeight - 28,
                        (panelWidth - 32) / 2, 20)
                .build().style(ACGButton.Style.PLAIN).zOffset(MODAL_WIDGET_Z));

        context.add(ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.storage.confirm"),
                        button -> confirmQuantityPrompt(context))
                .bounds(panelX + panelWidth / 2 + 4, panelY + panelHeight - 28,
                        (panelWidth - 32) / 2, 20)
                .build().style(ACGButton.Style.TEAL).zOffset(MODAL_WIDGET_Z));
    }

    private void confirmQuantityPrompt(ACGScreenContext context) {
        StoredItem row = ClientStorageState.getRow(selection);
        if (quantityPrompt == null || row == null) {
            quantityPrompt = null;
            context.rebuild();
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(quantityInput.isEmpty() ? "0" : quantityInput);
        } catch (NumberFormatException exception) {
            amount = 0L;
        }
        amount = Math.max(0L, Math.min(amount, row.count()));
        if (amount > 0L) {
            ModNetworking.sendToServer(new StorageActionPacket(quantityPrompt, row, amount));
        }
        quantityPrompt = null;
        context.rebuild();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        dragCursorX = mouseX;
        dragCursorY = mouseY;
        int contentX = context.contentX();
        int contentWidth = context.contentWidth();
        int contentTop = context.contentTop();
        int contentBottom = context.contentBottom();
        int showcaseWidth = context.showcaseWidth();
        graphics.drawString(context.font(), getTranslatableString(
                        "screen.aegis_ascension.acg.storage.header",
                        ClientStorageState.getRowCount(), ClientStorageState.getMaxItemTypes()),
                contentX, contentTop - 10, ACGTheme.GOLD_BRIGHT, false);

        StoredItem row = ClientStorageState.getRow(selection);
        if (row == null) {
            boolean filteredOut = ClientStorageState.getRowCount() > 0;
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(filteredOut
                            ? "screen.aegis_ascension.acg.storage.no_matches"
                            : "screen.aegis_ascension.acg.storage.empty"),
                    contentX + contentWidth / 2, (contentTop + contentBottom) / 2,
                    ACGTheme.TEXT_MUTED);
            return;
        }

        int centerX = contentX + showcaseWidth / 2;
        int ringY = contentTop + 100;
        context.drawShowcaseBackdrop(graphics, centerX, ringY);
        ACGStorageRowWidget.drawRowIcon(graphics, row,
                centerX - 32, ringY - 32, 64);
        drawCenteredString(graphics, context.font(), ACGTheme.asHeader(row.displayComponent()),
                centerX, ringY + 56, ACGInventoryStyle.TEXT_CREAM);
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.acg.storage.stored", row.count()),
                centerX, ringY + 70, ACGInventoryStyle.ACCENT_ORANGE);

        if (row.isVirtual()) {
            VirtualItems.Definition definition = VirtualItems.byId(row.virtualId());
            if (definition != null) {
                int lineY = ringY + 84;
                int lineBottom = contentBottom - 34;
                for (var line : context.font().split(definition.descriptionComponent(),
                        Math.max(80, showcaseWidth - 16))) {
                    if (lineY > lineBottom) {
                        break;
                    }
                    drawCenteredString(graphics, context.font(), line, centerX, lineY,
                            ACGInventoryStyle.TEAL_BORDER);
                    lineY += 10;
                }
            }
        } else {
            int unit = ClientStorageState.getSellUnitValue(selection);
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(
                            ClientPerkState.usesGoldCurrency()
                                    ? "screen.aegis_ascension.acg.storage.sell_value_gold"
                                    : "screen.aegis_ascension.acg.storage.sell_value", unit),
                    centerX, ringY + 84,
                    unit > 0 ? ACGInventoryStyle.ACCENT_ORANGE
                            : ACGInventoryStyle.TEXT_DIM);
        }

        if (draggingRow && dragSourceIndex >= 0) {
            StoredItem dragged = ClientStorageState.getRow(dragSourceIndex);
            if (dragged != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, MODAL_Z);
                ACGStorageRowWidget.drawRowIcon(graphics, dragged,
                        dragCursorX - 16, dragCursorY - 16, 32);
                graphics.pose().popPose();
            }
        }

        if (confirmUsePrompt || quantityPrompt != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, MODAL_Z);
            if (confirmUsePrompt) {
                renderConfirmUsePrompt(context, graphics, row);
            } else {
                renderQuantityPrompt(context, graphics, row);
            }
            graphics.pose().popPose();
        }
    }

    private void initConfirmUsePrompt(ACGScreenContext context) {
        StoredItem row = ClientStorageState.getRow(selection);
        if (row == null || !row.isVirtual() || !requiresUseConfirmation(row.virtualId())) {
            confirmUsePrompt = false;
            return;
        }
        int panelWidth = 240;
        int panelHeight = 104;
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;

        context.add(ACGButton.builder(getTranslatableString("gui.cancel"), button -> {
                    confirmUsePrompt = false;
                    context.rebuild();
                })
                .bounds(panelX + 12, panelY + panelHeight - 28,
                        (panelWidth - 32) / 2, 20)
                .build().style(ACGButton.Style.PLAIN).zOffset(MODAL_WIDGET_Z));

        StoredItem confirmRow = row;
        context.add(ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.storage.confirm_use"),
                        button -> {
                            confirmUsePrompt = false;
                            ModNetworking.sendToServer(new StorageActionPacket(
                                    StorageActionPacket.Action.USE, confirmRow, 1L));
                            context.rebuild();
                        })
                .bounds(panelX + panelWidth / 2 + 4, panelY + panelHeight - 28,
                        (panelWidth - 32) / 2, 20)
                .build().style(ACGButton.Style.TEAL).zOffset(MODAL_WIDGET_Z));
    }

    private void renderConfirmUsePrompt(ACGScreenContext context, GuiGraphics graphics,
                                        StoredItem row) {
        int panelWidth = 240;
        int panelHeight = 104;
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;
        graphics.fill(context.contentX() - 6, context.contentTop() - 12,
                context.contentX() + context.contentWidth(), context.contentBottom() + 6,
                0xC0000000);
        ACGTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.acg.storage.confirm_title"),
                panelX + panelWidth / 2, panelY + 10, ACGTheme.RARITY_SSR);
        drawCenteredString(graphics, context.font(), row.displayComponent(),
                panelX + panelWidth / 2, panelY + 24, ACGTheme.TEXT_PRIMARY);

        int lineY = panelY + 40;
        for (var line : context.font().split(
                getTranslatableString("screen.aegis_ascension.acg.storage.confirm_warning"),
                panelWidth - 24)) {
            if (lineY > panelY + panelHeight - 34) {
                break;
            }
            drawCenteredString(graphics, context.font(), line,
                    panelX + panelWidth / 2, lineY, ACGTheme.TEXT_MUTED);
            lineY += 10;
        }
    }

    private void renderQuantityPrompt(ACGScreenContext context, GuiGraphics graphics,
                                      StoredItem row) {
        int panelWidth = 200;
        int panelHeight = 96;
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;
        graphics.fill(context.contentX() - 6, context.contentTop() - 12,
                context.contentX() + context.contentWidth(), context.contentBottom() + 6,
                0xC0000000);
        ACGTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        Component title = switch (quantityPrompt) {
            case EXTRACT -> getTranslatableString(
                    "screen.aegis_ascension.acg.storage.extract_amount");
            case DISCARD -> getTranslatableString(
                    "screen.aegis_ascension.acg.storage.discard_amount");
            case SELL -> getTranslatableString(
                    "screen.aegis_ascension.acg.storage.sell_amount");
            case USE -> getTranslatableString(
                    "screen.aegis_ascension.acg.storage.use_amount");
        };
        drawCenteredString(graphics, context.font(), title,
                panelX + panelWidth / 2, panelY + 10, ACGTheme.TEXT_PRIMARY);
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.acg.storage.available",
                        row.displayComponent(), row.count()),
                panelX + panelWidth / 2, panelY + 22, ACGTheme.TEXT_MUTED);
    }

    @Override
    public void tick(ACGScreenContext context) {
        if (!draggingRow || context.gridMaxScroll() <= 0 || !context.gridScrollMode()) {
            return;
        }
        int distanceFromTop = dragCursorY - context.gridViewportTop();
        int distanceFromBottom = context.gridViewportBottom() - dragCursorY;
        int delta = 0;
        if (distanceFromTop < DRAG_SCROLL_EDGE) {
            delta = -speedFor(distanceFromTop);
        } else if (distanceFromBottom < DRAG_SCROLL_EDGE) {
            delta = speedFor(distanceFromBottom);
        }
        if (delta == 0) {
            return;
        }
        int updated = Math.max(0,
                Math.min(context.gridMaxScroll(), context.gridScroll() + delta));
        if (updated != context.gridScroll()) {
            context.gridScroll(updated);
            context.rebuild();
        }
    }

    private static int speedFor(int distanceFromEdge) {
        return distanceFromEdge < DRAG_SCROLL_EDGE / 2
                ? DRAG_SCROLL_STEP * 2 : DRAG_SCROLL_STEP;
    }

    private int storageRowAt(ACGScreenContext context, double mouseX, double mouseY) {
        for (var child : context.children()) {
            if (child instanceof ACGStorageRowWidget card && card.visible
                    && card.isMouseOver(mouseX, mouseY)) {
                return card.storageIndex();
            }
        }
        return -1;
    }

    private int storageDropTarget(ACGScreenContext context, double mouseX, double mouseY) {
        int exact = storageRowAt(context, mouseX, mouseY);
        if (exact >= 0) {
            return exact;
        }
        if (mouseX < context.contentX() || mouseY > context.gridViewportBottom()) {
            return -1;
        }

        int firstIndex = -1;
        int lastIndex = -1;
        int topEdge = Integer.MAX_VALUE;
        int bottomEdge = Integer.MIN_VALUE;
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (var child : context.children()) {
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

    @Override
    public boolean keyPressed(ACGScreenContext context, int keyCode,
                              int scanCode, int modifiers) {
        if (confirmUsePrompt) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                confirmUsePrompt = false;
                context.rebuild();
            }
            return true;
        }
        if (quantityPrompt != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                quantityPrompt = null;
                context.rebuild();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                confirmQuantityPrompt(context);
                return true;
            }
        }
        // The original Storage page is not an AbstractContainerScreen, so its storage
        // cards never pass through ACGInventoryScreen's key handler. Handle the same
        // configurable shortcut here and deliberately reuse the Extract button path:
        // small stacks extract immediately, while larger rows open the amount prompt.
        // Do not intercept letters while the search field has keyboard focus.
        if (context.focused() != searchBox
                && ClientLifecycle.PUT_INTO_STORAGE_UI.matches(keyCode, scanCode)) {
            for (var child : context.children()) {
                if (child instanceof ACGStorageRowWidget card
                        && card.visible && card.isHoveredNow()) {
                    if (!card.row().isVirtual()) {
                        selection = card.storageIndex();
                        beginStorageAction(context, StorageActionPacket.Action.EXTRACT);
                    }
                    // Virtual entries cannot be extracted, but the shortcut is still
                    // consumed so it cannot activate an unrelated control underneath.
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(ACGScreenContext context, double mouseX,
                                double mouseY, int button) {
        if (button == 0 && quantityPrompt == null && !confirmUsePrompt) {
            dragSourceIndex = storageRowAt(context, mouseX, mouseY);
            dragOriginX = mouseX;
            dragOriginY = mouseY;
            draggingRow = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(ACGScreenContext context, double mouseX,
                                double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragSourceIndex >= 0 && !draggingRow
                && (Math.abs(mouseX - dragOriginX) > DRAG_THRESHOLD
                || Math.abs(mouseY - dragOriginY) > DRAG_THRESHOLD)) {
            draggingRow = true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(ACGScreenContext context, double mouseX,
                                 double mouseY, int button) {
        if (button == 0 && draggingRow && dragSourceIndex >= 0) {
            int target = storageDropTarget(context, mouseX, mouseY);
            draggingRow = false;
            int source = dragSourceIndex;
            dragSourceIndex = -1;
            if (target >= 0 && target != source) {
                ClientStorageState.moveInManualOrder(source, target);
                context.rebuild();
            }
            return true;
        }
        dragSourceIndex = -1;
        draggingRow = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(ACGScreenContext context, double mouseX,
                                 double mouseY, double delta) {
        if (context.gridMaxScroll() <= 0 || quantityPrompt != null || confirmUsePrompt
                || mouseX < context.contentX()
                || mouseY <= ACGPerkSelectionScreen.TOP_BAR_HEIGHT
                || Math.abs(delta) <= 1.0E-9D) {
            return false;
        }
        context.gridScroll(Math.max(0, Math.min(context.gridMaxScroll(),
                context.gridScroll()
                        + (int) Math.round((delta < 0.0D ? 1 : -1)
                        * GRID_SCROLL_STEP))));
        context.rebuild();
        return true;
    }

    @Override
    public void onDeactivated(ACGScreenContext context) {
        quantityPrompt = null;
        confirmUsePrompt = false;
        dragSourceIndex = -1;
        draggingRow = false;
    }
}
