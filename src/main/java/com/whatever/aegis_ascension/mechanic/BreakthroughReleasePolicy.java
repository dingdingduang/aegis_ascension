package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.capability.PlayerPerkData;

/**
 * Central decision for converting stored Breakthrough triggers into immediate effects.
 *
 * <p>Receiving every lifetime XP award is deliberately not a release condition. Stored
 * triggers remain paired with usable Perk selections until the player spends those
 * selections, exhausts the available talent catalog, or reaches a talent-slot cap that
 * cannot still be expanded by an obtainable Aegis or talent.</p>
 */
public final class BreakthroughReleasePolicy {
    private BreakthroughReleasePolicy() {
    }

    public static boolean shouldReleaseRemaining(PlayerPerkData data) {
        if (data.getPendingBreakthroughTriggers() <= 0) {
            return false;
        }
        if (data.getSelectionCharges() <= 0) {
            return true;
        }
        if (data.hasAcquirableTalent()) {
            return false;
        }
        if (!data.isTalentSlotCapReached()) {
            // The catalog is exhausted for another reason: all entries are owned,
            // max-ranked, hidden, locked, or missing their required mods.
            return true;
        }
        return !data.canStillObtainExtraTalentSlots();
    }
}
