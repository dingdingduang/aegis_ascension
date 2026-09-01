package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.blitScaledRegion;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectOpaqueBounds;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectTextureSize;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.ClientQuestCatalog;
import com.whatever.aegis_ascension.client.ClientQuestState;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.MiscLocalSettings;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.client.QuestIconRenderer;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.QuestActionPacket;
import com.whatever.aegis_ascension.quest.QuestConfig;
import com.whatever.aegis_ascension.quest.QuestCompletionView;
import com.whatever.aegis_ascension.quest.QuestObjective;
import com.whatever.aegis_ascension.quest.QuestRewardSummary;
import com.whatever.aegis_ascension.quest.QuestType;
import com.whatever.aegis_ascension.quest.QuestView;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shop-style quest catalogue with type tabs, selectable tasks, and a detail pane. */
final class ACGQuestCenterPage implements ACGPage {
    private static final int TAB_GAP = 6;
    private static final int TAB_HEIGHT = 30;
    private static final int MAIN_GAP = 8;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 4;

    private static final ResourceLocation CENTER_BACKGROUND = texture("quest_center_bg.png");
    private static final ResourceLocation QUEST_BACKGROUND = texture("quest_bg.png");
    private static final ResourceLocation DAILY_HEADER = texture("quest_daily.png");
    private static final ResourceLocation CHALLENGE_HEADER = texture("quest_challenge.png");
    private static final ResourceLocation COMMON_HEADER = texture("quest_common.png");
    private static final ResourceLocation CHUNK_HEADER = texture("quest_chunk.png");
    private static final ResourceLocation SIDE_HEADER = texture("quest_side.png");
    /** Stand-in art for objectives with no fitting vanilla item texture. */
    private static final ResourceLocation UNKNOWN_ICON = texture("quest_unknown.png");
    private static final ResourceLocation DONE_HEADER = texture("quest_done.png");
    private static final ResourceLocation FAILED_STATUS = texture("quest_failed.png");
    private static final ResourceLocation COMPLETE_STATUS = texture("quest_complete.png");
    private static final ResourceLocation OTHER_REWARD = texture("quest_reward_other_icon.png");
    private static final ResourceLocation TRACKER_HIDDEN = texture("quest_invisible.png");
    private static final ResourceLocation TRACKER_VISIBLE = texture("quest_visible.png");
    private static final ResourceLocation CHEST_TEXTURE =
            GeneralClientMethods.fromNamespaceAndPath("minecraft", "textures/entity/chest/normal.png");

    private QuestTab selectedTab = QuestTab.DAILY;
    private String selectedQuestId = "";
    private static final int DETAIL_SCROLL_STEP = 12;
    private int taskScroll;
    private int maxTaskScroll;
    /** Pixels the quest detail panel is scrolled by, and how far it may go. */
    private int detailScroll;
    private int maxDetailScroll;
    private int visibleRows = 1;

    @Override
    public void init(ACGScreenContext context) {
        PageLayout layout = layout(context);
        addTypeTabs(context, layout);
        context.page(0);
        context.pageCount(1);
        context.gridScroll(0);
        context.gridMaxScroll(0);

        visibleRows = visibleRowCount(layout);
        int rowWidth = Math.max(1, layout.listWidth() - 12);
        if (selectedTab == QuestTab.COMPLETE) {
            List<QuestCompletionView> completions = ClientQuestState.completions();
            ensureCompletionSelection(completions);
            clampTaskScroll(completions.size());
            int last = Math.min(completions.size(), taskScroll + visibleRows);
            for (int index = taskScroll; index < last; index++) {
                QuestCompletionView completion = completions.get(index);
                int row = index - taskScroll;
                ResourceLocation icon = completionIcon(completion);
                QuestRowButton button = new QuestRowButton(
                        layout.listX() + 5,
                        layout.rowsTop() + row * (ROW_HEIGHT + ROW_GAP),
                        rowWidth, ROW_HEIGHT,
                        completionRowLabel(completion),
                        ignored -> selectQuest(context, completion.questId()),
                        icon == null ? "" : icon.toString(),
                        info(completion).profession);
                context.add(button.style(completion.questId().equals(selectedQuestId)
                        ? ACGButton.Style.CTA : ACGButton.Style.PLAIN));
            }
            return;
        }

        List<QuestView> quests = ClientQuestState.byType(selectedTab.type());
        ensureSelection(quests);
        clampTaskScroll(quests.size());
        int last = Math.min(quests.size(), taskScroll + visibleRows);
        for (int index = taskScroll; index < last; index++) {
            QuestView quest = quests.get(index);
            int row = index - taskScroll;
            int rowX = layout.listX() + 5;
            int rowY = layout.rowsTop() + row * (ROW_HEIGHT + ROW_GAP);
            ACGButton button = new QuestRowButton(rowX, rowY, rowWidth, ROW_HEIGHT,
                    rowLabel(quest), ignored -> selectQuest(context, quest.id()),
                    questIcon(quest).toString(), info(quest).profession);
            context.add(button.style(quest.id().equals(selectedQuestId)
                    ? ACGButton.Style.CTA : ACGButton.Style.PLAIN));
        }

        QuestView selected = selectedQuest();
        if (selected != null) addQuestActions(context, layout, selected);
    }

    private void addTypeTabs(ACGScreenContext context, PageLayout layout) {
        QuestTab[] tabs = QuestTab.values();
        for (int index = 0; index < tabs.length; index++) {
            QuestTab tab = tabs[index];
            context.add(new QuestTabButton(
                    layout.tabsX() + index * (layout.tabWidth() + TAB_GAP),
                    layout.tabsY(), layout.tabWidth(), TAB_HEIGHT,
                    GeneralTextMethods.getTranslatableString(tab.titleKey()),
                    ignored -> selectTab(context, tab), tab.texture(),
                    tab == selectedTab, false, tab == QuestTab.COMPLETE));
        }
    }

    private void addQuestActions(ACGScreenContext context, PageLayout layout, QuestView quest) {
        addTrackerVisibilityAction(context, layout, quest);

        int availableWidth = Math.max(1, layout.detailWidth() - 24);
        int buttonWidth = Math.max(1, Math.min(140, (availableWidth - MAIN_GAP) / 2));
        int totalWidth = buttonWidth * 2 + MAIN_GAP;
        int startX = layout.detailX() + (layout.detailWidth() - totalWidth) / 2;
        int y = context.contentBottom() - 26;

        boolean submission = quest.accepted() && isItemSubmission(quest);
        Component primaryLabel = submission
                ? GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.submit")
                : quest.type() == QuestType.CHALLENGE
                ? GeneralTextMethods.getTranslatableString(ClientPerkState.usesGoldCurrency()
                ? "screen.aegis_ascension.acg.quest.accept_deposit_gold"
                : "screen.aegis_ascension.acg.quest.accept_deposit",
                questStake(quest))
                : GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.accept");
        QuestActionPacket.Action primaryAction = submission
                ? QuestActionPacket.Action.SUBMIT : QuestActionPacket.Action.ACCEPT;
        ACGButton accept = ACGButton.builder(primaryLabel,
                        ignored -> sendAction(quest, primaryAction))
                .bounds(startX, y, buttonWidth, 20)
                .build()
                .style(ACGButton.Style.CTA);
        // Enabled only when pressing it would actually do something. Submitting stays the
        // label for an accepted hand-in quest so it does not flip back to "Accept", but a
        // finished quest, or one whose items are all in, has nothing left to submit.
        accept.active = submission
                ? hasOutstandingSubmission(quest) : canAccept(context, quest);
        context.add(accept);

        // The options are listed on the offer, but only a finished quest can claim one.
        if (quest.completed() && !quest.rewardChoices().isEmpty()) {
            addRewardChoiceActions(context, layout, quest, y - 24);
        }

        ACGButton cancel = ACGButton.builder(
                        GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.cancel_free"),
                        ignored -> sendAction(quest, QuestActionPacket.Action.CANCEL))
                .bounds(startX + buttonWidth + MAIN_GAP, y, buttonWidth, 20)
                .build()
                .style(ACGButton.Style.PLAIN);
        cancel.active = canCancel(quest);
        context.add(cancel);
    }

    private void addTrackerVisibilityAction(
            ACGScreenContext context,
            PageLayout layout,
            QuestView quest
    ) {
        if (!isTrackerEligible(quest)) return;
        MiscLocalSettings settings = MiscLocalSettings.get();
        boolean trackerVisible = settings.isQuestTrackerVisible(quest.id());
        Component label = GeneralTextMethods.getTranslatableString(trackerVisible
                ? "screen.aegis_ascension.acg.quest.tracker_hide"
                : "screen.aegis_ascension.acg.quest.tracker_show");
        ResourceLocation icon = trackerVisible ? TRACKER_VISIBLE : TRACKER_HIDDEN;
        ACGButton button = ACGButton.builder(label, ignored -> {
                    settings.setQuestTrackerVisible(quest.id(), !trackerVisible);
                    context.rebuild();
                })
                .bounds(layout.detailX() + layout.detailWidth() - 36,
                        layout.mainTop() + 6, 26, 24)
                .icon(icon, 200, 18)
                .iconOnly()
                .build()
                .style(ACGButton.Style.PLAIN);
        button.setTooltip(Tooltip.create(label));
        context.add(button);
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int contentX = context.contentX();
        int contentTop = context.contentTop();
        int contentWidth = context.contentWidth();
        int contentHeight = Math.max(1, context.contentBottom() - contentTop);

        // Do not place an opaque fallback behind this image. Its own PNG alpha now reveals
        // the shared ACG background instead of compositing every transparent pixel as black.
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        blitOpaqueFittedTexture(graphics, CENTER_BACKGROUND,
                contentX, contentTop, contentWidth, contentHeight, 1408);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.fill(contentX, contentTop, contentX + contentWidth,
                contentTop + 24, 0x78000000);

        int centerX = contentX + contentWidth / 2;
        drawCenteredString(graphics, context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.title"),
                centerX, contentTop + 2, ACGTheme.TEXT_PRIMARY);
        drawCenteredString(graphics, context.font(),
                GeneralTextMethods.getTranslatableString(ClientQuestState.penaltyActive()
                        ? "screen.aegis_ascension.acg.quest.penalty"
                        : "screen.aegis_ascension.acg.quest.hint"),
                centerX, contentTop + 13,
                ClientQuestState.penaltyActive() ? 0xFFFF6666 : ACGTheme.TEXT_MUTED);

        PageLayout layout = layout(context);
        renderQuestList(context, graphics, layout);
        if (selectedTab == QuestTab.COMPLETE) {
            renderCompletionDetails(context, graphics, layout, selectedCompletion());
        } else {
            renderDetails(context, graphics, layout, selectedQuest());
        }
    }

    private void renderQuestList(ACGScreenContext context, GuiGraphics graphics,
                                 PageLayout layout) {
        ACGTheme.drawPanel(graphics, layout.listX(), layout.mainTop(),
                layout.listWidth(), layout.mainHeight(), 0.90F);
        boolean empty = selectedTab == QuestTab.COMPLETE
                ? ClientQuestState.completions().isEmpty()
                : ClientQuestState.byType(selectedTab.type()).isEmpty();
        if (empty) {
            drawCenteredString(graphics, context.font(),
                    GeneralTextMethods.getTranslatableString(selectedTab == QuestTab.COMPLETE
                            ? "screen.aegis_ascension.acg.quest.complete.empty"
                            : "screen.aegis_ascension.acg.quest.empty"),
                    layout.listX() + layout.listWidth() / 2,
                    layout.mainTop() + layout.mainHeight() / 2,
                    ACGTheme.TEXT_MUTED);
        }
        renderTaskScrollbar(graphics, layout);
    }

    private void renderTaskScrollbar(GuiGraphics graphics, PageLayout layout) {
        if (maxTaskScroll <= 0) return;
        int count = selectedTab == QuestTab.COMPLETE
                ? ClientQuestState.completions().size()
                : ClientQuestState.byType(selectedTab.type()).size();
        int trackTop = layout.rowsTop();
        int trackBottom = layout.mainTop() + layout.mainHeight() - 5;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(16, trackHeight * visibleRows / Math.max(1, count));
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + Math.round(thumbTravel * (taskScroll / (float) maxTaskScroll));
        int thumbX = layout.listX() + layout.listWidth() - 4;
        graphics.fill(thumbX, trackTop, thumbX + 2, trackBottom, 0x553E3428);
        graphics.fill(thumbX, thumbY, thumbX + 2, thumbY + thumbHeight, ACGTheme.GOLD_DIM);
    }

    /**
     * Mirrors the quest list's scrollbar so hidden detail is discoverable. Uses the range
     * measured on the previous frame, which is one frame stale only on the frame a quest
     * is first shown.
     */
    private void renderDetailScrollbar(GuiGraphics graphics, PageLayout layout,
                                       int viewTop, int viewBottom) {
        if (maxDetailScroll <= 0) return;
        int trackHeight = Math.max(1, viewBottom - viewTop);
        int contentHeight = trackHeight + maxDetailScroll;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = viewTop
                + Math.round(thumbTravel * (detailScroll / (float) maxDetailScroll));
        int thumbX = layout.detailX() + layout.detailWidth() - 4;
        graphics.fill(thumbX, viewTop, thumbX + 2, viewBottom, 0x553E3428);
        graphics.fill(thumbX, thumbY, thumbX + 2, thumbY + thumbHeight, ACGTheme.GOLD_DIM);
    }

    private void renderDetails(ACGScreenContext context, GuiGraphics graphics,
                               PageLayout layout, QuestView quest) {
        ACGTheme.drawPanel(graphics, layout.detailX(), layout.mainTop(),
                layout.detailWidth(), layout.mainHeight(), 0.94F);
        blitOpaqueFittedTexture(graphics, QUEST_BACKGROUND,
                layout.detailX() + 4, layout.mainTop() + 4,
                Math.max(1, layout.detailWidth() - 8),
                Math.max(1, layout.mainHeight() - 8), 256);
        if (quest == null) {
            drawCenteredString(graphics, context.font(),
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.select_prompt"),
                    layout.detailX() + layout.detailWidth() / 2,
                    layout.mainTop() + layout.mainHeight() / 2,
                    ACGTheme.TEXT_MUTED);
            return;
        }

        int x = layout.detailX() + 12;
        int width = Math.max(1, layout.detailWidth() - 24);
        // A compound quest at a small GUI scale is taller than this panel, so the body
        // scrolls and is clipped short of the action buttons rather than drawing over them.
        int viewTop = layout.mainTop() + 4;
        int viewBottom = Math.max(viewTop + 20, context.contentBottom() - 32);
        graphics.enableScissor(layout.detailX(), viewTop,
                layout.detailX() + layout.detailWidth(), viewBottom);
        int contentTop = layout.mainTop() + 10 - detailScroll;
        int y = contentTop;
        Component title = questTitle(quest);
        drawQuestIcon(graphics, quest, x, y - 2, 18, 18);
        int trackerButtonReserve = isTrackerEligible(quest) ? 34 : 0;
        String visibleTitle = ellipsize(context.font(), title.getString(),
                Math.max(1, width - 22 - trackerButtonReserve));
        graphics.drawString(context.font(), ACGTheme.asHeader(
                        GeneralTextMethods.getLiteralString(visibleTitle)),
                x + 22, y, titleColor(quest), true);

        Component status = GeneralTextMethods.getTranslatableString(statusKey(quest));
        graphics.drawString(context.font(), status, x + 22, y + 13, statusColor(quest), false);
        String tierLabel = tierLabel(quest);
        if (!tierLabel.isEmpty()) {
            graphics.drawString(context.font(),
                    GeneralTextMethods.getLiteralString(tierLabel),
                    x + 22 + context.font().width(status) + 6, y + 13,
                    GeneralConstants.rarityColor(quest.tier()), false);
        }
        if (quest.completed()) {
            blitOpaqueFittedTexture(graphics, COMPLETE_STATUS,
                    layout.detailX() + layout.detailWidth() - 34,
                    layout.mainTop() + 6, 24, 24, 64);
        } else if (quest.expired()) {
            blitOpaqueFittedTexture(graphics, FAILED_STATUS,
                    layout.detailX() + layout.detailWidth() - 94,
                    layout.mainTop() + 9, 82, 18, 136);
        }

        int lineY = y + 32;
        if (quest.type() == QuestType.SIDE && !info(quest).profession.isBlank()) {
            lineY = drawSideGiver(context, graphics, quest, x, lineY);
        }
        if (quest.type() == QuestType.SIDE && !info(quest).story.isBlank()) {
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(info(quest).story),
                    x, lineY, width, ACGTheme.TEXT_SECONDARY, 5);
            lineY += 4;
        }
        lineY = drawWrapped(context, graphics, description(quest),
                x, lineY, width, ACGTheme.TEXT_SECONDARY, 4);
        lineY += 4;
        String prerequisiteTitle = prerequisiteTitle(quest);
        if (!quest.prerequisiteMet() && !prerequisiteTitle.isBlank()) {
            Component prerequisite = GeneralTextMethods.getTranslatableString(prerequisiteTitle);
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.prerequisite", prerequisite),
                    x, lineY, width, 0xFFFF7777, 3);
            lineY += 4;
        }
        int requirementX = x;
        int requirementWidth = width;
        ItemStack targetIcon = targetIconStack(quest);
        if (!targetIcon.isEmpty()) {
            drawItemIcon(graphics, targetIcon, x, lineY - 1, 12);
            requirementX += 14;
            requirementWidth -= 14;
        }
        lineY = drawWrapped(context, graphics, requirement(quest),
                requirementX, lineY, requirementWidth, ACGTheme.GOLD_BRIGHT, 4);
        lineY += 5;

        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.progress",
                        quest.progress(), quest.target()),
                x, lineY, ACGTheme.TEXT_MUTED, false);
        float progress = quest.target() <= 0 ? 0.0F
                : Math.min(1.0F, quest.progress() / (float) quest.target());
        ACGTheme.drawProgressBar(graphics, x, lineY + 11, width, 6, progress,
                0xFF241F1A, quest.completed() ? 0xFF67D78A : ACGTheme.ORANGE_ACTION);
        lineY += 23;

        // A compound quest gets a line and a bar per requirement. One shared bar would
        // read as nearly finished while a whole other requirement was untouched.
        for (QuestView.Requirement extra : quest.requirements()) {
            int extraX = x;
            int extraWidth = width;
            ItemStack extraIcon = rewardItemStack(extra.targetId());
            if (!extraIcon.isEmpty()) {
                drawItemIcon(graphics, extraIcon, x, lineY - 1, 12);
                extraX += 14;
                extraWidth -= 14;
            }
            lineY = drawWrapped(context, graphics, extraRequirement(quest, extra),
                    extraX, lineY, extraWidth, ACGTheme.GOLD_BRIGHT, 3);
            lineY += 3;
            graphics.drawString(context.font(),
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.progress",
                            extra.progress(), extra.target()),
                    x, lineY, ACGTheme.TEXT_MUTED, false);
            float extraFraction = Math.min(1.0F, extra.progress() / (float) extra.target());
            ACGTheme.drawProgressBar(graphics, x, lineY + 11, width, 6, extraFraction,
                    0xFF241F1A, extra.progress() >= extra.target()
                            ? 0xFF67D78A : ACGTheme.ORANGE_ACTION);
            lineY += 23;
        }

        lineY = drawRewardLine(context, graphics, quest, x, lineY, width) + 5;

        if (quest.repeatable() && quest.rewardReadyAt() > 0L
                && context.minecraft().level != null) {
            long remaining = Math.max(0L,
                    quest.rewardReadyAt() - context.minecraft().level.getGameTime());
            if (remaining > 0L) {
                graphics.drawString(context.font(), GeneralTextMethods.getTranslatableString(
                                "screen.aegis_ascension.acg.quest.repeat_reward_ready_in",
                                formatTime(remaining)),
                        x, lineY, 0xFFFFB36B, false);
                lineY += 12;
            }
        }

        if (quest.expiresAt() > 0L && context.minecraft().level != null) {
            long remaining = Math.max(0L,
                    quest.expiresAt() - context.minecraft().level.getGameTime());
            String timerKey = quest.type() == QuestType.CHALLENGE
                    ? "screen.aegis_ascension.acg.quest.expires_in"
                    : "screen.aegis_ascension.acg.quest.refreshes_in";
            graphics.drawString(context.font(),
                    GeneralTextMethods.getTranslatableString(timerKey, formatTime(remaining)),
                    x, lineY, 0xFFFFB36B, false);
            lineY += 12;
        }

        // Constraints fail the quest the instant they are broken, so they have to be
        // stated on the offer; a quest that failed for an unstated reason reads as a bug.
        for (String constraint : info(quest).constraints.split(",")) {
            String key = constraint.trim();
            if (key.isEmpty()) continue;
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.constraint." + key),
                    x, lineY, width, 0xFFFF7777, 2);
            lineY += 2;
        }

        if (!quest.rewardChoices().isEmpty()) {
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.reward_choice_prompt"),
                    x, lineY, width, 0xFF67D78A, 2);
            lineY += 3;
        }

        // A stake has to be visible before the quest is accepted, so this uses the
        // amount the server resolved for this quest rather than what has been paid.
        int stake = questStake(quest);
        if (quest.type() == QuestType.CHALLENGE || stake > 0) {
            boolean gold = ClientPerkState.usesGoldCurrency();
            // Only a Challenge carries the failure penalty, so only it says so.
            String key = quest.type() == QuestType.CHALLENGE
                    ? (gold ? "screen.aegis_ascension.acg.quest.challenge_warning_gold"
                    : "screen.aegis_ascension.acg.quest.challenge_warning")
                    : (gold ? "screen.aegis_ascension.acg.quest.stake_warning_gold"
                    : "screen.aegis_ascension.acg.quest.stake_warning");
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(key, stake),
                    x, lineY, width, 0xFFFF7777, 3);
        } else if (quest.type() == QuestType.COMMON) {
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.common_cancel_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        } else if (quest.type() == QuestType.SIDE && isItemSubmission(quest)) {
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.side_submit_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        } else {
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.cancel_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        }
        graphics.disableScissor();
        renderDetailScrollbar(graphics, layout, viewTop, viewBottom);

        // Measured from what was just laid out, so the range always matches the content
        // actually drawn rather than an estimate that can drift as the panel grows.
        int contentHeight = (lineY + 10) - contentTop;
        maxDetailScroll = Math.max(0, contentHeight - (viewBottom - viewTop));
        detailScroll = Math.min(detailScroll, maxDetailScroll);
    }

    private void renderCompletionDetails(ACGScreenContext context, GuiGraphics graphics,
                                         PageLayout layout, QuestCompletionView completion) {
        ACGTheme.drawPanel(graphics, layout.detailX(), layout.mainTop(),
                layout.detailWidth(), layout.mainHeight(), 0.94F);
        blitOpaqueFittedTexture(graphics, QUEST_BACKGROUND,
                layout.detailX() + 4, layout.mainTop() + 4,
                Math.max(1, layout.detailWidth() - 8),
                Math.max(1, layout.mainHeight() - 8), 256);

        int x = layout.detailX() + 12;
        int width = Math.max(1, layout.detailWidth() - 24);
        // This panel's content is short and fixed, unlike the quest detail, so the clip
        // is mostly insurance against a very small window. It shares the detail scroll
        // state because the two panels belong to different tabs and never show together.
        int viewTop = layout.mainTop() + 4;
        int viewBottom = Math.max(viewTop + 20, context.contentBottom() - 32);
        graphics.enableScissor(layout.detailX(), viewTop,
                layout.detailX() + layout.detailWidth(), viewBottom);
        int contentTop = layout.mainTop() + 12 - detailScroll;
        int y = contentTop;
        graphics.drawString(context.font(),
                ACGTheme.asHeader(GeneralTextMethods.getTranslatableString(QuestTab.COMPLETE.titleKey())),
                x, y, ACGTheme.TEXT_PRIMARY, true);
        blitOpaqueFittedTexture(graphics, COMPLETE_STATUS,
                layout.detailX() + layout.detailWidth() - 38,
                layout.mainTop() + 6, 28, 28, 64);

        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.total",
                        ClientQuestState.totalCompleted()),
                x, y + 19, 0xFF67D78A, false);
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.total_experience",
                        ClientQuestState.experienceDisplayName(),
                        ClientQuestState.totalExperienceEarned()),
                x, y + 32, ACGTheme.CYAN_ACCENT, false);
        graphics.fill(x, y + 47, x + width, y + 48, ACGTheme.GOLD_DIM);

        // Lifetime totals: what this player has actually done, counted from every
        // qualifying event rather than from quest progress. Only objectives with a
        // total are listed, so an early save shows a short honest list.
        int statY = y + 53;
        for (Map.Entry<QuestObjective, Integer> total
                : ClientQuestState.lifetimeTotals().entrySet()) {
            graphics.drawString(context.font(),
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.lifetime."
                                    + total.getKey().name().toLowerCase(),
                            total.getValue()),
                    x, statY, ACGTheme.TEXT_SECONDARY, false);
            statY += 11;
        }
        if (statY > y + 53) {
            graphics.fill(x, statY + 2, x + width, statY + 3, ACGTheme.GOLD_DIM);
            statY += 8;
        }

        if (completion == null) {
            drawCenteredString(graphics, context.font(),
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.select_prompt"),
                    layout.detailX() + layout.detailWidth() / 2,
                    layout.mainTop() + layout.mainHeight() / 2,
                    ACGTheme.TEXT_MUTED);
            graphics.disableScissor();
            maxDetailScroll = 0;
            detailScroll = 0;
            return;
        }

        Component title = completionTitle(completion);
        drawCompletionIcon(graphics, completion, x, statY, 18, 18);
        String visibleTitle = ellipsize(context.font(), title.getString(),
                Math.max(1, width - 22));
        graphics.drawString(context.font(), ACGTheme.asHeader(
                        GeneralTextMethods.getLiteralString(visibleTitle)),
                x + 22, statY + 3, ACGTheme.TEXT_PRIMARY, true);
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.count",
                        completion.completions()),
                x, statY + 20, 0xFF67D78A, false);
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.experience",
                        ClientQuestState.experienceDisplayName(),
                        completion.experienceEarned()),
                x, statY + 34, ACGTheme.CYAN_ACCENT, false);
        graphics.disableScissor();
        renderDetailScrollbar(graphics, layout, viewTop, viewBottom);

        int contentHeight = (statY + 44) - contentTop;
        maxDetailScroll = Math.max(0, contentHeight - (viewBottom - viewTop));
        detailScroll = Math.min(detailScroll, maxDetailScroll);
    }

    private static int drawSideGiver(ACGScreenContext context, GuiGraphics graphics,
                                     QuestView quest, int x, int y) {
        String profession0 = info(quest).profession;
        drawVillagerProfessionIcon(graphics, profession0, x, y - 3);
        Component profession = GeneralTextMethods.getTranslatableString(
                "entity.minecraft.villager." + profession0.toLowerCase());
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.quest.side.requested_by", profession),
                x + 20, y + 1, ACGTheme.CYAN_ACCENT, false);

        // Standing with this villager, on its own line under their name so a long
        // profession name cannot push it off the panel.
        int reputationY = y + 14;
        ItemStack icon = rewardItemStack(ClientQuestState.reputationIcon());
        if (icon.isEmpty()) {
            blitOpaqueFittedTexture(graphics, OTHER_REWARD, x + 20, reputationY, 12, 12, 116);
        } else {
            drawItemIcon(graphics, icon, x + 20, reputationY, 12);
        }
        // Shown as current / required when the quest gates on standing, matching the
        // progress counters elsewhere in this panel. A quest with no requirement shows
        // the bare figure instead: "5 / 0" would read as a broken counter.
        int standing = professionReputation(profession0);
        int required = info(quest).minimumReputation;
        boolean met = standing >= required;
        String text = required > 0 ? ": " + standing + " / " + required
                : ": " + standing;
        graphics.drawString(context.font(), GeneralTextMethods.getLiteralString(text),
                x + 34, reputationY + 2,
                required > 0 && !met ? 0xFFFF7777 : ACGTheme.GOLD_BRIGHT, false);
        return y + 31;
    }

    /**
     * Standing with one profession, counted the same way the server counts it: every
     * completion of a quest that profession asked for. The completion list already
     * carries the profession of each finished quest, so this needs nothing from the
     * server that the client has not already been sent.
     */
    /** Whether the player's standing meets what this quest asks of them. */
    private static boolean reputationMet(QuestView quest) {
        QuestConfig.CatalogEntry entry = info(quest);
        return entry.minimumReputation <= 0
                || professionReputation(entry.profession) >= entry.minimumReputation;
    }

    private static int professionReputation(String profession) {
        if (profession == null || profession.isBlank()) return 0;
        int total = 0;
        for (QuestCompletionView completion : ClientQuestState.completions()) {
            if (profession.equals(info(completion).profession)) {
                total += Math.max(0, completion.completions());
            }
        }
        return total;
    }

    private static void drawVillagerProfessionIcon(GuiGraphics graphics, String profession,
                                                    int x, int y) {
        QuestIconRenderer.drawVillagerProfessionIcon(
                graphics, profession, x, y, 16, 1.0F);
    }

    /** Draws the quest-specific texture configured by the server's quest template. */
    private static void drawQuestIcon(GuiGraphics graphics, QuestView quest,
                                      int x, int y, int width, int height) {
        ResourceLocation icon = questIcon(quest);
        if (icon != null && GeneralClientMethods.resourceExists(icon)) {
            blitQuestIconTexture(graphics, icon, x, y, width, height);
        }
    }

    private static void drawCompletionIcon(GuiGraphics graphics,
                                           QuestCompletionView completion,
                                           int x, int y, int width, int height) {
        if (completion == null) return;
        if (!info(completion).profession.isBlank()
                && QuestIconRenderer.drawVillagerProfessionIcon(
                graphics, info(completion).profession, x, y,
                Math.min(width, height), 1.0F)) {
            return;
        }
        ResourceLocation icon = completionIcon(completion);
        if (icon != null && GeneralClientMethods.resourceExists(icon)) {
            blitQuestIconTexture(graphics, icon, x, y, width, height);
        }
    }

    private static void blitQuestIconTexture(GuiGraphics graphics, ResourceLocation icon,
                                             int x, int y, int width, int height) {
        if (CHEST_TEXTURE.equals(icon)) {
            // Vanilla's chest texture is a packed block-entity sheet; use the front body
            // face so quest rows and details show a recognizable chest icon.
            blitScaledRegion(graphics, icon, x, y, width, height,
                    14.0F, 33.0F, 14, 10, 64, 64);
        } else {
            blitOpaqueFittedTexture(graphics, icon, x, y, width, height, 199);
        }
    }

    private static ResourceLocation questIcon(QuestView quest) {
        if (quest == null) return null;
        return resolvedQuestIcon(info(quest).icon, quest.objective(), quest.type());
    }

    private static ResourceLocation completionIcon(QuestCompletionView completion) {
        if (completion == null) return null;
        QuestConfig.CatalogEntry entry = info(completion);
        return resolvedQuestIcon(entry.icon, entry.objective, entry.type);
    }

    private static ResourceLocation resolvedQuestIcon(String configuredIcon,
                                                      QuestObjective objective,
                                                      QuestType type) {
        ResourceLocation configured = ResourceLocation.tryParse(configuredIcon);
        if (configured != null && GeneralClientMethods.resourceExists(configured)) {
            return configured;
        }
        ResourceLocation objectiveIcon = objectiveIcon(objective, type);
        if (objectiveIcon != null && GeneralClientMethods.resourceExists(objectiveIcon)) {
            return objectiveIcon;
        }
        return switch (type) {
            case DAILY -> DAILY_HEADER;
            case CHALLENGE -> CHALLENGE_HEADER;
            case COMMON -> COMMON_HEADER;
            case CHUNK -> CHUNK_HEADER;
            case SIDE -> SIDE_HEADER;
        };
    }

    /** Default objective art used when a server template omits its optional icon field. */
    private static ResourceLocation objectiveIcon(QuestObjective objective, QuestType type) {
        // Crafting has no vanilla item texture of its own, so it uses the mod's stand-in.
        if (objective == QuestObjective.CRAFT_ITEM) return UNKNOWN_ICON;
        String path = switch (objective) {
            case KILL -> type == QuestType.CHALLENGE
                    ? "textures/item/diamond_sword.png" : "textures/item/golden_sword.png";
            case PLANT -> "textures/item/wheat_seeds.png";
            case OPEN_CHEST -> "textures/entity/chest/normal.png";
            case WALK -> "textures/mob_effect/speed.png";
            case BREAK_BLOCK -> "textures/item/iron_pickaxe.png";
            case SHOOT_ARROW, HIT_ARROW -> "textures/item/arrow.png";
            case REACH_LOCATION -> "textures/item/filled_map.png";
            default -> null;
        };
        return path == null ? null : GeneralClientMethods.fromNamespaceAndPath("minecraft", path);
    }

    /**
     * Lists the payout one reward per row. A single row ran out of width as soon as
     * amounts were added to it, and truncating the tail hid rewards the quest actually
     * pays; a row each also lets the icons line up as a readable column.
     *
     * @return the y coordinate below the last row drawn.
     */
    private int drawRewardLine(ACGScreenContext context, GuiGraphics graphics,
                               QuestView quest, int x, int y, int width) {
        Font font = context.font();
        Component label = GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.reward_label");
        graphics.drawString(font, label, x, y + 2, ACGTheme.CYAN_ACCENT, false);
        int rowY = y + 13;
        int rowX = x + 4;
        int textWidth = Math.max(20, width - 22);

        if (quest.experience() > 0) {
            blitOpaqueFittedTexture(graphics, OTHER_REWARD, rowX, rowY, 12, 12, 116);
            drawRewardText(graphics, font, quest.experience() + " "
                    + ClientQuestState.experienceLabel(), rowX + 14, rowY, textWidth);
            rowY += 13;
        }

        if (ClientPerkState.usesGoldCurrency() && quest.goldReward() > 0L) {
            blitOpaqueFittedTexture(graphics, GoldCurrency.ICON, rowX, rowY, 12, 12, 128);
            drawRewardText(graphics, font, quest.goldReward() + " Gold",
                    rowX + 14, rowY, textWidth);
            rowY += 13;
        }

        for (QuestRewardSummary.Entry reward : QuestRewardSummary.merge(itemRewards(quest))) {
            drawRewardItemIcon(graphics, reward.id(), rowX, rowY, 12);
            String name = rewardDisplayName(reward.id());
            drawRewardText(graphics, font,
                    reward.count() > 1 ? reward.count() + "x " + name : name,
                    rowX + 14, rowY, textWidth);
            rowY += 13;
        }

        // Alternatives are listed here too, so the payout is legible on the offer rather
        // than only once the quest is finished and the buttons appear.
        List<String> choices = quest.rewardChoices();
        if (!choices.isEmpty()) {
            graphics.drawString(font, GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.reward_choice_count",
                            choices.size()),
                    x, rowY + 2, ACGTheme.GOLD_BRIGHT, false);
            rowY += 13;
            for (String choice : choices) {
                QuestRewardSummary.Entry entry = QuestRewardSummary.parse(choice).stream()
                        .findFirst().orElse(new QuestRewardSummary.Entry(choice, 1));
                drawRewardItemIcon(graphics, entry.id(), rowX, rowY, 12);
                String name = rewardDisplayName(entry.id());
                drawRewardText(graphics, font,
                        entry.count() > 1 ? entry.count() + "x " + name : name,
                        rowX + 14, rowY, textWidth);
                rowY += 13;
            }
        }
        return rowY;
    }

    private static void drawRewardText(GuiGraphics graphics, Font font, String text,
                                       int x, int y, int maxWidth) {
        graphics.drawString(font, GeneralTextMethods.getLiteralString(
                ellipsize(font, text, maxWidth)), x, y + 2, ACGTheme.CYAN_ACCENT, false);
    }

    private static int drawWrapped(ACGScreenContext context, GuiGraphics graphics,
                                   Component text, int x, int y, int width,
                                   int color, int maxLines) {
        int drawn = 0;
        for (var line : context.font().split(text, Math.max(20, width))) {
            if (drawn >= maxLines) break;
            graphics.drawString(context.font(), line, x, y + drawn * 10, color, false);
            drawn++;
        }
        return y + drawn * 10;
    }

    @Override
    public boolean mouseScrolled(ACGScreenContext context, double mouseX,
                                 double mouseY, double delta) {
        PageLayout layout = layout(context);
        if (Math.abs(delta) <= 1.0E-9D
                || mouseY < layout.mainTop()
                || mouseY >= layout.mainTop() + layout.mainHeight()) {
            return false;
        }
        if (mouseX >= layout.detailX()
                && mouseX < layout.detailX() + layout.detailWidth()) {
            return scrollDetails(context, delta);
        }
        if (maxTaskScroll <= 0
                || mouseX < layout.listX() || mouseX >= layout.listX() + layout.listWidth()) {
            return false;
        }
        int direction = delta < 0.0D ? 1 : -1;
        int next = Math.max(0, Math.min(maxTaskScroll, taskScroll + direction));
        if (next == taskScroll) return true;
        taskScroll = next;
        context.rebuild();
        return true;
    }

    /** Scrolls the detail panel by roughly one text line per notch. */
    private boolean scrollDetails(ACGScreenContext context, double delta) {
        if (maxDetailScroll <= 0) return false;
        int step = delta < 0.0D ? DETAIL_SCROLL_STEP : -DETAIL_SCROLL_STEP;
        int next = Math.max(0, Math.min(maxDetailScroll, detailScroll + step));
        if (next == detailScroll) return true;
        detailScroll = next;
        context.rebuild();
        return true;
    }

    private void selectTab(ACGScreenContext context, QuestTab tab) {
        if (tab == selectedTab) return;
        selectedTab = tab;
        selectedQuestId = "";
        taskScroll = 0;
        detailScroll = 0;
        context.page(0);
        context.gridScroll(0);
        context.rebuild();
    }

    private void selectQuest(ACGScreenContext context, String id) {
        if (id == null || id.equals(selectedQuestId)) return;
        selectedQuestId = id;
        detailScroll = 0;
        context.rebuild();
    }

    private void ensureSelection(List<QuestView> quests) {
        if (!selectedQuestId.isBlank()
                && quests.stream().noneMatch(quest -> quest.id().equals(selectedQuestId))) {
            selectedQuestId = "";
        }
    }

    private void ensureCompletionSelection(List<QuestCompletionView> completions) {
        if (!selectedQuestId.isBlank()
                && completions.stream().noneMatch(
                completion -> completion.questId().equals(selectedQuestId))) {
            selectedQuestId = "";
        }
    }

    private QuestView selectedQuest() {
        if (selectedTab == QuestTab.COMPLETE) return null;
        for (QuestView quest : ClientQuestState.byType(selectedTab.type())) {
            if (quest.id().equals(selectedQuestId)) return quest;
        }
        return null;
    }

    private QuestCompletionView selectedCompletion() {
        if (selectedTab != QuestTab.COMPLETE) return null;
        for (QuestCompletionView completion : ClientQuestState.completions()) {
            if (completion.questId().equals(selectedQuestId)) return completion;
        }
        return null;
    }

    private void clampTaskScroll(int itemCount) {
        maxTaskScroll = Math.max(0, itemCount - visibleRows);
        taskScroll = Math.max(0, Math.min(maxTaskScroll, taskScroll));
    }

    private static void sendAction(QuestView quest, QuestActionPacket.Action action) {
        ModNetworking.sendToServer(new QuestActionPacket(quest.id(), action));
    }

    private static boolean canAccept(ACGScreenContext context, QuestView quest) {
        if (quest.accepted() || quest.completed() || quest.cancelled() || quest.expired()
                || !quest.prerequisiteMet() || !reputationMet(quest)) return false;
        return quest.type() != QuestType.CHALLENGE
                || context.minecraft().player == null
                || (ClientPerkState.usesGoldCurrency()
                ? ClientPerkState.getGoldCurrency() >= questStake(quest)
                : context.minecraft().player.totalExperience >= questStake(quest));
    }

    /**
     * Whether any item this quest asks for is still outstanding. A quest can be accepted
     * and still have nothing to hand in: it may be finished, or its submission
     * requirements may be settled while another requirement is not.
     */
    private static boolean hasOutstandingSubmission(QuestView quest) {
        if (!quest.accepted() || quest.completed() || quest.cancelled() || quest.expired()) {
            return false;
        }
        if (isSubmissionObjective(quest.objective()) && quest.progress() < quest.target()) {
            return true;
        }
        for (QuestView.Requirement requirement : quest.requirements()) {
            if (isSubmissionObjective(requirement.objective())
                    && requirement.progress() < requirement.target()) {
                return true;
            }
        }
        return false;
    }

    /**
     * What accepting this quest will actually cost. The server resolves it from the
     * template and the rarity the quest rolled at, so the screen must use that figure
     * rather than the catalogue's unscaled value or the global Challenge default; those
     * disagree, and the player was being quoted two numbers and charged a third.
     */
    private static int questStake(QuestView quest) {
        if (quest.securityDeposit() > 0) return quest.securityDeposit();
        // Only a Challenge falls back to the catalogue-wide deposit. Every other type
        // stakes nothing, and inheriting that default made ordinary quests warn about a
        // wager the server was never going to take.
        return quest.type() == QuestType.CHALLENGE ? ClientQuestState.depositCost() : 0;
    }

    private static boolean canCancel(QuestView quest) {
        return quest.accepted() && !quest.completed()
                && !quest.cancelled() && !quest.expired();
    }

    private static boolean isTrackerEligible(QuestView quest) {
        return quest.accepted() && !quest.completed()
                && !quest.cancelled() && !quest.expired();
    }

    /**
     * Whether this quest has anything to hand in, counting its extra requirements: a
     * quest whose main objective is fought or mined can still ask for materials too.
     */
    private static boolean isItemSubmission(QuestView quest) {
        if (isSubmissionObjective(quest.objective())) return true;
        for (QuestView.Requirement requirement : quest.requirements()) {
            if (isSubmissionObjective(requirement.objective())) return true;
        }
        return false;
    }

    private static boolean isSubmissionObjective(QuestObjective objective) {
        return objective == QuestObjective.TRADE_ITEM
                || objective == QuestObjective.GIVE_MATERIAL;
    }

    /**
     * Ordinary quests keep the usual heading colour; only the rarer tiers are tinted, so
     * a rare draw stands out instead of every quest carrying a rarity colour.
     */
    private static int titleColor(QuestView quest) {
        return GeneralConstants.TIER_R.equals(GeneralConstants.normalizeTier(quest.tier()))
                ? ACGTheme.TEXT_PRIMARY : GeneralConstants.rarityColor(quest.tier());
    }

    /** Blank for R, so only a rare quest is badged. */
    private static String tierLabel(QuestView quest) {
        String tier = GeneralConstants.normalizeTier(quest.tier());
        return GeneralConstants.TIER_R.equals(tier) ? "" : tier;
    }

    /**
     * One button per offered reward, shown only while a finished quest still has a choice
     * open. They sit above the accept/cancel row so the pending decision is the nearest
     * thing to hand.
     */
    private void addRewardChoiceActions(ACGScreenContext context, PageLayout layout,
                                        QuestView quest, int y) {
        List<String> choices = quest.rewardChoices();
        int available = Math.max(1, layout.detailWidth() - 24);
        int gap = 4;
        int buttonWidth = Math.max(1,
                (available - gap * (choices.size() - 1)) / choices.size());
        int startX = layout.detailX() + 12;
        for (int index = 0; index < choices.size(); index++) {
            QuestRewardSummary.Entry entry =
                    QuestRewardSummary.parse(choices.get(index)).stream().findFirst()
                            .orElse(new QuestRewardSummary.Entry(choices.get(index), 1));
            String name = entry.count() > 1
                    ? entry.count() + "x " + rewardDisplayName(entry.id())
                    : rewardDisplayName(entry.id());
            int choiceIndex = index;
            ACGButton choice = ACGButton.builder(
                            GeneralTextMethods.getLiteralString(
                                    ellipsize(context.font(), name, buttonWidth - 8)),
                            ignored -> sendRewardChoice(quest, choiceIndex))
                    .bounds(startX + index * (buttonWidth + gap), y, buttonWidth, 20)
                    .build()
                    .style(ACGButton.Style.CTA);
            context.add(choice);
        }
    }

    private void sendRewardChoice(QuestView quest, int index) {
        ModNetworking.sendToServer(new QuestActionPacket(quest.id(),
                QuestActionPacket.Action.CHOOSE_REWARD, index));
    }

    /** The server's fixed presentation for a quest, held once instead of per sync. */
    private static QuestConfig.CatalogEntry info(QuestView quest) {
        return ClientQuestCatalog.get(quest.id());
    }

    /**
     * A completed quest's title, falling back to its id so a record whose template has
     * since been removed from the catalogue still names something.
     */
    private static Component completionTitle(QuestCompletionView completion) {
        String key = info(completion).title;
        return key == null || key.isBlank()
                ? GeneralTextMethods.getLiteralString(completion.questId())
                : GeneralTextMethods.getTranslatableString(key);
    }

    /** The same fixed presentation, for a quest known only by its completion record. */
    private static QuestConfig.CatalogEntry info(QuestCompletionView completion) {
        return ClientQuestCatalog.get(completion.questId());
    }

    /** The title of the stage this quest waits on, resolved through the catalog. */
    private static String prerequisiteTitle(QuestView quest) {
        String prerequisiteId = info(quest).prerequisiteId;
        if (prerequisiteId == null || prerequisiteId.isBlank()) return "";
        String title = ClientQuestCatalog.get(prerequisiteId).title;
        // Falling back to the id keeps a chain readable even if that stage is missing.
        return title == null || title.isBlank() ? prerequisiteId : title;
    }

    private static Component questTitle(QuestView quest) {
        String titleKey = info(quest).title;
        Component title = titleKey == null || titleKey.isBlank()
                ? GeneralTextMethods.getLiteralString(quest.objective().name())
                : GeneralTextMethods.getTranslatableString(titleKey);
        return quest.repeatable()
                ? title.copy().append(GeneralTextMethods.getTranslatableString(
                "screen.aegis_ascension.acg.quest.repeat_cycle", quest.cycle()))
                : title;
    }

    /**
     * Whether finishing this quest leaves it able to be offered again.
     *
     * <p>Common quests are a fixed ladder rather than a draw: a finished rung is
     * withdrawn and never returns, so they are one-time however their template reads.
     * Every other type is drawn from a pool, where anything not retired by
     * once-per-player comes back on a later refresh.</p>
     */
    private static boolean isRepeatableOffer(QuestView quest) {
        return quest.type() != QuestType.COMMON && !info(quest).oncePerPlayer;
    }

    private static Component description(QuestView quest) {
        String descriptionKey = info(quest).description;
        Component text = descriptionKey == null || descriptionKey.isBlank()
                ? GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.no_description")
                : GeneralTextMethods.getTranslatableString(descriptionKey);
        if (!isRepeatableOffer(quest)) return text;
        return text.copy().append(GeneralTextMethods.getLiteralString(" ")).append(
                GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.quest.repeatable_marker"));
    }

    private static Component requirement(QuestView quest) {
        return requirementLine(quest.type(), quest.objective(), quest.targetId(),
                quest.target());
    }

    /**
     * Formats one requirement, whether it is the quest's main objective or an extra.
     *
     * <p>Both used to have their own copy of this, which is how a submission extra came
     * to render its raw format placeholders: only one copy knew that submission wording
     * names the item inline and therefore takes a second argument.</p>
     */
    private static Component requirementLine(QuestType type, QuestObjective objective,
                                             String targetId, int target) {
        String key = requirementKey(objective, type);
        Component name = objectiveTargetName(objective, targetId);
        if (isSubmissionObjective(objective)) {
            return GeneralTextMethods.getTranslatableString(key, target, name);
        }
        Component base = GeneralTextMethods.getTranslatableString(key, target);
        if (targetId == null || targetId.isBlank()) return base;
        return base.copy().append(GeneralTextMethods.getLiteralString(" ")).append(
                GeneralTextMethods.getTranslatableString(
                        "screen.aegis_ascension.acg.quest.requirement.target", name));
    }

    private static String requirementKey(QuestObjective objective, QuestType type) {
        return switch (objective) {
            case KILL -> "screen.aegis_ascension.acg.quest.requirement.kill";
            case PLANT -> "screen.aegis_ascension.acg.quest.requirement.plant";
            case WALK -> "screen.aegis_ascension.acg.quest.requirement.walk";
            case OPEN_CHEST -> switch (type) {
                case DAILY -> "screen.aegis_ascension.acg.quest.requirement.daily_chest";
                case CHUNK -> "screen.aegis_ascension.acg.quest.requirement.chunk_chest";
                default -> "screen.aegis_ascension.acg.quest.requirement.chest";
            };
            case EXPLORE_BIOME -> "screen.aegis_ascension.acg.quest.requirement.biome";
            case TRADE_ITEM -> "screen.aegis_ascension.acg.quest.requirement.trade";
            case GIVE_MATERIAL -> "screen.aegis_ascension.acg.quest.requirement.material";
            case CRAFT_ITEM -> "screen.aegis_ascension.acg.quest.requirement.craft";
            case BREAK_BLOCK -> "screen.aegis_ascension.acg.quest.requirement.break_block";
            case SHOOT_ARROW -> "screen.aegis_ascension.acg.quest.requirement.shoot_arrow";
            case HIT_ARROW -> "screen.aegis_ascension.acg.quest.requirement.hit_arrow";
            case REACH_LOCATION -> "screen.aegis_ascension.acg.quest.requirement.reach_location";
        };
    }

    /** The same requirement wording as the main objective, for one extra requirement. */
    private static Component extraRequirement(QuestView quest,
                                              QuestView.Requirement extra) {
        return requirementLine(quest.type(), extra.objective(), extra.targetId(),
                extra.target());
    }

    private static Component targetDisplayName(QuestView quest) {
        return objectiveTargetName(quest.objective(), quest.targetId());
    }

    private static Component objectiveTargetName(QuestObjective objective, String targetId) {
        if (targetId == null || targetId.isBlank()) return GeneralTextMethods.getEmpty();
        ResourceLocation location = ResourceLocation.tryParse(targetId);
        if (location == null) return GeneralTextMethods.getLiteralString(targetId);
        Component name = switch (objective) {
            // These name a creature, so only the entity translation key describes them.
            // Structures carry no vanilla display name, so the mod supplies one.
            case REACH_LOCATION -> GeneralTextMethods.getTranslatableString(
                    "quest.aegis_ascension.structure." + location.getNamespace() + "."
                            + location.getPath().replace('/', '.'));
            case KILL, SHOOT_ARROW, HIT_ARROW -> GeneralTextMethods.getTranslatableString(
                    "entity." + location.getNamespace() + "."
                            + location.getPath().replace('/', '.'));
            // Everything else names a thing that is held or placed. Most blocks carry a
            // matching BlockItem, so the item name is tried first and the block registry
            // covers the few that have no item form.
            default -> {
                Component itemName = itemName(location);
                yield itemName != null ? itemName : blockName(location);
            }
        };
        return name == null ? GeneralTextMethods.getLiteralString(targetId) : name;
    }

    private static Component itemName(ResourceLocation location) {
        Item item = GeneralClientMethods.resolveItem(location);
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        return stack.isEmpty() ? null : stack.getHoverName();
    }

    private static Component blockName(ResourceLocation location) {
        return BuiltInRegistries.BLOCK.containsKey(location)
                ? BuiltInRegistries.BLOCK.get(location).getName() : null;
    }

    private static Component rowLabel(QuestView quest) {
        String tierLabel = tierLabel(quest);
        Component label = GeneralTextMethods.getEmpty().append(questTitle(quest))
                .append(GeneralTextMethods.getLiteralString("  ·  "))
                .append(GeneralTextMethods.getTranslatableString(statusKey(quest)));
        return tierLabel.isEmpty() ? label
                : label.copy().append(GeneralTextMethods.getLiteralString("  ·  " + tierLabel));
    }

    private static Component completionRowLabel(QuestCompletionView completion) {
        return GeneralTextMethods.getEmpty().append(completionTitle(completion)).append(GeneralTextMethods.getLiteralString("  ·  x"))
                .append(GeneralTextMethods.getLiteralString(Integer.toString(completion.completions())));
    }

    private static String statusKey(QuestView quest) {
        if (quest.completed()) return "screen.aegis_ascension.acg.quest.status.completed";
        if (quest.expired()) return "screen.aegis_ascension.acg.quest.status.failed";
        if (quest.cancelled()) return "screen.aegis_ascension.acg.quest.status.cancelled";
        if (quest.accepted()) return "screen.aegis_ascension.acg.quest.status.active";
        if (!quest.prerequisiteMet() || !reputationMet(quest)) {
            return "screen.aegis_ascension.acg.quest.status.locked";
        }
        return "screen.aegis_ascension.acg.quest.status.available";
    }

    private static int statusColor(QuestView quest) {
        if (quest.completed()) return 0xFF67D78A;
        if (quest.expired() || quest.cancelled()) return 0xFFFF6666;
        if (quest.accepted()) return ACGTheme.CYAN_ACCENT;
        if (!quest.prerequisiteMet()) return ACGTheme.TEXT_MUTED;
        return ACGTheme.GOLD_BRIGHT;
    }

    private static List<QuestRewardSummary.Entry> itemRewards(QuestView quest) {
        String summary = quest.rewardSummary() == null ? "" : quest.rewardSummary().trim();
        if (summary.isBlank()) return List.of();
        if (quest.experience() > 0) {
            summary = QuestRewardSummary.stripPrefix(summary,
                    quest.experience() + " " + ClientQuestState.experienceLabel());
        }
        if (ClientPerkState.usesGoldCurrency() && quest.goldReward() > 0L) {
            summary = QuestRewardSummary.stripPrefix(summary, quest.goldReward() + " Gold");
        }
        return QuestRewardSummary.parse(summary);
    }

    private static void drawRewardItemIcon(GuiGraphics graphics, String id,
                                           int x, int y, int size) {
        drawItemIcon(graphics, rewardItemStack(id), x, y, size);
    }

    /** Draws the real item model used by inventory/storage UIs when one exists. */
    private static void drawItemIcon(GuiGraphics graphics, ItemStack stack,
                                     int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hasItemModel = !stack.isEmpty()
                && minecraft.getItemRenderer().getModel(stack, null, null, 0)
                != minecraft.getModelManager().getMissingModel();
        if (!hasItemModel) {
            blitOpaqueFittedTexture(graphics, OTHER_REWARD, x, y, size, size, 116);
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 116.0F);
        float scale = size / 16.0F;
        graphics.pose().scale(scale, scale, scale);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * The item shown beside a quest's requirement line. Entity targets usually have no
     * item of their own, so those quests simply render without an icon; arrows are the
     * happy exception, since the entity and the item share an id.
     */
    private static ItemStack targetIconStack(QuestView quest) {
        if (quest.targetId() == null || quest.targetId().isBlank()) return ItemStack.EMPTY;
        return rewardItemStack(quest.targetId());
    }

    private static ItemStack rewardItemStack(String id) {
        if (VirtualItems.byId(id) != null) return ItemStack.EMPTY;
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : GeneralClientMethods.resolveItem(location);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String rewardDisplayName(String id) {
        VirtualItems.Definition virtual = VirtualItems.byId(id);
        if (virtual != null) return GeneralTextMethods.getTranslatableString(virtual.nameKey()).getString();
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : GeneralClientMethods.resolveItem(location);
        if (item == null) return id;
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? id : stack.getHoverName().getString();
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        if (font.width(ellipsis) >= maxWidth) return font.plainSubstrByWidth(ellipsis, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - font.width(ellipsis)) + ellipsis;
    }

    private static String formatTime(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + remainder + "s";
        return remainder + "s";
    }

    private static int visibleRowCount(PageLayout layout) {
        int bottom = layout.mainTop() + layout.mainHeight() - 5;
        int usable = Math.max(ROW_HEIGHT, bottom - layout.rowsTop());
        return Math.max(1, usable / (ROW_HEIGHT + ROW_GAP));
    }

    private static PageLayout layout(ACGScreenContext context) {
        int contentWidth = context.contentWidth();
        int tabCount = QuestTab.values().length;
        int tabWidth = Math.max(1, Math.min(112,
                (contentWidth - TAB_GAP * (tabCount - 1)) / tabCount));
        int tabsWidth = tabWidth * tabCount + TAB_GAP * (tabCount - 1);
        int tabsX = context.contentX() + (contentWidth - tabsWidth) / 2;
        int tabsY = context.contentTop() + 27;
        int mainTop = tabsY + TAB_HEIGHT + 5;
        int mainHeight = Math.max(1, context.contentBottom() - mainTop);
        int listWidth = Math.max(72, Math.min(250, contentWidth * 35 / 100));
        if (contentWidth - listWidth - MAIN_GAP < 120) {
            listWidth = Math.max(60, contentWidth - 120 - MAIN_GAP);
        }
        int listX = context.contentX();
        int detailX = listX + listWidth + MAIN_GAP;
        int detailWidth = Math.max(1, contentWidth - listWidth - MAIN_GAP);
        int rowsTop = mainTop + 5;
        return new PageLayout(tabsX, tabsY, tabWidth, mainTop, mainHeight,
                listX, listWidth, detailX, detailWidth, rowsTop);
    }

    /** Fits visible alpha bounds without discarding the source texture's per-pixel alpha. */
    private static void blitOpaqueFittedTexture(GuiGraphics graphics, ResourceLocation texture,
                                                int boxX, int boxY, int boxWidth, int boxHeight,
                                                int fallbackSize) {
        if (boxWidth <= 0 || boxHeight <= 0) return;
        int[] textureSize = detectTextureSize(texture, fallbackSize);
        int[] bounds = detectOpaqueBounds(texture, fallbackSize);
        int sourceWidth = Math.max(1, bounds[2]);
        int sourceHeight = Math.max(1, bounds[3]);
        double scale = Math.min(boxWidth / (double) sourceWidth,
                boxHeight / (double) sourceHeight);
        int drawWidth = Math.min(boxWidth, Math.max(1, (int) Math.round(sourceWidth * scale)));
        int drawHeight = Math.min(boxHeight, Math.max(1, (int) Math.round(sourceHeight * scale)));
        int drawX = boxX + (boxWidth - drawWidth) / 2;
        int drawY = boxY + (boxHeight - drawHeight) / 2;
        blitScaledRegion(graphics, texture, drawX, drawY, drawWidth, drawHeight,
                bounds[0], bounds[1], sourceWidth, sourceHeight,
                Math.max(1, textureSize[0]), Math.max(1, textureSize[1]));
    }

    private static ResourceLocation texture(String name) {
        return GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "textures/gui/quest_ui/" + name);
    }

    private enum QuestTab {
        DAILY(QuestType.DAILY, "screen.aegis_ascension.acg.quest.daily", DAILY_HEADER),
        CHALLENGE(QuestType.CHALLENGE, "screen.aegis_ascension.acg.quest.challenge", CHALLENGE_HEADER),
        COMMON(QuestType.COMMON, "screen.aegis_ascension.acg.quest.common", COMMON_HEADER),
        CHUNK(QuestType.CHUNK, "screen.aegis_ascension.acg.quest.chunk", CHUNK_HEADER),
        SIDE(QuestType.SIDE, "screen.aegis_ascension.acg.quest.side", SIDE_HEADER),
        COMPLETE(null, "screen.aegis_ascension.acg.quest.complete.tab", DONE_HEADER);

        private final QuestType type;
        private final String titleKey;
        private final ResourceLocation texture;

        QuestTab(QuestType type, String titleKey, ResourceLocation texture) {
            this.type = type;
            this.titleKey = titleKey;
            this.texture = texture;
        }

        QuestType type() { return type; }
        String titleKey() { return titleKey; }
        ResourceLocation texture() { return texture; }
    }

    private static final class QuestTabButton extends ACGButton {
        private final ResourceLocation background;
        private final boolean selected;
        private final boolean darkText;
        private final boolean completeTab;

        private QuestTabButton(int x, int y, int width, int height, Component message,
                               ACGButton.OnPress onPress, ResourceLocation background,
                               boolean selected, boolean darkText, boolean completeTab) {
            super(x, y, width, height, message, onPress);
            this.background = background;
            this.selected = selected;
            this.darkText = darkText;
            this.completeTab = completeTab;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                    float partialTick) {
            blitOpaqueFittedTexture(graphics, background,
                    getX(), getY(), width, height, 199);
            if (completeTab) {
                blitOpaqueFittedTexture(graphics, COMPLETE_STATUS,
                        getX() + 4, getY() + 7, 16, 16, 64);
            }
            if (selected || isHoveredOrFocused()) {
                int border = selected ? ACGTheme.ORANGE_ACTION : ACGTheme.GOLD_BRIGHT;
                graphics.fill(getX(), getY(), getX() + width, getY() + 1, border);
                graphics.fill(getX(), getY() + height - 1,
                        getX() + width, getY() + height, border);
                graphics.fill(getX(), getY(), getX() + 1, getY() + height, border);
                graphics.fill(getX() + width - 1, getY(),
                        getX() + width, getY() + height, border);
            }
            Font font = Minecraft.getInstance().font;
            int textRoom = Math.max(1, width - (completeTab ? 24 : 6));
            String label = font.plainSubstrByWidth(getMessage().getString(), textRoom);
            drawCenteredString(graphics, font, label,
                    getX() + width / 2 + (completeTab ? 7 : 0),
                    getY() + (height - 8) / 2,
                    darkText ? 0xFF202428 : 0xFFF5F1E6);
            if (!active) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88101018);
            }
        }
    }

    private static final class QuestRowButton extends ACGButton {
        private final String icon;
        private final String profession;

        private QuestRowButton(int x, int y, int width, int height, Component message,
                               ACGButton.OnPress onPress, String icon, String profession) {
            super(x, y, width, height, message, onPress);
            this.icon = icon == null ? "" : icon;
            this.profession = profession == null ? "" : profession;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                    float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (!profession.isBlank()) {
                drawVillagerProfessionIcon(graphics, profession,
                        getX() + 5, getY() + (height - 16) / 2);
            } else {
                ResourceLocation configured = ResourceLocation.tryParse(icon);
                if (configured != null && GeneralClientMethods.resourceExists(configured)) {
                    blitQuestIconTexture(graphics, configured,
                            getX() + 5, getY() + (height - 16) / 2, 16, 16);
                }
            }
        }
    }

    private record PageLayout(int tabsX, int tabsY, int tabWidth,
                              int mainTop, int mainHeight,
                              int listX, int listWidth,
                              int detailX, int detailWidth,
                              int rowsTop) {
    }
}
