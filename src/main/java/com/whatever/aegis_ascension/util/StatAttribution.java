package com.whatever.aegis_ascension.util;

/**
 * Naming scheme for per-source shadow records of accumulated custom stats.
 *
 * <p>Several talents grant a stat gradually rather than all at once — a Breakthrough
 * adds a little Physical Amplification each time it fires, Shrine Maiden Dance rolls a
 * Damage Reduction penalty. Those gains are folded into one shared custom stat, which
 * is correct for gameplay but destroys the record of who contributed what: the stat
 * screen can only show the leftover as an unattributed remainder.</p>
 *
 * <p>Alongside the shared key, each grant is therefore also recorded under
 * {@code __from.<sourceId>/<statKey>}. Those records are bookkeeping for display only.
 * Gameplay never reads them, so one that is missing, stale, or left behind by a talent
 * the server owner deleted can affect what a tooltip says but never what the stat
 * does.</p>
 *
 * <p>Because the source id comes from the catalog JSON rather than from code, talents
 * added or renamed in {@code talents_serverside.json} start recording under their own id with no
 * code change. A record whose id no longer resolves is shown as coming from a removed
 * talent rather than being silently dropped.</p>
 */
public final class StatAttribution {
    /** Marks a custom-stat key as a per-source record rather than a gameplay value. */
    public static final String PREFIX = "__from.";
    /** Prefix the server puts on raw custom-stat keys when building the display map. */
    public static final String CUSTOM_STAT_PREFIX = "__custom.";
    /** The display-map prefix a per-source record arrives under on the client. */
    public static final String SYNCED_PREFIX = CUSTOM_STAT_PREFIX + PREFIX;
    /** Separates the source id from the stat key. Neither may contain this character. */
    public static final char SEPARATOR = '/';

    private StatAttribution() {
    }

    /** Builds the record key for one source's contribution to one stat. */
    public static String key(String sourceId, String statKey) {
        return PREFIX + sourceId + SEPARATOR + statKey;
    }

    /** Whether a custom-stat key is one of these records. */
    public static boolean isRecord(String key) {
        return key != null
                && key.startsWith(PREFIX)
                && key.indexOf(SEPARATOR, PREFIX.length()) > PREFIX.length();
    }

    /** The contributing talent, Aegis, or Soul Link id, or {@code null} if not a record. */
    public static String sourceOf(String key) {
        if (!isRecord(key)) {
            return null;
        }
        return key.substring(PREFIX.length(), key.indexOf(SEPARATOR, PREFIX.length()));
    }

    /** The stat the record contributes to, or {@code null} if not a record. */
    public static String statOf(String key) {
        if (!isRecord(key)) {
            return null;
        }
        return key.substring(key.indexOf(SEPARATOR, PREFIX.length()) + 1);
    }

    /**
     * Whether a source id or stat key is usable in a record key. Ids containing the
     * separator would make the two halves ambiguous, so their grants are recorded on
     * the shared key only and show up as unattributed.
     */
    public static boolean isUsable(String value) {
        return value != null && !value.isBlank() && value.indexOf(SEPARATOR) < 0;
    }
}
