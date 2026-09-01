package com.whatever.aegis_ascension.util;

/**
 * How much of the display-stat map a synchronization carries.
 *
 * <p>Display stats are derived numbers that exist to draw the Collection screen; no
 * gameplay decision is made from them on the client. Almost every synchronization
 * happens for an unrelated reason — a shop purchase, a storage action, a quest event, a
 * milestone award — and at that moment the screen is usually closed, so shipping the
 * whole map is waste. This says which slice the receiver actually needs.</p>
 *
 * <p>The scope travels with the packet because the client has to know whether it was
 * handed a complete set it can swap in, or a fragment it must merge.</p>
 */
public enum DisplayStatScope {
    /**
     * Only the handful of values screens outside Custom Stats read. Sent with routine
     * progression syncs. The client merges these over what it already has.
     */
    ESSENTIAL,
    /**
     * Every display value, without the per-source attribution records. For Collection
     * tabs that show stat values but no source breakdown. Replaces the client's values.
     */
    VALUES,
    /**
     * Everything, including the per-source records behind the stat-source panel. Only
     * the Custom Stats tab asks for this.
     */
    FULL;

    /** Whether the per-source attribution records are included. */
    public boolean includesAttribution() {
        return this == FULL;
    }

    /** Whether this carries a complete set of values rather than a fragment to merge. */
    public boolean isComplete() {
        return this != ESSENTIAL;
    }

    public int wireValue() {
        return ordinal();
    }

    public static DisplayStatScope fromWireValue(int value) {
        DisplayStatScope[] values = values();
        return value >= 0 && value < values.length ? values[value] : ESSENTIAL;
    }
}
