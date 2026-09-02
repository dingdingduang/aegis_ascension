package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectOpaqueBounds;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.perk.TalentConstants;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.ClientRefreshRequestLimiter;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ExchangePerkChargePacket;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RefreshPerkOffersPacket;
import com.whatever.aegis_ascension.network.RequestPerkOffersPacket;
import com.whatever.aegis_ascension.network.SelectAllOfferedPerksPacket;
import com.whatever.aegis_ascension.network.SelectPerkPacket;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Random talent-offer selection page. */
final class ACGPerkSelectionPage implements ACGAwaitingPage {
    private static final int CARD_GAP = 8;

    private List<Perk> offers = List.of();
    private boolean awaitingSelection;
    private boolean awaitingRefresh;

    @Override
    public void init(ACGScreenContext context) {
        if (offers.isEmpty()) {
            if (ClientPerkState.getSelectionCharges() > 0) {
                ACGButton request = ACGButton.builder(
                                getTranslatableString(
                                        "screen.aegis_ascension.acg.request_perk_offers",
                                        ClientPerkState.getSelectionCharges()),
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
            Perk perk = offers.get(index);
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + CARD_GAP);
            ResourceLocation cardTexture = cardTextureFor(perk.tier());
            int[] art = detectOpaqueBounds(cardTexture, ACGTheme.CARD_TEXTURE_SIZE);
            Component title = getLiteralString("[" + perk.tier().name() + "] ")
                    .withStyle(style -> style.withColor(rarityColor(perk.tier())))
                    .append(perk.title());
            context.add(ACGCardWidget.builder(
                            x, y, layout.cardWidth(), layout.cardHeight(), title)
                    .presentation(Presentation.BIG)
                    .cardBackground(cardTexture, art[0], art[1], art[2], art[3])
                    .icon(perk.iconTexture(), 32)
                    .subtitle(perk.description())
                    .status(perk.repeatable()
                                    ? getTranslatableString(
                                    "screen.aegis_ascension.repeatable_rank_limited",
                                    ClientPerkState.getRank(perk) + 1, perk.maxRank())
                                    : getTranslatableString(
                                    "screen.aegis_ascension.one_time"),
                            0xFFFFD166)
                    .accentColor(rarityColor(perk.tier()))
                    .enabled(!isAwaitingServer())
                    .onClick(widget -> selectOffer(context, perk))
                    .build());
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
        addActions(context);
    }

    private void addActions(ACGScreenContext context) {
        Aegis authority = Aegis.byId(AegisConstants.AUTHORITY)
                .filter(ClientPerkState::isAegisEnabled)
                .orElse(null);
        int actionGap = 8;
        int authorityButtonSize = 20;
        int actionFixedWidth = actionGap
                + (authority == null ? 0 : actionGap + authorityButtonSize);
        int actionWidth = Math.min(160,
                Math.max(80, (context.contentWidth() - actionFixedWidth) / 2));
        int actionTotalWidth = actionWidth * 2 + actionFixedWidth;
        int actionStartX = context.contentX()
                + (context.contentWidth() - actionTotalWidth) / 2;

        ACGButton refresh = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.refresh",
                                ClientPerkState.getPerkRefreshCharges()),
                        button -> refreshOffers(context))
                .bounds(actionStartX, context.contentTop() + 34, actionWidth, 20)
                .build();
        refresh.active = ClientPerkState.getPerkRefreshCharges() > 0
                && !isAwaitingServer();
        context.add(refresh);

        ACGButton giveUp = ACGButton.builder(
                        getTranslatableString("screen.aegis_ascension.give_up",
                                ClientPerkState
                                        .getSkillEnhancementChargesPerPerkExchange()),
                        button -> giveUp(context))
                .bounds(actionStartX + actionWidth + actionGap,
                        context.contentTop() + 34, actionWidth, 20)
                .build();
        giveUp.active = ClientPerkState.getSelectionCharges() > 0
                && !isAwaitingServer();
        context.add(giveUp);

        if (authority == null) {
            return;
        }
        int used = Math.max(0, (int) Math.floor(ClientPerkState.getDisplayStat(
                "__custom." + AegisConstants.AUTHORITY_SELECT_ALL_USES)));
        int maximum = Math.max(0, (int) Math.floor(
                authority.stat(AegisConstants.SELECT_ALL_MAX_USES)));
        Component label = getTranslatableString(
                "screen.aegis_ascension.authority_select_all", used, maximum);
        ACGButton selectAll = ACGButton.builder(label, button -> selectAll(context))
                .bounds(actionStartX + actionWidth * 2 + actionGap * 2,
                        context.contentTop() + 34,
                        authorityButtonSize, authorityButtonSize)
                .icon(authority.iconTexture(), 128, 16)
                .iconOnly().build();
        selectAll.setTooltip(Tooltip.create(label));
        selectAll.active = used < maximum
                && ClientPerkState.getSelectionCharges() > 0
                && !isAwaitingServer();
        context.add(selectAll);
    }

    void requestOffers() {
        if (!isAwaitingServer()) {
            ModNetworking.sendToServer(new RequestPerkOffersPacket());
        }
    }

    private void selectOffer(ACGScreenContext context, Perk perk) {
        if (isAwaitingServer() || ClientPerkState.getSelectionCharges() <= 0) {
            return;
        }
        awaitingSelection = true;
        offers = List.of();
        ModNetworking.sendToServer(new SelectPerkPacket(perk.id()));
        context.rebuild();
    }

    private void selectAll(ACGScreenContext context) {
        if (isAwaitingServer() || ClientPerkState.getSelectionCharges() <= 0
                || offers.isEmpty()) {
            return;
        }
        Aegis authority = Aegis.byId(AegisConstants.AUTHORITY)
                .filter(ClientPerkState::isAegisEnabled).orElse(null);
        if (authority == null) {
            return;
        }
        int used = Math.max(0, (int) Math.floor(ClientPerkState.getDisplayStat(
                "__custom." + AegisConstants.AUTHORITY_SELECT_ALL_USES)));
        int maximum = Math.max(0, (int) Math.floor(
                authority.stat(AegisConstants.SELECT_ALL_MAX_USES)));
        if (used >= maximum) {
            return;
        }
        awaitingSelection = true;
        offers = List.of();
        ModNetworking.sendToServer(new SelectAllOfferedPerksPacket());
        context.rebuild();
    }

    private void refreshOffers(ACGScreenContext context) {
        if (isAwaitingServer() || ClientPerkState.getPerkRefreshCharges() <= 0
                || !ClientRefreshRequestLimiter.tryAcquire()) {
            return;
        }
        awaitingRefresh = true;
        ModNetworking.sendToServer(new RefreshPerkOffersPacket());
        context.rebuild();
    }

    private void giveUp(ACGScreenContext context) {
        if (isAwaitingServer() || ClientPerkState.getSelectionCharges() <= 0) {
            return;
        }
        awaitingSelection = true;
        ModNetworking.sendToServer(new ExchangePerkChargePacket());
        context.rebuild();
    }

    void setOffers(ACGScreenContext context, List<Perk> offers) {
        this.offers = List.copyOf(offers);
        clearAwaiting();
    }

    boolean hasOffers() {
        return !offers.isEmpty();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        graphics.drawString(context.font(),
                getTranslatableString("screen.aegis_ascension.talent_slots",
                        ClientPerkState.getUsedTalentSlots(),
                        ClientPerkState.getMaxTalentSlots()),
                context.contentX(), context.contentTop() - 10,
                ACGTheme.TEXT_SECONDARY, false);
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.charges",
                        ClientPerkState.getSelectionCharges(),
                        ClientPerkState.getPendingBreakthroughTriggers()),
                context.contentX() + context.contentWidth() / 2,
                context.contentTop(), ACGTheme.GOLD_BRIGHT);

        int perkInterval = PlatformServices.config().perkLevelsPerCharge();
        int trackWidth = Math.max(90,
                Math.min(180, context.contentWidth() / 2 - 24));
        context.drawLevelProgress(graphics,
                "screen.aegis_ascension.acg.perk_progress",
                "screen.aegis_ascension.acg.perk_progress_max",
                TalentConstants.HIGHEST_PERK_LEVEL, perkInterval,
                PlatformServices.config().maximumPerkChargesFromExperience(),
                context.contentTop() + 12,
                context.contentX() + context.contentWidth() / 4, trackWidth);
        context.drawLevelProgress(graphics,
                "screen.aegis_ascension.acg.breakthrough_progress",
                "screen.aegis_ascension.acg.breakthrough_progress_max",
                TalentConstants.HIGHEST_PERK_LEVEL, perkInterval,
                PlatformServices.config().maximumBreakthroughsFromExperience(),
                context.contentTop() + 12,
                context.contentX() + context.contentWidth() * 3 / 4, trackWidth);

        if (offers.isEmpty() && ClientPerkState.getSelectionCharges() <= 0) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString("message.aegis_ascension.no_charges"),
                    context.contentX() + context.contentWidth() / 2,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
        }
    }

    @Override
    public void onServerSync(ACGScreenContext context) {
        clearAwaiting();
        if (ClientPerkState.getSelectionCharges() <= 0
                || !ClientPerkState.hasAvailableChoice()) {
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

    private static ResourceLocation cardTextureFor(Perk.Tier tier) {
        return switch (tier) {
            case R -> ACGTheme.PERK_R_CARD;
            case SR -> ACGTheme.PERK_SR_CARD;
            case SSR -> ACGTheme.PERK_SSR_CARD;
        };
    }

    private static int rarityColor(Perk.Tier tier) {
        return switch (tier) {
            case R -> ACGTheme.RARITY_R;
            case SR -> ACGTheme.RARITY_SR;
            case SSR -> ACGTheme.RARITY_SSR;
        };
    }
}
