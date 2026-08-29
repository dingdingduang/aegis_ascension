package com.whatever.aegis_ascension.storage;

import com.whatever.aegis_ascension.util.ItemNbt;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.whatever.aegis_ascension.util.GeneralConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One storage row: an item type and how many of it are banked.
 *
 * <p>The count is a {@code long} held <em>outside</em> the {@link ItemStack}, and the
 * prototype stack is always kept at count 1. This is deliberate:
 * {@code ItemStack#save} writes the count as a <em>byte</em>, so a stack of 96 stone —
 * exactly the case this UI is built around — cannot survive an NBT round trip inside a
 * plain ItemStack. Keeping the amount separate is what lets storage hold arbitrarily large
 * banked quantities while still reusing ItemStack for item identity and NBT.</p>
 *
 * <p>A non-empty {@code virtualId} marks the row as a {@link VirtualItems} book rather than
 * a real item. Those rows still carry a prototype, but only as an icon — they can never be
 * extracted into the world, so the prototype is never handed to a player.</p>
 */
public record StoredItem(ItemStack prototype, long count, String virtualId, int rarityColor) {
    public StoredItem {
        prototype = prototype.copy();
        prototype.setCount(1);
        count = Math.max(0L, count);
        virtualId = virtualId == null ? "" : virtualId;
    }

    /** A row holding a real, extractable item of unremarkable rarity. */
    public StoredItem(ItemStack prototype, long count) {
        this(prototype, count, "", GeneralConstants.RARITY_R);
    }

    public StoredItem(ItemStack prototype, long count, String virtualId) {
        this(prototype, count, virtualId, GeneralConstants.RARITY_R);
    }

    public boolean isVirtual() {
        return !virtualId.isEmpty();
    }

    public StoredItem withCount(long newCount) {
        return new StoredItem(prototype, newCount, virtualId, rarityColor);
    }

    public StoredItem plus(long delta) {
        return withCount(count + delta);
    }

    /**
     * Whether another row merges into this one. Virtual rows match only by their id, so a
     * "primary stat book" (drawn as an enchanted book) never stacks with a real enchanted
     * book that happens to share the icon.
     */
    public boolean matches(ItemStack stack, String otherVirtualId) {
        String other = otherVirtualId == null ? "" : otherVirtualId;
        if (isVirtual() || !other.isEmpty()) {
            return virtualId.equals(other);
        }
        return ItemNbt.sameItemSameData(prototype, stack);
    }

    public boolean matches(ItemStack stack) {
        return matches(stack, "");
    }

    /** Virtual rows are named from their lang key; real rows use the stack's own hover name. */
    public Component displayComponent() {
        if (isVirtual()) {
            VirtualItems.Definition definition = VirtualItems.byId(virtualId);
            if (definition != null) {
                return GeneralTextMethods.getTranslatableString(definition.nameKey());
            }
        }
        return prototype.getHoverName();
    }

    public String displayName() {
        return displayComponent().getString();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Item", ItemNbt.save(prototype));
        tag.putLong("Count", count);
        tag.putInt("Rarity", rarityColor);
        if (isVirtual()) {
            tag.putString("VirtualId", virtualId);
        }
        return tag;
    }

    public static StoredItem load(CompoundTag tag) {
        return new StoredItem(
                ItemNbt.load(tag.getCompound("Item")),
                tag.getLong("Count"),
                tag.getString("VirtualId"),
                tag.contains("Rarity") ? tag.getInt("Rarity") : GeneralConstants.RARITY_R
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeItem(prototype);
        buffer.writeVarLong(count);
        buffer.writeUtf(virtualId, 128);
        buffer.writeInt(rarityColor);
    }

    public static StoredItem read(FriendlyByteBuf buffer) {
        return new StoredItem(buffer.readItem(), buffer.readVarLong(), buffer.readUtf(128),
                buffer.readInt());
    }
}
