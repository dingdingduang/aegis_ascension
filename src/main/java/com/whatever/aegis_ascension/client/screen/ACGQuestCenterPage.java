package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.blitScaledRegion;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectOpaqueBounds;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.detectTextureSize;
import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.ClientQuestState;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.MiscLocalSettings;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.client.QuestIconRenderer;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.QuestActionPacket;
import com.whatever.aegis_ascension.quest.QuestCompletionView;
import com.whatever.aegis_ascension.quest.QuestObjective;
import com.whatever.aegis_ascension.quest.QuestType;
import com.whatever.aegis_ascension.quest.QuestView;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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
    private int taskScroll;
    private int maxTaskScroll;
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
                        completion.profession());
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
                    questIcon(quest).toString(), quest.profession());
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
                ClientQuestState.depositCost())
                : GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.accept");
        QuestActionPacket.Action primaryAction = submission
                ? QuestActionPacket.Action.SUBMIT : QuestActionPacket.Action.ACCEPT;
        ACGButton accept = ACGButton.builder(primaryLabel,
                        ignored -> sendAction(quest, primaryAction))
                .bounds(startX, y, buttonWidth, 20)
                .build()
                .style(ACGButton.Style.CTA);
        accept.active = submission || canAccept(context, quest);
        context.add(accept);

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
        int y = layout.mainTop() + 10;
        Component title = questTitle(quest);
        drawQuestIcon(graphics, quest, x, y - 2, 18, 18);
        int trackerButtonReserve = isTrackerEligible(quest) ? 34 : 0;
        String visibleTitle = ellipsize(context.font(), title.getString(),
                Math.max(1, width - 22 - trackerButtonReserve));
        graphics.drawString(context.font(), ACGTheme.asHeader(
                        GeneralTextMethods.getLiteralString(visibleTitle)),
                x + 22, y, ACGTheme.TEXT_PRIMARY, true);

        Component status = GeneralTextMethods.getTranslatableString(statusKey(quest));
        graphics.drawString(context.font(), status, x + 22, y + 13, statusColor(quest), false);
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
        if (quest.type() == QuestType.SIDE && !quest.profession().isBlank()) {
            lineY = drawSideGiver(context, graphics, quest, x, lineY);
        }
        if (quest.type() == QuestType.SIDE && !quest.story().isBlank()) {
            lineY = drawWrapped(context, graphics, GeneralTextMethods.getTranslatableString(quest.story()),
                    x, lineY, width, ACGTheme.TEXT_SECONDARY, 5);
            lineY += 4;
        }
        lineY = drawWrapped(context, graphics, description(quest),
                x, lineY, width, ACGTheme.TEXT_SECONDARY, 4);
        lineY += 4;
        if (!quest.prerequisiteMet() && !quest.prerequisiteTitle().isBlank()) {
            Component prerequisite = GeneralTextMethods.getTranslatableString(quest.prerequisiteTitle());
            lineY = drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(
                            "screen.aegis_ascension.acg.quest.prerequisite", prerequisite),
                    x, lineY, width, 0xFFFF7777, 3);
            lineY += 4;
        }
        lineY = drawWrapped(context, graphics, requirement(quest),
                x, lineY, width, ACGTheme.GOLD_BRIGHT, 4);
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

        drawRewardLine(context, graphics, quest, x, lineY, width);
        lineY += 18;

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

        if (quest.type() == QuestType.CHALLENGE) {
            drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString(ClientPerkState.usesGoldCurrency()
                                    ? "screen.aegis_ascension.acg.quest.challenge_warning_gold"
                                    : "screen.aegis_ascension.acg.quest.challenge_warning",
                            quest.securityDepositPaid() > 0
                                    ? quest.securityDepositPaid()
                                    : ClientQuestState.depositCost()),
                    x, lineY, width, 0xFFFF7777, 3);
        } else if (quest.type() == QuestType.COMMON) {
            drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.common_cancel_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        } else if (quest.type() == QuestType.SIDE && isItemSubmission(quest)) {
            drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.side_submit_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        } else {
            drawWrapped(context, graphics,
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.cancel_hint"),
                    x, lineY, width, ACGTheme.TEXT_MUTED, 3);
        }
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
        int y = layout.mainTop() + 12;
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

        if (completion == null) {
            drawCenteredString(graphics, context.font(),
                    GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.select_prompt"),
                    layout.detailX() + layout.detailWidth() / 2,
                    layout.mainTop() + layout.mainHeight() / 2,
                    ACGTheme.TEXT_MUTED);
            return;
        }

        Component title = completion.title() == null || completion.title().isBlank()
                ? GeneralTextMethods.getLiteralString(completion.questId())
                : GeneralTextMethods.getTranslatableString(completion.title());
        drawCompletionIcon(graphics, completion, x, y + 57, 18, 18);
        String visibleTitle = ellipsize(context.font(), title.getString(),
                Math.max(1, width - 22));
        graphics.drawString(context.font(), ACGTheme.asHeader(
                        GeneralTextMethods.getLiteralString(visibleTitle)),
                x + 22, y + 60, ACGTheme.TEXT_PRIMARY, true);
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.count",
                        completion.completions()),
                x, y + 77, 0xFF67D78A, false);
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.complete.experience",
                        ClientQuestState.experienceDisplayName(),
                        completion.experienceEarned()),
                x, y + 91, ACGTheme.CYAN_ACCENT, false);
    }

    private static int drawSideGiver(ACGScreenContext context, GuiGraphics graphics,
                                     QuestView quest, int x, int y) {
        drawVillagerProfessionIcon(graphics, quest.profession(), x, y - 3);
        Component profession = GeneralTextMethods.getTranslatableString(
                "entity.minecraft.villager." + quest.profession().toLowerCase());
        graphics.drawString(context.font(),
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.side.requested_by",
                        profession),
                x + 20, y + 1, ACGTheme.CYAN_ACCENT, false);
        return y + 18;
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
        if (!completion.profession().isBlank()
                && QuestIconRenderer.drawVillagerProfessionIcon(
                graphics, completion.profession(), x, y,
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
        return resolvedQuestIcon(quest.icon(), quest.objective(), quest.type());
    }

    private static ResourceLocation completionIcon(QuestCompletionView completion) {
        if (completion == null) return null;
        return resolvedQuestIcon(completion.icon(), completion.objective(), completion.type());
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
        String path = switch (objective) {
            case KILL -> type == QuestType.CHALLENGE
                    ? "textures/item/diamond_sword.png" : "textures/item/golden_sword.png";
            case PLANT -> "textures/item/wheat_seeds.png";
            case OPEN_CHEST -> "textures/entity/chest/normal.png";
            case WALK -> "textures/mob_effect/speed.png";
            default -> null;
        };
        return path == null ? null : GeneralClientMethods.fromNamespaceAndPath("minecraft", path);
    }

    private void drawRewardLine(ACGScreenContext context, GuiGraphics graphics,
                                QuestView quest, int x, int y, int width) {
        Font font = context.font();
        Component label = GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.reward_label");
        graphics.drawString(font, label, x, y + 2, ACGTheme.CYAN_ACCENT, false);
        int cursor = x + font.width(label) + 3;
        int right = x + width;

        if (quest.experience() > 0 && cursor < right) {
            blitOpaqueFittedTexture(graphics, OTHER_REWARD, cursor, y, 12, 12, 116);
            cursor += 13;
            String experience = quest.experience() + " " + ClientQuestState.experienceLabel();
            graphics.drawString(font, GeneralTextMethods.getLiteralString(experience),
                    cursor, y + 2, ACGTheme.CYAN_ACCENT, false);
            cursor += font.width(experience) + 6;
        }

        if (ClientPerkState.usesGoldCurrency() && quest.goldReward() > 0L
                && cursor < right) {
            blitOpaqueFittedTexture(graphics, GoldCurrency.ICON, cursor, y, 12, 12, 128);
            cursor += 13;
            String gold = quest.goldReward() + " Gold";
            graphics.drawString(font, GeneralTextMethods.getLiteralString(gold),
                    cursor, y + 2, ACGTheme.CYAN_ACCENT, false);
            cursor += font.width(gold) + 6;
        }

        List<String> rewardIds = itemRewardIds(quest);
        for (String rewardId : rewardIds) {
            if (cursor >= right) break;
            drawRewardItemIcon(graphics, rewardId, cursor, y, 12);
            cursor += 13;
            String name = ellipsize(font, rewardDisplayName(rewardId), right - cursor);
            graphics.drawString(font, GeneralTextMethods.getLiteralString(name),
                    cursor, y + 2, ACGTheme.CYAN_ACCENT, false);
            cursor += font.width(name) + 6;
        }
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
        if (maxTaskScroll <= 0 || Math.abs(delta) <= 1.0E-9D
                || mouseX < layout.listX() || mouseX >= layout.listX() + layout.listWidth()
                || mouseY < layout.mainTop()
                || mouseY >= layout.mainTop() + layout.mainHeight()) {
            return false;
        }
        int direction = delta < 0.0D ? 1 : -1;
        int next = Math.max(0, Math.min(maxTaskScroll, taskScroll + direction));
        if (next == taskScroll) return true;
        taskScroll = next;
        context.rebuild();
        return true;
    }

    private void selectTab(ACGScreenContext context, QuestTab tab) {
        if (tab == selectedTab) return;
        selectedTab = tab;
        selectedQuestId = "";
        taskScroll = 0;
        context.page(0);
        context.gridScroll(0);
        context.rebuild();
    }

    private void selectQuest(ACGScreenContext context, String id) {
        if (id == null || id.equals(selectedQuestId)) return;
        selectedQuestId = id;
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
                || !quest.prerequisiteMet()) return false;
        return quest.type() != QuestType.CHALLENGE
                || context.minecraft().player == null
                || (ClientPerkState.usesGoldCurrency()
                ? ClientPerkState.getGoldCurrency() >= ClientQuestState.depositCost()
                : context.minecraft().player.totalExperience >= ClientQuestState.depositCost());
    }

    private static boolean canCancel(QuestView quest) {
        return quest.accepted() && !quest.completed()
                && !quest.cancelled() && !quest.expired();
    }

    private static boolean isTrackerEligible(QuestView quest) {
        return quest.accepted() && !quest.completed()
                && !quest.cancelled() && !quest.expired();
    }

    private static boolean isItemSubmission(QuestView quest) {
        return quest.objective() == QuestObjective.TRADE_ITEM
                || quest.objective() == QuestObjective.GIVE_MATERIAL;
    }

    private static Component questTitle(QuestView quest) {
        Component title = quest.title() == null || quest.title().isBlank()
                ? GeneralTextMethods.getLiteralString(quest.objective().name())
                : GeneralTextMethods.getTranslatableString(quest.title());
        return quest.repeatable()
                ? title.copy().append(GeneralTextMethods.getTranslatableString(
                "screen.aegis_ascension.acg.quest.repeat_cycle", quest.cycle()))
                : title;
    }

    private static Component description(QuestView quest) {
        return quest.description() == null || quest.description().isBlank()
                ? GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.no_description")
                : GeneralTextMethods.getTranslatableString(quest.description());
    }

    private static Component requirement(QuestView quest) {
        String key = switch (quest.objective()) {
            case KILL -> "screen.aegis_ascension.acg.quest.requirement.kill";
            case PLANT -> "screen.aegis_ascension.acg.quest.requirement.plant";
            case WALK -> "screen.aegis_ascension.acg.quest.requirement.walk";
            case OPEN_CHEST -> switch (quest.type()) {
                case DAILY -> "screen.aegis_ascension.acg.quest.requirement.daily_chest";
                case CHUNK -> "screen.aegis_ascension.acg.quest.requirement.chunk_chest";
                default -> "screen.aegis_ascension.acg.quest.requirement.chest";
            };
            case EXPLORE_BIOME -> "screen.aegis_ascension.acg.quest.requirement.biome";
            case TRADE_ITEM -> "screen.aegis_ascension.acg.quest.requirement.trade";
            case GIVE_MATERIAL -> "screen.aegis_ascension.acg.quest.requirement.material";
        };
        Component target = targetDisplayName(quest);
        if (isItemSubmission(quest)) {
            return GeneralTextMethods.getTranslatableString(key, quest.target(), target);
        }
        Component base = GeneralTextMethods.getTranslatableString(key, quest.target());
        if (quest.targetId() == null || quest.targetId().isBlank()) return base;
        return base.copy().append(GeneralTextMethods.getLiteralString(" ")).append(
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.requirement.target",
                        target));
    }

    private static Component targetDisplayName(QuestView quest) {
        if (quest.targetId() == null || quest.targetId().isBlank()) return GeneralTextMethods.getEmpty();
        ResourceLocation location = ResourceLocation.tryParse(quest.targetId());
        if (location == null) return GeneralTextMethods.getLiteralString(quest.targetId());
        if (isItemSubmission(quest) || quest.objective() == QuestObjective.PLANT) {
            Item item = GeneralClientMethods.resolveItem(location);
            ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
            if (!stack.isEmpty()) return stack.getHoverName();
        }
        if (quest.objective() == QuestObjective.KILL) {
            return GeneralTextMethods.getTranslatableString("entity." + location.getNamespace() + "."
                    + location.getPath().replace('/', '.'));
        }
        return GeneralTextMethods.getLiteralString(quest.targetId());
    }

    private static Component rowLabel(QuestView quest) {
        return GeneralTextMethods.getEmpty().append(questTitle(quest)).append(GeneralTextMethods.getLiteralString("  ·  "))
                .append(GeneralTextMethods.getTranslatableString(statusKey(quest)));
    }

    private static Component completionRowLabel(QuestCompletionView completion) {
        Component title = completion.title() == null || completion.title().isBlank()
                ? GeneralTextMethods.getLiteralString(completion.questId())
                : GeneralTextMethods.getTranslatableString(completion.title());
        return GeneralTextMethods.getEmpty().append(title).append(GeneralTextMethods.getLiteralString("  ·  x"))
                .append(GeneralTextMethods.getLiteralString(Integer.toString(completion.completions())));
    }

    private static String statusKey(QuestView quest) {
        if (quest.completed()) return "screen.aegis_ascension.acg.quest.status.completed";
        if (quest.expired()) return "screen.aegis_ascension.acg.quest.status.failed";
        if (quest.cancelled()) return "screen.aegis_ascension.acg.quest.status.cancelled";
        if (quest.accepted()) return "screen.aegis_ascension.acg.quest.status.active";
        if (!quest.prerequisiteMet()) return "screen.aegis_ascension.acg.quest.status.locked";
        return "screen.aegis_ascension.acg.quest.status.available";
    }

    private static int statusColor(QuestView quest) {
        if (quest.completed()) return 0xFF67D78A;
        if (quest.expired() || quest.cancelled()) return 0xFFFF6666;
        if (quest.accepted()) return ACGTheme.CYAN_ACCENT;
        if (!quest.prerequisiteMet()) return ACGTheme.TEXT_MUTED;
        return ACGTheme.GOLD_BRIGHT;
    }

    private static List<String> itemRewardIds(QuestView quest) {
        String summary = quest.rewardSummary() == null ? "" : quest.rewardSummary().trim();
        if (summary.isBlank()) return List.of();
        if (quest.experience() > 0) {
            String experiencePrefix = quest.experience() + " "
                    + ClientQuestState.experienceLabel();
            if (summary.equals(experiencePrefix)) return List.of();
            String itemPrefix = experiencePrefix + ", ";
            if (summary.startsWith(itemPrefix)) summary = summary.substring(itemPrefix.length());
        }
        if (ClientPerkState.usesGoldCurrency() && quest.goldReward() > 0L) {
            String goldPrefix = quest.goldReward() + " Gold";
            if (summary.equals(goldPrefix)) return List.of();
            String itemPrefix = goldPrefix + ", ";
            if (summary.startsWith(itemPrefix)) summary = summary.substring(itemPrefix.length());
        }
        return List.of(summary.split(",")).stream()
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList();
    }

    /** Draws the real item model used by inventory/storage UIs when one exists. */
    private static void drawRewardItemIcon(GuiGraphics graphics, String id,
                                           int x, int y, int size) {
        ItemStack stack = rewardItemStack(id);
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
