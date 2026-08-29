package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectFrameBounds;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectOpaqueBounds;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.ClientRefreshRequestLimiter;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RefreshAegisOffersPacket;
import com.whatever.aegis_ascension.network.RequestAegisOffersPacket;
import com.whatever.aegis_ascension.network.SelectAegisPacket;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Random Aegis-offer selection page. */
final class ACGAegisSelectionPage implements ACGAwaitingPage {
    private static final int CARD_GAP = 8;

    private List<Aegis> offers = List.of();
    private boolean awaitingSelection;
    private boolean awaitingRefresh;

    @Override
    public void init(ACGScreenContext context) {
        if (offers.isEmpty()) {
            if (ClientPerkState.getAegisSelectionCharges() > 0) {
                ACGButton request = ACGButton.builder(
                                getTranslatableString(
                                        "screen.aegis_ascension.acg.request_aegis_offers",
                                        ClientPerkState.getAegisSelectionCharges()),
                                button -> requestOffers())
                        .bounds(context.contentX() + context.contentWidth() / 2 - 90,
                                (context.contentTop() + context.contentBottom()) / 2,
                                180, 20)
                        .build();
                request.active = !isAwaitingServer();
                context.add(request);
            }
            return;
        }

        int gridTop = context.contentTop() + 62;
        int gridBottom = context.contentBottom() - 30;
        int cardWidth = ClientSettings.get().cardWidth;
        int cardHeight = ClientSettings.get().cardHeight;
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                offers.size(), context.contentX(), context.contentWidth(),
                gridTop, gridBottom, cardWidth, cardWidth, cardHeight, 4, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            Aegis aegis = offers.get(index);
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + CARD_GAP);
            int[] glow = detectOpaqueBounds(
                    ACGTheme.AEGIS_CARD, ACGTheme.CARD_TEXTURE_SIZE);
            int[] frame = detectFrameBounds(
                    ACGTheme.AEGIS_CARD, ACGTheme.CARD_TEXTURE_SIZE);
            context.add(ACGCardWidget.builder(
                            x, y, layout.cardWidth(), layout.cardHeight(), aegis.title())
                    .presentation(Presentation.BIG)
                    .cardBackground(ACGTheme.AEGIS_CARD,
                            glow[0], glow[1], glow[2], glow[3],
                            frame[0], frame[1], frame[2], frame[3])
                    .icon(aegis.iconTexture(), 128)
                    .subtitle(aegis.description())
                    .status(getTranslatableString(
                            "screen.aegis_ascension.aegis.unique"),
                            ACGTheme.STATUS_ACTIVE)
                    .accentColor(ACGTheme.RARITY_AEGIS)
                    .enabled(!isAwaitingServer())
                    .onClick(widget -> selectOffer(context, aegis))
                    .build());
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);

        ACGButton refresh = ACGButton.builder(
                        getTranslatableString(
                                "screen.aegis_ascension.aegis.refresh",
                                ClientPerkState.getAegisRefreshCharges()),
                        button -> refreshOffers(context))
                .bounds(context.contentX() + context.contentWidth() / 2 - 70,
                        context.contentTop() + 34, 140, 20)
                .build();
        refresh.active = ClientPerkState.getAegisRefreshCharges() > 0
                && !isAwaitingServer();
        context.add(refresh);
    }

    void requestOffers() {
        if (!isAwaitingServer()) {
            ModNetworking.sendToServer(new RequestAegisOffersPacket());
        }
    }

    private void selectOffer(ACGScreenContext context, Aegis aegis) {
        if (isAwaitingServer() || ClientPerkState.getAegisSelectionCharges() <= 0) {
            return;
        }
        awaitingSelection = true;
        offers = List.of();
        ModNetworking.sendToServer(new SelectAegisPacket(aegis.id()));
        context.rebuild();
    }

    private void refreshOffers(ACGScreenContext context) {
        if (isAwaitingServer() || ClientPerkState.getAegisRefreshCharges() <= 0
                || !ClientRefreshRequestLimiter.tryAcquire()) {
            return;
        }
        awaitingRefresh = true;
        ModNetworking.sendToServer(new RefreshAegisOffersPacket());
        context.rebuild();
    }

    void setOffers(ACGScreenContext context, List<Aegis> offers) {
        this.offers = List.copyOf(offers);
        clearAwaiting();
    }

    boolean hasOffers() {
        return !offers.isEmpty();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.aegis.charges",
                        ClientPerkState.getAegisSelectionCharges()),
                context.contentX() + context.contentWidth() / 2,
                context.contentTop(), ACGTheme.GOLD_BRIGHT);
        context.drawLevelProgress(graphics,
                "screen.aegis_ascension.acg.aegis_progress",
                "screen.aegis_ascension.acg.aegis_progress_max",
                PlatformServices.config().aegisLevelsPerCharge(),
                Math.max(0, PlatformServices.config().maximumAegisCharges() - 1),
                context.contentTop() + 12);
        if (offers.isEmpty() && ClientPerkState.getAegisSelectionCharges() <= 0) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString("message.aegis_ascension.no_aegis_charges"),
                    context.contentX() + context.contentWidth() / 2,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
        }
    }

    @Override
    public void onServerSync(ACGScreenContext context) {
        clearAwaiting();
        if (ClientPerkState.getAegisSelectionCharges() <= 0
                || !ClientPerkState.hasAvailableAegisChoice()) {
            offers = List.of();
        }
    }

    @Override
    public boolean isAwaitingServer() {
        return awaitingSelection || awaitingRefresh;
    }

    @Override
    public void clearAwaiting() {
        awaitingSelection = false;
        awaitingRefresh = false;
    }
}
