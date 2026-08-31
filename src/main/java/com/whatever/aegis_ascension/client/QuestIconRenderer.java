package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Shared ResourceLocation-backed quest icon compositions used by the ACG and HUD. */
public final class QuestIconRenderer {
    private static final ResourceLocation VILLAGER_HEAD = GeneralClientMethods.fromNamespaceAndPath(
            "minecraft", "textures/entity/villager/villager.png");

    private QuestIconRenderer() {
    }

    /** Draws the same base villager face and profession overlay at any requested HUD size. */
    public static boolean drawVillagerProfessionIcon(GuiGraphics graphics,
                                                      String profession,
                                                      int x, int y, int size,
                                                      float alpha) {
        if (profession == null || profession.isBlank() || size <= 0) return false;
        boolean drawn = false;
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        if (GeneralClientMethods.resourceExists(VILLAGER_HEAD)) {
            GeneralClientMethods.blitScaledRegion(graphics, VILLAGER_HEAD,
                    x, y, size, size, 8, 8, 8, 8, 64, 64);
            drawn = true;
        }
        ResourceLocation overlay = ResourceLocation.tryParse(
                "minecraft:textures/entity/villager/profession/"
                        + profession.toLowerCase(Locale.ROOT) + ".png");
        if (overlay != null && GeneralClientMethods.resourceExists(overlay)) {
            GeneralClientMethods.blitScaledRegion(graphics, overlay,
                    x, y, size, size, 8, 8, 8, 8, 64, 64);
            drawn = true;
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        return drawn;
    }
}
