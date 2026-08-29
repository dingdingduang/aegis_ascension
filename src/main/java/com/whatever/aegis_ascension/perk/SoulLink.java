package com.whatever.aegis_ascension.perk;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.util.ConfigDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

public record SoulLink(
        String id,
        String nameKey,
        String descriptionKey,
        ResourceLocation iconTexture,
        List<String> requirements,
        List<String> rankPerks,
        Map<String, Double> bonusStats,
        boolean enabled,
        int sourceRow
) {
    public SoulLink {
        requirements = List.copyOf(requirements);
        rankPerks = List.copyOf(rankPerks);
        bonusStats = Collections.unmodifiableMap(new LinkedHashMap<>(bonusStats));
    }

    public Component title() {
        return getTranslatableString(nameKey);
    }

    public Component description() {
        return ConfigDescription.render(descriptionKey, bonusStats);
    }

    public double bonusStat(String key) {
        return bonusStats.getOrDefault(key, 0.0D);
    }

    public boolean isActive(PlayerPerkData data) {
        return isActive(data::owns);
    }

    public boolean isActive(Predicate<String> ownsTalent) {
        return enabled && !requirements.isEmpty()
                && requirements.stream().allMatch(ownsTalent);
    }

    public int rank(PlayerPerkData data) {
        return rank(data::owns);
    }

    public int rank(Predicate<String> ownsTalent) {
        if (!isActive(ownsTalent)) {
            return 0;
        }
        if (rankPerks.isEmpty()) {
            return 1;
        }
        return (int) rankPerks.stream().filter(ownsTalent).count();
    }

    public int maxRank() {
        return rankPerks.isEmpty() ? 1 : rankPerks.size();
    }

    /** Returns the configured additive bonus for the Soul Link's current rank. */
    public double rankBonus(PlayerPerkData data) {
        return rankBonus(data::owns);
    }

    public double rankBonus(Predicate<String> ownsTalent) {
        int currentRank = rank(ownsTalent);
        return currentRank <= 0 ? 0.0D : bonusStat(
                "level_" + currentRank + "_bonus"
        );
    }
}
