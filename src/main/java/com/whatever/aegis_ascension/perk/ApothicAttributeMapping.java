package com.whatever.aegis_ascension.perk;

import com.whatever.aegis_ascension.platform.AttributeOperation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Maps one persisted custom stat to an optional Apothic/vanilla attribute.
 *
 * <p>{@code excludedPerks} names talents whose contribution to this custom stat
 * must not be published as an attribute, because mod logic applies it under a
 * condition an attribute cannot express, such as a single damage category.</p>
 */
public record ApothicAttributeMapping(
        String customStat,
        ResourceLocation attribute,
        AttributeOperation operation,
        double scale,
        boolean enabled,
        List<String> excludedPerks
) {
}
