package com.whatever.aegis_ascension.client.screen.collectiontabs;

import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.DevourClientSettings;
import com.whatever.aegis_ascension.network.SyncDevourDataPacket;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Devoured items and their inherited attribute modifiers, grouped for display.
 *
 * <p>Lives beside the other collection-tab data sources rather than inside a screen, so the
 * ACG Devoured tab can read it the same way {@link OwnedAegis} and {@link SoulLinks} are
 * read. The client receives a flat list of (itemId, attribute) rows; grouping them by item
 * here is what turns that into one card per devoured item.</p>
 */
public final class DevouredItems {
    private DevouredItems() {
    }

    /** One devoured item and every attribute modifier inherited from it. */
    public record DevouredItem(String itemId, List<SyncDevourDataPacket.Entry> attributes) {
    }

    /**
     * Display order for the Devoured grid, mirroring the Inventory's. MANUAL is absent: these
     * cards have no drag-to-reorder, so there is no arrangement to preserve.
     */
    public enum SortMode {
        /** Alphabetical, A first. */
        NAME_ASC,
        /** Alphabetical, Z first. */
        NAME_DESC,
        /** Rarest first (SSR, SR, R), alphabetical within each tier. */
        RARITY
    }

    /**
     * A devoured item's rarity tint, read from how many attributes it carried.
     *
     * <p>Counts every inherited attribute, banned ones included, so the gem always agrees
     * with the {@code xN} badge drawn beneath it on the same card. Thresholds are walked from
     * the top down, so a hand-edited config that inverts them still resolves to one tier.</p>
     */
    public static int rarityColor(DevouredItem item) {
        return rarityColor(item.attributes().size());
    }

    public static int rarityColor(int attributeCount) {
        DevourClientSettings settings = DevourClientSettings.get();
        if (attributeCount >= settings.ssrMinAttributes) {
            return GeneralConstants.RARITY_SSR;
        }
        if (attributeCount >= settings.srMinAttributes) {
            return GeneralConstants.RARITY_SR;
        }
        return GeneralConstants.RARITY_R;
    }

    public static SortMode getSortMode() {
        try {
            return SortMode.valueOf(DevourClientSettings.get().devourSortMode);
        } catch (IllegalArgumentException ignored) {
            return SortMode.NAME_ASC;
        }
    }

    /** Advances to the next order and persists it. Purely local — no packet, no round trip. */
    public static void cycleSortMode() {
        SortMode next = switch (getSortMode()) {
            case NAME_ASC -> SortMode.NAME_DESC;
            case NAME_DESC -> SortMode.RARITY;
            default -> SortMode.NAME_ASC;
        };
        DevourClientSettings settings = DevourClientSettings.get();
        settings.devourSortMode = next.name();
        settings.save();
    }

    private static Comparator<DevouredItem> comparator() {
        Comparator<DevouredItem> byName = Comparator
                .comparing((DevouredItem item) -> itemName(item.itemId()).getString(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DevouredItem::itemId);
        return switch (getSortMode()) {
            case NAME_DESC -> byName.reversed();
            case RARITY -> Comparator
                    .comparingInt((DevouredItem item) ->
                            GeneralConstants.rarityRank(rarityColor(item)))
                    .reversed()
                    .thenComparing(byName);
            default -> byName;
        };
    }

    /** All devoured items, in the current display order. */
    public static List<DevouredItem> all() {
        Map<String, List<SyncDevourDataPacket.Entry>> grouped = new LinkedHashMap<>();
        ClientPerkState.getDevouredAttributes().forEach(entry ->
                grouped.computeIfAbsent(entry.itemId(), ignored -> new ArrayList<>()).add(entry)
        );
        List<DevouredItem> items = new ArrayList<>();
        grouped.forEach((itemId, attributes) ->
                items.add(new DevouredItem(itemId, List.copyOf(attributes))));
        items.sort(comparator());
        return List.copyOf(items);
    }

    /** Items matching a lowercased query; blank matches everything. */
    public static List<DevouredItem> matching(String query) {
        String trimmed = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return all().stream().filter(item -> matchesSearch(item, trimmed)).toList();
    }

    /**
     * Same syntax as the Inventory search: a bare word matches the display name, and
     * {@code namespace:path} (optionally {@code namespace:[path]}) matches the item id.
     */
    public static boolean matchesSearch(DevouredItem item, String query) {
        if (query.isBlank()) {
            return true;
        }
        String localizedName = itemName(item.itemId()).getString().toLowerCase(Locale.ROOT);
        if (!query.contains(":")) {
            return localizedName.contains(query);
        }
        ResourceLocation location = PlatformServices.resources().tryParse(item.itemId());
        if (location == null) {
            return item.itemId().toLowerCase(Locale.ROOT).contains(query);
        }
        int separator = query.indexOf(':');
        String namespaceQuery = query.substring(0, separator).trim();
        String pathQuery = query.substring(separator + 1).trim();
        if (pathQuery.startsWith("[") && pathQuery.endsWith("]") && pathQuery.length() >= 2) {
            pathQuery = pathQuery.substring(1, pathQuery.length() - 1).trim();
        }
        pathQuery = pathQuery.replace(' ', '_');
        return location.getNamespace().equals(namespaceQuery)
                && location.getPath().contains(pathQuery);
    }

    /** The item's stack, or a barrier when the id no longer resolves (a removed mod). */
    public static ItemStack itemStack(String itemId) {
        ResourceLocation location = PlatformServices.resources().tryParse(itemId);
        Item item = location == null ? null : GeneralClientMethods.resolveItem(location);
        return new ItemStack(item == null ? Items.BARRIER : item);
    }

    public static Component itemName(String itemId) {
        ItemStack stack = itemStack(itemId);
        return stack.is(Items.BARRIER) ? getLiteralString(itemId) : stack.getHoverName();
    }

    public static Component attributeName(String attributeId) {
        ResourceLocation location = PlatformServices.resources().tryParse(attributeId);
        Attribute attribute = location == null
                ? null
                : GeneralClientMethods.resolveAttribute(location);
        return attribute == null
                ? getLiteralString(attributeId)
                : getTranslatableString(attribute.getDescriptionId());
    }

    /** Additive modifiers read as a flat number; multiplicative ones as a percentage. */
    public static Component formattedAmount(SyncDevourDataPacket.Entry entry) {
        double value = entry.operation() == AttributeOperation.ADDITION
                ? entry.amount()
                : entry.amount() * 100.0D;
        String suffix = entry.operation() == AttributeOperation.ADDITION ? "" : "%";
        return getLiteralString((value >= 0.0D ? "+" : "") + compact(value) + suffix);
    }

    public static String operationKey(AttributeOperation operation) {
        return switch (operation) {
            case ADDITION -> "screen.aegis_ascension.devour.operation.addition";
            case MULTIPLY_BASE -> "screen.aegis_ascension.devour.operation.multiply_base";
            case MULTIPLY_TOTAL -> "screen.aegis_ascension.devour.operation.multiply_total";
        };
    }
}
