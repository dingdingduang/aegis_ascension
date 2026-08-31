package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.util.ItemNbt;
import com.whatever.aegis_ascension.util.GeneralConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One shop slot: what it sells, what it costs in raw experience points, its rarity tier,
 * and whether this player has already bought it since the last reroll. Immutable — buying
 * produces a new instance via {@link #asPurchased()} rather than mutating shared state.
 *
 * <p>The rarity is stored as a resolved colour rather than a tier name, because the client
 * has no access to the server-only {@link ShopConfig} that assigns it and only ever needs
 * the tint to draw the slot's gem.</p>
 */
public record ShopOffer(ItemStack stack, int experienceCost, boolean purchased,
                        String virtualId, int rarityColor) {
    public ShopOffer {
        virtualId = virtualId == null ? "" : virtualId;
    }

    public ShopOffer(ItemStack stack, int experienceCost) {
        this(stack, experienceCost, false, "", GeneralConstants.RARITY_R);
    }

    public ShopOffer(ItemStack stack, int experienceCost, int rarityColor) {
        this(stack, experienceCost, false, "", rarityColor);
    }

    public ShopOffer(ItemStack stack, int experienceCost, String virtualId, int rarityColor) {
        this(stack, experienceCost, false, virtualId, rarityColor);
    }

    /** A virtual book: {@link #stack} is only its icon and is never given to the player. */
    public boolean isVirtual() {
        return !virtualId.isEmpty();
    }

    public ShopOffer asPurchased() {
        return new ShopOffer(stack, experienceCost, true, virtualId, rarityColor);
    }

    /** Makes a retained shop slot purchasable again after its unique item is reset. */
    public ShopOffer asAvailable() {
        return new ShopOffer(stack, experienceCost, false, virtualId, rarityColor);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", ItemNbt.save(stack));
        tag.putInt("Cost", experienceCost);
        tag.putBoolean("Purchased", purchased);
        tag.putInt("Rarity", rarityColor);
        if (isVirtual()) {
            tag.putString("VirtualId", virtualId);
        }
        return tag;
    }

    public static ShopOffer load(CompoundTag tag) {
        return new ShopOffer(
                ItemNbt.load(tag.getCompound("Stack")),
                tag.getInt("Cost"),
                tag.getBoolean("Purchased"),
                tag.getString("VirtualId"),
                // Stock rolled before rarity existed has no tag; default to R.
                tag.contains("Rarity") ? tag.getInt("Rarity") : GeneralConstants.RARITY_R
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeItem(stack);
        buffer.writeVarInt(experienceCost);
        buffer.writeBoolean(purchased);
        buffer.writeUtf(virtualId, 128);
        buffer.writeInt(rarityColor);
    }

    public static ShopOffer read(FriendlyByteBuf buffer) {
        return new ShopOffer(buffer.readItem(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readUtf(128), buffer.readInt());
    }
}
