package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleFunction;
import java.util.function.DoubleConsumer;

/**
 * A themed drag slider for a {@code [min, max]} double range, flat-filled to match the
 * rest of the ACG chrome instead of vanilla's button-texture slider. {@code onChange}
 * fires on every value change (drag or arrow-key nudge), so callers doing something
 * expensive (a file write, a full rebuild) should debounce there rather than here.
 */
public final class ACGSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer onChange;

    public ACGSlider(int x, int y, int width, int height, double min, double max, double initialValue,
                     DoubleFunction<Component> label, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), normalize(initialValue, min, max));
        this.min = min;
        this.max = max;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    private static double normalize(double actual, double min, double max) {
        double span = max - min;
        return span <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (actual - min) / span));
    }

    public double actualValue() {
        return min + value * (max - min);
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(actualValue()));
    }

    @Override
    protected void applyValue() {
        updateMessage();
        onChange.accept(actualValue());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hovered = isHoveredOrFocused();
        graphics.fill(x, y, x + width, y + height, ACGTheme.PANEL_FILL);
        int fillWidth = Math.round((float) value * (width - 2));
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, ACGTheme.ORANGE_ACTION);
        }
        int border = hovered ? ACGTheme.GOLD_BRIGHT : ACGTheme.GOLD_DIM;
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
        GeneralClientMethods.drawCenteredString(graphics, Minecraft.getInstance().font, getMessage(),
                x + width / 2, y + (height - 8) / 2, ACGTheme.TEXT_PRIMARY);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
