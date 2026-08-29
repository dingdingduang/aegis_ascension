package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.network.SyncStorageDataPacket;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import com.whatever.aegis_ascension.storage.StoredItem;

import java.util.List;

/**
 * Client-side mirror of the player's virtual storage. Display only — every extract, sell,
 * and discard is re-validated and re-priced server-side against its own copy.
 */
public final class ClientStorageState {
    /**
     * Display order for the Inventory grid. Client-side: the server addresses rows by
     * identity and has no stake in how they're arranged on screen.
     */
    public enum SortMode {
        /** Alphabetical, A first. */
        NAME_ASC,
        /** Alphabetical, Z first. */
        NAME_DESC,
        /** Rarest first (SSR, SR, R), alphabetical within each tier. */
        RARITY,
        /** Player-arranged by dragging; never re-sorts. */
        MANUAL
    }

    private static List<StoredItem> items = List.of();
    private static List<Integer> sellUnitValues = List.of();

    private static int maxItemTypes = 60;

    private ClientStorageState() {
    }

    public static void accept(SyncStorageDataPacket packet) {
        items = List.copyOf(packet.items());
        sellUnitValues = List.copyOf(packet.sellUnitValues());

        maxItemTypes = packet.maxItemTypes();
    }

    public static void clear() {
        items = List.of();
        sellUnitValues = List.of();

        maxItemTypes = 60;
    }

    // ------------------------------------------------------------------
    // GUI integration hooks
    // ------------------------------------------------------------------

    /** The server's own order. Only ordering code should need this; the UI uses display order. */
    public static List<StoredItem> getItems() {
        return items;
    }

    public static int getRowCount() {
        return items.size();
    }

    /**
     * Rows in display order.
     *
     * <p>Every index-taking method here is in <em>display</em> space, so the screen has a
     * single coordinate system: sorting and dragging rearrange this list freely, and the
     * packets that act on a row carry its identity rather than any index at all.</p>
     */
    public static StoredItem getRow(int index) {
        List<StoredItem> sorted = getSortedItems();
        return index >= 0 && index < sorted.size() ? sorted.get(index) : null;
    }

    /** Experience granted per unit sold, already including the configured sell ratio's source price. */
    public static int getSellUnitValue(int index) {
        StoredItem row = getRow(index);
        if (row == null) {
            return 0;
        }
        // The values arrive parallel to the server's list, so map back through identity
        // rather than reusing the display index.
        int serverIndex = items.indexOf(row);
        return serverIndex >= 0 && serverIndex < sellUnitValues.size()
                ? sellUnitValues.get(serverIndex) : 0;
    }

    public static boolean isSellable(int index) {
        StoredItem row = getRow(index);
        return row != null && !row.isVirtual();
    }

    /** Virtual books can be consumed but never withdrawn into the real inventory. */
    public static boolean isExtractable(int index) {
        StoredItem row = getRow(index);
        return row != null && !row.isVirtual();
    }

    public static boolean isUsable(int index) {
        StoredItem row = getRow(index);
        return row != null && row.isVirtual();
    }

    public static SortMode getSortMode() {
        try {
            return SortMode.valueOf(ClientSettings.get().storageSortMode);
        } catch (IllegalArgumentException ignored) {
            return SortMode.NAME_ASC;
        }
    }

    /**
     * Stable per-row key for the manual order: the virtual id, or the item id plus a hash of
     * its NBT. Mirrors {@link StoredItem#matches} — the same thing that makes each row
     * unique on the server — so a key names exactly one row.
     */
    public static String keyOf(StoredItem row) {
        if (row.isVirtual()) {
            return "virtual:" + row.virtualId();
        }
        var id = GeneralClientMethods.getItemKey(row.prototype().getItem());
        var tag = row.prototype().getTag();
        return (id == null ? "?" : id.toString()) + "#" + (tag == null ? "" : tag.hashCode());
    }

    /** Reorders the display list and switches to MANUAL, persisting the arrangement. */
    public static void moveInManualOrder(int fromDisplayIndex, int toDisplayIndex) {
        List<StoredItem> display = getSortedItems();
        if (fromDisplayIndex < 0 || fromDisplayIndex >= display.size()
                || toDisplayIndex < 0 || toDisplayIndex >= display.size()
                || fromDisplayIndex == toDisplayIndex) {
            return;
        }
        List<String> order = new java.util.ArrayList<>();
        for (StoredItem row : display) {
            order.add(keyOf(row));
        }
        order.add(toDisplayIndex, order.remove(fromDisplayIndex));
        ClientSettings settings = ClientSettings.get();
        settings.storageManualOrder = order;
        settings.storageSortMode = SortMode.MANUAL.name();
        settings.save();
    }

    /** Advances to the next order and persists it. Purely local — no packet, no round trip. */
    public static void cycleSortMode() {
        SortMode next = switch (getSortMode()) {
            case NAME_ASC -> SortMode.NAME_DESC;
            case NAME_DESC -> SortMode.RARITY;
            default -> SortMode.NAME_ASC;
        };
        ClientSettings settings = ClientSettings.get();
        settings.storageSortMode = next.name();
        settings.save();
    }

    /** The rows in display order. The server's own order is left untouched. */
    public static List<StoredItem> getSortedItems() {
        List<StoredItem> sorted = new java.util.ArrayList<>(getItems());
        java.util.Comparator<StoredItem> byName = java.util.Comparator.comparing(
                item -> item.displayName().toLowerCase(java.util.Locale.ROOT));
        switch (getSortMode()) {
            case NAME_DESC -> sorted.sort(byName.reversed());
            case RARITY -> sorted.sort(
                    java.util.Comparator.comparingInt((StoredItem item) ->
                                    GeneralConstants.rarityRank(item.rarityColor()))
                            .reversed()
                            .thenComparing(byName));
            case MANUAL -> {
                // Rows the saved arrangement doesn't mention (newly banked since) sort to
                // the end rather than jumping to the front.
                List<String> order = ClientSettings.get().storageManualOrder;
                sorted.sort(java.util.Comparator.comparingInt(item -> {
                    int position = order.indexOf(keyOf(item));
                    return position < 0 ? Integer.MAX_VALUE : position;
                }));
            }
            default -> sorted.sort(byName);
        }
        return sorted;
    }

    public static int getMaxItemTypes() {
        return maxItemTypes;
    }
}
