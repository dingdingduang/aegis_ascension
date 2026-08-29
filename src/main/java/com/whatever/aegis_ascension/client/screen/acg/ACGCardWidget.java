package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/**
 * A single, reusable card button used across every ACG mode: Aegis/Perk/Skill-Enhancement
 * offer cards, the Owned Aegis / Owned Perks right-hand grids, Soul Link cards, and
 * Custom Stat cards. Two presentation styles cover every reference layout without
 * duplicating rendering code per mode.
 */
public final class ACGCardWidget extends AbstractButton implements ClippableWidget {
    private int clipTop = ClippableWidget.NO_CLIP_TOP;
    private int clipBottom = ClippableWidget.NO_CLIP_BOTTOM;

    @Override
    public void setClipBounds(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    public enum Presentation {
        /** Full-bleed card art (aegis_card / r_card / sr_card / ssr_card), used for offers. */
        BIG,
        /** Small icon + title + status row, used for collection grids. */
        COMPACT
    }

    public record DetailLine(Component text, int color) {
    }

    private final Presentation presentation;
    private final ResourceLocation cardBackground;
    private final int glowU;
    private final int glowV;
    private final int glowW;
    private final int glowH;
    private final int artU;
    private final int artV;
    private final int artW;
    private final int artH;
    private final ResourceLocation icon;
    private final int iconTextureSize;
    private final Component subtitle;
    private final Component status;
    private final int accentColor;
    private final int statusColor;
    private final List<DetailLine> detailLines;
    private final boolean selected;
    private final Component tooltip;
    private final String statKey;
    private final Consumer<ACGCardWidget> onClick;

    /** Which page of {@link #subtitle} is currently shown; see {@link #advanceDescriptionPage}. */
    private int descriptionPage;
    /** Set by the last {@link #renderBig} call; 1 if the description fit on one page. */
    private int descriptionPageCount = 1;

    private ACGCardWidget(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.title);
        this.presentation = builder.presentation;
        this.cardBackground = builder.cardBackground;
        this.glowU = builder.glowU;
        this.glowV = builder.glowV;
        this.glowW = builder.glowW;
        this.glowH = builder.glowH;
        this.artU = builder.artU;
        this.artV = builder.artV;
        this.artW = builder.artW;
        this.artH = builder.artH;
        this.icon = builder.icon;
        this.iconTextureSize = builder.iconTextureSize;
        this.subtitle = builder.subtitle;
        this.status = builder.status;
        this.accentColor = builder.accentColor;
        this.statusColor = builder.statusColor;
        this.detailLines = builder.detailLines;
        this.selected = builder.selected;
        this.tooltip = builder.tooltip;
        this.statKey = builder.statKey;
        this.onClick = builder.onClick;
        this.active = builder.enabled;
    }

    public static Builder builder(int x, int y, int width, int height, Component title) {
        return new Builder(x, y, width, height, title);
    }

    /** The tooltip to show while hovered, or {@code null} for none. Set via {@link Builder#tooltip}. */
    public Component tooltipText() {
        return tooltip;
    }

    /**
     * The {@code CustomStats} definition key this card represents, or {@code null} if it
     * isn't a stat card. When set, hovering shows the per-source breakdown panel
     * ({@link ACGStatSourceBreakdown}) instead of the plain {@link #tooltipText()}.
     */
    public String statKey() {
        return statKey;
    }

    /** Exposes {@link AbstractButton}'s protected hover flag for the screen's tooltip pass. */
    public boolean isHoveredNow() {
        return this.isHovered;
    }

    /**
     * Advances to the next page of an overlong description, wrapping back to the first
     * page after the last. A no-op if the description fits on one page. Called by
     * {@code ACGPerkSelectionScreen#keyPressed} on the down-arrow key while this card is
     * hovered; {@link #descriptionPageCount} is only accurate after at least one
     * {@link #renderBig} call, which has always happened by the time a key can be pressed.
     */
    public void advanceDescriptionPage() {
        if (descriptionPageCount > 1) {
            descriptionPage = (descriptionPage + 1) % descriptionPageCount;
        }
    }

    /** Whether this card currently has more than one description page to page through. */
    public boolean hasMultipleDescriptionPages() {
        return descriptionPageCount > 1;
    }

    /**
     * Note: no explicit click-sound call here — {@code AbstractWidget#mouseClicked}
     * already plays {@code UI_BUTTON_CLICK} via {@code playDownSound} before invoking
     * this, so adding another would double it up.
     */
    @Override
    public void onPress() {
        if (onClick != null) {
            onClick.accept(this);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    /** Eased toward 1.0 (idle) or {@link #HOVER_SCALE} (hovered) a little each frame. */
    private float scale = 1.0F;
    private static final float HOVER_SCALE = 1.035F;
    private static final float SCALE_EASING = 0.35F;

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClippableWidget.clipped(graphics, clipTop, clipBottom,
                () -> renderCardContents(graphics, mouseX, mouseY, partialTick));
    }

    private void renderCardContents(GuiGraphics graphics, int mouseX, int mouseY,
                                    float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int x = getX();
        int y = getY();

        float target = active && hovered ? HOVER_SCALE : 1.0F;
        scale += (target - scale) * SCALE_EASING;

        graphics.pose().pushPose();
        if (Math.abs(scale - 1.0F) > 0.001F) {
            float centerX = x + width / 2.0F;
            float centerY = y + height / 2.0F;
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.pose().translate(-centerX, -centerY, 0.0F);
        }

        renderBackground(graphics, x, y, hovered);
        if (selected) {
            if (GeneralClientMethods.resourceExists(ACGTheme.SELECTED_FRAME)) {
                GeneralClientMethods.blitScaledRegion(graphics, ACGTheme.SELECTED_FRAME, x - 2, y - 2, width + 4, height + 4,
                        0.0F, 0.0F, ACGTheme.SELECTED_FRAME_TEXTURE_SIZE, ACGTheme.SELECTED_FRAME_TEXTURE_SIZE,
                        ACGTheme.SELECTED_FRAME_TEXTURE_SIZE, ACGTheme.SELECTED_FRAME_TEXTURE_SIZE);
            } else {
                ACGTheme.drawGoldBorder(graphics, x - 1, y - 1, width + 2, height + 2);
            }
        }

        switch (presentation) {
            case BIG -> renderBig(graphics, x, y);
            case COMPACT -> renderCompact(graphics, x, y);
        }

        graphics.pose().popPose();
    }

    private void renderBackground(GuiGraphics graphics, int x, int y, boolean hovered) {
        boolean hasArt = cardBackground != null && GeneralClientMethods.resourceExists(cardBackground);
        if (hasArt) {
            // The art already paints its own border, so a flat accent-color rectangle on
            // top of it just reads as a stray colored box. Hover feedback instead swaps
            // to a "_glow" sibling texture (e.g. ssr_card.png -> ssr_card_glow.png) when
            // one exists, falling back to the plain art if it doesn't.
            ResourceLocation texture = cardBackground;
            if (hovered) {
                ResourceLocation glow = glowVariant(cardBackground);
                if (GeneralClientMethods.resourceExists(glow)) {
                    texture = glow;
                }
            }
            GeneralClientMethods.blitCardArt(
                    graphics, texture, x, y, width, height,
                    new int[]{glowU, glowV, glowW, glowH},
                    new int[]{artU, artV, artW, artH},
                    ACGTheme.CARD_TEXTURE_SIZE, ACGTheme.CARD_TEXTURE_SIZE
            );
            if (!active) {
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x88101018);
            }
            return;
        }
        int background = !active ? 0xCC1C1B22 : hovered ? 0xE0343044 : 0xD9262432;
        graphics.fill(x, y, x + width, y + height, background);
        int border = !active ? 0xFF55505F : hovered ? ACGTheme.GOLD_BRIGHT : accentColor;
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
        if (!active) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x88101018);
        }
    }

    /** {@code foo/bar.png} -> {@code foo/bar_glow.png}, same namespace. */
    private static ResourceLocation glowVariant(ResourceLocation texture) {
        String path = texture.getPath();
        int dot = path.lastIndexOf('.');
        String withGlow = dot >= 0 ? path.substring(0, dot) + "_glow" + path.substring(dot) : path + "_glow";
        return PlatformServices.resources().create(texture.getNamespace(), withGlow);
    }

    private void renderBig(GuiGraphics graphics, int x, int y) {
        var font = Minecraft.getInstance().font;
        int iconSize = Math.min(56, Math.max(28, height / 3));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + 8;
        if (icon != null) {
                GeneralClientMethods.blitScaledRegion(graphics, icon, iconX, iconY, iconSize, iconSize,
                    0.0F, 0.0F, iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
        }

        int textColor = active ? ACGTheme.TEXT_PRIMARY : ACGTheme.TEXT_DISABLED;
        int titleY = iconY + iconSize + 6;
        List<FormattedCharSequence> titleLines = font.split(getMessage(), Math.max(48, width - 12));
        for (int line = 0; line < Math.min(2, titleLines.size()); line++) {
            GeneralClientMethods.drawCenteredString(graphics, font, titleLines.get(line),
                    x + width / 2, titleY + line * 10, textColor);
        }

        if (subtitle != null) {
            int descriptionTop = titleY + Math.min(2, titleLines.size()) * 10 + 4;
            int descriptionBottom = y + height - 16;
            List<FormattedCharSequence> descriptionLines = font.split(subtitle, Math.max(48, width - 14));
            int capacity = Math.max(0, (descriptionBottom - descriptionTop) / 10);

            // Text this long for the card's height gets paginated instead of clipped: one
            // line of capacity is given up for a "page X/Y" footer, and the down-arrow key
            // (while this card is hovered, see ACGPerkSelectionScreen#keyPressed) advances
            // descriptionPage, wrapping back to the first page after the last.
            boolean paginated = descriptionLines.size() > capacity;
            int linesPerPage = paginated ? Math.max(1, capacity - 1) : capacity;
            descriptionPageCount = linesPerPage > 0
                    ? Math.max(1, (descriptionLines.size() + linesPerPage - 1) / linesPerPage)
                    : 1;
            if (descriptionPage >= descriptionPageCount) {
                descriptionPage = 0;
            }

            int startLine = descriptionPage * linesPerPage;
            int shown = Math.max(0, Math.min(linesPerPage, descriptionLines.size() - startLine));
            for (int line = 0; line < shown; line++) {
                GeneralClientMethods.drawCenteredString(graphics, font, descriptionLines.get(startLine + line),
                        x + width / 2, descriptionTop + line * 10,
                        active ? ACGTheme.TEXT_SECONDARY : ACGTheme.TEXT_DISABLED);
            }
            if (descriptionPageCount > 1) {
                GeneralClientMethods.drawCenteredString(graphics, font,
                        GeneralTextMethods.getLiteralString((descriptionPage + 1) + "/" + descriptionPageCount),
                        x + width / 2, descriptionTop + linesPerPage * 10, ACGTheme.TEXT_MUTED);
            }
        }

        if (status != null) {
            GeneralClientMethods.drawCenteredString(graphics, font, status, x + width / 2,
                    y + height - 13, active ? statusColor : ACGTheme.TEXT_DISABLED);
        }
    }

    private void renderCompact(GuiGraphics graphics, int x, int y) {
        var font = Minecraft.getInstance().font;
        int size = 28;
        int iconX = x + 6;
        int iconY = y + 7;
        if (icon != null) {
            if (iconTextureSize == size) {
                GeneralClientMethods.bindAndBlit(graphics, icon, iconX, iconY, 0.0F, 0.0F, size, size, size, size);
            } else {
                GeneralClientMethods.blitScaledRegion(graphics, icon, iconX, iconY, size, size,
                        0.0F, 0.0F, iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
            }
        }

        int textColor = active ? ACGTheme.TEXT_PRIMARY : ACGTheme.TEXT_DISABLED;
        List<FormattedCharSequence> titleLines = font.split(getMessage(), Math.max(42, width - 40));
        for (int line = 0; line < Math.min(2, titleLines.size()); line++) {
            graphics.drawString(font, titleLines.get(line), x + 38, y + 7 + line * 10, textColor, false);
        }

        if (!detailLines.isEmpty()) {
            int lineY = y + 42;
            for (DetailLine detail : detailLines) {
                String clipped = font.plainSubstrByWidth(detail.text().getString(), width - 12);
                graphics.drawString(font, clipped, x + 6, lineY, detail.color(), false);
                lineY += 12;
            }
        } else if (status != null) {
            graphics.drawString(font, status, x + 38, y + height - 15,
                    active ? statusColor : ACGTheme.TEXT_DISABLED, false);
        }
    }

    public static final class Builder {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final Component title;
        private Presentation presentation = Presentation.COMPACT;
        private ResourceLocation cardBackground;
        private int glowU;
        private int glowV;
        private int glowW = ACGTheme.CARD_TEXTURE_SIZE;
        private int glowH = ACGTheme.CARD_TEXTURE_SIZE;
        private int artU;
        private int artV;
        private int artW = ACGTheme.CARD_TEXTURE_SIZE;
        private int artH = ACGTheme.CARD_TEXTURE_SIZE;
        private ResourceLocation icon;
        private int iconTextureSize = 28;
        private Component subtitle;
        private Component status;
        private int accentColor = ACGTheme.GOLD;
        private int statusColor = ACGTheme.TEXT_SECONDARY;
        private List<DetailLine> detailLines = List.of();
        private boolean selected;
        private boolean enabled = true;
        private Component tooltip;
        private String statKey;
        private Consumer<ACGCardWidget> onClick;

        private Builder(int x, int y, int width, int height, Component title) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
        }

        public Builder presentation(Presentation value) {
            this.presentation = value;
            return this;
        }

        /** No separate glow layer: the frame rect is drawn as-is, cover-scaled to fit. */
        public Builder cardBackground(ResourceLocation texture, int u, int v, int w, int h) {
            return cardBackground(texture, u, v, w, h, u, v, w, h);
        }

        /**
         * {@code glowU/V/W/H} is the texture's full glow-inclusive bounds (wider than the
         * frame for art with a soft outer glow, identical to the frame otherwise);
         * {@code frameU/V/W/H} is the tight solid-content rect used to size/position the
         * card consistently with its neighbors. See {@link GeneralClientMethods#blitCardArt}.
         */
        public Builder cardBackground(ResourceLocation texture,
                                       int glowU, int glowV, int glowW, int glowH,
                                       int frameU, int frameV, int frameW, int frameH) {
            this.cardBackground = texture;
            this.glowU = glowU;
            this.glowV = glowV;
            this.glowW = glowW;
            this.glowH = glowH;
            this.artU = frameU;
            this.artV = frameV;
            this.artW = frameW;
            this.artH = frameH;
            return this;
        }

        public Builder icon(ResourceLocation texture, int textureSize) {
            this.icon = texture;
            this.iconTextureSize = textureSize;
            return this;
        }

        public Builder subtitle(Component value) {
            this.subtitle = value;
            return this;
        }

        public Builder status(Component value, int color) {
            this.status = value;
            this.statusColor = color;
            return this;
        }

        public Builder accentColor(int value) {
            this.accentColor = value;
            return this;
        }

        public Builder detailLines(List<DetailLine> value) {
            this.detailLines = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public Builder selected(boolean value) {
            this.selected = value;
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder tooltip(Component value) {
            this.tooltip = value;
            return this;
        }

        public Builder statKey(String value) {
            this.statKey = value;
            return this;
        }

        public Builder onClick(Consumer<ACGCardWidget> value) {
            this.onClick = value;
            return this;
        }

        public ACGCardWidget build() {
            return new ACGCardWidget(this);
        }
    }
}
