package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client HUD overlay that draws the shield icon and the player's current shield
 * amount to its right, at a position chosen in {@link ClientSettings}.
 *
 * <p>The shield value is the client mirror in {@link ClientShieldState}, kept fresh
 * by {@code SyncShieldPacket}. Placement (anchor + offset) and visibility come from
 * {@link ClientSettings}, so they are per-client and edited locally.</p>
 */
@Mod.EventBusSubscriber(
        modid = AegisAscensionMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ShieldHudOverlay {
    private static final ResourceLocation SHIELD_ICON = PlatformServices.resources().create(
            AegisAscensionMod.MOD_ID,
            "textures/gui/commonui/shield1.png"
    );
    private static final int ICON_TEXTURE_SIZE = 30;
    private static final int ICON_RENDER_SIZE = 13;
    private static final int ICON_TEXT_GAP = 2;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private ShieldHudOverlay() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("shield_hud", ShieldHudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        ClientSettings settings = ClientSettings.get();
        if (!settings.showShieldHud) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        float shield = ClientShieldState.get();
        if (shield <= 0.0F) {
            return;
        }

        Font font = minecraft.font;
        String text = Integer.toString((int) Math.ceil(shield));
        int textWidth = font.width(text);
        int elementWidth = ICON_RENDER_SIZE + ICON_TEXT_GAP + textWidth;
        int elementHeight = ICON_RENDER_SIZE;

        int x = anchorX(settings, screenWidth, elementWidth) + settings.shieldHudOffsetX;
        int y = anchorY(settings, screenHeight, elementHeight) + settings.shieldHudOffsetY;

        GeneralClientMethods.blitScaledRegion(graphics, SHIELD_ICON, x, y,
                ICON_RENDER_SIZE, ICON_RENDER_SIZE,
                0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);

        int textY = y + (ICON_RENDER_SIZE - font.lineHeight) / 2 + 1;
        GeneralClientMethods.drawString(graphics, font, GeneralTextMethods.getLiteralString(text),
                x + ICON_RENDER_SIZE + ICON_TEXT_GAP, textY, TEXT_COLOR, true);
    }

    private static int anchorX(ClientSettings settings, int screenWidth, int elementWidth) {
        return switch (settings.shieldHudAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> 0;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - elementWidth;
            case CENTER -> (screenWidth - elementWidth) / 2;
        };
    }

    private static int anchorY(ClientSettings settings, int screenHeight, int elementHeight) {
        return switch (settings.shieldHudAnchor) {
            case TOP_LEFT, TOP_RIGHT -> 0;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - elementHeight;
            case CENTER -> (screenHeight - elementHeight) / 2;
        };
    }
}
