package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A drop-in replacement for vanilla {@link net.minecraft.client.gui.components.Button}
 * that renders with the same flat-fill-plus-gold-border language as {@link ACGSlider}
 * (and the drawer's own buttons) instead of vanilla's tan three-slice sprite, while
 * keeping every other button behavior (hover, disabled dimming, narration, click sound)
 * identical. This is the default for every {@code ACGButton} — no atlas art required —
 * because {@code ACGTheme.ATLAS} doesn't actually have a generic action-button sprite to
 * skin with; it only has tab-row and panel slices for their own specific widgets.
 *
 * <p>{@code customTexture} is still supported and takes over {@link #renderWidget}
 * entirely when set, for the day a real action-button sheet gets painted — but leaving it
 * null (the default) is the normal, complete way to use this class, not a placeholder
 * state.</p>
 */
public class ACGButton extends AbstractButton {
    /** Mirrors {@code Button.OnPress}, retyped to {@code ACGButton} so a handler can call back into it (e.g. {@code setMessage}). */
    @FunctionalInterface
    public interface OnPress {
        void onPress(ACGButton button);
    }

    /** Texture sheet for the background sprite; null means "fall back to vanilla rendering". */
    private ResourceLocation customTexture;
    private int u;
    private int v;
    private int textureWidth = 256;
    private int textureHeight = 256;
    /** Added past the base UV when hovered/pressed; V-offset siblings included for atlases (like ACGTheme.ATLAS) that lay out button states as stacked rows rather than side by side. */
    private int hoveredUOffset;
    private int hoveredVOffset;
    private int clickedUOffset;
    private int clickedVOffset;
    private ResourceLocation iconTexture;
    private int iconTextureSize = 16;
    private int iconDrawSize = 16;
    /** Centers the icon and keeps the message for narration without drawing its text. */
    private boolean iconOnly;

    /**
     * Optional Inventory-screen styling: flat fill plus a clipped top-left corner, in place
     * of the default gold-trim look. Null means the standard {@link ACGTheme} styling.
     */
    public enum Style {
        /** Primary action — solid orange, dark text. */
        CTA,
        /** Secondary action — dark plate with a light border. */
        PLAIN,
        /** Emphasised action — teal plate, used for the destructive Discard. */
        TEAL
    }

    private Style style;
    /** Raises this button above lower layers; see ACGPerkSelectionScreen's modal z-stack. */
    private float zOffset;

    private final OnPress pressHandler;
    /** True for as long as the primary mouse button is held down after a click landed on this widget. */
    private boolean isPressed;

    public ACGButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, null, 0, 0);
    }

    public ACGButton(int x, int y, int width, int height, Component message, OnPress onPress,
                     ResourceLocation customTexture, int u, int v) {
        super(x, y, width, height, message);
        this.pressHandler = onPress;
        this.customTexture = customTexture;
        this.u = u;
        this.v = v;
    }

    /** Overrides the 256x256 default, for a texture sheet with different dimensions (e.g. a 512-sized atlas). */
    public ACGButton textureSize(int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        return this;
    }

    /** UV offset added to the base (u, v) while hovered/focused, before {@link #clicked}'s offset takes over. */
    public ACGButton hovered(int uOffset, int vOffset) {
        this.hoveredUOffset = uOffset;
        this.hoveredVOffset = vOffset;
        return this;
    }

    /** UV offset added to the base (u, v) while the mouse is held down on this button. */
    public ACGButton clicked(int uOffset, int vOffset) {
        this.clickedUOffset = uOffset;
        this.clickedVOffset = vOffset;
        return this;
    }

    /** Draws this button {@code z} units forward, for use inside a raised modal. */
    public ACGButton zOffset(float z) {
        this.zOffset = z;
        return this;
    }

    /** Applies the Inventory screen's flat card-game button styling. */
    public ACGButton style(Style value) {
        this.style = value;
        return this;
    }

    /** Swaps in a custom texture after construction (e.g. a vanilla-fallback button upgraded to styled art later). */
    public ACGButton texture(ResourceLocation texture, int u, int v) {
        this.customTexture = texture;
        this.u = u;
        this.v = v;
        return this;
    }

    /** Adds a square icon at the left side while preserving the normal button background. */
    public ACGButton icon(ResourceLocation texture, int sourceTextureSize, int drawSize) {
        this.iconTexture = texture;
        this.iconTextureSize = Math.max(1, sourceTextureSize);
        this.iconDrawSize = Math.max(1, drawSize);
        return this;
    }

    /** Renders only a centered icon; the message remains available to narration. */
    public ACGButton iconOnly() {
        this.iconOnly = true;
        return this;
    }

    @Override
    public void onPress() {
        if (pressHandler != null) {
            pressHandler.onPress(this);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            isPressed = true;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isPressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (zOffset != 0.0F) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, zOffset);
            renderWidgetContents(graphics);
            graphics.pose().popPose();
            return;
        }
        renderWidgetContents(graphics);
    }

    private void renderWidgetContents(GuiGraphics graphics) {
        if (customTexture != null) {
            renderTexturedBackground(graphics);
        } else if (style != null) {
            renderStyledBackground(graphics);
        } else {
            renderThemedBackground(graphics);
        }

        int textColor = !active
                ? ACGTheme.TEXT_DISABLED
                : style == Style.CTA ? ACGInventoryStyle.CTA_TEXT
                : style != null ? ACGInventoryStyle.TEXT_CREAM
                : ACGTheme.TEXT_PRIMARY;
        int textCenterX = getX() + width / 2;
        if (iconTexture != null) {
            if (iconOnly) {
                int padding = 2;
                int iconBoxX = getX() + padding;
                int iconBoxY = getY() + padding;
                int iconBoxWidth = Math.max(1, width - padding * 2);
                int iconBoxHeight = Math.max(1, height - padding * 2);
                GeneralClientMethods.blitFittedTexture(
                        graphics,
                        iconTexture,
                        iconBoxX,
                        iconBoxY,
                        iconBoxWidth,
                        iconBoxHeight,
                        iconTextureSize
                );
                if (!active) {
                    graphics.fill(
                            iconBoxX,
                            iconBoxY,
                            iconBoxX + iconBoxWidth,
                            iconBoxY + iconBoxHeight,
                            0x88101018
                    );
                }
                return;
            }
            int drawSize = Math.min(iconDrawSize, Math.max(1, height - 4));
            int iconX = getX() + 3;
            int iconY = getY() + (height - drawSize) / 2;
                GeneralClientMethods.bindAndBlit(
                    graphics,
                    iconTexture,
                    iconX,
                    iconY,
                    0,
                    0,
                    drawSize,
                    drawSize,
                    iconTextureSize,
                    iconTextureSize
            );
            if (!active) {
                graphics.fill(iconX, iconY, iconX + drawSize, iconY + drawSize,
                        0x88101018);
            }
            int textLeft = iconX + drawSize + 3;
            textCenterX = textLeft + Math.max(0, getX() + width - 3 - textLeft) / 2;
        }
        if (style != null) {
            // Drawn shadowless, like the reference design. drawCenteredString always forces
            // a drop shadow, which on dark-on-orange text reads as a muddy double-strike
            // rather than the crisp label the mockup has.
            // Clipped to the button: a label longer than its own bounds used to render
            // straight over the neighbouring buttons rather than being trimmed.
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), width - 6);
            ACGInventoryStyle.text(graphics, font, label, textCenterX,
                    getY() + (height - 8) / 2.0F, 1.0F, textColor,
                    ACGInventoryStyle.ALIGN_CENTER);
        } else {
            GeneralClientMethods.drawCenteredString(graphics, Minecraft.getInstance().font, getMessage(),
                    textCenterX, getY() + (height - 8) / 2, textColor);
        }
    }

    /** Flat plate plus the design's clipped top-left corner triangle. */
    private void renderStyledBackground(GuiGraphics graphics) {
        boolean hovered = active && isHoveredOrFocused();
        int fill;
        int border;
        if (!active) {
            fill = ACGInventoryStyle.BTN_FILL;
            border = ACGInventoryStyle.TEXT_DIM;
        } else {
            switch (style) {
                case CTA -> {
                    fill = ACGInventoryStyle.CTA_FILL;
                    border = ACGInventoryStyle.CTA_BORDER;
                }
                case TEAL -> {
                    fill = ACGInventoryStyle.TEAL_FILL;
                    border = ACGInventoryStyle.TEAL_BORDER;
                }
                default -> {
                    fill = ACGInventoryStyle.BTN_FILL;
                    border = ACGInventoryStyle.BTN_BORDER;
                }
            }
        }
        ACGInventoryStyle.box(graphics, getX(), getY(), width, height, fill, border);
        if (hovered) {
            ACGInventoryStyle.outline(graphics, getX(), getY(), width, height,
                    ACGInventoryStyle.TEXT_CREAM);
        }
        ACGInventoryStyle.cornerTriangle(graphics, getX() + 1, getY() + 1, 4.0F,
                style == Style.CTA ? ACGInventoryStyle.CTA_CORNER : border);
    }

    private void renderTexturedBackground(GuiGraphics graphics) {
        int stateU = u;
        int stateV = v;
        if (active && isPressed) {
            stateU += clickedUOffset;
            stateV += clickedVOffset;
        } else if (active && isHoveredOrFocused()) {
            stateU += hoveredUOffset;
            stateV += hoveredVOffset;
        }

        GeneralClientMethods.bindAndBlit(graphics, customTexture, getX(), getY(), stateU, stateV,
                width, height, textureWidth, textureHeight);
        if (!active) {
            // No dedicated disabled slice in the atlas layout this targets (ACGTheme.ATLAS
            // only has normal/active rows) — dim via overlay instead, matching how
            // ACGCardWidget already darkens a disabled card over its own background art.
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88101018);
        }
    }

    /**
     * Flat fill + 1px gold border, matching {@link ACGSlider}'s own border-drawing code
     * exactly (same {@code GOLD_DIM}/{@code GOLD_BRIGHT} hover swap, same four-fill border)
     * so a button and a slider sitting in the same panel — as they do on the Client Side
     * Setting screen — read as one consistent widget language rather than two different
     * button styles bolted together.
     */
    private void renderThemedBackground(GuiGraphics graphics) {
        int x = getX();
        int y = getY();
        boolean hovered = active && isHoveredOrFocused();
        int fill = !active ? ACGTheme.PANEL_FILL : hovered ? ACGTheme.DRAWER_HOVER_FILL : ACGTheme.PANEL_FILL_RAISED;
        graphics.fill(x, y, x + width, y + height, fill);
        int border = !active ? ACGTheme.GOLD_DIM : hovered ? ACGTheme.GOLD_BRIGHT : ACGTheme.GOLD_DIM;
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    /**
     * Mirrors vanilla {@code Button.Builder}'s {@code .bounds(...).build()} shape so that
     * migrating an existing {@code Button.builder(...)} call site is a type-name swap: see
     * the migration guide in {@code ACGButton}'s class-level usage notes.
     */
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private ResourceLocation texture;
        private int u;
        private int v;
        private int textureWidth = 256;
        private int textureHeight = 256;
        private int hoveredU;
        private int hoveredV;
        private int clickedU;
        private int clickedV;
        private ResourceLocation icon;
        private int iconTextureSize = 16;
        private int iconDrawSize = 16;
        private boolean iconOnly;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder texture(ResourceLocation texture, int u, int v) {
            this.texture = texture;
            this.u = u;
            this.v = v;
            return this;
        }

        public Builder textureSize(int textureWidth, int textureHeight) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            return this;
        }

        public Builder hovered(int uOffset, int vOffset) {
            this.hoveredU = uOffset;
            this.hoveredV = vOffset;
            return this;
        }

        public Builder clicked(int uOffset, int vOffset) {
            this.clickedU = uOffset;
            this.clickedV = vOffset;
            return this;
        }

        public Builder icon(ResourceLocation texture, int sourceTextureSize, int drawSize) {
            this.icon = texture;
            this.iconTextureSize = sourceTextureSize;
            this.iconDrawSize = drawSize;
            return this;
        }

        public Builder iconOnly() {
            this.iconOnly = true;
            return this;
        }

        public ACGButton build() {
            ACGButton button = new ACGButton(x, y, width, height, message, onPress, texture, u, v);
            button.textureSize(textureWidth, textureHeight);
            button.hovered(hoveredU, hoveredV);
            button.clicked(clickedU, clickedV);
            if (icon != null) {
                button.icon(icon, iconTextureSize, iconDrawSize);
            }
            if (iconOnly) {
                button.iconOnly();
            }
            return button;
        }
    }
}
