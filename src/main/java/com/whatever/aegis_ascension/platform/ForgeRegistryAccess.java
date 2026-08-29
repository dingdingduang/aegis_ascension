package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Forge 1.20.1 implementation of {@link RegistryAccess}. */
public final class ForgeRegistryAccess implements RegistryAccess {
    @Override
    public Item resolveItem(ResourceLocation itemId) {
        return itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
    }

    @Override
    public ResourceLocation itemKey(Item item) {
        return item == null ? null : ForgeRegistries.ITEMS.getKey(item);
    }

    @Override
    public Iterable<Item> allItems() {
        return ForgeRegistries.ITEMS.getValues();
    }

    @Override
    public ResourceLocation entityTypeKey(EntityType<?> entityType) {
        return entityType == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(entityType);
    }
}
