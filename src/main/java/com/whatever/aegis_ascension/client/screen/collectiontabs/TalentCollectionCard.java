package com.whatever.aegis_ascension.client.screen.collectiontabs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared, immutable rendering model used by every Talent Collection tab. */
public record TalentCollectionCard(
        ResourceLocation icon,
        int iconTextureSize,
        Component title,
        Component description,
        Component status,
        Component tooltip,
        int color,
        int statusColor,
        boolean active,
        String togglePerkId,
        String toggleAegisId,
        String skillEnhancementId,
        String statKey
) {
}
