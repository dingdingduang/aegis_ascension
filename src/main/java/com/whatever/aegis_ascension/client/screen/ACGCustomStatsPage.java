package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.CustomStatSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Breakdown;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Definition;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.ListGroup;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.ListRow;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player custom-stat tab, in either of two presentations.
 *
 * <p>{@link ClientSettings.CustomStatView#CARDS} is the icon-and-breakdown grid: three
 * lines per stat, sized to be read one stat at a time.
 * {@link ClientSettings.CustomStatView#LIST} is the classic RPG status panel — one dense
 * row per stat under a group heading, sized to take the whole build in at a glance. Both
 * build {@link ACGCardWidget}s carrying a {@code statKey}, so the source-breakdown
 * tooltip stays a shared screen overlay in either view.</p>
 */
final class ACGCustomStatsPage implements ACGPage {
    private static final int CARD_GAP = 8;
    /** Rows sit far closer together than cards; see the gap argument to computeGrid. */
    private static final int ROW_GAP = 2;
    /** Narrowest a row may be before the layout drops to fewer columns. */
    private static final int MIN_ROW_WIDTH = 150;
    /** Drawn before a value this mod contributes to, as the reference status panel does. */
    private static final String BOOSTED_MARKER = "▲ ";

    /** Group headings placed by the last {@link #init}, drawn by {@link #render}. */
    private final List<PlacedHeader> headers = new ArrayList<>();

    /** A heading and the row it was laid out on. */
    private record PlacedHeader(String titleKey, int x, int y, int width, int height) {
    }

    /**
     * One cell of the list grid: a heading, a stat row, or the padding that keeps a
     * heading alone on its own line. Exactly one field is set, or neither for padding.
     */
    private record Cell(String headerKey, ListRow row) {
    }

    @Override
    public void init(ACGScreenContext context) {
        headers.clear();
        addViewToggle(context);
        if (ClientSettings.get().customStatView == ClientSettings.CustomStatView.LIST) {
            initList(context);
        } else {
            initCards(context);
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
    }

    /** Sits immediately left of the Pages/Scroll toggle that addPaginationButtons adds. */
    private void addViewToggle(ACGScreenContext context) {
        boolean list = ClientSettings.get().customStatView == ClientSettings.CustomStatView.LIST;
        context.add(ACGButton.builder(
                        getTranslatableString(list
                                ? "screen.aegis_ascension.acg.custom_stat.view_list"
                                : "screen.aegis_ascension.acg.custom_stat.view_cards"),
                        button -> toggleView(context))
                .bounds(context.contentX() + context.contentWidth() - 128,
                        context.contentBottom() - 24, 62, 20)
                .build()
                .style(ACGButton.Style.PLAIN));
    }

    private void toggleView(ACGScreenContext context) {
        ClientSettings settings = ClientSettings.get();
        settings.customStatView =
                settings.customStatView == ClientSettings.CustomStatView.LIST
                        ? ClientSettings.CustomStatView.CARDS
                        : ClientSettings.CustomStatView.LIST;
        settings.save();
        // A row offset and a card offset measure different things, so carrying either
        // across the switch would land the player at an arbitrary point in the other view.
        context.page(0);
        context.gridScroll(0);
        context.rebuild();
    }

    private void initCards(ACGScreenContext context) {
        List<TalentCollectionCard> cards = CustomStats.cards();
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cards.size(), context.contentX(), context.contentWidth(),
                context.contentTop() + 20, context.contentBottom() - 30,
                128, 190, 82, 3, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            TalentCollectionCard card = cards.get(index);
            Definition definition = card.statKey() == null
                    ? null : CustomStats.definition(card.statKey());
            List<ACGCardWidget.DetailLine> details = List.of();
            if (definition != null) {
                Breakdown breakdown = CustomStats.breakdown(definition);
                // A card of this height fits three lines, so each side's flat and
                // percentage share one. The pair reads "additive / multiplicative".
                details = List.of(
                        new ACGCardWidget.DetailLine(getTranslatableString(
                                "screen.aegis_ascension.collection.stat.mod_values",
                                breakdown.flatText(),
                                breakdown.percentageText()), 0xFF72E39A),
                        new ACGCardWidget.DetailLine(getTranslatableString(
                                "screen.aegis_ascension.collection.stat.other_values",
                                breakdown.otherFlatText(),
                                breakdown.otherPercentageText()),
                                breakdown.hasOtherSources()
                                        ? ACGTheme.GOLD : ACGTheme.TEXT_MUTED),
                        new ACGCardWidget.DetailLine(getTranslatableString(
                                "screen.aegis_ascension.collection.stat.final_value",
                                breakdown.finalText(definition)), card.statusColor())
                );
            }
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + CARD_GAP);
            context.add(ACGCardWidget.builder(
                            x, y, layout.cardWidth(), layout.cardHeight(), card.title())
                    .presentation(Presentation.COMPACT)
                    .icon(card.icon(), card.iconTextureSize())
                    .tooltip(card.tooltip())
                    .detailLines(details)
                    .accentColor(card.color())
                    .statKey(card.statKey())
                    .build());
        }
    }

    private void initList(ACGScreenContext context) {
        CustomStatSettings.ListView view = CustomStatSettings.get().listView();
        // Same rule computeGrid applies, needed one step earlier here: a heading is padded
        // out to a whole line, so the cell count can't be known until the width is.
        int columns = Math.max(1, Math.min(view.columns,
                Math.max(1, context.contentWidth() / (MIN_ROW_WIDTH + ROW_GAP))));
        List<Cell> cells = cells(columns, view.showGroupHeaders);
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cells.size(), context.contentX(), context.contentWidth(),
                context.contentTop() + 20, context.contentBottom() - 30,
                MIN_ROW_WIDTH, 600, view.rowHeight, columns, false, ROW_GAP);

        int gridWidth = layout.cardWidth() * layout.columns()
                + ROW_GAP * (layout.columns() - 1);
        Map<String, TalentCollectionCard> byStat = cardsByStat();
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            Cell cell = cells.get(index);
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + ROW_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + ROW_GAP);
            if (cell.headerKey() != null) {
                headers.add(new PlacedHeader(cell.headerKey(), layout.startX(), y,
                        gridWidth, layout.cardHeight()));
                continue;
            }
            if (cell.row() == null) {
                continue;
            }
            addRow(context, cell.row(), byStat, x, y, layout.cardWidth(), layout.cardHeight());
        }
    }

    private void addRow(ACGScreenContext context, ListRow row,
                        Map<String, TalentCollectionCard> byStat,
                        int x, int y, int width, int height) {
        Definition definition = row.definition();
        TalentCollectionCard card = byStat.get(definition.key());
        if (card == null) {
            return;
        }
        Breakdown breakdown = CustomStats.breakdown(definition);
        Component value = breakdown.hasModSources()
                ? getLiteralString(BOOSTED_MARKER).append(card.status())
                : card.status();
        context.add(ACGCardWidget.builder(x, y, width, height, card.title())
                .presentation(Presentation.ROW)
                .icon(row.icon(), row.iconTextureSize())
                .status(value, card.statusColor())
                .tooltip(card.tooltip())
                .accentColor(card.color())
                .statKey(definition.key())
                .build());
    }

    /** The card model keyed by stat, so a row reuses its label, value, and colours. */
    private static Map<String, TalentCollectionCard> cardsByStat() {
        Map<String, TalentCollectionCard> byStat = new HashMap<>();
        for (TalentCollectionCard card : CustomStats.cards()) {
            if (card.statKey() != null) {
                byStat.put(card.statKey(), card);
            }
        }
        return byStat;
    }

    /**
     * Flattens the configured groups into one uniform grid. A heading takes a whole line —
     * padding first to the end of the current one, then filling the rest of its own — which
     * is what lets a grid that only knows about equal cells lay out a grouped list.
     */
    private static List<Cell> cells(int columns, boolean showGroupHeaders) {
        List<Cell> cells = new ArrayList<>();
        for (ListGroup group : CustomStats.listGroups()) {
            if (showGroupHeaders && group.titleKey() != null) {
                while (cells.size() % columns != 0) {
                    cells.add(new Cell(null, null));
                }
                cells.add(new Cell(group.titleKey(), null));
                for (int filler = 1; filler < columns; filler++) {
                    cells.add(new Cell(null, null));
                }
            }
            for (ListRow row : group.rows()) {
                cells.add(new Cell(null, row));
            }
        }
        return cells;
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        for (PlacedHeader header : headers) {
            // Scroll mode lays every row out at once and hides what overflows; a heading is
            // drawn here rather than as a widget, so it has to make that check itself.
            if (header.y() + header.height() <= context.gridViewportTop()
                    || header.y() >= context.gridViewportBottom()) {
                continue;
            }
            graphics.drawString(context.font(), getTranslatableString(header.titleKey()),
                    header.x() + 2, header.y() + (header.height() - 8) / 2,
                    ACGTheme.GOLD, false);
            graphics.fill(header.x(), header.y() + header.height() - 1,
                    header.x() + header.width(), header.y() + header.height(),
                    ACGTheme.GOLD_DIM);
        }
    }
}
