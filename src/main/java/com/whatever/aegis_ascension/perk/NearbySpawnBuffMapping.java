package com.whatever.aegis_ascension.perk;

import com.whatever.aegis_ascension.platform.AttributeOperation;
import net.minecraft.resources.ResourceLocation;

/**
 * Maps one talent stat to the attribute it strengthens on mobs spawning near its owner.
 *
 * <p>A talent joins in by declaring {@code stat} alongside a {@code nearby_spawn_radius};
 * which attribute that stat moves, and how, is decided here rather than in code, so a new
 * kind of buff is a row in this table rather than a new branch.</p>
 */
public record NearbySpawnBuffMapping(
        String stat,
        ResourceLocation attribute,
        AttributeOperation operation,
        boolean enabled
) {
}
