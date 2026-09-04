package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_MATTER_TO_MAGIC_CONVERSION;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.SkillEnhancementClientSettings;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.ClientRefreshRequestLimiter;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.RefreshSkillEnhancementOffersPacket;
import com.whatever.aegis_ascension.network.SelectSkillEnhancementPacket;
import com.whatever.aegis_ascension.network.SetPrimarySkillEnhancementPacket;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Skill-enhancement purchase and primary-stat selection page. */
final class ACGSkillEnhancementPage implements ACGAwaitingPage {
    private static final int CARD_GAP = 8;
    private static final int CARD_HEIGHT = 128;

    private boolean choosingPrimary;
    private boolean awaitingSelection;
    private boolean awaitingRefresh;
    private int primarySwitchCooldownTicks;

    @Override
    public void init(ACGScreenContext context) {
        boolean chosen = ClientPerkState.hasChosenPrimarySkillEnhancement();
        boolean conversionUnlocked = ClientPerkState.owns(PERK_MATTER_TO_MAGIC_CONVERSION);
        if (choosingPrimary) {
            context.add(ACGButton.builder(
                            getTranslatableString(
                                    "screen.aegis_ascension.collection.primary.back"),
                            button -> {
                                choosingPrimary = false;
                                context.page(0);
                                context.rebuild();
                            })
                    .bounds(context.contentX() + context.contentWidth() / 2 - 110,
                            context.contentTop(), 220, 20)
                    .build());
        } else {
            Component primaryLabel = !chosen
                    ? getTranslatableString(
                    "screen.aegis_ascension.collection.primary.choose_initial")
                    : getTranslatableString(conversionUnlocked
                                    ? "screen.aegis_ascension.collection.primary.button"
                                    : "screen.aegis_ascension.collection.primary.locked",
                            SkillEnhancementClientSettings.title(
                                    ClientPerkState.getPrimarySkillEnhancement()));
            int controlsWidth = Math.min(364,
                    Math.max(220, context.contentWidth() - 16));
            int refreshWidth = Math.min(140, Math.max(92, controlsWidth / 3));
            int gap = 4;
            int primaryWidth = controlsWidth - refreshWidth - gap;
            int controlsX = context.contentX()
                    + (context.contentWidth() - controlsWidth) / 2;
            ACGButton primary = ACGButton.builder(primaryLabel, button -> {
                        choosingPrimary = true;
                        context.page(0);
                        context.rebuild();
                    }).bounds(controlsX, context.contentTop(), primaryWidth, 20).build();
            primary.active = !chosen || conversionUnlocked;
            context.add(primary);

            boolean freeRefresh = ClientPerkState.isSkillEnhancementRefreshFree()
                    || ClientPerkState.getSkillEnhancementRefreshExperienceCost() == 0;
            Component refreshLabel = freeRefresh
                    ? getTranslatableString(
                    "screen.aegis_ascension.collection.skill_enhancement.refresh_free")
                    : getTranslatableString(
                    ClientPerkState.usesGoldCurrency()
                            ? "screen.aegis_ascension.collection.skill_enhancement.refresh_gold"
                            : "screen.aegis_ascension.collection.skill_enhancement.refresh",
                    ClientPerkState.getSkillEnhancementRefreshExperienceCost());
            ACGButton refresh = ACGButton.builder(
                            refreshLabel, button -> refreshOffers(context))
                    .bounds(controlsX + primaryWidth + gap, context.contentTop(),
                            refreshWidth, 20)
                    .build();
            refresh.active = ClientPerkState.getSkillEnhancementCharges() > 0
                    && !ClientPerkState.getSkillEnhancementOffers().isEmpty()
                    && ClientPerkState.getSkillEnhancementOffers().size()
                    < SkillEnhancement.values().size()
                    && !isAwaitingServer();
            context.add(refresh);
        }

        List<TalentCollectionCard> cards =
                com.whatever.aegis_ascension.client.screen.collectiontabs
                        .SkillEnhancement.cards(
                                choosingPrimary, awaitingSelection, awaitingRefresh);
        int gridTop = context.contentTop() + 28;
        int gridBottom = context.contentBottom() - 30;
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cards.size(), context.contentX(), context.contentWidth(),
                gridTop, gridBottom, 130, 190, CARD_HEIGHT, 4, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            TalentCollectionCard card = cards.get(index);
            int local = index - layout.firstIndex();
            int x = layout.startX()
                    + (local % layout.columns()) * (layout.cardWidth() + CARD_GAP);
            int y = layout.startY()
                    + (local / layout.columns()) * (layout.cardHeight() + CARD_GAP);
            context.add(ACGCardWidget.builder(
                            x, y, layout.cardWidth(), layout.cardHeight(), card.title())
                    .presentation(Presentation.BIG)
                    .icon(card.icon(), card.iconTextureSize())
                    .tooltip(card.tooltip())
                    .subtitle(card.description())
                    .status(card.status(), card.statusColor())
                    .accentColor(card.color())
                    .enabled(card.active())
                    .onClick(widget -> cardClicked(context, card))
                    .build());
        }
        context.addPaginationButtons(
                context.contentX() + context.contentWidth() / 2,
                context.contentBottom() - 24, true);
    }

    private void refreshOffers(ACGScreenContext context) {
        if (isAwaitingServer() || !ClientRefreshRequestLimiter.tryAcquire()) {
            return;
        }
        awaitingRefresh = true;
        ModNetworking.sendToServer(new RefreshSkillEnhancementOffersPacket());
        context.rebuild();
    }

    private void cardClicked(ACGScreenContext context, TalentCollectionCard card) {
        if (card.skillEnhancementId() == null || !card.active() || isAwaitingServer()) {
            return;
        }
        if (choosingPrimary) {
            if (primarySwitchCooldownTicks > 0) {
                return;
            }
            primarySwitchCooldownTicks = 20;
            SkillEnhancement.byId(card.skillEnhancementId()).ifPresent(enhancement -> {
                ClientPerkState.setPrimarySkillEnhancement(enhancement);
                ModNetworking.sendToServer(
                        new SetPrimarySkillEnhancementPacket(enhancement.id()));
            });
            choosingPrimary = false;
            context.page(0);
            context.rebuild();
            return;
        }
        awaitingSelection = true;
        ModNetworking.sendToServer(
                new SelectSkillEnhancementPacket(card.skillEnhancementId()));
        context.rebuild();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        graphics.drawString(context.font(),
                getTranslatableString(
                        "screen.aegis_ascension.collection.skill_enhancement_charges",
                        ClientPerkState.getSkillEnhancementCharges()),
                context.contentX(), context.contentTop() - 10,
                ACGTheme.TEXT_SECONDARY, false);
        List<TalentCollectionCard> cards =
                com.whatever.aegis_ascension.client.screen.collectiontabs
                        .SkillEnhancement.cards(
                                choosingPrimary, awaitingSelection, awaitingRefresh);
        if (cards.isEmpty()) {
            String key = ClientPerkState.getSkillEnhancementCharges() <= 0
                    ? "screen.aegis_ascension.collection.skill_enhancement.no_charges"
                    : "screen.aegis_ascension.collection.skill_enhancement.loading";
            drawCenteredString(graphics, context.font(), getTranslatableString(key),
                    context.contentX() + context.contentWidth() / 2,
                    (context.contentTop() + context.contentBottom()) / 2,
                    ACGTheme.TEXT_MUTED);
        }
    }

    @Override
    public void tick(ACGScreenContext context) {
        if (primarySwitchCooldownTicks > 0) {
            primarySwitchCooldownTicks--;
        }
    }

    @Override
    public void onServerSync(ACGScreenContext context) {
        clearAwaiting();
        if (ClientPerkState.hasChosenPrimarySkillEnhancement()
                && !ClientPerkState.owns(PERK_MATTER_TO_MAGIC_CONVERSION)) {
            choosingPrimary = false;
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

    @Override
    public void onDeactivated(ACGScreenContext context) {
        choosingPrimary = false;
    }
}
