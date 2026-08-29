package com.whatever.aegis_ascension.client.screen;

import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.collectiontabs.SoulLinks;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Active, disabled, and locked Soul Link grid. */
final class ACGSoulLinksPage implements ACGPage {
    private static final int CARD_GAP = 8;

    @Override
    public void init(ACGScreenContext context) {
        List<TalentCollectionCard> cards = SoulLinks.cards();
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cards.size(), context.contentX(), context.contentWidth(),
                context.contentTop() + 20, context.contentBottom() - 30,
                150, 230, 82, 3, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            TalentCollectionCard card = cards.get(index);
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
                    .status(card.status(), card.statusColor())
                    .accentColor(card.color())
                    .enabled(card.active())
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
