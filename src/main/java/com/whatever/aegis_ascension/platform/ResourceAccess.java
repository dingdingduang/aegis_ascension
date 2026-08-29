package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;

/** Boundary for resource identifier factories whose signatures vary by MC version. */
public interface ResourceAccess {
    ResourceLocation create(String namespace, String path);

    ResourceLocation tryParse(String value);
}
