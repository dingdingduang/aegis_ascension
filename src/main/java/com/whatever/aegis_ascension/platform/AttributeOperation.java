package com.whatever.aegis_ascension.platform;

/**
 * Stable attribute-modifier semantics used by Aegis Ascension.
 *
 * <p>The Minecraft names for these operations changed across supported
 * versions. Keeping the semantic names and wire values here lets each
 * platform adapter translate them independently.</p>
 */
public enum AttributeOperation {
    ADDITION(0),
    MULTIPLY_BASE(1),
    MULTIPLY_TOTAL(2);

    private final int wireValue;

    AttributeOperation(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static AttributeOperation fromWireValue(int value) {
        for (AttributeOperation operation : values()) {
            if (operation.wireValue == value) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown attribute operation value: " + value);
    }
}
