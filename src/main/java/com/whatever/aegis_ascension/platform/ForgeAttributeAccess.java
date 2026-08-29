package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Forge 1.20.1 implementation of {@link AttributeAccess}. */
public final class ForgeAttributeAccess implements AttributeAccess {
    @Override
    public Attribute resolve(ResourceLocation attributeId) {
        return attributeId == null ? null : ForgeRegistries.ATTRIBUTES.getValue(attributeId);
    }

    @Override
    public ResourceLocation keyOf(Attribute attribute) {
        return attribute == null ? null : ForgeRegistries.ATTRIBUTES.getKey(attribute);
    }

    @Override
    public Iterable<Attribute> allAttributes() {
        return ForgeRegistries.ATTRIBUTES.getValues();
    }

    @Override
    public Attribute entityReach() {
        return ForgeMod.ENTITY_REACH.get();
    }

    @Override
    public AttributeOperation operationOf(AttributeModifier modifier) {
        if (modifier == null) {
            return null;
        }
        switch (modifier.getOperation()) {
            case ADDITION:
                return AttributeOperation.ADDITION;
            case MULTIPLY_BASE:
                return AttributeOperation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL:
                return AttributeOperation.MULTIPLY_TOTAL;
            default:
                throw new IllegalArgumentException(
                        "Unsupported Forge attribute operation: " + modifier.getOperation()
                );
        }
    }

    @Override
    public Iterable<ItemAttributeModifier> itemModifiers(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<ItemAttributeModifier> snapshots = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stack.getAttributeModifiers(slot).forEach((attribute, modifier) -> {
                ResourceLocation attributeId = keyOf(attribute);
                if (attributeId == null || !Double.isFinite(modifier.getAmount())) {
                    return;
                }
                snapshots.add(new ItemAttributeModifier(
                        attributeId,
                        modifier.getId(),
                        modifier.getAmount(),
                        operationOf(modifier)
                ));
            });
        }
        return snapshots;
    }

    @Override
    public AttributeInstance getInstance(LivingEntity entity, Attribute attribute) {
        return entity == null || attribute == null ? null : entity.getAttribute(attribute);
    }

    @Override
    public AttributeInstance getInstance(LivingEntity entity, ResourceLocation attributeId) {
        if (entity == null || attributeId == null) {
            return null;
        }
        return getInstance(entity, resolve(attributeId));
    }

    @Override
    public double getValue(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance == null ? fallback : instance.getValue();
    }

    @Override
    public double getValue(LivingEntity entity, ResourceLocation attributeId, double fallback) {
        AttributeInstance instance = getInstance(entity, attributeId);
        return instance == null ? fallback : instance.getValue();
    }

    @Override
    public double getBaseValue(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance == null ? fallback : instance.getBaseValue();
    }

    @Override
    public AttributeModifier getModifier(LivingEntity entity, Attribute attribute,
                                          UUID modifierId) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance == null ? null : instance.getModifier(modifierId);
    }

    @Override
    public Iterable<AttributeModifier> getModifiers(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance == null ? java.util.Collections.emptyList() : instance.getModifiers();
    }

    @Override
    public Iterable<AttributeModifier> getModifiers(LivingEntity entity, Attribute attribute,
                                                    AttributeOperation operation) {
        AttributeInstance instance = getInstance(entity, attribute);
        return instance == null
                ? java.util.Collections.emptyList()
                : instance.getModifiers(toForgeOperation(operation));
    }

    @Override
    public void removeModifier(LivingEntity entity, Attribute attribute, UUID modifierId) {
        if (getModifier(entity, attribute, modifierId) != null) {
            AttributeInstance instance = getInstance(entity, attribute);
            if (instance != null) {
                instance.removeModifier(modifierId);
            }
        }
    }

    @Override
    public void addPermanentModifier(LivingEntity entity, Attribute attribute, UUID modifierId,
                                     String name, double amount,
                                     AttributeOperation operation) {
        if (Math.abs(amount) < 1.0E-9D) {
            return;
        }
        AttributeInstance instance = getInstance(entity, attribute);
        if (instance != null) {
            instance.addPermanentModifier(new AttributeModifier(
                    modifierId, name, amount, toForgeOperation(operation)
            ));
        }
    }

    @Override
    public void addTransientModifier(LivingEntity entity, Attribute attribute, UUID modifierId,
                                     String name, double amount,
                                     AttributeOperation operation) {
        if (Math.abs(amount) < 1.0E-9D) {
            return;
        }
        AttributeInstance instance = getInstance(entity, attribute);
        if (instance != null) {
            instance.addTransientModifier(new AttributeModifier(
                    modifierId, name, amount, toForgeOperation(operation)
            ));
        }
    }

    private static AttributeModifier.Operation toForgeOperation(AttributeOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Attribute operation cannot be null");
        }
        switch (operation) {
            case ADDITION:
                return AttributeModifier.Operation.ADDITION;
            case MULTIPLY_BASE:
                return AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL:
                return AttributeModifier.Operation.MULTIPLY_TOTAL;
            default:
                throw new IllegalArgumentException("Unsupported attribute operation: " + operation);
        }
    }
}
