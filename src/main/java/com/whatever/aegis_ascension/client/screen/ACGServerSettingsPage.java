package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.drawCenteredString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientQuestState;
import com.whatever.aegis_ascension.client.screen.acg.ACGButton;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.SetQuestAutoAcceptPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Per-player preferences persisted and enforced by the server. */
final class ACGServerSettingsPage implements ACGPage {
    private boolean awaitingServer;

    @Override
    public void init(ACGScreenContext context) {
        int fieldWidth = Math.min(420, context.contentWidth());
        int fieldX = context.contentX() + (context.contentWidth() - fieldWidth) / 2;
        boolean enabled = ClientQuestState.autoAcceptEligibleQuests();
        ACGButton button = ACGButton.builder(autoAcceptLabel(enabled), pressed -> {
                    awaitingServer = true;
                    pressed.active = false;
                    ModNetworking.sendToServer(new SetQuestAutoAcceptPacket(!enabled));
                })
                .bounds(fieldX, context.contentTop() + 54, fieldWidth, 20)
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

        int textWidth = Math.min(420, context.contentWidth());
        graphics.drawWordWrap(context.font(),
                getTranslatableString(
                        "screen.aegis_ascension.acg.server_settings.quest_auto_accept.note"),
                centerX - textWidth / 2, context.contentTop() + 84,
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
