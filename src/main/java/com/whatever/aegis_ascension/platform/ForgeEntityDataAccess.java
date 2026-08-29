package com.whatever.aegis_ascension.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Forge 1.20.1 implementation backed by the entity persistent-data tag. */
public final class ForgeEntityDataAccess implements EntityDataAccess {
    @Override
    public CompoundTag persistentData(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        return entity.getPersistentData();
    }
}
