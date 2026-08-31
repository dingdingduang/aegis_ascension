package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.mojang.blaze3d.systems.RenderSystem;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared visual language for the ACG-styled screens: palette, texture locations, and
 * small stateless drawing helpers used by {@link ACGPerkSelectionScreen} and
 * {@link ACGCardWidget}.
 *
 * <p>Every texture referenced here is optional. {@link #hasAtlas()} /
 * {@link com.whatever.aegis_ascension.util.GeneralClientMethods#resourceExists(ResourceLocation)}
 * gate every blit, so the screen renders correctly with flat {@code GuiGraphics.fill()}
 * panels the day this class is compiled, and automatically upgrades to real art the
 * moment matching PNGs are dropped into {@code assets/aegis_ascension/textures/gui/acg/}.</p>
 */
public final class ACGTheme {
    private ACGTheme() {
    }

    // ------------------------------------------------------------------
    // Textures
    // ------------------------------------------------------------------

    /** Single shared atlas for chrome: panels, tab buttons, progress bar, diamonds, rings. */
    public static final ResourceLocation ATLAS = rl("textures/gui/acg_ui_sheet.png");
    public static final int ATLAS_WIDTH = 256;
    public static final int ATLAS_HEIGHT = 256;

    /** Per-rarity/Aegis card art. Each is a standalone, non-atlased texture. */
    public static final ResourceLocation AEGIS_CARD = rl("textures/gui/acg/aegis_card.png");
    public static final ResourceLocation PERK_R_CARD = rl("textures/gui/acg/perk_r_card.png");
    public static final ResourceLocation PERK_SR_CARD = rl("textures/gui/acg/perk_sr_card.png");
    public static final ResourceLocation PERK_SSR_CARD = rl("textures/gui/acg/perk_ssr_card.png");
    public static final ResourceLocation REFRESH_BUTTON = rl("textures/gui/acg/refreshbutton.png");

    /**
     * Real painted/curated art pulled from the source game's own split sprites
     * (see {@code ui_asset/_legacy_asset/output/} and {@code .../ui/viewres/equip/}),
     * not procedural fills. Each is optional and gated by {@link GeneralClientMethods#resourceExists}
     * exactly like the atlas, so the screen still renders correctly if one is missing.
     */
    public static final ResourceLocation BG_MAIN = rl("textures/gui/acg/bg_main.png");
    public static final int BG_MAIN_W = 1920;
    public static final int BG_MAIN_H = 800;
    public static final ResourceLocation RING = rl("textures/gui/acg/ring.png");
    public static final int RING_TEXTURE_SIZE = 144;
    public static final ResourceLocation SELECTED_FRAME = rl("textures/gui/acg/selected_frame.png");
    public static final int SELECTED_FRAME_TEXTURE_SIZE = 72;

    /**
     * The drawer's active-row indicator: a 4-frame spin/pop-in ("btn_1.png".."btn_4.png"
     * in the source game's {@code ui/viewres/equip/}), squashed-wide-then-tiny before
     * settling on the full diamond. {@link #INDICATOR_FRAMES} is indexed 0..3 for frames
     * 1..4; each entry is {texture, nativeWidth, nativeHeight}.
     */
    public static final ResourceLocation[] INDICATOR_TEXTURES = {
            rl("textures/gui/acg/indicator_1.png"),
            rl("textures/gui/acg/indicator_2.png"),
            rl("textures/gui/acg/indicator_3.png"),
            rl("textures/gui/acg/indicator_4.png"),
    };
    public static final int[][] INDICATOR_SIZES = {{36, 20}, {16, 16}, {8, 8}, {56, 56}};
    /** Milliseconds each indicator frame holds before advancing; frame 4 (index 3) holds forever. */
    public static final long INDICATOR_FRAME_MS = 70L;

    /**
     * Fallback native size for card sprites if a texture is missing/unreadable. The
     * actual visible-art crop rect is auto-detected per texture at runtime via
     * {@link com.whatever.aegis_ascension.util.GeneralClientMethods#detectOpaqueBounds} instead
     * of being hardcoded here, since different card art files use different padding.
     */
    public static final int CARD_TEXTURE_SIZE = 512;

    public static final int REFRESH_BUTTON_TEXTURE_W = 256;
    public static final int REFRESH_BUTTON_TEXTURE_H = 128;

    /**
     * Optional custom serif display font for headers. An unregistered font ID resolves
     * to an *empty* glyph set (every character renders as a missing-glyph box), not a
     * fallback to vanilla, so {@link #asHeader} gates this on
     * {@link #HEADER_FONT_DEFINITION} actually existing before applying it.
     */
    private static final ResourceLocation HEADER_FONT = rl("acg_header");
    private static final ResourceLocation HEADER_FONT_DEFINITION = rl("font/acg_header.json");

    // ------------------------------------------------------------------
    // Atlas UV slices (placeholder layout; repack to match the real sheet once painted)
    // ------------------------------------------------------------------

    public record Slice(int u, int v, int w, int h) {
    }

    public static final Slice PANEL_FRAME = new Slice(0, 0, 64, 64);
    public static final int PANEL_FRAME_CORNER = 10;
    public static final Slice TAB_BUTTON_NORMAL = new Slice(64, 0, 104, 24);
    public static final Slice TAB_BUTTON_ACTIVE = new Slice(64, 24, 104, 24);
    public static final Slice PROGRESS_TRACK = new Slice(0, 64, 182, 10);
    public static final Slice PROGRESS_FILL = new Slice(0, 74, 182, 10);
    public static final Slice CIRCULAR_RING = new Slice(0, 84, 96, 96);

    /**
     * Draws a virtual book's icon texture into a square box, auto-detecting the texture's
     * native size so icons authored at different resolutions (128x128 and 64x64 both ship
     * today) each render whole instead of being sampled against one fixed assumption.
     * Falls back to the item-stack renderer when the texture is missing.
     */
    public static void drawVirtualItemIcon(GuiGraphics graphics, ResourceLocation texture,
                                           int x, int y, int size) {
        int[] native_ = GeneralClientMethods.detectTextureSize(texture, 128);
        GeneralClientMethods.blitScaledRegion(graphics, texture, x, y, size, size,
                0.0F, 0.0F, native_[0], native_[1], native_[0], native_[1]);
    }

    public static boolean hasAtlas() {
        return GeneralClientMethods.resourceExists(ATLAS);
    }

    // ------------------------------------------------------------------
    // Palette — dark slate vignette, metallic gold trim, orange actions, cyan accents.
    //
    // Warm brown/black, not cool gray: the reference composition (reference_layout.webp)
    // is lit like leather and torchlight, not slate. Every neutral below leans warm for
    // that reason, matched against the reference's background and panel tones.
    // ------------------------------------------------------------------

    public static final int BACKGROUND_TOP = 0xFF241A12;
    public static final int BACKGROUND_BOTTOM = 0xFF0A0704;
    public static final int VIGNETTE_EDGE = 0xB3120C08;
    /** Soft warm glow pooled behind the OWNED_AEGIS/OWNED_PERKS showcase ring. */
    public static final int SHOWCASE_GLOW = 0x40C97A3A;

    public static final int PANEL_FILL = 0xE0201812;
    public static final int PANEL_FILL_RAISED = 0xE6291F16;
    public static final int PANEL_BORDER = 0xFF4A3B26;

    public static final int GOLD = 0xFFD8B463;
    public static final int GOLD_DIM = 0xFF8C7238;
    public static final int GOLD_BRIGHT = 0xFFF3DFA4;

    public static final int ORANGE_ACTION = 0xFFE07A2D;
    public static final int ORANGE_ACTION_HOVER = 0xFFF39A4C;
    public static final int ORANGE_ACTION_DISABLED = 0xFF5A4530;

    public static final int CYAN_ACCENT = 0xFF55C7E8;
    public static final int CYAN_ACCENT_DIM = 0xFF2E7186;

    public static final int TEXT_PRIMARY = 0xFFF1E9D2;
    public static final int TEXT_SECONDARY = 0xFFC9BBA0;
    public static final int TEXT_MUTED = 0xFF8C8171;
    public static final int TEXT_DISABLED = 0xFF6A6255;

    public static final int SELECTED_RED_DIAMOND = 0xFFE0473F;

    /** The drawer's active-row pill and its hover-only preview, both warm and translucent. */
    public static final int DRAWER_ACTIVE_FILL = 0x664A3018;
    public static final int DRAWER_HOVER_FILL = 0x40382418;

    public static final int RARITY_R = GeneralConstants.RARITY_R;
    public static final int RARITY_SR = GeneralConstants.RARITY_SR;
    public static final int RARITY_SSR = GeneralConstants.RARITY_SSR;
    public static final int RARITY_AEGIS = ORANGE_ACTION;


    public static final int STATUS_ACTIVE = 0xFF72E39A;
    public static final int STATUS_LOCKED = 0xFFE07A7A;
    public static final int STATUS_DISABLED = 0xFF888888;

    // ------------------------------------------------------------------
    // Header typography
    // ------------------------------------------------------------------

    /** Applies the optional serif display font; falls back to vanilla (bold only) if it's missing. */
    public static Component asHeader(Component component) {
        if (!GeneralClientMethods.resourceExists(HEADER_FONT_DEFINITION)) {
            return component.copy().withStyle(style -> style.withBold(true));
        }
        return component.copy().withStyle(style -> style.withFont(HEADER_FONT).withBold(true));
    }

    // ------------------------------------------------------------------
    // Shared drawing helpers
    // ------------------------------------------------------------------

    /**
     * Warm dark-brown vignette. Prefers the real curated background art
     * ({@link #BG_MAIN}, sourced from the reference game's own "equip" screen
     * background) stretched to fill the screen; falls back to a procedural gradient
     * when that art is missing. Either way, the same edge darkening is layered on top
     * for extra depth at the corners.
     */
    /**
     * @param opacity 0 (fully transparent — the game world behind the screen shows
     *                through untouched, since {@code ACGPerkSelectionScreen} isn't a
     *                pause screen and the world keeps rendering behind it every frame)
     *                to 1 (fully opaque, the original always-on look).
     */
    public static void drawVignetteBackground(GuiGraphics graphics, int width, int height, float opacity) {
        float clamped = Math.max(0.0F, Math.min(1.0F, opacity));
        if (GeneralClientMethods.resourceExists(BG_MAIN)) {
            // Real painted art already fades toward its own edges; stacking the synthetic
            // edge-darkening gradient below on top of it band-crushes visibly (8-bit
            // gradients through near-black content show a hard-edged banding line rather
            // than a smooth ramp), which read as a seam. Only the flat-fill fallback below
            // needs the synthetic vignette to fake depth.
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clamped);
            GeneralClientMethods.blitScaledRegion(graphics, BG_MAIN, 0, 0, width, height,
                    0.0F, 0.0F, BG_MAIN_W, BG_MAIN_H, BG_MAIN_W, BG_MAIN_H);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        graphics.fillGradient(0, 0, width, height, fadeAlpha(BACKGROUND_TOP, clamped), fadeAlpha(BACKGROUND_BOTTOM, clamped));
        int edge = Math.max(24, Math.min(width, height) / 6);
        int vignette = fadeAlpha(VIGNETTE_EDGE, clamped);
        graphics.fillGradient(0, 0, width, edge, vignette, 0x00000000);
        graphics.fillGradient(0, height - edge, width, height, 0x00000000, vignette);
        graphics.fillGradient(0, 0, edge, height, vignette, 0x00000000);
        graphics.fillGradient(width - edge, 0, width, height, 0x00000000, vignette);
    }

    /** Scales an ARGB color's alpha channel by {@code factor} (0..1); RGB unchanged. */
    private static int fadeAlpha(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Bordered fallback panel, or the atlas 9-slice frame when {@link #hasAtlas()}. */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        drawPanel(graphics, x, y, w, h, 1.0F);
    }

    /**
     * @param opacity scales the plate's alpha, 0..1. The atlas path tints via the shader
     *                colour because a nine-slice blit has no per-fill alpha to scale.
     */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, float opacity) {
        float alpha = Math.max(0.0F, Math.min(1.0F, opacity));
        if (alpha <= 0.0F) {
            return;
        }
        if (hasAtlas()) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            drawNineSlice(graphics, PANEL_FRAME, PANEL_FRAME_CORNER, x, y, w, h);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        graphics.fill(x, y, x + w, y + h, scaleAlpha(PANEL_FILL, alpha));
        drawGoldBorder(graphics, x, y, w, h, alpha);
    }

    /** Multiplies an ARGB colour's alpha channel by {@code factor}; RGB untouched. */
    public static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 1px metallic-gold outline; the cheap fallback for a frame slice. */
    public static void drawGoldBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        drawGoldBorder(graphics, x, y, w, h, 1.0F);
    }

    public static void drawGoldBorder(GuiGraphics graphics, int x, int y, int w, int h,
                                      float opacity) {
        int gold = scaleAlpha(GOLD, opacity);
        graphics.fill(x, y, x + w, y + 1, gold);
        graphics.fill(x, y + h - 1, x + w, y + h, gold);
        graphics.fill(x, y, x + 1, y + h, gold);
        graphics.fill(x + w - 1, y, x + w, y + h, gold);
    }

    /** Naive 9-slice: stretches the slice's corner-sized edges across the target rect. */
    public static void drawNineSlice(GuiGraphics graphics, Slice slice, int corner,
                                     int x, int y, int w, int h) {
        int c = Math.min(corner, Math.min(slice.w(), slice.h()) / 2);
        // Corners (unscaled).
        GeneralClientMethods.bindAndBlit(graphics, ATLAS, x, y, slice.u(), slice.v(), c, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.bindAndBlit(graphics, ATLAS, x + w - c, y, slice.u() + slice.w() - c, slice.v(), c, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.bindAndBlit(graphics, ATLAS, x, y + h - c, slice.u(), slice.v() + slice.h() - c, c, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.bindAndBlit(graphics, ATLAS, x + w - c, y + h - c, slice.u() + slice.w() - c, slice.v() + slice.h() - c, c, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        // Edges (stretched) and center (stretched).
        GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x + c, y, w - c * 2, c, slice.u() + c, slice.v(), slice.w() - c * 2, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x + c, y + h - c, w - c * 2, c, slice.u() + c, slice.v() + slice.h() - c, slice.w() - c * 2, c, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x, y + c, c, h - c * 2, slice.u(), slice.v() + c, c, slice.h() - c * 2, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x + w - c, y + c, c, h - c * 2, slice.u() + slice.w() - c, slice.v() + c, c, slice.h() - c * 2, ATLAS_WIDTH, ATLAS_HEIGHT);
        GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x + c, y + c, w - c * 2, h - c * 2, slice.u() + c, slice.v() + c, slice.w() - c * 2, slice.h() - c * 2, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    /** Horizontal experience/level progress track, e.g. "Next Aegis to be obtained". */
    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int w, int h,
                                       float progress01, int trackColor, int fillColor) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress01));
        if (hasAtlas()) {
            GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x, y, w, h,
                    PROGRESS_TRACK.u(), PROGRESS_TRACK.v(), PROGRESS_TRACK.w(), PROGRESS_TRACK.h(),
                    ATLAS_WIDTH, ATLAS_HEIGHT);
            int fillWidth = Math.round(w * clamped);
            if (fillWidth > 0) {
                GeneralClientMethods.blitScaledRegion(graphics, ATLAS, x, y, fillWidth, h,
                        PROGRESS_FILL.u(), PROGRESS_FILL.v(),
                        Math.max(1, Math.round(PROGRESS_FILL.w() * clamped)), PROGRESS_FILL.h(),
                        ATLAS_WIDTH, ATLAS_HEIGHT);
            }
            return;
        }
        graphics.fill(x, y, x + w, y + h, trackColor);
        int fillWidth = Math.round(w * clamped);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + h, fillColor);
        }
        drawGoldBorder(graphics, x, y, w, h);
    }

    /**
     * Same as {@link #drawProgressBar}, plus a label centered on top of the bar itself —
     * the reference composition bakes its "0/1000" readout directly onto the track
     * instead of captioning it above, so this lets callers match that placement without
     * duplicating the bar-drawing logic.
     */
    public static void drawProgressBar(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                       int x, int y, int w, int h, float progress01,
                                       int trackColor, int fillColor, Component label) {
        drawProgressBar(graphics, x, y, w, h, progress01, trackColor, fillColor);
        if (label != null) {
            GeneralClientMethods.drawCenteredString(graphics, font, label, x + w / 2, y + (h - 8) / 2, TEXT_PRIMARY);
        }
    }

    /** The drawer's active-selection marker; the spec calls for a literal "◆" glyph. */
    public static Component activeDiamond() {
        return GeneralTextMethods.getLiteralString("◆").withStyle(style ->
                style.withColor(SELECTED_RED_DIAMOND));
    }

    /**
     * Draws the drawer's active-row indicator: a one-shot spin/pop-in through
     * {@link #INDICATOR_TEXTURES} (squashed-wide, tiny, then the full diamond), holding
     * on the last frame once {@code elapsedMs} since the row became active runs out.
     * Falls back to the plain {@link #activeDiamond()} text glyph if the art is missing.
     * Each frame keeps its own native aspect ratio, fit within a {@code boxSize} square
     * and centered, so the squash-stretch reads correctly instead of stretching to a
     * fixed box.
     */
    public static void drawActiveIndicator(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                           int x, int y, int boxSize, long elapsedMs) {
        if (!GeneralClientMethods.resourceExists(INDICATOR_TEXTURES[0])) {
            graphics.drawString(font, activeDiamond(), x, y, SELECTED_RED_DIAMOND, false);
            return;
        }
        int frame = (int) Math.min(INDICATOR_TEXTURES.length - 1, Math.max(0, elapsedMs) / INDICATOR_FRAME_MS);
        ResourceLocation texture = INDICATOR_TEXTURES[frame];
        int nativeW = INDICATOR_SIZES[frame][0];
        int nativeH = INDICATOR_SIZES[frame][1];
        float scale = Math.min((float) boxSize / nativeW, (float) boxSize / nativeH);
        int w = Math.max(1, Math.round(nativeW * scale));
        int h = Math.max(1, Math.round(nativeH * scale));
        GeneralClientMethods.blitScaledRegion(graphics, texture, x + (boxSize - w) / 2, y + (boxSize - h) / 2, w, h,
                0.0F, 0.0F, nativeW, nativeH, nativeW, nativeH);
    }

    /**
     * Soft warm bleed pooled behind the showcase ring, echoing the ambient glow around
     * the artifact in the reference composition. Purely additive: draw before the ring.
     *
     * <p>{@code fillGradient} only interpolates top-to-bottom, which would read as a
     * translucent rectangle rather than a glow, so this approximates radial falloff with
     * concentric octagons (reusing {@link #fillOctagon}) that fade out toward the edge.
     */
    public static void drawShowcaseGlow(GuiGraphics graphics, int centerX, int centerY, int radius) {
        int outer = Math.round(radius * 1.8F);
        int glowAlpha = (SHOWCASE_GLOW >> 24) & 0xFF;
        int glowRgb = SHOWCASE_GLOW & 0x00FFFFFF;
        int steps = 6;
        for (int step = steps; step >= 1; step--) {
            int r = outer * step / steps;
            int alpha = Math.max(1, glowAlpha * (steps - step + 1) / (steps * 2));
            fillOctagon(graphics, centerX, centerY, r, (alpha << 24) | glowRgb);
        }
    }

    /**
     * Circular showcase frame (Owned Aegis / Owned Perks center portrait). Falls back to
     * a faceted octagon of flat fills, which reads as "circular enough" at UI scale
     * without a texture.
     */
    public static void drawCircularRing(GuiGraphics graphics, int centerX, int centerY,
                                        int radius, float pulse) {
        if (GeneralClientMethods.resourceExists(RING)) {
            int size = radius * 2;
            GeneralClientMethods.blitScaledRegion(graphics, RING, centerX - radius, centerY - radius, size, size,
                    0.0F, 0.0F, RING_TEXTURE_SIZE, RING_TEXTURE_SIZE, RING_TEXTURE_SIZE, RING_TEXTURE_SIZE);
            return;
        }
        if (hasAtlas()) {
            int size = radius * 2;
            GeneralClientMethods.blitScaledRegion(graphics, ATLAS,
                    centerX - radius, centerY - radius, size, size,
                    CIRCULAR_RING.u(), CIRCULAR_RING.v(), CIRCULAR_RING.w(), CIRCULAR_RING.h(),
                    ATLAS_WIDTH, ATLAS_HEIGHT);
            return;
        }
        int glow = Math.round(255 * (0.35F + 0.25F * pulse)) & 0xFF;
        int glowColor = (glow << 24) | (GOLD & 0x00FFFFFF);
        fillOctagon(graphics, centerX, centerY, radius + 3, glowColor);
        fillOctagon(graphics, centerX, centerY, radius, PANEL_FILL_RAISED);
        fillOctagonRing(graphics, centerX, centerY, radius, 2, GOLD);
    }

    /** Cheap circle approximation: an octagon built from {@code fill()} rectangles only. */
    public static void fillOctagon(GuiGraphics graphics, int cx, int cy, int r, int color) {
        int cut = Math.max(1, Math.round(r * 0.4142F)); // r * tan(22.5deg)
        graphics.fill(cx - r + cut, cy - r, cx + r - cut, cy + r, color);
        graphics.fill(cx - r, cy - r + cut, cx + r, cy + r - cut, color);
    }

    private static void fillOctagonRing(GuiGraphics graphics, int cx, int cy, int r,
                                        int thickness, int color) {
        fillOctagon(graphics, cx, cy, r, color);
        fillOctagon(graphics, cx, cy, r - thickness, PANEL_FILL_RAISED);
    }

    private static ResourceLocation rl(String path) {
        return PlatformServices.resources().create(AegisAscensionMod.MOD_ID, path);
    }
}
