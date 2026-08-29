package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Boundary for loader-specific registry access used by gameplay and screens. */
public interface RegistryAccess {
    Item resolveItem(ResourceLocation itemId);

    ResourceLocation itemKey(Item item);

    Iterable<Item> allItems();

    ResourceLocation entityTypeKey(EntityType<?> entityType);
}
