package com.whatever.aegis_ascension.platform;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Forge 1.20.1 implementation of {@link PathAccess}. */
public final class ForgePathAccess implements PathAccess {
    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
