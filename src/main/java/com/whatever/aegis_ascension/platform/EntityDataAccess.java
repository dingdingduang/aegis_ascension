package com.whatever.aegis_ascension.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Boundary for target-specific persistent data attached to an entity. */
public interface EntityDataAccess {
    CompoundTag persistentData(Entity entity);
}
