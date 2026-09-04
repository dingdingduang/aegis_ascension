package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientQuestState;
import com.whatever.aegis_ascension.client.SettingNotes;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGSectionHeader;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.SetQuestAutoAcceptPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Per-player preferences persisted and enforced by the server. */
final class ACGServerSettingsPage implements ACGPage {
    private static final String SECTION_QUEST = "server_quest";
    private static final String SETTING_AUTO_ACCEPT = "quest_auto_accept";
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_INDENT = 10;
    private static final int ROW_Y = 64;
    private static final int NOTE_TOP_GAP = 4;

    private boolean awaitingServer;
    private boolean sectionOpen = true;

    @Override
    public void init(ACGScreenContext context) {
        int fieldWidth = Math.min(420, context.contentWidth());
        int fieldX = context.contentX() + (context.contentWidth() - fieldWidth) / 2;
        boolean collapsed = context.collapsedSections().isCollapsed(SECTION_QUEST);
        sectionOpen = !collapsed;
        context.add(new ACGSectionHeader(fieldX, context.contentTop() + 34,
                fieldWidth, ROW_HEIGHT,
                getTranslatableString(
                        "screen.aegis_ascension.acg.server_settings.section.quest"),
                () -> !collapsed,
                () -> {
                    context.collapsedSections().toggle(SECTION_QUEST);
                    context.rebuild();
                }));
        if (collapsed) {
            return;
        }
        boolean enabled = ClientQuestState.autoAcceptEligibleQuests();
        ACGButton button = ACGButton.builder(autoAcceptLabel(enabled), pressed -> {
                    awaitingServer = true;
                    pressed.active = false;
                    ModNetworking.sendToServer(new SetQuestAutoAcceptPacket(!enabled));
                })
                .bounds(fieldX + ROW_INDENT, context.contentTop() + ROW_Y,
                        fieldWidth - ROW_INDENT * 2, ROW_HEIGHT)
                .build();
        button.active = !awaitingServer;
        context.add(button);
    }

    @Override
    public void render(ACGScreenContext context, GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        int centerX = context.contentX() + context.contentWidth() / 2;
        drawCenteredString(graphics, context.font(),
                getTranslatableString("screen.aegis_ascension.acg.server_settings.hint"),
                centerX, context.contentTop() + 10, ACGTheme.TEXT_MUTED);

        String note = sectionOpen ? SettingNotes.server(SETTING_AUTO_ACCEPT) : null;
        if (note == null) {
            return;
        }
        int textWidth = Math.min(420, context.contentWidth()) - ROW_INDENT * 2;
        graphics.drawWordWrap(context.font(), getLiteralString(note),
                centerX - textWidth / 2,
                context.contentTop() + ROW_Y + ROW_HEIGHT + NOTE_TOP_GAP,
                textWidth, ACGTheme.TEXT_SECONDARY);
    }

    @Override
    public void onServerSync(ACGScreenContext context) {
        awaitingServer = false;
    }

    private static Component autoAcceptLabel(boolean enabled) {
        return getTranslatableString(
                "screen.aegis_ascension.acg.server_settings.quest_auto_accept",
                getTranslatableString(enabled
                        ? "screen.aegis_ascension.acg.settings.on"
                        : "screen.aegis_ascension.acg.settings.off"));
    }
}
