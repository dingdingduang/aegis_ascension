package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientShopState;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGShopSlotWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.BuyShopItemPacket;
import com.whatever.aegis_ascension.network.ManualRefreshShopPacket;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RequestShopDataPacket;
import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.shop.ShopType;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative Common and registry-backed Discovery shop page. */
final class ACGShopPage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int SLOT_SIZE = 76;
    private ShopType selectedShop = ShopType.COMMON;
    /**
     * The slots built this pass, so the hovered one can be found when the tooltip is
     * drawn. Tooltips cannot be drawn from inside a slot: they would be painted over by
     * whichever slots render after it, so they are left until the page has finished.
     */
    private final List<ACGShopSlotWidget> slots = new ArrayList<>();

    void requestSelectedShop() {
        ModNetworking.sendToServer(new RequestShopDataPacket(selectedShop));
    }

    @Override
    public void init(ACGScreenContext context) {
        if (selectedShop == ShopType.DISCOVERY
                && !ClientShopState.isEnabled(ShopType.DISCOVERY)) {
            selectedShop = ShopType.COMMON;
        }
        slots.clear();
        List<ShopOffer> offers = ClientShopState.getOffers(selectedShop);
        int refreshCost = ClientShopState.getRefreshExperienceCost(selectedShop);
        int centerX = context.contentX() + context.contentWidth() / 2;

        ACGButton commonTab = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.shop.tab.common"),
                        button -> selectShop(context, ShopType.COMMON))
                .bounds(centerX - 108, context.contentTop() + 26, 104, 18)
                .build()
                .style(selectedShop == ShopType.COMMON
                        ? ACGButton.Style.CTA : ACGButton.Style.PLAIN);
        context.add(commonTab);

        ACGButton discoveryTab = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.acg.shop.tab.discovery"),
                        button -> selectShop(context, ShopType.DISCOVERY))
                .bounds(centerX + 4, context.contentTop() + 26, 104, 18)
                .build()
                .style(selectedShop == ShopType.DISCOVERY
                        ? ACGButton.Style.CTA : ACGButton.Style.PLAIN);
        discoveryTab.active = ClientShopState.isEnabled(ShopType.DISCOVERY);
        context.add(discoveryTab);

        ShopType visibleShop = selectedShop;
        ACGButton refresh = ACGButton.builder(
                        getTranslatableString(
                                ClientPerkState.usesGoldCurrency()
                                        ? "screen.aegis_ascension.acg.shop.refresh.gold"
                                        : "screen.aegis_ascension.acg.shop.refresh",
                                refreshCost),
                        button -> ModNetworking.sendToServer(
                                new ManualRefreshShopPacket(visibleShop)))
                .bounds(centerX - 80, context.contentTop() + 49, 160, 20)
                .build();
        refresh.active = ClientShopState.canRefresh(selectedShop)
                && ClientShopState.canAffordRefresh(selectedShop);
        context.add(refresh);

        if (offers.isEmpty()) {
            return;
        }
        int gridTop = context.contentTop() + 77;
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
            ACGShopSlotWidget slot = new ACGShopSlotWidget(
                    x, y, layout.cardWidth(), layout.cardHeight(), index, offer,
                    ClientShopState.canPurchase(selectedShop, index),
                    slotIndex -> ModNetworking.sendToServer(
                            new BuyShopItemPacket(visibleShop, slotIndex)));
            slots.add(slot);
            context.add(slot);
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
    }

    private void selectShop(ACGScreenContext context, ShopType shopType) {
        if (shopType == selectedShop || !ClientShopState.isEnabled(shopType)) {
            return;
        }
        selectedShop = shopType;
        context.page(0);
        context.gridScroll(0);
        ModNetworking.sendToServer(new RequestShopDataPacket(selectedShop));
        context.rebuild();
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
                selectedShop == ShopType.DISCOVERY
                        ? (ClientPerkState.usesGoldCurrency()
                        ? "screen.aegis_ascension.acg.shop.header.discovery.gold"
                        : "screen.aegis_ascension.acg.shop.header.discovery")
                        : (ClientPerkState.usesGoldCurrency()
                        ? "screen.aegis_ascension.acg.shop.header.common.gold"
                        : "screen.aegis_ascension.acg.shop.header.common"),
                ClientShopState.getSlotCount(selectedShop),
                ClientPerkState.usesGoldCurrency()
                        ? ClientShopState.getPlayerGold()
                        : ClientShopState.getPlayerExperience());
        drawCenteredString(graphics, context.font(), header,
                centerX, context.contentTop() + 6, ACGTheme.TEXT_PRIMARY);

        Component reset = getTranslatableString(
                "screen.aegis_ascension.acg.shop.resets_in",
                formatRealTime(ClientShopState.getTicksUntilReset(selectedShop)),
                ClientShopState.getRemainingRefreshes(selectedShop));
        drawCenteredString(graphics, context.font(), reset,
                centerX, context.contentTop() + 16, ACGTheme.TEXT_MUTED);

        if (ClientShopState.getSlotCount(selectedShop) == 0) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(selectedShop == ShopType.DISCOVERY
                            ? "screen.aegis_ascension.acg.shop.empty.discovery"
                            : "screen.aegis_ascension.acg.shop.empty.common"),
                    centerX,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
        }
    }

    /**
     * Draws the full item tooltip for whichever slot the cursor is over, the way the
     * inventory does, so an item's stats can be read before it is bought rather than
     * after.
     *
     * <p>Called by the screen after every widget has drawn, not from this page's own
     * render: that runs before the widgets and the tooltip would be painted over.</p>
     *
     * @return true when a tooltip was drawn, so the screen can stop looking
     */
    boolean renderHoveredTooltip(net.minecraft.client.gui.Font font, GuiGraphics graphics,
                                 int mouseX, int mouseY) {
        for (ACGShopSlotWidget slot : slots) {
            if (!slot.isHoveredNow()) continue;
            ShopOffer offer = slot.offer();
            if (offer.isVirtual()) {
                // A virtual book's stack is only its icon, so its own text is shown
                // instead of the icon item's.
                graphics.renderComponentTooltip(font,
                        virtualTooltip(offer.virtualId()), mouseX, mouseY);
            } else if (!offer.stack().isEmpty()) {
                graphics.renderTooltip(font, offer.stack(), mouseX, mouseY);
            } else {
                continue;
            }
            return true;
        }
        return false;
    }

    private static List<Component> virtualTooltip(String virtualId) {
        List<Component> lines = new ArrayList<>();
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        if (definition == null) {
            lines.add(getTranslatableString(virtualId));
            return lines;
        }
        lines.add(getTranslatableString(definition.nameKey()));
        String description = definition.descriptionKey();
        if (description != null && !description.isBlank()) {
            lines.add(getTranslatableString(description));
        }
        return lines;
    }
}
