package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
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

/** Client-only settings page, including its own scrolling layout state. */
final class ACGClientSettingsPage implements ACGPage {
    private record SettingRow(AbstractWidget widget, int naturalY) {
    }

    private final List<SettingRow> rows = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int viewportTop;
    private int viewportBottom;

    @Override
    public void init(ACGScreenContext context) {
        ClientSettings settings = ClientSettings.get();
        rows.clear();
        int fieldWidth = Math.min(360, context.contentWidth());
        int fieldX = context.contentX() + (context.contentWidth() - fieldWidth) / 2;
        int y = context.contentTop() + 24;
        int rowGap = 30;

        addRow(context, ACGButton.builder(
                        rememberLastPositionLabel(settings.rememberLastPosition), button -> {
                            settings.rememberLastPosition = !settings.rememberLastPosition;
                            settings.save();
                            button.setMessage(rememberLastPositionLabel(
                                    settings.rememberLastPosition));
                        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += rowGap;

        addRow(context, ACGButton.builder(
                        rememberCollapsedTabsLabel(settings.rememberCollapsedTabs), button -> {
                            settings.rememberCollapsedTabs = !settings.rememberCollapsedTabs;
                            settings.save();
                            button.setMessage(rememberCollapsedTabsLabel(
                                    settings.rememberCollapsedTabs));
                        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += rowGap;

        addRow(context, ACGButton.builder(
                        instantDiscardAllLabel(settings.instantDiscardAll), button -> {
                            settings.instantDiscardAll = !settings.instantDiscardAll;
                            settings.save();
                            button.setMessage(instantDiscardAllLabel(
                                    settings.instantDiscardAll));
                        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += rowGap;

        addRow(context, new ACGSlider(fieldX, y, fieldWidth, 20,
                ClientSettings.MIN_CARD_WIDTH, ClientSettings.MAX_CARD_WIDTH,
                settings.cardWidth,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.card_width", Math.round(value)),
                value -> {
                    settings.cardWidth = (int) Math.round(value);
                    settings.save();
                }));
        y += rowGap;

        addRow(context, new ACGSlider(fieldX, y, fieldWidth, 20,
                ClientSettings.MIN_CARD_HEIGHT, ClientSettings.MAX_CARD_HEIGHT,
                settings.cardHeight,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.card_height", Math.round(value)),
                value -> {
                    settings.cardHeight = (int) Math.round(value);
                    settings.save();
                }));
        y += rowGap;

        addRow(context, new ACGSlider(fieldX, y, fieldWidth, 20,
                0.0D, 1.0D, settings.backgroundOpacity,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.background_opacity",
                        Math.round(value * 100.0D)),
                value -> {
                    settings.backgroundOpacity = value;
                    settings.save();
                }));
        y += rowGap;

        addRow(context, new ACGSlider(fieldX, y, fieldWidth, 20,
                0.0D, 1.0D, settings.drawerOpacity,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.drawer_opacity",
                        Math.round(value * 100.0D)),
                value -> {
                    settings.drawerOpacity = value;
                    settings.save();
                }));
        y += rowGap;

        addRow(context, ACGButton.builder(shieldHudLabel(settings.showShieldHud), button -> {
                    settings.showShieldHud = !settings.showShieldHud;
                    settings.save();
                    button.setMessage(shieldHudLabel(settings.showShieldHud));
                }).bounds(fieldX, y, fieldWidth, 20).build());
        y += rowGap;

        addRow(context, ACGButton.builder(
                        shieldHudAnchorLabel(settings.shieldHudAnchor), button -> {
                            settings.shieldHudAnchor = nextAnchor(settings.shieldHudAnchor);
                            settings.save();
                            button.setMessage(shieldHudAnchorLabel(settings.shieldHudAnchor));
                        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += rowGap;

        int halfWidth = (fieldWidth - 8) / 2;
        addRow(context, new ACGSlider(fieldX, y, halfWidth, 20,
                -200.0D, 200.0D, settings.shieldHudOffsetX,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.shield_hud_offset_x",
                        Math.round(value)),
                value -> {
                    settings.shieldHudOffsetX = (int) Math.round(value);
                    settings.save();
                }));
        addRow(context, new ACGSlider(fieldX + halfWidth + 8, y,
                fieldWidth - halfWidth - 8, 20,
                -200.0D, 200.0D, settings.shieldHudOffsetY,
                value -> getTranslatableString(
                        "screen.aegis_ascension.acg.settings.shield_hud_offset_y",
                        Math.round(value)),
                value -> {
                    settings.shieldHudOffsetY = (int) Math.round(value);
                    settings.save();
                }));

        viewportTop = context.contentTop() + 18;
        viewportBottom = context.contentBottom() - 4;
        int contentHeight = rows.isEmpty() ? 0
                : rows.get(rows.size() - 1).naturalY() + 20 - (context.contentTop() + 24);
        maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        layoutRows();
    }

    private <T extends AbstractWidget> T addRow(ACGScreenContext context, T widget) {
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

    private static Component shieldHudLabel(boolean enabled) {
        return onOffLabel("screen.aegis_ascension.acg.settings.show_shield_hud", enabled);
    }

    private static Component shieldHudAnchorLabel(ClientSettings.HudAnchor anchor) {
        return getTranslatableString(
                "screen.aegis_ascension.acg.settings.shield_hud_anchor",
                getTranslatableString("screen.aegis_ascension.acg.settings.anchor."
                        + anchor.name().toLowerCase(Locale.ROOT)));
    }

    private static ClientSettings.HudAnchor nextAnchor(ClientSettings.HudAnchor anchor) {
        ClientSettings.HudAnchor[] values = ClientSettings.HudAnchor.values();
        return values[(anchor.ordinal() + 1) % values.length];
    }

    private static Component rememberLastPositionLabel(boolean enabled) {
        return onOffLabel("screen.aegis_ascension.acg.settings.remember_position", enabled);
    }

    private static Component instantDiscardAllLabel(boolean enabled) {
        return onOffLabel("screen.aegis_ascension.acg.settings.instant_discard_all", enabled);
    }

    private static Component rememberCollapsedTabsLabel(boolean enabled) {
        return onOffLabel(
                "screen.aegis_ascension.acg.settings.remember_collapsed_tabs", enabled);
    }

    private static Component onOffLabel(String key, boolean enabled) {
        return getTranslatableString(key, getTranslatableString(enabled
                ? "screen.aegis_ascension.acg.settings.on"
                : "screen.aegis_ascension.acg.settings.off"));
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
                getTranslatableString("screen.aegis_ascension.acg.settings.hint"),
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
