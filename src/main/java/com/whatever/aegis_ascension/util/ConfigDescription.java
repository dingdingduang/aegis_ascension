package com.whatever.aegis_ascension.util;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/** Resolves named JSON-stat placeholders inside localized descriptions. */
public final class ConfigDescription {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{([a-z0-9_]+)(?::([a-z_]+))?}}"
    );

    private ConfigDescription() {
    }

    public static Component render(String translationKey, Map<String, Double> stats) {
        // "|n" is the config-authoring shorthand for a hard line break.
        String translated = getTranslatableString(translationKey).getString()
                .replace("|n", "\n");
        Matcher matcher = PLACEHOLDER.matcher(translated);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            Double value = stats.get(matcher.group(1));
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            String format = matcher.group(2) == null ? "number" : matcher.group(2);
            String replacement = switch (format) {
                case "percent" -> compact(value * 100.0D) + "%";
                case "absolute_percent" -> compact(Math.abs(value) * 100.0D) + "%";
                case "number" -> compact(value);
                default -> throw new IllegalStateException(
                        "Unknown description stat format: " + format
                );
            };
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return getLiteralString(rendered.toString());
    }
}
