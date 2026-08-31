package com.whatever.aegis_ascension.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Minecraft text construction kept separate from both gameplay and rendering.
 *
 * <p>The implementation is version-specific because text classes differ between
 * Minecraft releases. Callers should not need to know which text implementation
 * is active.</p>
 */
public final class GeneralTextMethods {
    private GeneralTextMethods() {
    }

    public static MutableComponent getTranslatableString(String translatableString) {
        return Component.translatable(translatableString);
    }

    public static MutableComponent getTranslatableString(String translatableString,
                                                          Object... formattedString) {
        return Component.translatable(translatableString, formattedString);
    }

    public static MutableComponent getLiteralString(String literalString) {
        return Component.literal(literalString);
    }

    public static MutableComponent getEmpty() {
        return Component.empty();
    }
}
