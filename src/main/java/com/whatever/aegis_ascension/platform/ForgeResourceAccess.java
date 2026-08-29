package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.20.1 implementation of resource identifier construction and parsing. */
public final class ForgeResourceAccess implements ResourceAccess {
    @Override
    public ResourceLocation create(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Override
    public ResourceLocation tryParse(String value) {
        return ResourceLocation.tryParse(value);
    }
}
