package com.whatever.aegis_ascension.client.screen.acg;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * An {@link EditBox} that draws at a raised z, so it clears a modal panel rendered above the
 * screen's normal content.
 *
 * <p>Subclassed rather than copied: the only thing that needs to change is the depth the
 * widget draws at, and every other behaviour — caret, selection, filtering, suggestion text,
 * scrolling — is vanilla's and should stay vanilla's. Duplicating {@code EditBox} to add one
 * pose translate would mean maintaining a fork of all of it.</p>
 */
public final class ACGEditBox extends EditBox {
    private final float zOffset;

    public ACGEditBox(Font font, int x, int y, int width, int height, Component message,
                      float zOffset) {
        super(font, x, y, width, height, message);
        this.zOffset = zOffset;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (zOffset == 0.0F) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, zOffset);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }
}
