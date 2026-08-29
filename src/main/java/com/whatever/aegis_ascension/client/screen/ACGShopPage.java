package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientShopState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGShopSlotWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.BuyShopItemPacket;
import com.whatever.aegis_ascension.network.ManualRefreshShopPacket;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.shop.ShopOffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Server-authoritative daily virtual-item shop page. */
final class ACGShopPage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int SLOT_SIZE = 76;

    @Override
    public void init(ACGScreenContext context) {
        List<ShopOffer> offers = ClientShopState.getOffers();
        int refreshCost = ClientShopState.getRefreshExperienceCost();
        ACGButton refresh = ACGButton.builder(
                        getTranslatableString(
                                "screen.aegis_ascension.acg.shop.refresh", refreshCost),
                        button -> ModNetworking.sendToServer(new ManualRefreshShopPacket()))
                .bounds(context.contentX() + context.contentWidth() / 2 - 80,
                        context.contentTop() + 26, 160, 20)
                .build();
        refresh.active = ClientShopState.canRefresh()
                && ClientShopState.canAffordRefresh();
        context.add(refresh);

        if (offers.isEmpty()) {
            return;
        }
        int gridTop = context.contentTop() + 56;
        int gridBottom = context.contentBottom() - 30;
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                offers.size(), context.contentX(), context.contentWidth(),
                gridTop, gridBottom, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, 8, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            ShopOffer offer = offers.get(index);
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + CARD_GAP);
            context.add(new ACGShopSlotWidget(
                    x, y, layout.cardWidth(), layout.cardHeight(), index, offer,
                    ClientShopState.canPurchase(index),
                    slotIndex -> ModNetworking.sendToServer(
                            new BuyShopItemPacket(slotIndex))));
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
    }

    private static String formatRealTime(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int centerX = context.contentX() + context.contentWidth() / 2;
        Component header = getTranslatableString(
                "screen.aegis_ascension.acg.shop.header",
                ClientShopState.getSlotCount(), ClientShopState.getPlayerExperience());
        drawCenteredString(graphics, context.font(), header,
                centerX, context.contentTop() + 6, ACGTheme.TEXT_PRIMARY);

        Component reset = getTranslatableString(
                "screen.aegis_ascension.acg.shop.resets_in",
                formatRealTime(ClientShopState.getTicksUntilReset()),
                ClientShopState.getRemainingRefreshes());
        drawCenteredString(graphics, context.font(), reset,
                centerX, context.contentTop() + 16, ACGTheme.TEXT_MUTED);

        if (ClientShopState.getSlotCount() == 0) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString("screen.aegis_ascension.acg.shop.empty"),
                    centerX,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
        }
    }
}
