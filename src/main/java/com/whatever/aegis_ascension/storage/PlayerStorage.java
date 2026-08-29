package com.whatever.aegis_ascension.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * A player's virtual item bank: what shop purchases land in, and what the Inventory screen
 * extracts, sells, and discards from.
 *
 * <p>Row order here is just insertion order and carries no meaning: mutating packets
 * address a row by <em>identity</em> (see {@link #indexOf}), not by position. That is what
 * lets the client sort, filter and drag-reorder its own view instantly, with no round trip
 * and no risk that a reordered list makes "sell row 3" hit the wrong item.</p>
 */
public final class PlayerStorage {
    private static final String ITEMS_TAG = "Items";

    private final List<StoredItem> items = new ArrayList<>();

    /**
     * Extra type slots this player has earned, supplied by the owner rather than read here:
     * the bonus lives on {@link com.whatever.aegis_ascension.capability.PlayerPerkData} as a
     * virtual-item use count, and storage has no reference to it.
     */
    private IntSupplier bonusTypeSlots = () -> 0;

    public void setBonusTypeSlotSupplier(IntSupplier supplier) {
        this.bonusTypeSlots = supplier == null ? () -> 0 : supplier;
    }

    /** Configured cap plus any slots unlocked by Storage Expansion books. */
    public int getMaxTypes() {
        return Math.max(1, StorageConfig.get().maxItemTypes + Math.max(0, bonusTypeSlots.getAsInt()));
    }

    public List<StoredItem> getItems() {
        return List.copyOf(items);
    }


    public int getTypeCount() {
        return items.size();
    }

    public boolean isValidIndex(int index) {
        return index >= 0 && index < items.size();
    }

    /**
     * Index of the row matching a key, or -1.
     *
     * <p>Reuses the same {@link StoredItem#matches} test that decides whether an incoming
     * stack merges into an existing row. Because {@code addInternal} merges on that test,
     * there is exactly one row per (item + NBT) or per virtual id — so the key is a primary
     * key by construction, and stays one for as long as the merge rule does.</p>
     */
    public int indexOf(ItemStack prototype, String virtualId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).matches(prototype, virtualId)) {
                return i;
            }
        }
        return -1;
    }

    public StoredItem get(int index) {
        return isValidIndex(index) ? items.get(index) : null;
    }

    /**
     * Banks a stack. Merges into an existing row when the item and NBT match, otherwise
     * opens a new row — which fails if that would exceed the configured type cap.
     *
     * @return false if the item could not be stored (type cap reached).
     */
    public boolean add(ItemStack stack, int rarityColor) {
        if (stack.isEmpty()) {
            return true;
        }
        return addInternal(stack, "", stack.getCount(), rarityColor);
    }

    /**
     * Banks a virtual book by id. Shares the type cap with real items, and merges only with
     * rows carrying the same virtual id.
     *
     * @return false if the item could not be stored (type cap reached).
     */
    public boolean addVirtual(String virtualId, long amount) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        if (definition == null || amount <= 0L) {
            return false;
        }
        return addInternal(definition.iconStack(), virtualId, amount,
                GeneralConstants.rarityColor(definition.parsedTier()));
    }

    /** Whether a virtual item can merge into storage or open a new type row. */
    public boolean canAcceptVirtual(String virtualId) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        if (definition == null) {
            return false;
        }
        ItemStack icon = definition.iconStack();
        for (StoredItem item : items) {
            if (item.matches(icon, virtualId)) {
                return true;
            }
        }
        return items.size() < getMaxTypes();
    }

    private boolean addInternal(ItemStack stack, String virtualId, long amount, int rarityColor) {
        if (amount <= 0L) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).matches(stack, virtualId)) {
                items.set(i, items.get(i).plus(amount));
                return true;
            }
        }
        if (items.size() >= getMaxTypes()) {
            return false;
        }
        // Merging above keeps the existing row's tier: a row's rarity is set when it is
        // first opened, so re-stocking can't silently relabel what the player already has.
        items.add(new StoredItem(stack, amount, virtualId, rarityColor));
        return true;
    }

    /** Whether a stack could be banked right now, without actually banking it. */
    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        for (StoredItem item : items) {
            if (item.matches(stack)) {
                return true;
            }
        }
        return items.size() < getMaxTypes();
    }

    /**
     * Removes up to {@code amount} from a row, dropping the row when it empties.
     *
     * @return how many were actually removed, which may be less than requested.
     */
    public long remove(int index, long amount) {
        if (!isValidIndex(index) || amount <= 0L) {
            return 0L;
        }
        StoredItem item = items.get(index);
        long removed = Math.min(amount, item.count());
        if (removed >= item.count()) {
            items.remove(index);
        } else {
            items.set(index, item.plus(-removed));
        }
        return removed;
    }

    /**
     * Moves up to {@code amount} of a row into the player's real inventory, in
     * max-stack-sized chunks, spilling to the ground only if the inventory is full.
     *
     * @return how many were extracted.
     */
    public long extractToPlayer(int index, long amount, ServerPlayer player) {
        StoredItem item = get(index);
        // Virtual books have no real-world form: their prototype is an icon, so handing it
        // over would mint a genuine item the player was never meant to hold.
        if (item == null || item.isVirtual() || amount <= 0L) {
            return 0L;
        }
        long requested = Math.min(amount, item.count());
        long delivered = 0L;
        int perStack = Math.max(1, item.prototype().getMaxStackSize());
        while (delivered < requested) {
            int chunk = (int) Math.min(perStack, requested - delivered);
            ItemStack payload = item.prototype().copy();
            payload.setCount(chunk);
            if (!player.getInventory().add(payload)) {
                // add() may have partially consumed the stack; only what's left gets dropped.
                if (!payload.isEmpty()) {
                    player.drop(payload, false);
                }
            }
            delivered += chunk;
        }
        remove(index, delivered);
        return delivered;
    }




    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        items.forEach(item -> list.add(item.save()));
        tag.put(ITEMS_TAG, list);
        return tag;
    }

    public void load(CompoundTag tag) {
        items.clear();
        ListTag list = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StoredItem item = StoredItem.load(list.getCompound(i));
            // A row that decayed to zero (or whose item was removed from the game) would
            // otherwise linger as an unusable ghost entry occupying a type slot.
            if (item.count() > 0L && !item.prototype().isEmpty()) {
                items.add(item);
            }
        }
    }
}
