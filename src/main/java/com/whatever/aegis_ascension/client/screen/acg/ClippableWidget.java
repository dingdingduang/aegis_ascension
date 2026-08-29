package com.whatever.aegis_ascension.client.screen.acg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A widget that can be told to clip its own drawing to a horizontal band.
 *
 * <p>Needed because a scrolling grid lets a card hang past the viewport edge. Culling alone
 * can't solve that: hiding every card not <em>entirely</em> inside the viewport blanks the
 * grid whenever the pane is shorter than one card, so partially visible cards have to
 * render — and without clipping they spill over the header, the action buttons, and the
 * title bar.</p>
 *
 * <p>The screen can't simply scissor around its {@code super.render()} call, since that one
 * pass also draws the drawer and every button; only the cards should be clipped, so each
 * card applies the band itself.</p>
 */
public interface ClippableWidget {
    /** Clips drawing to {@code [top, bottom)}; pass a full-screen band to disable. */
    void setClipBounds(int top, int bottom);

    /** No clipping: a band large enough to contain any screen. */
    int NO_CLIP_TOP = Integer.MIN_VALUE / 4;
    int NO_CLIP_BOTTOM = Integer.MAX_VALUE / 4;

    /**
     * Runs {@code body} inside a vertical scissor, or directly when the band is disabled.
     * Clipping is vertical only — the full screen width is used horizontally, since grids
     * overflow up and down but never sideways.
     */
    static void clipped(GuiGraphics graphics, int top, int bottom, Runnable body) {
        if (top <= NO_CLIP_TOP && bottom >= NO_CLIP_BOTTOM) {
            body.run();
            return;
        }
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        graphics.enableScissor(0, top, screenWidth, bottom);
        body.run();
        graphics.disableScissor();
    }
}
