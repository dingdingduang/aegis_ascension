package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.storage.StoredItem;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.resources.ResourceLocation;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;

import java.util.function.IntConsumer;

/**
 * One storage card in the right-hand grid: item icon, quantity, and selection highlight.
 * Mirrors the reference layout's item tiles — icon centred, label beneath, selected tile
 * ringed in the accent colour.
 */
public final class ACGStorageRowWidget extends AbstractButton implements ClippableWidget {
    private int clipTop = ClippableWidget.NO_CLIP_TOP;
    private int clipBottom = ClippableWidget.NO_CLIP_BOTTOM;

    @Override
    public void setClipBounds(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    private static final float COUNT_SCALE = 0.85F;
    /** Total horizontal padding around the count text inside its plate. */
    private static final float COUNT_PLATE_PADDING = 10.0F;

    private final int index;
    private final StoredItem row;
    private final boolean selected;
    private final IntConsumer onSelect;

    public ACGStorageRowWidget(int x, int y, int width, int height,
                               int index, StoredItem row, boolean selected, IntConsumer onSelect) {
        // displayComponent(), so narration reads the book's real name and not its
        // inert Items.PAPER placeholder.
        super(x, y, width, height, row.displayComponent());
        this.index = index;
        this.row = row;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public boolean isHoveredNow() {
        return this.isHovered;
    }

    public StoredItem row() {
        return row;
    }

    /** This card's index in the server's storage list, used as the drag source/target. */
    public int storageIndex() {
        return index;
    }

    @Override
    public void onPress() {
        if (onSelect != null) {
            onSelect.accept(index);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClippableWidget.clipped(graphics, clipTop, clipBottom,
                () -> renderCardContents(graphics, mouseX, mouseY, partialTick));
    }

    private void renderCardContents(GuiGraphics graphics, int mouseX, int mouseY,
                                    float partialTick) {
        float x = getX();
        float y = getY();
        boolean hovered = isHoveredOrFocused();
        var font = Minecraft.getInstance().font;

        // Base plate, then the frame art tinted over it — the frame carries the card's
        // arched top and bevelled edges, so it goes on top of the fill rather than behind.
        ACGInventoryStyle.rect(graphics, x, y, width, height,
                row.isVirtual() ? ACGInventoryStyle.CARD_BRIGHT : ACGInventoryStyle.CARD_DIM);
        ACGInventoryStyle.texTinted(graphics, ACGInventoryStyle.CARD_FRAME, x, y, width, height,
                ACGInventoryStyle.CARD_FRAME_W, ACGInventoryStyle.CARD_FRAME_H,
                ACGInventoryStyle.CARD_FRAME_TINT);

        // Rarity gem, using the same tier palette as the shop slot the item came from.
        ACGInventoryStyle.texSquareTinted(graphics, ACGInventoryStyle.GEM,
                x + width / 2.0F, y + 8.0F, 6.0F, ACGInventoryStyle.GEM_SIZE,
                row.rarityColor());

        int iconSize = Math.min(32, width - 12);
        int iconX = (int) x + (width - iconSize) / 2;
        int iconY = (int) y + 14;
        drawRowIcon(graphics, row, iconX, iconY, iconSize);

        // Label plate at the foot of the card, matching the reference's inset name strip.
        // Quantities here routinely exceed a vanilla stack (that's the point of the bank),
        // so the count is drawn by hand rather than via renderItemDecorations, which would
        // cap its display at the prototype's own count of 1.
        String countLabel = "x" + row.count();
        float plateY = y + height - 22.0F;
        // Plate hugs the count text rather than spanning the card: a full-width bar behind
        // a two-character "x1" reads as a stray stripe. Padded either side, and clamped so
        // a long count (x9999) still can't overflow the card.
        float plateWidth = Math.min(width - 6.0F,
                font.width(countLabel) * COUNT_SCALE + COUNT_PLATE_PADDING);
        ACGInventoryStyle.rect(graphics, x + (width - plateWidth) / 2.0F, plateY,
                plateWidth, 10.0F, ACGInventoryStyle.CARD_LABEL_PLATE);
        ACGInventoryStyle.text(graphics, font, countLabel,
                x + width / 2.0F, plateY + 1.5F, COUNT_SCALE,
                ACGInventoryStyle.TEXT_CREAM, ACGInventoryStyle.ALIGN_CENTER);

        ACGInventoryStyle.scrollingText(graphics, font, row.displayName(),
                x + 3.0F, y + height - 10.0F, width - 6.0F, 0.75F,
                ACGInventoryStyle.TEXT_MUTED, hovered, System.currentTimeMillis());

        if (selected) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, ACGInventoryStyle.TEAL_BORDER);
        } else if (hovered) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, 0x88FFFDE8);
        }
    }

    /**
     * Virtual books have no item model — their prototype is an inert placeholder — so they
     * blit their configured texture instead of going through the item renderer.
     */
    public static void drawRowIcon(GuiGraphics graphics, StoredItem row, int x, int y, int size) {
        if (row.isVirtual()) {
            VirtualItems.Definition definition = VirtualItems.byId(row.virtualId());
            ResourceLocation texture = definition == null ? null : definition.iconTexture();
            if (texture != null && GeneralClientMethods.resourceExists(texture)) {
                ACGTheme.drawVirtualItemIcon(graphics, texture, x, y, size);
                return;
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
        graphics.renderItem(row.prototype(), 0, 0);
        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
