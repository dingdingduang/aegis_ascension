package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Texture locations, palette, and drawing primitives for the Inventory screen's card-game
 * styling — ported from the reference design so the storage view reads as the same UI
 * family as its mockup (arcane disc and rings behind the showcase, framed grid cards with a
 * rarity gem, and flat buttons with a clipped top-left corner).
 *
 * <p>Kept separate from {@link ACGTheme} on purpose: that class owns the mod-wide chrome
 * (the drawer, panels, gold trim) which every other mode shares, while everything here is
 * specific to this one screen's look. Mixing the two palettes would make a change intended
 * for the Inventory bleed into six unrelated modes.</p>
 *
 * <p>Positions are floats rather than the ints vanilla blits use, because the card art is
 * scaled to arbitrary sizes and rounding each intermediate step visibly wobbles the
 * frame edges.</p>
 */
public final class ACGInventoryStyle {
    private ACGInventoryStyle() {
    }

    private static ResourceLocation tex(String name) {
        return PlatformServices.resources().create(
                com.whatever.aegis_ascension.AegisAscensionMod.MOD_ID,
                "textures/gui/inventory/" + name + ".png");
    }

    public static final ResourceLocation DISC = tex("disc");
    public static final int DISC_SIZE = 256;
    public static final ResourceLocation RING = tex("ring");
    public static final int RING_SIZE = 256;
    public static final ResourceLocation GLOW = tex("glow");
    public static final int GLOW_SIZE = 128;
    public static final ResourceLocation GLYPHS = tex("glyphs");
    public static final int GLYPHS_SIZE = 128;
    public static final ResourceLocation SPARKLE = tex("sparkle");
    public static final int SPARKLE_SIZE = 32;
    public static final ResourceLocation CARD_FRAME = tex("card_frame");
    public static final int CARD_FRAME_W = 64;
    public static final int CARD_FRAME_H = 128;
    public static final ResourceLocation GEM = tex("gem");
    public static final int GEM_SIZE = 16;
    public static final ResourceLocation COIN = tex("coin");
    public static final int COIN_SIZE = 32;
    public static final ResourceLocation CHECK = tex("checkmark_circle_green");
    public static final int CHECK_W = 74;
    public static final int CHECK_H = 73;

    // Palette sampled from the design reference.
    public static final int TEXT_CREAM = 0xFFFFFDE8;
    public static final int TEXT_MUTED = 0xFF8A7C6A;
    public static final int TEXT_DIM = 0xFF5A5048;
    public static final int ACCENT_ORANGE = 0xFFE8912F;
    public static final int CTA_FILL = 0xFFE58226;
    public static final int CTA_BORDER = 0xFFF3BE84;
    public static final int CTA_TEXT = 0xFF2A1608;
    public static final int CTA_CORNER = 0xFF7A3F10;
    public static final int BTN_FILL = 0xFF211D1A;
    public static final int BTN_BORDER = 0xFFB0A18C;
    public static final int TEAL_FILL = 0xFF477070;
    public static final int TEAL_BORDER = 0xFF8FCFC6;
    public static final int CARD_BRIGHT = 0xFF3A302A;
    public static final int CARD_DIM = 0xFF3C3531;
    public static final int CARD_FRAME_TINT = 0xFF393029;
    public static final int CARD_LABEL_PLATE = 0xB0221E19;
    public static final int RING_TINT = 0x1CC9A87A;
    public static final int RING_TINT_INNER = 0x15C9A87A;
    public static final int RING_TINT_OUTER = 0x0FC9A87A;
    public static final int GLYPH_TINT = 0x1CD8BB8A;
    public static final int TICK_TINT = 0x2AD8BB8A;
    /** Gem colour for a usable virtual book versus a plain stored item. */
    public static final int GEM_VIRTUAL = 0xFFE8B45A;
    public static final int GEM_ITEM = 0xFF6E8FA8;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    /** Stretches a whole texture into an arbitrary rect. */
    public static void tex(GuiGraphics graphics, ResourceLocation texture,
                           float x, float y, float w, float h, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(w / texW, h / texH, 1.0F);
        graphics.blit(texture, 0, 0, 0, 0, texW, texH, texW, texH);
        pose.popPose();
    }

    /** As {@link #tex}, multiplied by an ARGB tint. */
    public static void texTinted(GuiGraphics graphics, ResourceLocation texture,
                                 float x, float y, float w, float h,
                                 int texW, int texH, int argb) {
        float a = (argb >>> 24) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        graphics.setColor(r, g, b, a);
        tex(graphics, texture, x, y, w, h, texW, texH);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void texSquare(GuiGraphics graphics, ResourceLocation texture,
                                 float cx, float cy, float size, int texSize) {
        tex(graphics, texture, cx - size / 2.0F, cy - size / 2.0F, size, size, texSize, texSize);
    }

    public static void texSquareTinted(GuiGraphics graphics, ResourceLocation texture,
                                       float cx, float cy, float size, int texSize, int argb) {
        texTinted(graphics, texture, cx - size / 2.0F, cy - size / 2.0F, size, size,
                texSize, texSize, argb);
    }

    /**
     * As {@link #texSquareTinted}, spun {@code degrees} clockwise about its own centre.
     * The rotation is applied around the centre point rather than the texture's origin, so
     * a spinning ring stays concentric with the item instead of orbiting it.
     */
    public static void texSquareSpinning(GuiGraphics graphics, ResourceLocation texture,
                                         float cx, float cy, float size, int texSize,
                                         int argb, float degrees) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        pose.translate(-cx, -cy, 0.0F);
        texSquareTinted(graphics, texture, cx, cy, size, texSize, argb);
        pose.popPose();
    }

    /**
     * Multiplies an ARGB alpha by {@code factor}, clamping the <em>result</em> rather than
     * the factor — so a value above 1 brightens. {@link ACGTheme#scaleAlpha} clamps the
     * factor to 0..1 and so can only ever fade.
     */
    public static int breatheAlpha(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, factor));
        return (Math.min(255, alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public static void rect(GuiGraphics graphics, float x, float y, float w, float h, int argb) {
        graphics.fill(Mth.floor(x), Mth.floor(y), Mth.ceil(x + w), Mth.ceil(y + h), argb);
    }

    /** A 1px outline drawn as four thin fills. */
    public static void outline(GuiGraphics graphics, float x, float y, float w, float h, int argb) {
        rect(graphics, x, y, w, 1, argb);
        rect(graphics, x, y + h - 1, w, 1, argb);
        rect(graphics, x, y, 1, h, argb);
        rect(graphics, x + w - 1, y, 1, h, argb);
    }

    public static void box(GuiGraphics graphics, float x, float y, float w, float h,
                           int fill, int border) {
        rect(graphics, x, y, w, h, fill);
        outline(graphics, x, y, w, h, border);
    }

    /**
     * The clipped triangle tucked into the top-left of every button in the design. Drawn as
     * a stack of shrinking rows since there's no triangle primitive.
     */
    public static void cornerTriangle(GuiGraphics graphics, float x, float y,
                                      float size, int argb) {
        int rows = Math.max(1, Mth.floor(size));
        for (int i = 0; i < rows; i++) {
            rect(graphics, x, y + i, rows - i, 1, argb);
        }
    }

    /** One full out-and-back marquee sweep, including the pauses at each end. */
    private static final long MARQUEE_PERIOD_MS = 4_000L;

    /**
     * Draws a label centred in {@code boxWidth}, scrolling it on hover when it's too long
     * to fit.
     *
     * <p>A name that fits is simply centred. One that doesn't is truncated as before until
     * the card is hovered, at which point the full text slides out and back within a
     * scissor clipped to the label box — so the overflow is readable on demand without the
     * card ever getting wider or the text spilling onto its neighbours. The sweep pauses at
     * both ends so the start and end of a long name are both legible rather than sliding
     * past continuously.</p>
     */
    public static void scrollingText(GuiGraphics graphics, Font font, String text,
                                     float x, float y, float boxWidth, float scale,
                                     int argb, boolean hovered, long now) {
        int available = (int) (boxWidth / scale);
        int fullWidth = font.width(text);
        if (fullWidth <= available) {
            text(graphics, font, text, x + boxWidth / 2.0F, y, scale, argb, ALIGN_CENTER);
            return;
        }
        if (!hovered) {
            text(graphics, font, font.plainSubstrByWidth(text, available),
                    x + boxWidth / 2.0F, y, scale, argb, ALIGN_CENTER);
            return;
        }
        float shift = (fullWidth - available) * marqueeProgress(now) * scale;
        graphics.enableScissor(Mth.floor(x), Mth.floor(y - 1.0F),
                Mth.ceil(x + boxWidth), Mth.ceil(y + 9.0F * scale + 1.0F));
        text(graphics, font, text, x - shift, y, scale, argb, ALIGN_LEFT);
        graphics.disableScissor();
    }

    /** 0..1 sweep with a hold at each end, so both ends of a long name stay readable. */
    private static float marqueeProgress(long now) {
        float t = Math.floorMod(now, MARQUEE_PERIOD_MS) / (float) MARQUEE_PERIOD_MS;
        if (t < 0.15F) {
            return 0.0F;
        }
        if (t < 0.50F) {
            return (t - 0.15F) / 0.35F;
        }
        if (t < 0.65F) {
            return 1.0F;
        }
        return 1.0F - (t - 0.65F) / 0.35F;
    }

    /** Draws text at an arbitrary scale, since the vanilla font is fixed at 9px tall. */
    public static void text(GuiGraphics graphics, Font font, String content,
                            float x, float y, float scale, int argb, int align) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        int width = font.width(content);
        int dx = switch (align) {
            case ALIGN_CENTER -> -width / 2;
            case ALIGN_RIGHT -> -width;
            default -> 0;
        };
        graphics.drawString(font, content, dx, 0, argb, false);
        pose.popPose();
    }

    /**
     * The concentric rings, tick marks, glyphs, and sparkles that sit behind the showcase
     * item. {@code radius} scales the whole arrangement so it tracks the panel width.
     */
    public static void drawArcaneCircle(GuiGraphics graphics, float cx, float cy, float radius) {
        drawArcaneCircle(graphics, cx, cy, radius, 0.5F, 0.0F);
    }

    /**
     * @param pulse 0..1 breathing value; scales each ring's size and alpha so the whole
     *              arrangement swells with the glow rather than sitting inert behind it.
     * @param spinDegrees clockwise rotation applied to the middle ring only. Spinning all
     *                    three would read as the entire backdrop turning; leaving the inner
     *                    and outer rings fixed makes the motion legible as one moving band.
     */
    public static void drawArcaneCircle(GuiGraphics graphics, float cx, float cy, float radius,
                                        float pulse, float spinDegrees) {
        float size = 0.96F + pulse * 0.08F;
        float alpha = 0.70F + pulse * 0.60F;

        texSquare(graphics, DISC, cx, cy, radius * 4.2F * size, DISC_SIZE);
        texSquareSpinning(graphics, RING, cx, cy, radius * 2.0F * size, RING_SIZE,
                breatheAlpha(RING_TINT, alpha), spinDegrees);
        texSquareTinted(graphics, RING, cx, cy, radius * 1.48F * size, RING_SIZE,
                breatheAlpha(RING_TINT_INNER, alpha));
        texSquareTinted(graphics, RING, cx, cy, radius * 2.84F * size, RING_SIZE,
                breatheAlpha(RING_TINT_OUTER, alpha));

        // Tick marks ride the middle ring's rotation so they read as marks *on* that band.
        double spinRadians = Math.toRadians(spinDegrees);
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2 / 24) * i + spinRadians;
            rect(graphics, cx + (float) Math.cos(angle) * radius * size,
                    cy + (float) Math.sin(angle) * radius * size, 1.0F, 1.0F,
                    breatheAlpha(TICK_TINT, alpha));
        }

        texSquareTinted(graphics, GLYPHS, cx, cy, radius * 1.44F * size, GLYPHS_SIZE,
                breatheAlpha(GLYPH_TINT, alpha));
    }

    /** Sparkle emitters. Two always run; the rest skip cycles, so 2..6 are alive at once. */
    private static final int SPARKLE_SLOTS = 6;
    private static final int SPARKLE_ALWAYS_ON = 2;
    /** Per-sparkle size multiplier range, against the base size below. */
    private static final float SPARKLE_MIN_SCALE = 0.75F;
    private static final float SPARKLE_MAX_SCALE = 2.50F;
    /** How long one sparkle takes to drift out and fade, in milliseconds. */
    private static final long SPARKLE_LIFETIME_MS = 5_000L;

    /**
     * Drifting sparkles: each spawns near the centre, travels outward, and fades over
     * {@link #SPARKLE_LIFETIME_MS}.
     *
     * <p>Deliberately stateless — every sparkle's position, angle and opacity is a pure
     * function of the clock and its slot index, so there is no particle list to allocate,
     * tick, or reset when the screen switches modes. Each slot re-randomises its direction
     * per cycle from a hash of (slot, cycle), which is what stops all five from tracing the
     * same path every five seconds.</p>
     */
    public static void drawSparkles(GuiGraphics graphics, float cx, float cy, float radius,
                                    long now) {
        for (int slot = 0; slot < SPARKLE_SLOTS; slot++) {
            // Staggered so the slots don't all spawn on the same frame.
            long staggered = now + slot * (SPARKLE_LIFETIME_MS / SPARKLE_SLOTS);
            long cycle = Math.floorDiv(staggered, SPARKLE_LIFETIME_MS);
            int seed = hash(slot, cycle);
            // Optional slots sit out roughly half their cycles, varying the live count.
            if (slot >= SPARKLE_ALWAYS_ON && (seed & 1) == 0) {
                continue;
            }

            float life = Math.floorMod(staggered, SPARKLE_LIFETIME_MS)
                    / (float) SPARKLE_LIFETIME_MS;
            double angle = Math.toRadians((seed >>> 8) % 360);
            float start = radius * (0.04F + ((seed >>> 4) & 0xF) / 15.0F * 0.14F);
            float distance = start + life * radius * 1.05F;
            float x = cx + (float) Math.cos(angle) * distance;
            float y = cy + (float) Math.sin(angle) * distance;

            // sin gives a fade in *and* out, so a sparkle never pops into view at full
            // brightness the way a plain (1 - life) ramp would.
            float fade = (float) Math.sin(life * Math.PI);
            int sparkleAlpha = Math.round(0xE0 * fade);
            if (sparkleAlpha <= 0) {
                continue;
            }
            // Size drawn from bits the angle and spawn radius don't use, so a big sparkle
            // isn't correlated with a particular direction. Re-rolled each cycle, so one
            // slot varies between spawns rather than always being the large one.
            float sizeScale = SPARKLE_MIN_SCALE
                    + ((seed >>> 20) & 0xFF) / 255.0F * (SPARKLE_MAX_SCALE - SPARKLE_MIN_SCALE);
            float sparkleSize = radius * (0.13F - 0.05F * life) * sizeScale;
            texSquareTinted(graphics, SPARKLE, x, y, sparkleSize, SPARKLE_SIZE,
                    (sparkleAlpha << 24) | 0x00FFFFFF);
        }
    }

    /** Deterministic per-(slot, cycle) scramble; splitmix64-style finaliser. */
    private static int hash(int slot, long cycle) {
        long h = slot * 0x9E3779B97F4A7C15L ^ cycle * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (int) (h & 0x7FFFFFFFL);
    }
}
