package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.client.screen.collectiontabs.DevouredItems.DevouredItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * One devoured item in the Devoured tab's grid, drawn in the same framed-card style as the
 * Inventory so the two collection views read as one UI family.
 *
 * <p>The count badge shows how many attribute modifiers were inherited from the item, which
 * is the number that actually matters here — a devoured item is a single entry, so a stack
 * count would always read "x1". That same count is what the rarity gem above it is read
 * from, so the two halves of the card describe one thing.</p>
 */
public final class ACGDevourCardWidget extends AbstractButton implements ClippableWidget {
    private static final float COUNT_SCALE = 0.85F;
    private static final float COUNT_PLATE_PADDING = 10.0F;

    private final DevouredItem item;
    private final ItemStack icon;
    private final boolean selected;
    private final Consumer<String> onSelect;

    private int clipTop = ClippableWidget.NO_CLIP_TOP;
    private int clipBottom = ClippableWidget.NO_CLIP_BOTTOM;

    public ACGDevourCardWidget(int x, int y, int width, int height,
                               DevouredItem item, ItemStack icon, boolean selected,
                               Consumer<String> onSelect) {
        super(x, y, width, height,
                com.whatever.aegis_ascension.client.screen.collectiontabs.DevouredItems
                        .itemName(item.itemId()));
        this.item = item;
        this.icon = icon;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public String itemId() {
        return item.itemId();
    }

    @Override
    public void setClipBounds(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    @Override
    public void onPress() {
        if (onSelect != null) {
            onSelect.accept(item.itemId());
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClippableWidget.clipped(graphics, clipTop, clipBottom, () -> renderCard(graphics));
    }

    private void renderCard(GuiGraphics graphics) {
        float x = getX();
        float y = getY();
        boolean hovered = isHoveredOrFocused();
        var font = Minecraft.getInstance().font;

        ACGInventoryStyle.rect(graphics, x, y, width, height, ACGInventoryStyle.CARD_DIM);
        ACGInventoryStyle.texTinted(graphics, ACGInventoryStyle.CARD_FRAME, x, y, width, height,
                ACGInventoryStyle.CARD_FRAME_W, ACGInventoryStyle.CARD_FRAME_H,
                ACGInventoryStyle.CARD_FRAME_TINT);
        // Rarity gem, same palette as the Inventory's — here the tier is read from how many
        // attributes the item carried, against the thresholds in devour_client_setting.json.
        ACGInventoryStyle.texSquareTinted(graphics, ACGInventoryStyle.GEM,
                x + width / 2.0F, y + 8.0F, 6.0F, ACGInventoryStyle.GEM_SIZE,
                com.whatever.aegis_ascension.client.screen.collectiontabs.DevouredItems
                        .rarityColor(item));

        int iconSize = Math.min(32, width - 12);
        int iconX = (int) x + (width - iconSize) / 2;
        int iconY = (int) y + 14;
        graphics.pose().pushPose();
        graphics.pose().translate(iconX, iconY, 0.0F);
        graphics.pose().scale(iconSize / 16.0F, iconSize / 16.0F, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();

        String countLabel = "x" + item.attributes().size();
        float plateY = y + height - 22.0F;
        float plateWidth = Math.min(width - 6.0F,
                font.width(countLabel) * COUNT_SCALE + COUNT_PLATE_PADDING);
        ACGInventoryStyle.rect(graphics, x + (width - plateWidth) / 2.0F, plateY,
                plateWidth, 10.0F, ACGInventoryStyle.CARD_LABEL_PLATE);
        ACGInventoryStyle.text(graphics, font, countLabel, x + width / 2.0F, plateY + 1.5F,
                COUNT_SCALE, ACGInventoryStyle.TEXT_CREAM, ACGInventoryStyle.ALIGN_CENTER);

        ACGInventoryStyle.scrollingText(graphics, font, getMessage().getString(),
                x + 3.0F, y + height - 10.0F, width - 6.0F, 0.75F,
                ACGInventoryStyle.TEXT_MUTED, hovered, System.currentTimeMillis());

        if (selected) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, ACGInventoryStyle.TEAL_BORDER);
        } else if (hovered) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, 0x88FFFDE8);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
