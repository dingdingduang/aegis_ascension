package com.whatever.aegis_ascension.network;

import net.minecraft.network.FriendlyByteBuf;

/** Protocol limits that are deliberately independent of either installation's JSON files. */
public final class NetworkLimits {
    public static final int MAX_CATALOG_JSON_CHARS = 262_144;
    public static final int MAX_TOTAL_CATALOG_CHARS = 262_144;
    public static final int MAX_CATALOG_HASH_CHARS = 64;
    public static final int MAX_TALENTS = 512;
    public static final int MAX_AEGISES = 128;
    public static final int MAX_SKILL_ENHANCEMENTS = 256;
    public static final int MAX_VIRTUAL_ITEMS = 512;
    public static final int MAX_SOUL_LINKS = 256;
    public static final int MAX_SPECIAL_TALENT_OUTCOMES = 256;
    public static final int MAX_DISPLAY_STATS = 1_024;
    public static final int MAX_SHOP_OFFERS = 512;
    public static final int MAX_STORAGE_ROWS = 4_096;
    public static final int MAX_QUESTS = 128;
    public static final int MAX_QUEST_COMPLETIONS = 256;
    public static final int MAX_QUEST_PROGRESS_UPDATES = 128;
    /** Main objective plus extras, so a compound quest cannot be unbounded. */
    public static final int MAX_QUEST_REQUIREMENTS = 8;
    /** Alternatives a quest may offer to choose between. */
    public static final int MAX_QUEST_REWARD_CHOICES = 6;

    private NetworkLimits() {
    }

    public static int readBoundedCount(
            FriendlyByteBuf buffer,
            int maximum,
            String description
    ) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Invalid " + description + " count: " + count + " (maximum " + maximum + ")"
            );
        }
        return count;
    }
}
