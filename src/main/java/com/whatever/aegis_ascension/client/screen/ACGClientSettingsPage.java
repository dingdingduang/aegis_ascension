package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGSlider;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Client-only settings page: collapsible sections over its own scrolling layout.
 *
 * <p>Rows are emitted through small builders that own the label, the save, and the layout
 * cursor, so adding a setting is one line here instead of a widget block plus a bespoke
 * label method. A collapsed section emits no rows at all, which keeps the scroll extent
 * honest without a second height calculation.</p>
 */
final class ACGClientSettingsPage implements ACGPage {
    private static final String SECTION_INTERFACE = "interface";
    private static final String SECTION_COLLECTION = "collection";
    private static final String SECTION_SHIELD_HUD = "shield_hud";
    private static final String SECTION_QUEST_TRACKER = "quest_tracker";

    private static final String KEY_PREFIX = "screen.aegis_ascension.acg.settings.";
    private static final int ROW_GAP = 30;
    private static final int ROW_HEIGHT = 20;
    private static final double OFFSET_MIN = -200.0D;
    private static final double OFFSET_MAX = 200.0D;

    private record SettingRow(AbstractWidget widget, int naturalY) {
    }

    private final List<SettingRow> rows = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int viewportTop;
    private int viewportBottom;

    // Layout cursor. Only meaningful for the duration of one init call.
    private ACGScreenContext context;
    private ClientSettings settings;
    private int fieldX;
    private int fieldWidth;
    private int halfWidth;
    private int cursorY;
    private boolean sectionOpen;

    @Override
    public void init(ACGScreenContext context) {
        this.context = context;
        this.settings = ClientSettings.get();
        rows.clear();
        fieldWidth = Math.min(360, context.contentWidth());
        fieldX = context.contentX() + (context.contentWidth() - fieldWidth) / 2;
        halfWidth = (fieldWidth - 8) / 2;
        cursorY = context.contentTop() + 24;

        section(SECTION_INTERFACE);
        toggle("remember_position",
                () -> settings.rememberLastPosition,
                value -> settings.rememberLastPosition = value);
        toggle("remember_collapsed_tabs",
                () -> settings.rememberCollapsedTabs,
                value -> settings.rememberCollapsedTabs = value);
        slider("card_width", ClientSettings.MIN_CARD_WIDTH, ClientSettings.MAX_CARD_WIDTH,
                settings.cardWidth, value -> Math.round(value),
                value -> settings.cardWidth = (int) Math.round(value));
        slider("card_height", ClientSettings.MIN_CARD_HEIGHT, ClientSettings.MAX_CARD_HEIGHT,
                settings.cardHeight, value -> Math.round(value),
                value -> settings.cardHeight = (int) Math.round(value));
        slider("background_opacity", 0.0D, 1.0D, settings.backgroundOpacity,
                value -> Math.round(value * 100.0D),
                value -> settings.backgroundOpacity = value);
        slider("drawer_opacity", 0.0D, 1.0D, settings.drawerOpacity,
                value -> Math.round(value * 100.0D),
                value -> settings.drawerOpacity = value);
        toggle("show_jei_overlay",
                () -> settings.showJeiOverlay,
                value -> settings.showJeiOverlay = value);

        section(SECTION_COLLECTION);
        toggle("instant_discard_all",
                () -> settings.instantDiscardAll,
                value -> settings.instantDiscardAll = value);
        toggle("show_unformed_soul_links",
                () -> settings.showUnformedSoulLinks,
                value -> settings.showUnformedSoulLinks = value);

        section(SECTION_SHIELD_HUD);
        toggle("show_shield_hud",
                () -> settings.showShieldHud,
                value -> settings.showShieldHud = value);
        toggle("show_owned_gold",
                () -> settings.showGoldCurrency,
                value -> settings.showGoldCurrency = value);
        anchor("shield_hud_anchor",
                () -> settings.shieldHudAnchor,
                value -> settings.shieldHudAnchor = value);
        offsetPair("shield_hud_offset_x", "shield_hud_offset_y",
                settings.shieldHudOffsetX, settings.shieldHudOffsetY,
                value -> settings.shieldHudOffsetX = (int) Math.round(value),
                value -> settings.shieldHudOffsetY = (int) Math.round(value));

        section(SECTION_QUEST_TRACKER);
        slider("quest_tracker_scale", ClientSettings.MIN_QUEST_TRACKER_SCALE,
                ClientSettings.MAX_QUEST_TRACKER_SCALE, settings.questTrackerScale,
                value -> String.format(Locale.ROOT, "%.0f%%", value * 100.0D),
                value -> settings.questTrackerScale = value);
        slider("quest_tracker_quest_limit", ClientSettings.MIN_QUEST_TRACKER_QUEST_LIMIT,
                ClientSettings.MAX_QUEST_TRACKER_QUEST_LIMIT, settings.questTrackerQuestLimit,
                value -> Math.round(value),
                value -> settings.questTrackerQuestLimit = (int) Math.round(value));
        anchor("quest_tracker_hud_anchor",
                () -> settings.questTrackerHudAnchor,
                value -> settings.questTrackerHudAnchor = value);
        offsetPair("quest_tracker_hud_offset_x", "quest_tracker_hud_offset_y",
                settings.questTrackerHudOffsetX, settings.questTrackerHudOffsetY,
                value -> settings.questTrackerHudOffsetX = (int) Math.round(value),
                value -> settings.questTrackerHudOffsetY = (int) Math.round(value));

        viewportTop = context.contentTop() + 18;
        viewportBottom = context.contentBottom() - 4;
        int contentHeight = cursorY - (context.contentTop() + 24);
        maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        layoutRows();
    }

    /**
     * Starts a collapsible group. Every row builder after this one is a no-op until the
     * next section, so collapsing costs nothing to lay out.
     */
    private void section(String id) {
        boolean collapsed = settings.collapsedSettingSections.contains(id);
        add(ACGButton.builder(sectionLabel(id, collapsed), button -> {
            if (!settings.collapsedSettingSections.remove(id)) {
                settings.collapsedSettingSections.add(id);
            }
            settings.save();
            // A full rebuild, not a re-layout: the row set itself changes, so the scroll
            // extent has to be recomputed rather than adjusted.
            context.rebuild();
        }).bounds(fieldX, cursorY, fieldWidth, ROW_HEIGHT).build());
        cursorY += ROW_GAP;
        sectionOpen = !collapsed;
    }

    private void toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        if (!sectionOpen) {
            return;
        }
        add(ACGButton.builder(onOffLabel(key, getter.getAsBoolean()), button -> {
            setter.accept(!getter.getAsBoolean());
            settings.save();
            button.setMessage(onOffLabel(key, getter.getAsBoolean()));
        }).bounds(fieldX, cursorY, fieldWidth, ROW_HEIGHT).build());
        cursorY += ROW_GAP;
    }

    /** @param display turns the slider's raw value into the argument its label formats */
    private void slider(String key, double minimum, double maximum, double value,
                        DoubleFunction<Object> display, DoubleConsumer apply) {
        if (!sectionOpen) {
            return;
        }
        add(new ACGSlider(fieldX, cursorY, fieldWidth, ROW_HEIGHT, minimum, maximum, value,
                current -> getTranslatableString(key(key), display.apply(current)),
                current -> {
                    apply.accept(current);
                    settings.save();
                }));
        cursorY += ROW_GAP;
    }

    /** Two offset sliders sharing one row, the way X and Y read best side by side. */
    private void offsetPair(String keyX, String keyY, double valueX, double valueY,
                            DoubleConsumer applyX, DoubleConsumer applyY) {
        if (!sectionOpen) {
            return;
        }
        add(new ACGSlider(fieldX, cursorY, halfWidth, ROW_HEIGHT,
                OFFSET_MIN, OFFSET_MAX, valueX,
                current -> getTranslatableString(key(keyX), Math.round(current)),
                current -> {
                    applyX.accept(current);
                    settings.save();
                }));
        add(new ACGSlider(fieldX + halfWidth + 8, cursorY, fieldWidth - halfWidth - 8,
                ROW_HEIGHT, OFFSET_MIN, OFFSET_MAX, valueY,
                current -> getTranslatableString(key(keyY), Math.round(current)),
                current -> {
                    applyY.accept(current);
                    settings.save();
                }));
        cursorY += ROW_GAP;
    }

    private void anchor(String key, Supplier<ClientSettings.HudAnchor> getter,
                        Consumer<ClientSettings.HudAnchor> setter) {
        if (!sectionOpen) {
            return;
        }
        add(ACGButton.builder(anchorLabel(key, getter.get()), button -> {
            setter.accept(nextAnchor(getter.get()));
            settings.save();
            button.setMessage(anchorLabel(key, getter.get()));
        }).bounds(fieldX, cursorY, fieldWidth, ROW_HEIGHT).build());
        cursorY += ROW_GAP;
    }

    private <T extends AbstractWidget> T add(T widget) {
        rows.add(new SettingRow(widget, widget.getY()));
        context.add(widget);
        return widget;
    }

    private void layoutRows() {
        for (SettingRow row : rows) {
            int y = row.naturalY() - scroll;
            row.widget().setY(y);
            boolean fits = y >= viewportTop
                    && y + row.widget().getHeight() <= viewportBottom;
            row.widget().visible = fits;
            row.widget().active = fits;
        }
    }

    private static String key(String suffix) {
        return KEY_PREFIX + suffix;
    }

    private static Component sectionLabel(String id, boolean collapsed) {
        return getLiteralString(collapsed ? "▶ " : "▼ ")
                .append(getTranslatableString(key("section." + id)));
    }

    private static Component onOffLabel(String key, boolean enabled) {
        return getTranslatableString(key(key), getTranslatableString(enabled
                ? key("on")
                : key("off")));
    }

    private static Component anchorLabel(String key, ClientSettings.HudAnchor anchor) {
        return getTranslatableString(key(key), getTranslatableString(
                key("anchor." + anchor.name().toLowerCase(Locale.ROOT))));
    }

    private static ClientSettings.HudAnchor nextAnchor(ClientSettings.HudAnchor anchor) {
        ClientSettings.HudAnchor[] values = ClientSettings.HudAnchor.values();
        return values[(anchor.ordinal() + 1) % values.length];
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        if (maxScroll > 0) {
            int trackHeight = viewportBottom - viewportTop;
            int contentHeight = trackHeight + maxScroll;
            int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
            int thumbY = viewportTop + Math.round(
                    (trackHeight - thumbHeight) * (scroll / (float) maxScroll));
            int thumbX = context.contentX() + context.contentWidth() - 4;
            graphics.fill(thumbX, thumbY, thumbX + 2,
                    thumbY + thumbHeight, ACGTheme.GOLD_DIM);
        }
        drawCenteredString(graphics, context.font(),
                getTranslatableString(key("hint")),
                context.contentX() + context.contentWidth() / 2,
                context.contentTop() + 6, ACGTheme.TEXT_MUTED);
    }

    @Override
    public boolean mouseScrolled(ACGScreenContext context, double mouseX,
                                 double mouseY, double delta) {
        if (maxScroll <= 0 || mouseX < context.contentX()
                || mouseY <= ACGPerkSelectionScreen.TOP_BAR_HEIGHT
                || Math.abs(delta) <= 1.0E-9D) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll,
                scroll + (int) Math.round((delta < 0.0D ? 1 : -1) * 30.0D)));
        layoutRows();
        return true;
    }
}
