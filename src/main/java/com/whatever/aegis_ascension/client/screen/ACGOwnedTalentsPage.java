package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.perk.TalentConstants.R_DIVINE_SAKURA_POWER;
import static com.whatever.aegis_ascension.perk.TalentConstants.SR_SHARED_FORTUNE;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.blitScaledRegion;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget;
import com.whatever.aegis_ascension.client.screen.acg.ACGCardWidget.Presentation;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.client.screen.collectiontabs.OwnedTalents;
import com.whatever.aegis_ascension.client.screen.collectiontabs.TalentCollectionCard;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.SetSharedFortunePartnerPacket;
import com.whatever.aegis_ascension.network.ToggleTalentPacket;
import com.whatever.aegis_ascension.network.UnlockConstellationPacket;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Owned-talent showcase, toggles, constellation action, and Shared Fortune modal. */
final class ACGOwnedTalentsPage implements ACGPage {
    private static final int CARD_GAP = 8;
    private static final int COMPACT_CARD_HEIGHT = 58;
    private static final int SHOWCASE_ACTION_HEIGHT = 20;
    private static final int PLAYER_ROWS = 6;
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 248;
    private static final float MODAL_Z = 400.0F;
    private static final float MODAL_WIDGET_Z = 410.0F;

    private record Candidate(UUID id, String name) {
    }

    private String selectedId;
    private boolean choosingPartner;
    private int partnerPage;
    private boolean rebindLocked;
    private int actionCooldownTicks;
    private ACGButton actionButton;

    @Override
    public void init(ACGScreenContext context) {
        List<Perk> owned = ClientPerkState.getOwnedPerks();
        if (selectedId == null
                || owned.stream().noneMatch(perk -> perk.id().equals(selectedId))) {
            selectedId = owned.isEmpty() ? null : owned.get(0).id();
        }
        if (choosingPartner && !SR_SHARED_FORTUNE.equals(selectedId)) {
            choosingPartner = false;
        }
        if (choosingPartner) {
            context.gridMaxScroll(0);
            initPartnerModal(context);
            return;
        }

        int showcaseWidth = context.showcaseWidth();
        int rightX = context.contentX() + showcaseWidth + CARD_GAP;
        int rightWidth = context.contentWidth() - showcaseWidth - CARD_GAP;
        List<TalentCollectionCard> cards = OwnedTalents.cards();
        ACGPerkSelectionScreen.GridLayout layout = context.computeGrid(
                cards.size(), rightX, rightWidth,
                context.contentTop() + 6, context.contentBottom() - 30,
                76, 108, COMPACT_CARD_HEIGHT, 4, false);
        for (int index = layout.firstIndex(); index < layout.lastIndex(); index++) {
            TalentCollectionCard card = cards.get(index);
            Perk perk = owned.get(index);
            boolean selected = perk.id().equals(selectedId);
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
                        selectedId = perk.id();
                        context.rebuild();
                    }).build());
        }
        context.addPaginationButtons(
                rightX + rightWidth / 2, context.contentBottom() - 24, true);
        addShowcaseAction(context, showcaseWidth);
    }

    private void addShowcaseAction(ACGScreenContext context, int showcaseWidth) {
        actionButton = null;
        if (selectedId == null) {
            return;
        }
        Perk perk = Perk.byId(selectedId).orElse(null);
        if (perk == null || (!perk.manuallyToggleable()
                && !perk.id().equals(R_DIVINE_SAKURA_POWER)
                && !perk.id().equals(SR_SHARED_FORTUNE))) {
            return;
        }
        boolean constellation = perk.id().equals(R_DIVINE_SAKURA_POWER);
        boolean enabled = ClientPerkState.isTalentEnabled(perk);
        Component label;
        if (perk.id().equals(SR_SHARED_FORTUNE)) {
            String partnerName = ClientPerkState.getSharedFortunePartnerName();
            label = partnerName.isBlank()
                    ? getTranslatableString(
                    "screen.aegis_ascension.shared_fortune.bind_action")
                    : getTranslatableString(
                    "screen.aegis_ascension.shared_fortune.bound_action", partnerName);
        } else {
            label = constellation
                    ? getTranslatableString(
                    "screen.aegis_ascension.collection.constellation_action")
                    : getTranslatableString(enabled
                    ? "screen.aegis_ascension.collection.toggle_on"
                    : "screen.aegis_ascension.collection.toggle_off");
        }
        actionButton = ACGButton.builder(
                        label, button -> performAction(context, perk))
                .bounds(context.contentX() + (showcaseWidth - 160) / 2,
                        context.contentBottom() - 26, 160, SHOWCASE_ACTION_HEIGHT)
                .build();
        actionButton.active = actionCooldownTicks <= 0;
        context.add(actionButton);
    }

    private void performAction(ACGScreenContext context, Perk perk) {
        if (perk.id().equals(SR_SHARED_FORTUNE)) {
            choosingPartner = true;
            partnerPage = 0;
            context.rebuild();
            return;
        }
        if (actionCooldownTicks > 0) {
            return;
        }
        actionCooldownTicks = 20;
        if (perk.id().equals(R_DIVINE_SAKURA_POWER)) {
            ModNetworking.sendToServer(
                    new UnlockConstellationPacket(R_DIVINE_SAKURA_POWER));
            context.rebuild();
            return;
        }
        boolean enabled = !ClientPerkState.isTalentEnabled(perk);
        ClientPerkState.setTalentEnabled(perk.id(), enabled);
        ModNetworking.sendToServer(new ToggleTalentPacket(perk.id(), enabled));
        context.rebuild();
    }

    private void initPartnerModal(ACGScreenContext context) {
        List<Candidate> candidates = onlineCandidates(context);
        int rows = visibleRows(context);
        int pages = Math.max(1, (candidates.size() + rows - 1) / rows);
        partnerPage = Math.max(0, Math.min(pages - 1, partnerPage));
        int panelWidth = panelWidth(context);
        int panelHeight = panelHeight(context);
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;
        int first = partnerPage * rows;
        int last = Math.min(candidates.size(), first + rows);
        UUID currentPartner = ClientPerkState.getSharedFortunePartnerId();
        boolean canRebind = ClientPerkState.getSharedFortuneRebindCooldownSeconds() <= 0;
        rebindLocked = !canRebind;

        for (int index = first; index < last; index++) {
            Candidate candidate = candidates.get(index);
            boolean current = candidate.id().equals(currentPartner);
            Component label = current
                    ? getTranslatableString(
                    "screen.aegis_ascension.shared_fortune.current_candidate",
                    candidate.name())
                    : getLiteralString(candidate.name());
            ACGButton candidateButton = ACGButton.builder(label, button -> {
                        ModNetworking.sendToServer(
                                new SetSharedFortunePartnerPacket(candidate.id()));
                        choosingPartner = false;
                        context.rebuild();
                    })
                    .bounds(panelX + 12,
                            panelY + 48 + (index - first) * 24,
                            panelWidth - 24, 20)
                    .build().style(current
                            ? ACGButton.Style.TEAL : ACGButton.Style.PLAIN)
                    .zOffset(MODAL_WIDGET_Z);
            candidateButton.active = canRebind && !current;
            context.add(candidateButton);
        }

        int pagerY = panelY + 48 + rows * 24;
        if (pages > 1) {
            ACGButton previous = ACGButton.builder(getLiteralString("‹"), button -> {
                        partnerPage--;
                        context.rebuild();
                    }).bounds(panelX + panelWidth / 2 - 54, pagerY, 28, 18)
                    .build().style(ACGButton.Style.PLAIN).zOffset(MODAL_WIDGET_Z);
            previous.active = partnerPage > 0;
            context.add(previous);

            ACGButton next = ACGButton.builder(getLiteralString("›"), button -> {
                        partnerPage++;
                        context.rebuild();
                    }).bounds(panelX + panelWidth / 2 + 26, pagerY, 28, 18)
                    .build().style(ACGButton.Style.PLAIN).zOffset(MODAL_WIDGET_Z);
            next.active = partnerPage + 1 < pages;
            context.add(next);
        }

        int footerY = panelY + panelHeight - 28;
        int footerWidth = (panelWidth - 32) / 2;
        ACGButton unbind = ACGButton.builder(
                        getTranslatableString(
                                "screen.aegis_ascension.shared_fortune.unbind_action"),
                        button -> {
                            ModNetworking.sendToServer(
                                    SetSharedFortunePartnerPacket.unbind());
                            choosingPartner = false;
                            context.rebuild();
                        })
                .bounds(panelX + 12, footerY, footerWidth, 20)
                .build().style(ACGButton.Style.PLAIN).zOffset(MODAL_WIDGET_Z);
        unbind.active = currentPartner != null;
        context.add(unbind);

        context.add(ACGButton.builder(getTranslatableString("gui.cancel"), button -> {
                    choosingPartner = false;
                    context.rebuild();
                })
                .bounds(panelX + panelWidth / 2 + 4, footerY, footerWidth, 20)
                .build().style(ACGButton.Style.TEAL).zOffset(MODAL_WIDGET_Z));
    }

    private List<Candidate> onlineCandidates(ACGScreenContext context) {
        if (context.minecraft() == null || context.minecraft().player == null
                || context.minecraft().getConnection() == null) {
            return List.of();
        }
        UUID self = context.minecraft().player.getUUID();
        return context.minecraft().getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getProfile().getId() != null)
                .filter(info -> !self.equals(info.getProfile().getId()))
                .map(info -> new Candidate(
                        info.getProfile().getId(), info.getProfile().getName()))
                .sorted(Comparator.comparing(
                        Candidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int visibleRows(ACGScreenContext context) {
        return Math.max(1, Math.min(PLAYER_ROWS,
                Math.max(1, context.contentBottom() - context.contentTop() - 104) / 24));
    }

    private int panelWidth(ACGScreenContext context) {
        return Math.min(PANEL_WIDTH, Math.max(140, context.contentWidth() - 24));
    }

    private int panelHeight(ACGScreenContext context) {
        return Math.min(PANEL_HEIGHT, 104 + visibleRows(context) * 24);
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int showcaseWidth = context.showcaseWidth();
        graphics.drawString(context.font(),
                getTranslatableString("screen.aegis_ascension.talent_slots",
                        ClientPerkState.getUsedTalentSlots(),
                        ClientPerkState.getMaxTalentSlots()),
                context.contentX(), context.contentTop() - 10,
                ACGTheme.TEXT_SECONDARY, false);
        if (selectedId == null) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString("screen.aegis_ascension.collection.empty"),
                    context.contentX() + showcaseWidth / 2,
                    context.contentTop() + 80, ACGTheme.TEXT_MUTED);
            return;
        }
        Perk perk = Perk.byId(selectedId).orElse(null);
        if (perk == null) {
            return;
        }

        int centerX = context.contentX() + showcaseWidth / 2;
        int ringY = context.contentTop() + 100;
        int rarity = rarityColor(perk.tier());
        context.drawShowcaseBackdrop(graphics, centerX, ringY);
        int iconSize = 48;
        blitScaledRegion(graphics, perk.iconTexture(),
                centerX - iconSize / 2, ringY - iconSize / 2,
                iconSize, iconSize, 0.0F, 0.0F, 32, 32, 32, 32);
        Component title = getLiteralString("[" + perk.tier().name() + "] ")
                .withStyle(style -> style.withColor(rarity)).append(perk.title());
        drawCenteredString(graphics, context.font(), ACGTheme.asHeader(title),
                centerX, ringY + 60, ACGTheme.TEXT_PRIMARY);

        int rank = ClientPerkState.getRank(perk);
        Component rankLine = perk.repeatable()
                ? getTranslatableString("screen.aegis_ascension.collection.rank",
                rank, perk.maxRank())
                : getTranslatableString("screen.aegis_ascension.one_time");
        drawCenteredString(graphics, context.font(), rankLine,
                centerX, ringY + 74, rarity);
        if (perk.repeatable() && perk.maxRank() > 0) {
            ACGTheme.drawProgressBar(graphics, centerX - 60, ringY + 86,
                    120, 6, rank / (float) perk.maxRank(), 0xFF241F1A, rarity);
        }

        int textY = ringY + 100;
        int textBottom = context.contentBottom() - 52;
        for (var line : context.font().split(
                perk.description(), Math.max(80, showcaseWidth - 16))) {
            if (textY > textBottom) {
                break;
            }
            drawCenteredString(graphics, context.font(), line,
                    centerX, textY, ACGTheme.TEXT_SECONDARY);
            textY += 10;
        }
        if (choosingPartner) {
            renderPartnerModal(context, graphics);
        }
    }

    private void renderPartnerModal(ACGScreenContext context, GuiGraphics graphics) {
        List<Candidate> candidates = onlineCandidates(context);
        int rows = visibleRows(context);
        int pages = Math.max(1, (candidates.size() + rows - 1) / rows);
        int panelWidth = panelWidth(context);
        int panelHeight = panelHeight(context);
        int panelX = context.contentX() + (context.contentWidth() - panelWidth) / 2;
        int panelY = (context.contentTop() + context.contentBottom() - panelHeight) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, MODAL_Z);
        graphics.fill(context.contentX(), context.contentTop(),
                context.contentX() + context.contentWidth(), context.contentBottom(),
                0xC0100D12);
        ACGTheme.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 1.0F);
        drawCenteredString(graphics, context.font(), ACGTheme.asHeader(
                        getTranslatableString(
                                "screen.aegis_ascension.shared_fortune.choose_title")),
                panelX + panelWidth / 2, panelY + 10, ACGTheme.GOLD_BRIGHT);

        String partnerName = ClientPerkState.getSharedFortunePartnerName();
        Component current = partnerName.isBlank()
                ? getTranslatableString(
                "screen.aegis_ascension.shared_fortune.current_none")
                : getTranslatableString(
                "screen.aegis_ascension.shared_fortune.current", partnerName);
        drawCenteredString(graphics, context.font(), current,
                panelX + panelWidth / 2, panelY + 24, ACGTheme.TEXT_SECONDARY);

        int cooldown = ClientPerkState.getSharedFortuneRebindCooldownSeconds();
        if (cooldown > 0) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(
                            "screen.aegis_ascension.shared_fortune.cooldown", cooldown),
                    panelX + panelWidth / 2, panelY + 36, ACGTheme.STATUS_LOCKED);
        }
        if (candidates.isEmpty()) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(
                            "screen.aegis_ascension.shared_fortune.no_players"),
                    panelX + panelWidth / 2, panelY + 58, ACGTheme.TEXT_MUTED);
        }
        if (pages > 1) {
            drawCenteredString(graphics, context.font(),
                    getTranslatableString(
                            "screen.aegis_ascension.shared_fortune.page",
                            partnerPage + 1, pages),
                    panelX + panelWidth / 2, panelY + 53 + rows * 24,
                    ACGTheme.TEXT_MUTED);
        }
        graphics.pose().popPose();
    }

    @Override
    public void tick(ACGScreenContext context) {
        if (actionCooldownTicks > 0) {
            actionCooldownTicks--;
        }
        if (actionButton != null) {
            actionButton.active = actionCooldownTicks <= 0;
        }
        if (choosingPartner && rebindLocked
                && ClientPerkState.getSharedFortuneRebindCooldownSeconds() <= 0) {
            rebindLocked = false;
            context.rebuild();
        }
    }

    @Override
    public boolean keyPressed(ACGScreenContext context, int keyCode,
                              int scanCode, int modifiers) {
        if (!choosingPartner) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            choosingPartner = false;
            context.rebuild();
        }
        return true;
    }

    @Override
    public void onDeactivated(ACGScreenContext context) {
        choosingPartner = false;
        partnerPage = 0;
    }

    private static int rarityColor(Perk.Tier tier) {
        return switch (tier) {
            case R -> ACGTheme.RARITY_R;
            case SR -> ACGTheme.RARITY_SR;
            case SSR -> ACGTheme.RARITY_SSR;
        };
    }
}
