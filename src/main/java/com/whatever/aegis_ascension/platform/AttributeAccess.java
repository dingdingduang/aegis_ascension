package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Boundary for attribute registry lookup and modifier operations.
 *
 * <p>Gameplay code should use this boundary instead of calling the version-specific
 * entity and Forge registry APIs directly. The initial interface still exposes the
 * Minecraft attribute types so the migration can be incremental; the next step can
 * add semantic attribute keys without forcing a one-shot rewrite.</p>
 */
public interface AttributeAccess {
    Attribute resolve(ResourceLocation attributeId);

    ResourceLocation keyOf(Attribute attribute);

    Iterable<Attribute> allAttributes();

    Attribute entityReach();

    AttributeOperation operationOf(AttributeModifier modifier);

    Iterable<ItemAttributeModifier> itemModifiers(ItemStack stack);

    AttributeInstance getInstance(LivingEntity entity, Attribute attribute);

    AttributeInstance getInstance(LivingEntity entity, ResourceLocation attributeId);

    double getValue(LivingEntity entity, Attribute attribute, double fallback);

    double getValue(LivingEntity entity, ResourceLocation attributeId, double fallback);

    double getBaseValue(LivingEntity entity, Attribute attribute, double fallback);

    AttributeModifier getModifier(LivingEntity entity, Attribute attribute, UUID modifierId);

    Iterable<AttributeModifier> getModifiers(LivingEntity entity, Attribute attribute);

    Iterable<AttributeModifier> getModifiers(LivingEntity entity, Attribute attribute,
                                             AttributeOperation operation);

    void removeModifier(LivingEntity entity, Attribute attribute, UUID modifierId);

    void addPermanentModifier(LivingEntity entity, Attribute attribute, UUID modifierId,
                              String name, double amount, AttributeOperation operation);

    void addTransientModifier(LivingEntity entity, Attribute attribute, UUID modifierId,
                              String name, double amount, AttributeOperation operation);
}
