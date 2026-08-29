package com.whatever.aegis_ascension.perk;

import com.whatever.aegis_ascension.platform.AttributeOperation;
import net.minecraft.resources.ResourceLocation;

/** Maps one persisted custom stat to an optional Apothic/vanilla attribute. */
public record ApothicAttributeMapping(
        String customStat,
        ResourceLocation attribute,
        AttributeOperation operation,
        double scale,
        boolean enabled
) {
}
