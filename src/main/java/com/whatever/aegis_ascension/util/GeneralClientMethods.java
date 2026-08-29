package com.whatever.aegis_ascension.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only rendering and resource helpers.
 *
 * <p>All methods in this class are candidates for replacement by a target-specific
 * renderer. In particular, callers should not directly depend on GuiGraphics,
 * PoseStack, or the resource manager.</p>
 */
public final class GeneralClientMethods {
    private static final Map<ResourceLocation, Boolean> RESOURCE_EXISTS_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, int[]> OPAQUE_BOUNDS_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, int[]> FRAME_BOUNDS_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZE_CACHE =
            new ConcurrentHashMap<>();

    private static final float MAX_GLOW_OVERFLOW_PX = 4.0F;

    private GeneralClientMethods() {
    }

    public static Attribute resolveAttribute(ResourceLocation attributeId) {
        return PlatformServices.attributes().resolve(attributeId);
    }

    public static Item resolveItem(ResourceLocation itemId) {
        return PlatformServices.registries().resolveItem(itemId);
    }

    public static ResourceLocation getItemKey(Item item) {
        return PlatformServices.registries().itemKey(item);
    }

    public static void drawCenteredString(GuiGraphics graphics, Font font, String text,
                                          int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    public static void drawCenteredString(GuiGraphics graphics, Font font, Component text,
                                          int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    public static void drawCenteredString(GuiGraphics graphics, Font font,
                                          net.minecraft.util.FormattedCharSequence text,
                                          int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    public static void drawString(GuiGraphics graphics, Font font, Component text,
                                  int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public static void bindAndBlit(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, float u, float v, int width, int height,
                                   int textureWidth, int textureHeight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public static void blitScaledRegion(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int destWidth, int destHeight,
                                        float u, float v, int srcWidth, int srcHeight,
                                        int textureWidth, int textureHeight) {
        if (destWidth <= 0 || destHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        float scaleX = destWidth / (float) srcWidth;
        float scaleY = destHeight / (float) srcHeight;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        bindAndBlit(graphics, texture, 0, 0, u, v, srcWidth, srcHeight,
                textureWidth, textureHeight);
        graphics.pose().popPose();
    }

    public static void blitFittedTexture(GuiGraphics graphics, ResourceLocation texture,
                                         int boxX, int boxY, int boxWidth, int boxHeight,
                                         int fallbackSize) {
        if (boxWidth <= 0 || boxHeight <= 0) {
            return;
        }
        int[] textureSize = detectTextureSize(texture, Math.max(1, fallbackSize));
        int sourceWidth = Math.max(1, textureSize[0]);
        int sourceHeight = Math.max(1, textureSize[1]);
        double scale = Math.min(boxWidth / (double) sourceWidth,
                boxHeight / (double) sourceHeight);
        int drawWidth = Math.min(boxWidth, Math.max(1, (int) Math.round(sourceWidth * scale)));
        int drawHeight = Math.min(boxHeight, Math.max(1, (int) Math.round(sourceHeight * scale)));
        int drawX = boxX + (boxWidth - drawWidth) / 2;
        int drawY = boxY + (boxHeight - drawHeight) / 2;
        blitScaledRegion(graphics, texture, drawX, drawY, drawWidth, drawHeight,
                0.0F, 0.0F, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    public static void blitCoverRegion(GuiGraphics graphics, ResourceLocation texture,
                                       int x, int y, int destWidth, int destHeight,
                                       float u, float v, int srcWidth, int srcHeight,
                                       int textureWidth, int textureHeight) {
        if (destWidth <= 0 || destHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        float[] crop = coverCrop(u, v, srcWidth, srcHeight, destWidth, destHeight);
        blitScaledRegion(graphics, texture, x, y, destWidth, destHeight,
                crop[0], crop[1], Math.round(crop[2]), Math.round(crop[3]),
                textureWidth, textureHeight);
    }

    private static float[] coverCrop(float u, float v, int srcWidth, int srcHeight,
                                     int destWidth, int destHeight) {
        float destAspect = destWidth / (float) destHeight;
        float srcAspect = srcWidth / (float) srcHeight;
        float cropU = u;
        float cropV = v;
        int cropWidth = srcWidth;
        int cropHeight = srcHeight;
        if (srcAspect > destAspect) {
            cropWidth = Math.round(srcHeight * destAspect);
            cropU = u + (srcWidth - cropWidth) / 2.0F;
        } else if (srcAspect < destAspect) {
            cropHeight = Math.round(srcWidth / destAspect);
            cropV = v + (srcHeight - cropHeight) / 2.0F;
        }
        return new float[]{cropU, cropV, cropWidth, cropHeight};
    }

    public static void blitCardArt(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int destWidth, int destHeight,
                                   int[] glowBounds, int[] frameBounds,
                                   int textureWidth, int textureHeight) {
        if (destWidth <= 0 || destHeight <= 0 || frameBounds[2] <= 0 || frameBounds[3] <= 0) {
            return;
        }
        float[] frameCrop = coverCrop(frameBounds[0], frameBounds[1], frameBounds[2],
                frameBounds[3], destWidth, destHeight);
        float scale = destWidth / frameCrop[2];

        if (glowBounds[2] > 0 && glowBounds[3] > 0 && !Arrays.equals(glowBounds, frameBounds)) {
            float maxExtension = MAX_GLOW_OVERFLOW_PX / scale;
            float frameRight = frameCrop[0] + frameCrop[2];
            float frameBottom = frameCrop[1] + frameCrop[3];
            float glowU = Math.max(glowBounds[0], frameCrop[0] - maxExtension);
            float glowV = Math.max(glowBounds[1], frameCrop[1] - maxExtension);
            float glowRight = Math.min(glowBounds[0] + glowBounds[2], frameRight + maxExtension);
            float glowBottom = Math.min(glowBounds[1] + glowBounds[3], frameBottom + maxExtension);
            float glowW = glowRight - glowU;
            float glowH = glowBottom - glowV;
            float glowX = x + (glowU - frameCrop[0]) * scale;
            float glowY = y + (glowV - frameCrop[1]) * scale;
            int glowDestW = Math.round(glowW * scale);
            int glowDestH = Math.round(glowH * scale);
            blitScaledRegion(graphics, texture, Math.round(glowX), Math.round(glowY),
                    glowDestW, glowDestH, glowU, glowV, Math.round(glowW), Math.round(glowH),
                    textureWidth, textureHeight);
        }

        blitScaledRegion(graphics, texture, x, y, destWidth, destHeight,
                frameCrop[0], frameCrop[1], Math.round(frameCrop[2]), Math.round(frameCrop[3]),
                textureWidth, textureHeight);
    }

    public static boolean resourceExists(ResourceLocation location) {
        return RESOURCE_EXISTS_CACHE.computeIfAbsent(location, key ->
                Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    public static void clearResourceExistsCache() {
        RESOURCE_EXISTS_CACHE.clear();
    }

    public static int[] detectOpaqueBounds(ResourceLocation texture, int fallbackSize) {
        return OPAQUE_BOUNDS_CACHE.computeIfAbsent(texture,
                key -> computeBoundsAtThreshold(key, 1, fallbackSize));
    }

    public static int[] detectFrameBounds(ResourceLocation texture, int fallbackSize) {
        return FRAME_BOUNDS_CACHE.computeIfAbsent(texture,
                key -> computeBoundsAtThreshold(key, 200, fallbackSize));
    }

    private static int[] computeBoundsAtThreshold(ResourceLocation texture, int alphaThreshold,
                                                   int fallbackSize) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (!resource.isPresent()) {
            return new int[]{0, 0, fallbackSize, fallbackSize};
        }
        try (InputStream stream = resource.get().open();
             NativeImage image = NativeImage.read(stream)) {
            int width = image.getWidth();
            int height = image.getHeight();
            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (((image.getPixelRGBA(x, y) >>> 24) & 0xFF) >= alphaThreshold) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < minX || maxY < minY) {
                return new int[]{0, 0, width, height};
            }
            return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
        } catch (IOException exception) {
            return new int[]{0, 0, fallbackSize, fallbackSize};
        }
    }

    public static int[] detectTextureSize(ResourceLocation texture, int fallbackSize) {
        return TEXTURE_SIZE_CACHE.computeIfAbsent(texture, key -> {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(key);
            if (!resource.isPresent()) {
                return new int[]{fallbackSize, fallbackSize};
            }
            try (InputStream stream = resource.get().open();
                 NativeImage image = NativeImage.read(stream)) {
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (IOException exception) {
                return new int[]{fallbackSize, fallbackSize};
            }
        });
    }

    public static void clearOpaqueBoundsCache() {
        OPAQUE_BOUNDS_CACHE.clear();
        FRAME_BOUNDS_CACHE.clear();
        TEXTURE_SIZE_CACHE.clear();
    }
}
