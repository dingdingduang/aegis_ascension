package com.whatever.aegis_ascension.platform;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Immutable, target-neutral snapshot of one attribute modifier exposed by an item. */
public final class ItemAttributeModifier {
    private final ResourceLocation attributeId;
    private final UUID modifierId;
    private final double amount;
    private final AttributeOperation operation;

    public ItemAttributeModifier(
            ResourceLocation attributeId,
            UUID modifierId,
            double amount,
            AttributeOperation operation
    ) {
        this.attributeId = attributeId;
        this.modifierId = modifierId;
        this.amount = amount;
        this.operation = operation;
    }

    public ResourceLocation attributeId() {
        return attributeId;
    }

    public UUID modifierId() {
        return modifierId;
    }

    public double amount() {
        return amount;
    }

    public AttributeOperation operation() {
        return operation;
    }
}
