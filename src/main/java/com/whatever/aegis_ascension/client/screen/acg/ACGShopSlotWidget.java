package com.whatever.aegis_ascension.client.screen.acg;

import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.IntConsumer;

/**
 * One daily-shop slot: the item on sale, its stack count, and its configured currency price.
 *
 * <p>Three visual states, all derived from data the server sent rather than from local
 * guesses — sold out (dimmed, struck price), unaffordable (red price, inert), and buyable
 * (gold border, hover highlight). The widget is {@code active} only in the buyable case, so
 * a sold-out or unaffordable slot can't even fire a packet; the server re-checks both
 * anyway, this just avoids the pointless round trip.</p>
 */
public final class ACGShopSlotWidget extends AbstractButton implements ClippableWidget {
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

    /** Height:width of checkmark_circle_green.png, so the tick isn't stretched. */
    private static final float CHECK_ASPECT =
            ACGInventoryStyle.CHECK_H / (float) ACGInventoryStyle.CHECK_W;

    private final int slotIndex;
    private final ShopOffer offer;
    private final boolean affordable;
    private final IntConsumer onBuy;

    public ACGShopSlotWidget(int x, int y, int width, int height,
                             int slotIndex, ShopOffer offer, boolean affordable,
                             IntConsumer onBuy) {
        super(x, y, width, height, displayName(offer));
        this.slotIndex = slotIndex;
        this.offer = offer;
        this.affordable = affordable;
        this.onBuy = onBuy;
        this.active = !offer.purchased() && affordable;
    }

    public ShopOffer offer() {
        return offer;
    }

    /** The book's icon texture, or null when this offer is a real item. */
    private static ResourceLocation virtualIcon(ShopOffer offer) {
        if (!offer.isVirtual()) {
            return null;
        }
        VirtualItems.Definition definition = VirtualItems.byId(offer.virtualId());
        ResourceLocation texture = definition == null ? null : definition.iconTexture();
        return texture != null && GeneralClientMethods.resourceExists(texture) ? texture : null;
    }

    /** Virtual books are named from their lang key, not from the icon item they borrow. */
    private static Component displayName(ShopOffer offer) {
        if (offer.isVirtual()) {
            VirtualItems.Definition definition = VirtualItems.byId(offer.virtualId());
            if (definition != null) {
                return GeneralTextMethods.getTranslatableString(definition.nameKey());
            }
        }
        return offer.stack().getHoverName();
    }

    /** Exposes the hover flag for the screen's tooltip pass, matching {@link ACGCardWidget}. */
    public boolean isHoveredNow() {
        return this.isHovered;
    }

    @Override
    public void onPress() {
        if (onBuy != null) {
            onBuy.accept(slotIndex);
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
        boolean sold = offer.purchased();
        boolean hovered = active && isHoveredOrFocused();
        var font = Minecraft.getInstance().font;

        // Same framed-card treatment as the Inventory grid: base plate, then the frame art
        // tinted over it for the arched top and bevelled edges.
        ACGInventoryStyle.rect(graphics, x, y, width, height,
                offer.isVirtual() ? ACGInventoryStyle.CARD_BRIGHT : ACGInventoryStyle.CARD_DIM);
        ACGInventoryStyle.texTinted(graphics, ACGInventoryStyle.CARD_FRAME, x, y, width, height,
                ACGInventoryStyle.CARD_FRAME_W, ACGInventoryStyle.CARD_FRAME_H,
                ACGInventoryStyle.CARD_FRAME_TINT);

        // Gem shows the offer's rarity, reusing the mod-wide tier palette so a slot reads
        // the same way as an R/SR/SSR perk card: cyan, purple, gold.
        ACGInventoryStyle.texSquareTinted(graphics, ACGInventoryStyle.GEM,
                x + width / 2.0F, y + 8.0F, 6.0F, ACGInventoryStyle.GEM_SIZE,
                sold ? ACGInventoryStyle.TEXT_DIM : offer.rarityColor());
        ACGInventoryStyle.text(graphics, font,
                GeneralConstants.rarityTier(offer.rarityColor()),
                x + 4.0F, y + 4.0F, 0.65F,
                sold ? ACGInventoryStyle.TEXT_DIM : offer.rarityColor(),
                ACGInventoryStyle.ALIGN_LEFT);

        // Icon slightly smaller than the Inventory's 32 to make room for the extra price
        // row this card carries.
        int iconSize = 30;
        int iconX = (int) x + (width - iconSize) / 2;
        int iconY = (int) y + 12;
        ResourceLocation virtualIcon = virtualIcon(offer);
        if (virtualIcon != null) {
            ACGTheme.drawVirtualItemIcon(graphics, virtualIcon, iconX, iconY, iconSize);
        } else {
            // Drawn scaled so it reads at card scale rather than inventory scale. The count
            // is drawn by hand afterwards instead of via renderItemDecorations, which would
            // inherit this pose and render a comically large number.
            graphics.pose().pushPose();
            graphics.pose().translate(iconX, iconY, 0.0F);
            graphics.pose().scale(iconSize / 16.0F, iconSize / 16.0F, 1.0F);
            graphics.renderItem(offer.stack(), 0, 0);
            graphics.pose().popPose();
        }

        // Count on its own inset plate, as in the Inventory, rather than tucked into the
        // icon's corner where it collided with the artwork.
        String countLabel = "x" + offer.stack().getCount();
        float plateY = y + height - 32.0F;
        // Plate hugs the count text rather than spanning the card: a full-width bar behind
        // a two-character "x1" reads as a stray stripe. Padded either side, and clamped so
        // a long count (x9999) still can't overflow the card.
        float plateWidth = Math.min(width - 6.0F,
                font.width(countLabel) * COUNT_SCALE + COUNT_PLATE_PADDING);
        ACGInventoryStyle.rect(graphics, x + (width - plateWidth) / 2.0F, plateY,
                plateWidth, 10.0F, ACGInventoryStyle.CARD_LABEL_PLATE);
        ACGInventoryStyle.text(graphics, font, countLabel,
                x + width / 2.0F, plateY + 1.5F, COUNT_SCALE,
                sold ? ACGInventoryStyle.TEXT_DIM : ACGInventoryStyle.TEXT_CREAM,
                ACGInventoryStyle.ALIGN_CENTER);

        // isHoveredOrFocused() rather than the `hovered` flag above: that one is gated on
        // `active`, so a sold-out card could never scroll its own name.
        ACGInventoryStyle.scrollingText(graphics, font, displayName(offer).getString(),
                x + 3.0F, y + height - 21.0F, width - 6.0F, 0.75F,
                sold ? ACGInventoryStyle.TEXT_DIM : ACGInventoryStyle.TEXT_MUTED,
                isHoveredOrFocused(), System.currentTimeMillis());

        Component price = sold
                ? GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.shop.sold_out")
                : GeneralTextMethods.getTranslatableString("screen.aegis_ascension.acg.shop.price",
                        offer.experienceCost());
        int priceColor = sold ? ACGInventoryStyle.TEXT_DIM
                : affordable ? ACGInventoryStyle.ACCENT_ORANGE : ACGTheme.STATUS_LOCKED;
        if (!sold && ClientPerkState.usesGoldCurrency()) {
            float coinSize = 9.0F;
            float textWidth = font.width(String.valueOf(offer.experienceCost())) * 0.75F;
            float totalWidth = coinSize + 2.0F + textWidth;
            ACGInventoryStyle.tex(graphics, ACGInventoryStyle.GOLD_CURRENCY,
                    x + (width - totalWidth) / 2.0F, y + height - 14.0F,
                    coinSize, coinSize, ACGInventoryStyle.GOLD_CURRENCY_SIZE,
                    ACGInventoryStyle.GOLD_CURRENCY_SIZE);
            ACGInventoryStyle.text(graphics, font, String.valueOf(offer.experienceCost()),
                    x + (width + totalWidth) / 2.0F, y + height - 10.0F, 0.75F,
                    priceColor, ACGInventoryStyle.ALIGN_RIGHT);
        } else {
            ACGInventoryStyle.text(graphics, font, price.getString(),
                    x + width / 2.0F, y + height - 10.0F, 0.75F, priceColor,
                    ACGInventoryStyle.ALIGN_CENTER);
        }

        // A sold-out slot is greyed under a scrim so it reads as spent at a glance; an
        // unaffordable one stays legible, since its price is the thing to look at.
        if (sold) {
            // Raised above the item before drawing: GuiGraphics#renderItem translates z by
            // ~100 internally and an item's model adds more depth on top of that, so a
            // scrim or badge blitted afterwards at z=0 still lands *behind* the item. A
            // flat-blitted virtual book has no such offset, which is why only real items
            // showed through. 300 clears the deepest vanilla item model comfortably.
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);

            ACGInventoryStyle.rect(graphics, x, y, width, height, 0x88101018);
            // Centred on the icon, not the card: the card's midpoint sits at the icon's
            // bottom edge, which pushed the tick down onto the count and name rows.
            float check = iconSize * 1.05F;
            float checkH = check * CHECK_ASPECT;
            ACGInventoryStyle.tex(graphics, ACGInventoryStyle.CHECK,
                    iconX + (iconSize - check) / 2.0F,
                    iconY + (iconSize - checkH) / 2.0F,
                    check, checkH,
                    ACGInventoryStyle.CHECK_W, ACGInventoryStyle.CHECK_H);

            graphics.pose().popPose();
        }
        if (hovered) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, ACGInventoryStyle.TEAL_BORDER);
        } else if (!sold && !affordable) {
            ACGInventoryStyle.outline(graphics, x, y, width, height, ACGTheme.STATUS_LOCKED);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
