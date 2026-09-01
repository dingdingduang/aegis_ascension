package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Breakdown;
import com.whatever.aegis_ascension.client.screen.collectiontabs.CustomStats.Definition;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Player custom-stat grid; source-breakdown tooltip remains a shared screen overlay. */
final class ACGCustomStatsPage implements ACGPage {
    private static final int CARD_GAP = 8;

    @Override
    public void init(ACGScreenContext context) {
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
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
    }
}
