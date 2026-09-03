package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.sound.ClientSoundServices;
import com.whatever.aegis_ascension.quest.QuestConfig;
import com.whatever.aegis_ascension.quest.QuestView;
import com.whatever.aegis_ascension.network.SyncQuestDataPacket;
import com.whatever.aegis_ascension.quest.QuestType;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.quest.QuestObjective;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only HUD list of accepted quests. The server remains the source of truth; this
 * class only filters and renders the quest mirror received in {@code SyncQuestDataPacket}.
 */
@Mod.EventBusSubscriber(
        modid = AegisAscensionMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class QuestTrackerOverlay {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_MARGIN = 8;
    private static final int ROW_GAP = 2;
    private static final int MAX_DESCRIPTION_LINES = 3;
    private static final int BACKGROUND_COLOR = 0x70000000;
    private static final int HEADER_TEXT_COLOR = 0xFFAAAAAA;
    private static final int TITLE_TEXT_COLOR = 0xFFFFFF55;
    private static final int DESCRIPTION_TEXT_COLOR = 0xFFDDDDDD;
    private static final float TITLE_SCALE = 0.75F;
    private static final float DESCRIPTION_SCALE = 0.75F;
    private static final float PROGRESS_SCALE = 0.60F;
    private static final float ICON_SCALE = 0.65F;
    private static final int ICON_SIZE = Math.round(16.0F * ICON_SCALE);
    private static final int TITLE_LEFT_OFFSET = ICON_SIZE + 5;
    private static final int DESCRIPTION_EXTRA_TOP = 2;
    private static final int SWEEP_MILLIS = 450;
    private static final int HOLD_MILLIS = 200;
    private static final int FADE_MILLIS = 350;
    private static final int COLLAPSE_MILLIS = 250;
    private static final int ENTER_MILLIS = 200;
    private static final int ENTER_OFFSET = 18;
    private static final int COMPLETION_GREEN = 0xFF67D78A;

    private static final ResourceLocation COMPLETION_EFFECT =
            GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "textures/gui/quest_ui/quest_complete_effect.png");
    private static final ResourceLocation CHEST_TEXTURE =
            GeneralClientMethods.fromNamespaceAndPath("minecraft", "textures/entity/chest/normal.png");

    private static int trackerPage;
    private static int completionReturnPage = -1;
    private static final List<CompletionEffect> completionEffects = new ArrayList<>();
    private static final Map<String, RowMotion> rowMotions = new HashMap<>();

    private QuestTrackerOverlay() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("quest_tracker", QuestTrackerOverlay::render);
    }

    /**
     * Flips the overlay on or off and remembers it. Held in the local settings file
     * rather than a static field so the choice survives a restart: a player who turns
     * the tracker off does not want it back every time they launch the game.
     */
    public static void toggleVisibility() {
        MiscLocalSettings.get().setQuestTrackerOverlayShown(!visible());
    }

    public static boolean visible() {
        return MiscLocalSettings.get().isQuestTrackerOverlayShown();
    }

    /** Requests the next accepted-quest page; render-time clamping handles list changes. */
    public static void advancePage() {
        long current = now();
        pruneEffects(current);
        boolean entering = rowMotions.values().stream().anyMatch(motion ->
                motion.enterStartedAt >= 0L
                        && current - motion.enterStartedAt < ENTER_MILLIS);
        if (!completionEffects.isEmpty() || entering) return;
        trackerPage++;
    }

    /** Detects active-to-complete transitions before the regular client quest mirror updates. */
    public static void onQuestData(SyncQuestDataPacket packet) {
        Map<String, QuestView> previous = new HashMap<>();
        for (QuestView quest : ClientQuestState.quests()) {
            previous.put(quest.id(), quest);
        }
        boolean completedAny = false;
        for (QuestView quest : packet.quests()) {
            QuestView old = previous.get(quest.id());
            if (old == null || !isActive(old) || !quest.completed()) {
                continue;
            }
            completedAny = true;
            if (hasCompletionEffect(quest.id())) continue;
            boolean wasTrackerVisible = MiscLocalSettings.get()
                    .isQuestTrackerVisible(old.id());
            int row = activeQuests(ClientQuestState.quests()).indexOf(old);
            // Visibility is local to the current active quest. A future refresh or
            // repeatable cycle with the same id starts visible again by default.
            MiscLocalSettings.get().setQuestTrackerVisible(old.id(), true);
            if (!wasTrackerVisible) continue;
            if (completionEffects.isEmpty()) completionReturnPage = trackerPage;
            // The timer starts only after render selects the page containing this row.
            completionEffects.add(new CompletionEffect(quest, Math.max(0, row)));
        }
        // One sound per synchronization batch avoids several overlapping copies when
        // a single action completes Daily, Common, and Chunk objectives together.
        if (completedAny) {
            ClientSoundServices.playUiSound(packet.questCompleteSound());
        }
    }

    public static void clear() {
        completionEffects.clear();
        rowMotions.clear();
        // Visibility is deliberately not reset here. It is a saved preference now, and
        // leaving a world should not turn the tracker back on for the next one.
        trackerPage = 0;
        completionReturnPage = -1;
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible() || minecraft.options.hideGui || minecraft.player == null
                || (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen))) {
            return;
        }

        long current = now();
        pruneEffects(current);
        List<QuestView> quests = activeQuests(ClientQuestState.quests());
        if (quests.isEmpty() && completionEffects.isEmpty()) {
            trackerPage = 0;
            completionReturnPage = -1;
            rowMotions.clear();
            return;
        }
        List<DisplayQuest> displayedQuests = displayedQuests(quests);
        rowMotions.keySet().removeIf(id -> displayedQuests.stream()
                .noneMatch(displayed -> displayed.quest().id().equals(id)));
        prepareRowTransitions(displayedQuests, current);

        ClientSettings settings = ClientSettings.get();
        float requestedScale = (float) Math.max(ClientSettings.MIN_QUEST_TRACKER_SCALE,
                Math.min(ClientSettings.MAX_QUEST_TRACKER_SCALE, settings.questTrackerScale));
        int availableWidth = Math.max(1, screenWidth - PANEL_MARGIN * 2);
        // Keep the scaled panel on-screen even when the client uses a small window.
        float scale = Math.min(requestedScale,
                Math.max(0.25F, availableWidth / (float) PANEL_WIDTH));
        int panelWidth = PANEL_WIDTH;
        int availableHeight = Math.max(1,
                (int) Math.floor((screenHeight - PANEL_MARGIN * 2) / scale));

        Font font = minecraft.font;
        int headerHeight = font.lineHeight + 2;
        int questLimit = Math.max(ClientSettings.MIN_QUEST_TRACKER_QUEST_LIMIT,
                Math.min(ClientSettings.MAX_QUEST_TRACKER_QUEST_LIMIT,
                        settings.questTrackerQuestLimit));
        List<List<DisplayQuest>> pages = paginate(font, displayedQuests, panelWidth,
                availableHeight, headerHeight, questLimit);
        int pageCount = Math.max(1, pages.size());
        CompletionEffect activeEffect = activeCompletionEffect();
        if (activeEffect != null) {
            int effectPage = pageContaining(pages, activeEffect);
            if (effectPage >= 0) {
                trackerPage = effectPage;
                activeEffect.start(current);
            }
        } else if (completionReturnPage >= 0) {
            trackerPage = completionReturnPage;
            completionReturnPage = -1;
        }
        trackerPage = Math.floorMod(trackerPage, pageCount);
        List<DisplayQuest> pageQuests = pages.isEmpty()
                ? List.of() : pages.get(trackerPage);
        int panelHeight = trackerPanelHeight(font, pageQuests, headerHeight,
                panelWidth, current);
        int scaledPanelWidth = Math.round(panelWidth * scale);
        int scaledPanelHeight = Math.round(panelHeight * scale);
        int panelX = anchorX(settings.questTrackerHudAnchor,
                screenWidth, scaledPanelWidth) + settings.questTrackerHudOffsetX;
        int panelY = anchorY(settings.questTrackerHudAnchor,
                screenHeight, scaledPanelHeight) + settings.questTrackerHudOffsetY;

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        graphics.fill(0, 0, panelWidth, headerHeight, BACKGROUND_COLOR);
        graphics.drawString(font,
                GeneralTextMethods.getTranslatableString("screen.aegis_ascension.quest_tracker.title"),
                2, 2, HEADER_TEXT_COLOR, false);
        if (pageCount > 1) {
            Component pageLabel = GeneralTextMethods.getTranslatableString(
                    "screen.aegis_ascension.quest_tracker.page",
                    trackerPage + 1, pageCount);
            graphics.drawString(font, pageLabel,
                    panelWidth - font.width(pageLabel) - 2, 2,
                    HEADER_TEXT_COLOR, false);
        }

        float targetRowY = headerHeight + ROW_GAP;
        for (DisplayQuest displayed : pageQuests) {
            QuestRowLayout layout = layout(font, displayed.quest(), panelWidth);
            CompletionEffect effect = displayed.effect();
            RowMotion motion = updateRowMotion(displayed.quest().id(), targetRowY,
                    effect != null);
            float alpha = 1.0F;
            float sweep = 0.0F;
            if (effect != null) {
                long elapsed = Math.max(0L, current - effect.startedAt());
                sweep = completionSweep(elapsed);
                alpha = completionAlpha(elapsed);
            }

            int drawX = 0;
            if (motion.enterStartedAt >= 0L) {
                float enterProgress = clamp01((current - motion.enterStartedAt)
                        / (float) ENTER_MILLIS);
                float enterEase = easeOutCubic(enterProgress);
                drawX = Math.round(ENTER_OFFSET * (1.0F - enterEase));
                alpha *= enterEase;
                if (enterProgress >= 1.0F) motion.enterStartedAt = -1L;
            }

            int drawY = Math.round(motion.y);
            if (alpha > 0.001F) {
                drawQuestRow(graphics, font, displayed.quest(), layout,
                        drawX, drawY, panelWidth, alpha);
                if (effect != null) {
                    drawCompletionSweep(graphics, drawX, drawY, panelWidth,
                            layout.height(), sweep, alpha);
                }
            }

            float occupancy = displayedOccupancy(displayed, current);
            targetRowY += (layout.advanceHeight() + ROW_GAP) * occupancy;
        }
        graphics.pose().popPose();
    }

    private static void drawQuestRow(GuiGraphics graphics, Font font, QuestView quest,
                                     QuestRowLayout layout, int x, int y,
                                     int width, float alpha) {
        graphics.fill(x, y, x + width, y + layout.height(),
                color(BACKGROUND_COLOR, alpha));
        drawQuestIcon(graphics, quest, x + 2, y + 2, ICON_SIZE, alpha);

        String progress = progressText(quest);
        boolean showProgress = quest.target() > 1 || quest.completed();
        int progressVisualWidth = showProgress
                ? Math.round(font.width(progress) * PROGRESS_SCALE) : 0;
        int progressReserve = progressVisualWidth > 0 ? 20 : 0;
        int titleVisualWidth = Math.max(1,
                width - TITLE_LEFT_OFFSET - 2 - progressReserve);
        int titleTextWidth = Math.max(1, (int) Math.floor(titleVisualWidth / TITLE_SCALE));
        String rowTitle = ellipsize(font, title(quest).getString(), titleTextWidth);
        drawScaledString(graphics, font, rowTitle, x + TITLE_LEFT_OFFSET, y + 3,
                TITLE_SCALE, color(TITLE_TEXT_COLOR, alpha));
        if (showProgress) {
            drawScaledString(graphics, font, progress,
                    x + width - progressVisualWidth - 2, y + 2,
                    PROGRESS_SCALE, color(quest.completed()
                            ? COMPLETION_GREEN : TITLE_TEXT_COLOR, alpha));
        }

        int lineY = y + layout.descriptionTopOffset();
        for (int line = 0; line < layout.descriptionLines().size(); line++) {
            drawScaledString(graphics, font, layout.descriptionLines().get(line),
                    x + 2, lineY + line * layout.descriptionLineHeight(),
                    DESCRIPTION_SCALE, color(DESCRIPTION_TEXT_COLOR, alpha));
            if (layout.truncated() && line == MAX_DESCRIPTION_LINES - 1) {
                int ellipsisWidth = Math.round(font.width("...") * DESCRIPTION_SCALE);
                drawScaledString(graphics, font, "...", x + width - ellipsisWidth - 2,
                        lineY + line * layout.descriptionLineHeight(),
                        DESCRIPTION_SCALE, color(DESCRIPTION_TEXT_COLOR, alpha));
            }
        }
    }

    private static void drawQuestIcon(GuiGraphics graphics, QuestView quest,
                                      int x, int y, int size, float alpha) {
        if (quest.type() == QuestType.SIDE
                && !ClientQuestCatalog.get(quest.id()).profession.isBlank()
                && QuestIconRenderer.drawVillagerProfessionIcon(
                graphics, ClientQuestCatalog.get(quest.id()).profession,
                x, y, size, alpha)) {
            return;
        }
        ResourceLocation icon = icon(quest);
        if (icon == null || !GeneralClientMethods.resourceExists(icon)) return;
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        if (CHEST_TEXTURE.equals(icon)) {
            GeneralClientMethods.blitScaledRegion(graphics, icon, x, y,
                    size, size, 14.0F, 33.0F, 14, 10, 64, 64);
        } else {
            GeneralClientMethods.blitFittedTexture(graphics, icon, x, y,
                    size, size, 16);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static QuestRowLayout layout(Font font, QuestView quest, int width) {
        int descriptionWidth = Math.max(1,
                (int) Math.floor((width - 4) / DESCRIPTION_SCALE));
        List<FormattedCharSequence> wrapped = font.split(description(quest), descriptionWidth);
        int visibleLines = Math.min(MAX_DESCRIPTION_LINES, wrapped.size());
        List<FormattedCharSequence> descriptionLines = new ArrayList<>(
                wrapped.subList(0, visibleLines));
        float titleBlockHeight = font.lineHeight * TITLE_SCALE + 3.0F;
        float descriptionBlockHeight = font.lineHeight * DESCRIPTION_SCALE + 3.0F;
        int descriptionTopOffset = 3 + (int) titleBlockHeight
                + DESCRIPTION_EXTRA_TOP;
        int descriptionLineHeight = (int) descriptionBlockHeight;
        int advanceHeight = descriptionTopOffset + descriptionLineHeight * visibleLines;
        return new QuestRowLayout(descriptionLines, Math.max(1, advanceHeight),
                Math.max(1, advanceHeight), descriptionTopOffset,
                descriptionLineHeight, wrapped.size() > visibleLines);
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, String text,
                                         int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaledString(GuiGraphics graphics, Font font,
                                         FormattedCharSequence text, int x, int y,
                                         float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawCompletionSweep(GuiGraphics graphics, int x, int y,
                                             int width, int height, float sweep,
                                             float alpha) {
        if (!GeneralClientMethods.resourceExists(COMPLETION_EFFECT)
                || sweep <= 0.0F || width <= 0 || height <= 0) return;
        int[] size = GeneralClientMethods.detectTextureSize(COMPLETION_EFFECT, 256);
        int[] bounds = GeneralClientMethods.detectOpaqueBounds(COMPLETION_EFFECT, 256);
        int sourceWidth = Math.max(1, bounds[2]);
        int visibleWidth = Math.max(1,
                Math.min(width, Math.round(width * Math.min(1.0F, sweep))));
        int visibleSourceWidth = Math.max(1,
                Math.min(sourceWidth,
                        Math.round(sourceWidth * Math.min(1.0F, sweep))));
        int drawX = x + width - visibleWidth;
        float sourceX = bounds[0] + sourceWidth - visibleSourceWidth;

        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        GeneralClientMethods.blitScaledRegion(graphics, COMPLETION_EFFECT,
                drawX, y, visibleWidth, height, sourceX, bounds[1],
                visibleSourceWidth, Math.max(1, bounds[3]),
                Math.max(1, size[0]), Math.max(1, size[1]));
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<QuestView> activeQuests(List<QuestView> source) {
        List<QuestView> result = new ArrayList<>();
        MiscLocalSettings localSettings = MiscLocalSettings.get();
        for (QuestView quest : source) {
            if (isActive(quest) && localSettings.isQuestTrackerVisible(quest.id())) {
                result.add(quest);
            }
        }
        return result;
    }

    /** Keeps a newly completed quest in its former list position for its full animation. */
    private static List<DisplayQuest> displayedQuests(List<QuestView> activeQuests) {
        List<DisplayQuest> result = new ArrayList<>();
        for (QuestView quest : activeQuests) result.add(new DisplayQuest(quest, null));

        CompletionEffect effect = activeCompletionEffect();
        if (effect == null) return result;
        int existingIndex = -1;
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).quest().id().equals(effect.quest().id())) {
                existingIndex = index;
                break;
            }
        }
        // Repeatable quests restart under the same stable id. Keep drawing the
        // completed cycle until its sweep/fade finishes, then reveal the active one.
        if (existingIndex >= 0) {
            result.set(existingIndex, new DisplayQuest(effect.quest(), effect));
            return result;
        }
        int index = Math.max(0, Math.min(effect.rowIndex(), result.size()));
        result.add(index, new DisplayQuest(effect.quest(), effect));
        return result;
    }

    private static CompletionEffect activeCompletionEffect() {
        return completionEffects.isEmpty() ? null : completionEffects.get(0);
    }

    private static int pageContaining(List<List<DisplayQuest>> pages,
                                      CompletionEffect target) {
        for (int page = 0; page < pages.size(); page++) {
            for (DisplayQuest displayed : pages.get(page)) {
                if (displayed.effect() == target) return page;
            }
        }
        return -1;
    }

    /**
     * Splits the tracker by the configured quest limit and, on small windows, by the
     * actual vertical space available. This guarantees every accepted quest remains
     * reachable with the next-page binding instead of becoming an unselectable "+more" row.
     */
    private static List<List<DisplayQuest>> paginate(Font font,
                                                     List<DisplayQuest> quests,
                                                     int panelWidth,
                                                     int availableHeight,
                                                     int headerHeight,
                                                     int questLimit) {
        List<List<DisplayQuest>> pages = new ArrayList<>();
        List<DisplayQuest> currentPage = new ArrayList<>();
        int availableRowsHeight = Math.max(1,
                availableHeight - headerHeight - ROW_GAP);
        int usedHeight = 0;

        for (DisplayQuest quest : quests) {
            QuestRowLayout row = layout(font, quest.quest(), panelWidth);
            int rowHeight = Math.max(row.height(), row.advanceHeight());
            int requiredHeight = currentPage.isEmpty()
                    ? rowHeight : ROW_GAP + rowHeight;
            if (!currentPage.isEmpty()
                    && (currentPage.size() >= questLimit
                    || usedHeight + requiredHeight > availableRowsHeight)) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                usedHeight = 0;
                requiredHeight = rowHeight;
            }
            currentPage.add(quest);
            usedHeight += requiredHeight;
        }
        if (!currentPage.isEmpty()) pages.add(currentPage);
        return pages;
    }

    private static int trackerPanelHeight(Font font, List<DisplayQuest> quests,
                                          int headerHeight, int panelWidth,
                                          long current) {
        float height = headerHeight;
        if (quests.isEmpty()) return headerHeight;
        height += ROW_GAP;
        for (int index = 0; index < quests.size(); index++) {
            DisplayQuest displayed = quests.get(index);
            QuestRowLayout row = layout(font, displayed.quest(), panelWidth);
            float occupancy = displayedOccupancy(displayed, current);
            height += Math.max(row.height(), row.advanceHeight()) * occupancy;
            if (index + 1 < quests.size()) height += ROW_GAP * occupancy;
        }
        return Math.max(headerHeight, Math.round(height));
    }

    private static int anchorX(ClientSettings.HudAnchor anchor,
                               int screenWidth, int elementWidth) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> 0;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - elementWidth;
            case CENTER -> (screenWidth - elementWidth) / 2;
        };
    }

    private static int anchorY(ClientSettings.HudAnchor anchor,
                               int screenHeight, int elementHeight) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> 0;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - elementHeight;
            case CENTER -> (screenHeight - elementHeight) / 2;
        };
    }

    private static boolean isActive(QuestView quest) {
        return quest.accepted() && !quest.completed() && !quest.cancelled() && !quest.expired();
    }

    private static boolean hasCompletionEffect(String id) {
        for (CompletionEffect effect : completionEffects) {
            if (effect.quest().id().equals(id)) return true;
        }
        return false;
    }

    private static void pruneEffects(long current) {
        CompletionEffect effect = activeCompletionEffect();
        if (effect != null && effect.started()
                && current - effect.startedAt() >= completionDuration()) {
            completionEffects.remove(0);
        }
    }

    private static void prepareRowTransitions(List<DisplayQuest> quests, long current) {
        for (DisplayQuest displayed : quests) {
            RowMotion motion = rowMotions.get(displayed.quest().id());
            if (motion == null) continue;
            boolean completing = displayed.effect() != null;
            if (completing) {
                motion.enterStartedAt = -1L;
            } else if (motion.wasCompletion) {
                // Endless Common quests restart under the same stable id after the
                // completed cycle disappears. Their replacement gets a fresh entrance.
                motion.enterStartedAt = current;
            }
            motion.wasCompletion = completing;
        }
    }

    private static RowMotion updateRowMotion(String questId, float targetY,
                                             boolean completing) {
        RowMotion motion = rowMotions.get(questId);
        if (motion == null) {
            motion = new RowMotion(targetY, completing);
            rowMotions.put(questId, motion);
        }
        // targetY already follows the cubic collapse curve, so assigning it directly
        // preserves that exact easing and avoids compounding it with frame-rate drift.
        motion.y = targetY;
        motion.wasCompletion = completing;
        return motion;
    }

    private static float displayedOccupancy(DisplayQuest displayed, long current) {
        CompletionEffect effect = displayed.effect();
        if (effect != null) {
            return completionOccupancy(Math.max(0L, current - effect.startedAt()));
        }
        RowMotion motion = rowMotions.get(displayed.quest().id());
        if (motion == null || motion.enterStartedAt < 0L) return 1.0F;
        float progress = clamp01((current - motion.enterStartedAt) / (float) ENTER_MILLIS);
        return easeInOutCubic(progress);
    }

    private static float completionSweep(long elapsed) {
        return easeOutCubic(clamp01(elapsed / (float) SWEEP_MILLIS));
    }

    private static float completionAlpha(long elapsed) {
        long fadeStart = SWEEP_MILLIS + HOLD_MILLIS;
        if (elapsed <= fadeStart) return 1.0F;
        return 1.0F - easeInOutCubic(clamp01(
                (elapsed - fadeStart) / (float) FADE_MILLIS));
    }

    private static float completionOccupancy(long elapsed) {
        long collapseStart = SWEEP_MILLIS + HOLD_MILLIS + FADE_MILLIS;
        if (elapsed <= collapseStart) return 1.0F;
        return 1.0F - easeInOutCubic(clamp01(
                (elapsed - collapseStart) / (float) COLLAPSE_MILLIS));
    }

    private static long completionDuration() {
        return (long) SWEEP_MILLIS + HOLD_MILLIS + FADE_MILLIS + COLLAPSE_MILLIS;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInOutCubic(float value) {
        return value < 0.5F
                ? 4.0F * value * value * value
                : 1.0F - (float) Math.pow(-2.0F * value + 2.0F, 3.0D) / 2.0F;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static Component title(QuestView quest) {
        String titleKey = ClientQuestCatalog.get(quest.id()).title;
        Component title = titleKey == null || titleKey.isBlank()
                ? GeneralTextMethods.getLiteralString(quest.objective().name())
                : GeneralTextMethods.getTranslatableString(titleKey,
                        professionName(quest));
        return quest.repeatable()
                ? title.copy().append(GeneralTextMethods.getTranslatableString(
                "screen.aegis_ascension.acg.quest.repeat_cycle", quest.cycle()))
                : title;
    }

    private static Component description(QuestView quest) {
        String descriptionKey = ClientQuestCatalog.get(quest.id()).description;
        return descriptionKey == null || descriptionKey.isBlank()
                ? GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.quest.no_description")
                : GeneralTextMethods.getTranslatableString(descriptionKey,
                        professionName(quest));
    }

    private static String progressText(QuestView quest) {
        return quest.completed() ? "\u2713" : quest.progress() + "/" + quest.target();
    }

    private static ResourceLocation icon(QuestView quest) {
        ResourceLocation configured = ResourceLocation.tryParse(
                ClientQuestCatalog.get(quest.id()).icon);
        if (configured != null && GeneralClientMethods.resourceExists(configured)) {
            return configured;
        }
        ResourceLocation objectiveIcon = objectiveIcon(quest);
        if (objectiveIcon != null && GeneralClientMethods.resourceExists(objectiveIcon)) {
            return objectiveIcon;
        }
        String name = switch (quest.type()) {
            case DAILY -> "quest_daily.png";
            case CHALLENGE -> "quest_challenge.png";
            case COMMON -> "quest_common.png";
            case CHUNK -> "quest_chunk.png";
            case SIDE -> "quest_side.png";
        };
        ResourceLocation fallback = GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "textures/gui/quest_ui/" + name);
        return GeneralClientMethods.resourceExists(fallback) ? fallback : null;
    }

    /** The villager's name, used by the composed quests' parameterised strings. */
    private static Component professionName(QuestView quest) {
        String profession = ClientQuestCatalog.get(quest.id()).profession;
        return profession == null || profession.isBlank()
                ? GeneralTextMethods.getEmpty()
                : GeneralTextMethods.getTranslatableString(
                        "entity.minecraft.villager." + profession.toLowerCase());
    }

    /** Default objective art used when a server template omits its optional icon field. */
    private static ResourceLocation objectiveIcon(QuestView quest) {
        // Crafting has no vanilla item texture of its own, so it uses the mod's stand-in.
        if (quest.objective() == QuestObjective.CRAFT_ITEM) {
            return GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID,
                    "textures/gui/quest_ui/quest_unknown.png");
        }
        String path = switch (quest.objective()) {
            case KILL -> quest.type() == QuestType.CHALLENGE
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

    private static int color(int argb, float alpha) {
        int sourceAlpha = (argb >>> 24) & 0xFF;
        int resultAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * alpha)));
        return (resultAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        if (font.width(ellipsis) >= maxWidth) return font.plainSubstrByWidth(ellipsis, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - font.width(ellipsis)) + ellipsis;
    }

    private record QuestRowLayout(List<FormattedCharSequence> descriptionLines,
                                  int height, int advanceHeight, int descriptionTopOffset,
                                  int descriptionLineHeight, boolean truncated) {
    }

    private record DisplayQuest(QuestView quest, CompletionEffect effect) {
    }

    private static final class CompletionEffect {
        private final QuestView quest;
        private final int rowIndex;
        private long startedAt = -1L;

        private CompletionEffect(QuestView quest, int rowIndex) {
            this.quest = quest;
            this.rowIndex = rowIndex;
        }

        private QuestView quest() {
            return quest;
        }

        private int rowIndex() {
            return rowIndex;
        }

        private long startedAt() {
            return startedAt;
        }

        private boolean started() {
            return startedAt >= 0L;
        }

        private void start(long current) {
            if (!started()) startedAt = current;
        }
    }

    private static final class RowMotion {
        private float y;
        private boolean wasCompletion;
        private long enterStartedAt = -1L;

        private RowMotion(float y, boolean wasCompletion) {
            this.y = y;
            this.wasCompletion = wasCompletion;
        }
    }
}
