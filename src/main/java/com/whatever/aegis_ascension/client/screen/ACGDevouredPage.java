package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGDevourCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGInventoryStyle;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.DevouredItems;
import com.whatever.aegis_ascension.network.DiscardDevouredItemPacket;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RequestDevourDataPacket;
import com.whatever.aegis_ascension.network.SyncDevourDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Devour Aegis collection page and all of its page-local interaction state. */
final class ACGDevouredPage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int CARD_SIZE = 72;

    private String selection;
    private int attributeScroll;
    private int attributeMaxScroll;
    private String search = "";
    private EditBox searchBox;
    private String pendingDiscardItemId;
    private boolean awaitingDiscard;
    private boolean requestedData;

    static boolean isAvailable() {
        return Aegis.byId(AegisConstants.DEVOUR)
                .map(ClientPerkState::ownsAegis)
                .orElse(false);
    }

    @Override
    public void init(ACGScreenContext context) {
        if (!requestedData) {
            requestedData = true;
            ModNetworking.sendToServer(new RequestDevourDataPacket());
        }

        List<DevouredItems.DevouredItem> rows = DevouredItems.matching(search);
        if (selection == null
                || rows.stream().noneMatch(item -> item.itemId().equals(selection))) {
            selection = rows.isEmpty() ? null : rows.get(0).itemId();
        }

        int showcaseWidth = context.showcaseWidth();
        int rightX = context.contentX() + showcaseWidth + CARD_GAP;
        int rightWidth = context.contentWidth() - showcaseWidth - CARD_GAP;

        if (searchBox == null) {
            searchBox = new EditBox(context.font(), rightX, context.contentTop() + 2,
                    rightWidth, 16,
                    getTranslatableString("screen.aegis_ascension.devour.search"));
            searchBox.setMaxLength(256);
            searchBox.setHint(
                    getTranslatableString("screen.aegis_ascension.devour.search_hint"));
            searchBox.setValue(search);
            searchBox.setResponder(value -> {
                if (value.equals(search)) {
                    return;
                }
                search = value;
                context.page(0);
                context.gridScroll(0);
                context.rebuild();
                context.focus(searchBox);
            });
        } else {
            searchBox.setX(rightX);
            searchBox.setY(context.contentTop() + 2);
            searchBox.setWidth(rightWidth);
        }
        context.add(searchBox);

        if (!rows.isEmpty()) {
            ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                    rows.size(), rightX, rightWidth,
                    context.contentTop() + 22, context.contentBottom() - 60,
                    CARD_SIZE, CARD_SIZE, CARD_SIZE, 10, true);
            int step = layout.cardHeight() + CARD_GAP;
            for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
                DevouredItems.DevouredItem item = rows.get(index);
                int local = index - layout.firstIndex();
                int x = layout.startX()
                        + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
                int y = layout.startY() + (local / layout.columns()) * step;
                context.add(new ACGDevourCardWidget(
                        x, y, layout.cardWidth(), layout.cardHeight(), item,
                        DevouredItems.itemStack(item.itemId()),
                        item.itemId().equals(selection),
                        itemId -> selectItem(context, itemId)));
            }
        } else {
            context.gridMaxScroll(0);
        }

        int actionWidth = Math.max(40, (rightWidth - CARD_GAP) / 2);
        int actionY = context.contentBottom() - 26;
        ACGButton sort = ACGButton.builder(
                        getTranslatableString(switch (DevouredItems.getSortMode()) {
                            case NAME_DESC -> "screen.aegis_ascension.acg.storage.sort_descending";
                            case RARITY -> "screen.aegis_ascension.acg.storage.sort_rarity";
                            default -> "screen.aegis_ascension.acg.storage.sort_ascending";
                        }), button -> {
                            DevouredItems.cycleSortMode();
                            context.gridScroll(0);
                            context.page(0);
                            context.rebuild();
                        })
                .bounds(rightX, actionY, actionWidth, 20)
                .build().style(ACGButton.Style.PLAIN);
        sort.active = !rows.isEmpty();
        context.add(sort);

        context.add(ACGButton.builder(
                        getTranslatableString(context.gridScrollMode()
                                ? "screen.aegis_ascension.acg.storage.view_scroll"
                                : "screen.aegis_ascension.acg.storage.view_paged"),
                        button -> context.toggleGridScrollMode())
                .bounds(rightX + actionWidth + CARD_GAP, actionY, actionWidth, 20)
                .build().style(ACGButton.Style.PLAIN));
        context.addPaginationButtons(
                rightX + rightWidth / 2, context.contentBottom() - 52, false);

        boolean confirming = selection != null && selection.equals(pendingDiscardItemId);
        ACGButton discard = ACGButton.builder(
                        getTranslatableString(confirming
                                ? "screen.aegis_ascension.acg.devour.discard_confirm"
                                : "screen.aegis_ascension.acg.devour.discard"),
                        button -> discardSelected(context))
                .bounds(context.contentX() + (showcaseWidth - 160) / 2,
                        context.contentBottom() - 26, 160, 20)
                .build().style(confirming ? ACGButton.Style.CTA : ACGButton.Style.TEAL);
        discard.active = selection != null && !awaitingDiscard;
        context.add(discard);
    }

    private void selectItem(ACGScreenContext context, String itemId) {
        selection = itemId;
        attributeScroll = 0;
        pendingDiscardItemId = null;
        context.rebuild();
    }

    private void discardSelected(ACGScreenContext context) {
        if (selection == null || awaitingDiscard) {
            return;
        }
        if (!selection.equals(pendingDiscardItemId)) {
            pendingDiscardItemId = selection;
            context.rebuild();
            return;
        }
        awaitingDiscard = true;
        ModNetworking.sendToServer(new DiscardDevouredItemPacket(selection));
        context.rebuild();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int showcaseWidth = context.showcaseWidth();
        List<DevouredItems.DevouredItem> rows = DevouredItems.matching(search);
        graphics.drawString(context.font(), DevouredItems.all().isEmpty()
                        ? getTranslatableString(
                        "screen.aegis_ascension.devour.item_count", 0)
                        : getTranslatableString(
                        "screen.aegis_ascension.devour.filtered_item_count",
                        rows.size(), DevouredItems.all().size()),
                context.contentX(), context.contentTop() - 10,
                ACGTheme.GOLD_BRIGHT, false);

        DevouredItems.DevouredItem selected = rows.stream()
                .filter(item -> item.itemId().equals(selection))
                .findFirst().orElse(null);
        if (selected == null) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(DevouredItems.all().isEmpty()
                            ? "screen.aegis_ascension.devour.empty"
                            : "screen.aegis_ascension.devour.no_search_results"),
                    context.contentX() + context.contentWidth() / 2,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
            return;
        }

        int centerX = context.contentX() + showcaseWidth / 2;
        int ringY = context.contentTop() + 100;
        context.drawShowcaseBackdrop(graphics, centerX, ringY);
        int iconSize = 48;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - iconSize / 2.0F,
                ringY - iconSize / 2.0F, 0.0F);
        graphics.pose().scale(iconSize / 16.0F, iconSize / 16.0F, 1.0F);
        graphics.renderItem(DevouredItems.itemStack(selected.itemId()), 0, 0);
        graphics.pose().popPose();

        drawCenteredString(graphics, context.font(),
                ACGTheme.asHeader(DevouredItems.itemName(selected.itemId())),
                centerX, ringY + 56, ACGInventoryStyle.TEXT_CREAM);
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.acg.devour.attributes",
                        selected.attributes().size()),
                centerX, ringY + 70, ACGInventoryStyle.ACCENT_ORANGE);

        int lineTop = ringY + 88;
        int lineBottom = context.contentBottom() - 34;
        int visibleLines = Math.max(1, (lineBottom - lineTop) / 10);
        List<SyncDevourDataPacket.Entry> attributes = selected.attributes();
        attributeMaxScroll = Math.max(0, attributes.size() - visibleLines);
        attributeScroll = Math.max(0, Math.min(attributeMaxScroll, attributeScroll));

        int lineY = lineTop;
        for (int i = attributeScroll;
             i < attributes.size() && lineY <= lineBottom; i++, lineY += 10) {
            SyncDevourDataPacket.Entry entry = attributes.get(i);
            Component line = getLiteralString("")
                    .append(DevouredItems.attributeName(entry.attributeId()))
                    .append(getLiteralString("  "))
                    .append(DevouredItems.formattedAmount(entry));
            if (entry.blacklisted()) {
                line = line.copy().append(getTranslatableString(
                        "screen.aegis_ascension.acg.devour.banned_suffix"));
            }
            drawCenteredString(graphics, context.font(), line, centerX, lineY,
                    entry.blacklisted()
                            ? ACGTheme.STATUS_LOCKED : ACGTheme.TEXT_SECONDARY);
        }

        if (attributeMaxScroll > 0) {
            drawCenteredString(graphics, context.font(), getTranslatableString(
                            "screen.aegis_ascension.acg.devour.more_attributes",
                            attributeScroll + visibleLines, attributes.size()),
                    centerX, lineBottom + 2, ACGTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseScrolled(ACGScreenContext context, double mouseX,
                                 double mouseY, double delta) {
        if (attributeMaxScroll <= 0
                || mouseX < context.contentX()
                || mouseX >= context.contentX() + context.showcaseWidth()
                || mouseY <= ACGPerkSelectionScreen.TOP_BAR_HEIGHT
                || Math.abs(delta) <= 1.0E-9D) {
            return false;
        }
        attributeScroll = Math.max(0, Math.min(attributeMaxScroll,
                attributeScroll + (delta < 0.0D ? 1 : -1)));
        return true;
    }

    @Override
    public void onServerSync(ACGScreenContext context) {
        awaitingDiscard = false;
        if (selection != null && DevouredItems.all().stream()
                .noneMatch(item -> item.itemId().equals(selection))) {
            selection = null;
            pendingDiscardItemId = null;
            attributeScroll = 0;
        }
    }
}
