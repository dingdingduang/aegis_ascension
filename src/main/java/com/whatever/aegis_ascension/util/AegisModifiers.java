package com.whatever.aegis_ascension.util;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ownership registry for every attribute modifier this mod applies.
 *
 * <p>A player attribute such as Attack Damage carries modifiers from many places at
 * once: the held weapon, worn armour, potions, other mods, and this mod's talents and
 * Aegises. The Custom Stats cards for those attributes have to say how much of the
 * number came from Aegis Ascension, and no arithmetic on the final value can recover
 * that — a diamond sword's {@code +7} is additive in exactly the way a talent's flat
 * bonus is. Identity can: this mod applies every modifier under a stable id, so a
 * modifier is ours precisely when its id is registered here.</p>
 *
 * <p><b>Contract.</b> Every modifier id the mod creates must come from
 * {@link #mint(String)} (for new ids) or {@link #adopt(String)} (for ids already written
 * into saved player data, whose values can never change). A {@code UUID} built for a
 * modifier any other way is indistinguishable from equipment and will be reported to
 * players as an outside source.</p>
 *
 * <p><b>Registration timing.</b> Ids register when their declaring class first loads. A
 * <em>permanent</em> modifier persists in player data and can therefore already be on
 * the player before its declaring class loads, so ids for permanent modifiers belong in
 * a class that always loads before stats are read — in practice {@code
 * TalentStatService}, which is what computes them. Ids for transient modifiers are safe
 * anywhere, because the class that applies one has by definition already loaded.</p>
 */
public final class AegisModifiers {
    private static final String NAMESPACE = "aegis_ascension:";
    private static final Set<UUID> OWNED = ConcurrentHashMap.newKeySet();

    private AegisModifiers() {
    }

    /**
     * Derives a stable modifier id from a stable path and records it as ours.
     *
     * <p>The id is a name-based UUID over {@code "aegis_ascension:" + path}, identical on
     * every client and server and across restarts. Prefer this for anything new: the
     * path documents the owner, and no literal has to be invented or kept unique by
     * hand.</p>
     */
    public static UUID mint(String path) {
        UUID id = UUID.nameUUIDFromBytes(
                (NAMESPACE + path).getBytes(StandardCharsets.UTF_8)
        );
        OWNED.add(id);
        return id;
    }

    /**
     * Records an existing literal modifier id as ours.
     *
     * <p>These predate {@link #mint(String)} and are written into saved player data by
     * permanent modifiers, so their values must never change: re-deriving one would
     * orphan the modifier already stored on every existing player, which would then be
     * counted as equipment forever.</p>
     */
    public static UUID adopt(String literal) {
        UUID id = UUID.fromString(literal);
        OWNED.add(id);
        return id;
    }

    /** Whether the given modifier id belongs to this mod. */
    public static boolean isOurs(UUID modifierId) {
        return modifierId != null && OWNED.contains(modifierId);
    }

    /** Number of registered ids; intended for diagnostics and tests. */
    public static int ownedCount() {
        return OWNED.size();
    }
}
