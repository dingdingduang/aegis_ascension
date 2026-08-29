package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.blitScaledRegion;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.OwnedAegis;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.ToggleAegisPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Owned Aegis showcase and toggle/manage actions. */
final class ACGOwnedAegisPage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int COMPACT_CARD_HEIGHT = 58;
    private static final int SHOWCASE_ACTION_HEIGHT = 20;

    private String selectedId;
    private int actionCooldownTicks;
    private ACGButton actionButton;

    @Override
    public void init(ACGScreenContext context) {
        List<Aegis> owned = Aegis.values().stream()
                .filter(ClientPerkState::ownsAegis).toList();
        if (selectedId == null
                || owned.stream().noneMatch(aegis -> aegis.id().equals(selectedId))) {
            selectedId = owned.isEmpty() ? null : owned.get(0).id();
        }

        int showcaseWidth = context.showcaseWidth();
        int rightX = context.contentX() + showcaseWidth + CARD_GAP;
        int rightWidth = context.contentWidth() - showcaseWidth - CARD_GAP;
        List<TalentCollectionCard> cards = OwnedAegis.cards();
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cards.size(), rightX, rightWidth,
                context.contentTop() + 6, context.contentBottom() - 30,
                82, 120, COMPACT_CARD_HEIGHT, 4, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            TalentCollectionCard card = cards.get(index);
            Aegis aegis = owned.get(index);
            boolean selected = aegis.id().equals(selectedId);
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
                    .selected(selected)
                    .onClick(widget -> {
                        selectedId = aegis.id();
                        context.rebuild();
                    }).build());
        }
        context.addPaginationButtons(
                rightX + rightWidth / 2, context.contentBottom() - 24, true);

        actionButton = null;
        if (selectedId == null) {
            return;
        }
        Aegis selected = Aegis.byId(selectedId).orElse(null);
        if (selected == null || (!selected.manuallyToggleable()
                && !selected.id().equals(AegisConstants.DEVOUR))) {
            return;
        }
        boolean isDevour = selected.id().equals(AegisConstants.DEVOUR);
        boolean enabled = ClientPerkState.isAegisEnabled(selected);
        Component label = isDevour
                ? getTranslatableString(
                "screen.aegis_ascension.acg.devour_manage_button")
                : getTranslatableString(enabled
                ? "screen.aegis_ascension.collection.toggle_on"
                : "screen.aegis_ascension.collection.toggle_off");
        actionButton = ACGButton.builder(
                        label, button -> performAction(context, selected))
                .bounds(context.contentX() + (showcaseWidth - 160) / 2,
                        context.contentBottom() - 26, 160, SHOWCASE_ACTION_HEIGHT)
                .build();
        actionButton.active = actionCooldownTicks <= 0;
        context.add(actionButton);
    }

    private void performAction(ACGScreenContext context, Aegis aegis) {
        if (aegis.id().equals(AegisConstants.DEVOUR)) {
            context.switchMode(ACGPerkSelectionScreen.UIMode.DEVOURED);
            return;
        }
        if (actionCooldownTicks > 0) {
            return;
        }
        actionCooldownTicks = 20;
        boolean enabled = !ClientPerkState.isAegisEnabled(aegis);
        ClientPerkState.setAegisEnabled(aegis, enabled);
        ModNetworking.sendToServer(new ToggleAegisPacket(aegis.id(), enabled));
        context.rebuild();
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int showcaseWidth = context.showcaseWidth();
        graphics.drawString(context.font(),
                getTranslatableString(
                        "screen.aegis_ascension.collection.aegis_count",
                        ClientPerkState.getChosenAegises().size()),
                context.contentX(), context.contentTop() - 10,
                ACGTheme.GOLD_BRIGHT, false);
        if (selectedId == null) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(
                            "screen.aegis_ascension.collection.aegis_empty"),
                    context.contentX() + showcaseWidth / 2,
                    context.contentTop() + 80, ACGTheme.TEXT_MUTED);
            return;
        }
        Aegis aegis = Aegis.byId(selectedId).orElse(null);
        if (aegis == null) {
            return;
        }

        int centerX = context.contentX() + showcaseWidth / 2;
        int ringY = context.contentTop() + 100;
        context.drawShowcaseBackdrop(graphics, centerX, ringY);
        int iconSize = 68;
        blitScaledRegion(graphics, aegis.iconTexture(),
                centerX - iconSize / 2, ringY - iconSize / 2,
                iconSize, iconSize, 0.0F, 0.0F, 128, 128, 128, 128);
        drawCenteredString(graphics, context.font(),
                ACGTheme.asHeader(aegis.title()), centerX, ringY + 60,
                ACGTheme.TEXT_PRIMARY);

        boolean owned = ClientPerkState.ownsAegis(aegis);
        boolean enabled = ClientPerkState.isAegisEnabled(aegis);
        Component status = !owned
                ? getTranslatableString("screen.aegis_ascension.collection.locked")
                : aegis.manuallyToggleable()
                ? getTranslatableString(enabled
                ? "screen.aegis_ascension.collection.toggle_on"
                : "screen.aegis_ascension.collection.toggle_off")
                : getTranslatableString(
                "screen.aegis_ascension.collection.aegis_owned");
        drawCenteredString(graphics, context.font(), status, centerX, ringY + 74,
                aegis.manuallyToggleable()
                        ? (enabled ? ACGTheme.STATUS_ACTIVE : ACGTheme.STATUS_LOCKED)
                        : ACGTheme.GOLD);

        int textY = ringY + 90;
        int textBottom = context.contentBottom() - 52;
        for (var line : context.font().split(
                aegis.description(), Math.max(80, showcaseWidth - 16))) {
            if (textY > textBottom) {
                break;
            }
            drawCenteredString(graphics, context.font(), line,
                    centerX, textY, ACGTheme.TEXT_SECONDARY);
            textY += 10;
        }
    }

    @Override
    public void tick(ACGScreenContext context) {
        if (actionCooldownTicks > 0) {
            actionCooldownTicks--;
        }
        if (actionButton != null) {
            actionButton.active = actionCooldownTicks <= 0;
        }
    }
}
