package com.whatever.aegis_ascension.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The single place this mod converts an {@link ItemStack} to or from NBT, and the single
 * place it compares two stacks for storage/shop stacking purposes.
 *
 * <p>Stack serialization breaks at every version boundary, and it breaks quietly: 1.20.5
 * moved stacks onto data components, so encoding now needs a {@code HolderLookup.Provider}
 * and {@code ItemStack#of} became {@code ItemStack#parse}; 1.12.2 instead writes an
 * id/damage pair through {@code writeToNBT}. Funnelling both directions through here means
 * a port rewrites this one file rather than hunting through the storage, shop, and devour
 * code for stray {@code save}/{@code of} calls.</p>
 *
 * <p>Callers must not invoke {@code ItemStack#save}, {@code ItemStack#of}, or
 * {@code ItemStack#isSameItemSameTags} directly.</p>
 *
 * <p>Networking is deliberately not routed through here. {@code FriendlyByteBuf#writeItem}
 * and {@code #readItem} face the same version churn, but they migrate to stream codecs on a
 * different schedule than the on-disk format, and conflating the two would tie a save-file
 * concern to a wire concern.</p>
 */
public final class ItemNbt {
    private ItemNbt() {
    }

    /** Serializes a stack for persistence. Inverse of {@link #load(CompoundTag)}. */
    public static CompoundTag save(ItemStack stack) {
        return stack.save(new CompoundTag());
    }

    /** Reads a stack written by {@link #save(ItemStack)}. Returns empty for unknown items. */
    public static ItemStack load(CompoundTag tag) {
        return ItemStack.of(tag);
    }

    /**
     * Whether two stacks are the same item carrying the same extra data, and so may be
     * merged into one storage row or shop entry. Counts are ignored.
     */
    public static boolean sameItemSameData(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }
}
