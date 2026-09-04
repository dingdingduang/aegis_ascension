package com.whatever.aegis_ascension.client.screen.acg;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Header row of a collapsible group: an open band (rules above and below, no side edges)
 * with left-aligned gold text and a rotating chevron, so it reads as a label for the rows
 * beneath it rather than as another pressable control.
 */
public class ACGSectionHeader extends AbstractButton {
    private final BooleanSupplier expanded;
    private final Runnable toggle;
    private float chevronProgress;

    public ACGSectionHeader(int x, int y, int width, int height, Component label,
                            BooleanSupplier expanded, Runnable toggle) {
        super(x, y, width, height, label);
        this.expanded = expanded;
        this.toggle = toggle;
        this.chevronProgress = expanded.getAsBoolean() ? 1.0F : 0.0F;
    }

    /** Drives the chevron from a caller's own easing; 0 is collapsed, 1 is expanded. */
    public void chevronProgress(float progress) {
        this.chevronProgress = progress;
    }

    @Override
    public void onPress() {
        toggle.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX,
                                int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int fill = hovered
                ? ACGTheme.DRAWER_HOVER_FILL : ACGTheme.PANEL_FILL_RAISED;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, fill);
        graphics.fill(getX(), getY(), getX() + width, getY() + 1,
                ACGTheme.GOLD_DIM);
        graphics.fill(getX(), getY() + height - 1,
                getX() + width, getY() + height, ACGTheme.GOLD_DIM);
        var font = Minecraft.getInstance().font;
        int textColor = hovered ? ACGTheme.GOLD_BRIGHT : ACGTheme.GOLD;
        graphics.drawString(font, getMessage(), getX() + 22,
                getY() + (height - 8) / 2, textColor, false);

        float angle = chevronProgress * 90.0F;
        int cx = getX() + 12;
        int cy = getY() + height / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(
                com.mojang.math.Axis.ZP.rotationDegrees(angle));
        graphics.pose().translate(-cx, -cy, 0.0F);
        graphics.drawString(font, ">", cx - 3, cy - 4, textColor, false);
        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
        narration.add(NarratedElementType.USAGE, getTranslatableString(
                expanded.getAsBoolean()
                        ? "screen.aegis_ascension.acg.section.expanded"
                        : "screen.aegis_ascension.acg.section.collapsed"));
    }
}
