package com.whatever.aegis_ascension.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wire format of a quest's reward line, which travels to the client as one string.
 *
 * <p>A summary is a comma-separated list. Any experience and gold come first, already
 * formatted for display, and every remaining entry is a reward item written as its id,
 * optionally followed by {@code *} and a count. A count of one is left implicit. The
 * separator cannot occur in a ResourceLocation, so an id is never ambiguous:</p>
 *
 * <pre>520 AAE, 237 Gold, minecraft:redstone*4</pre>
 *
 * <p>Both halves of the format live here on purpose. The server writes these strings and
 * the quest screen reads them back, and when the two halves sit in different classes they
 * drift: the amount was rolled and granted correctly for some time while the screen showed
 * only a bare item name, because the writer never emitted a count the reader could find.</p>
 */
public final class QuestRewardSummary {
    private static final char COUNT_SEPARATOR = '*';
    private static final String ENTRY_SEPARATOR = ", ";

    private QuestRewardSummary() {
    }

    /** Formats one reward item; a count of one is omitted rather than written as x1. */
    public static String entry(String id, int count) {
        String safeId = id == null ? "" : id;
        return count > 1 ? safeId + COUNT_SEPARATOR + count : safeId;
    }

    /** Appends one entry, inserting the separator only when something precedes it. */
    public static void append(StringBuilder summary, String id, int count) {
        if (summary == null) return;
        if (summary.length() > 0) summary.append(ENTRY_SEPARATOR);
        summary.append(entry(id, count));
    }

    /**
     * Removes one leading display prefix, such as the formatted experience or gold. A
     * summary consisting only of that prefix has no item entries left and yields "".
     */
    public static String stripPrefix(String summary, String prefix) {
        if (summary == null) return "";
        if (prefix == null || prefix.isEmpty()) return summary;
        if (summary.equals(prefix)) return "";
        String withSeparator = prefix + ENTRY_SEPARATOR;
        return summary.startsWith(withSeparator)
                ? summary.substring(withSeparator.length()) : summary;
    }

    /**
     * Splits the item portion of a summary. An entry whose count will not parse is kept
     * as a plain item rather than discarded, so a malformed summary still lists what the
     * quest pays out.
     */
    public static List<Entry> parse(String itemPortion) {
        String summary = itemPortion == null ? "" : itemPortion.trim();
        if (summary.isEmpty()) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (String raw : summary.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            int marker = entry.lastIndexOf(COUNT_SEPARATOR);
            if (marker < 0) {
                entries.add(new Entry(entry, 1));
                continue;
            }
            try {
                entries.add(new Entry(entry.substring(0, marker),
                        Integer.parseInt(entry.substring(marker + 1))));
            } catch (NumberFormatException ignored) {
                entries.add(new Entry(entry, 1));
            }
        }
        return entries;
    }

    /**
     * Combines repeated ids into one entry, summing their counts and keeping the order
     * they first appeared. Separate reward specs often resolve to the same item, and
     * listing it twice reads as a display fault rather than as two draws.
     */
    public static List<Entry> merge(List<Entry> entries) {
        if (entries == null || entries.size() < 2) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Entry entry : entries) {
            totals.merge(entry.id(), entry.count(),
                    (current, added) -> current > Integer.MAX_VALUE - added
                            ? Integer.MAX_VALUE : current + added);
        }
        List<Entry> merged = new ArrayList<>(totals.size());
        totals.forEach((id, count) -> merged.add(new Entry(id, count)));
        return merged;
    }

    /** One reward item read back out of a summary. */
    public record Entry(String id, int count) {
        public Entry {
            id = id == null ? "" : id;
            count = Math.max(1, count);
        }
    }
}
