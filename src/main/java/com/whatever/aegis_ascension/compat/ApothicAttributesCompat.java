package com.whatever.aegis_ascension.compat;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.ApothicAttributeMapping;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Optional, classloader-safe integration with Apothic Attributes 1.20.1. */
public final class ApothicAttributesCompat {
    public static final String MOD_ID = "attributeslib";

    private static final ResourceLocation CRIT_CHANCE_ATTRIBUTE =
            PlatformServices.resources().create(MOD_ID, CRIT_CHANCE);
    private static final ResourceLocation CRIT_DAMAGE_ATTRIBUTE =
            PlatformServices.resources().create(MOD_ID, CRIT_DAMAGE);
    private static final UUID CRIT_CHANCE_MODIFIER_ID =
            UUID.fromString("407cebb7-4ff4-4a3d-a805-fd79cd86b753");
    private static final UUID CRIT_DAMAGE_MODIFIER_ID =
            UUID.fromString("3be8eb03-d9ca-45dc-89fd-9b8582b2bfb9");
    private static final UUID FLAME_CRIT_DAMAGE_MODIFIER_ID =
            UUID.fromString("457004cf-5d91-4308-a11f-aae87b93664c");
    private static final UUID MILLENNIUM_OVERFLOW_MODIFIER_ID =
            UUID.fromString("79b5f43d-9883-4f77-89c8-9165cf4f75ea");

    private ApothicAttributesCompat() {
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /**
     * True when Apothic's synced critical attributes are attached to this player.
     * In that case Apothic owns the critical-hit roll and Aegis Ascension must not
     * perform a second independent roll.
     */
    public static boolean handlesCriticalHits(Player player) {
        return isLoaded()
                && getInstance(player, CRIT_CHANCE_ATTRIBUTE) != null
                && getInstance(player, CRIT_DAMAGE_ATTRIBUTE) != null;
    }

    /** True when an enabled mapping is backed by a live Apothic attribute. */
    public static boolean handlesMappedAttribute(Player player, String customStat) {
        if (!isLoaded()) {
            return false;
        }
        return Perk.apothicAttributeMappings().stream().anyMatch(mapping ->
                mapping.enabled()
                        && mapping.customStat().equals(customStat)
                        && getInstance(player, mapping.attribute()) != null
        );
    }

    public static double criticalChance(Player player, double fallback) {
        if (!isLoaded() || getInstance(player, CRIT_CHANCE_ATTRIBUTE) == null) {
            return fallback;
        }
        return GeneralServerMethods.getAttributeValue(player, CRIT_CHANCE_ATTRIBUTE, fallback);
    }

    public static double criticalDamage(Player player, double fallback) {
        if (!isLoaded() || getInstance(player, CRIT_DAMAGE_ATTRIBUTE) == null) {
            return fallback;
        }
        return GeneralServerMethods.getAttributeValue(player, CRIT_DAMAGE_ATTRIBUTE, fallback);
    }

    /**
     * Publishes persisted custom stats as real modifiers visible in Apothic's
     * Attributes GUI. Crit mappings use the complete calculated talent values,
     * while all other mappings read their named PlayerPerkData custom stat.
     */
    public static void updateAttributeModifiers(Player player, PlayerPerkData data,
                                                double criticalChanceBonus,
                                                double criticalDamageBonus,
                                                double flameDamagePerCriticalChance,
                                                double overflowChanceStep,
                                                double damagePerOverflowStep) {
        if (!isLoaded()) {
            return;
        }

        // Always clear the two modifier IDs used by the older compatibility
        // implementation so updating the mod cannot leave duplicate values.
        updateModifier(player, CRIT_CHANCE_ATTRIBUTE, CRIT_CHANCE_MODIFIER_ID,
                "aegis_ascension:critical_chance", 0.0D,
                AttributeOperation.ADDITION);
        updateModifier(player, CRIT_DAMAGE_ATTRIBUTE, CRIT_DAMAGE_MODIFIER_ID,
                "aegis_ascension:critical_damage", 0.0D,
                AttributeOperation.ADDITION);

        for (ApothicAttributeMapping mapping : Perk.apothicAttributeMappings()) {
            double amount = mappedAmount(
                    mapping,
                    data,
                    criticalChanceBonus,
                    criticalDamageBonus
            );
            if (!mapping.enabled()) {
                amount = 0.0D;
            }
            updateModifier(
                    player,
                    mapping.attribute(),
                    modifierId(mapping),
                    "aegis_ascension:" + mapping.customStat(),
                    amount * mapping.scale(),
                    mapping.operation()
            );
        }

        refreshDynamicCriticalDamageModifiers(
                player,
                flameDamagePerCriticalChance,
                overflowChanceStep,
                damagePerOverflowStep
        );
    }

    /**
     * Re-evaluates effects derived from final Crit Chance after commands,
     * equipment, or other mods change the attribute without changing Perk
     * Selection's own data.
     */
    public static void refreshDynamicCriticalDamageModifiers(
            Player player,
            double flameDamagePerCriticalChance,
            double overflowChanceStep,
            double damagePerOverflowStep) {
        if (!isLoaded()) {
            return;
        }
        double rawFlameCriticalDamage = Math.max(
                0.0D,
                GeneralServerMethods.getAttributeValue(
                        player, CRIT_CHANCE_ATTRIBUTE, 0.0D
                )
        )
                * Math.max(0.0D, flameDamagePerCriticalChance);
        ApothicAttributeMapping criticalDamageMapping = Perk.apothicAttributeMappings()
                .stream()
                .filter(ApothicAttributesCompat::isCriticalDamageMapping)
                .findFirst()
                .orElse(null);
        double flameCriticalDamage = criticalDamageMapping != null
                && criticalDamageMapping.enabled()
                ? rawFlameCriticalDamage * criticalDamageMapping.scale()
                : 0.0D;
        updateModifier(
                player,
                CRIT_DAMAGE_ATTRIBUTE,
                FLAME_CRIT_DAMAGE_MODIFIER_ID,
                "aegis_ascension:flame_aegis_critical_damage",
                flameCriticalDamage,
                criticalDamageMapping == null
                        ? AttributeOperation.ADDITION
                        : criticalDamageMapping.operation()
        );

        double overflowDamage = overflowCriticalDamage(
                player,
                overflowChanceStep,
                damagePerOverflowStep
        );
        updateModifier(
                player,
                CRIT_DAMAGE_ATTRIBUTE,
                MILLENNIUM_OVERFLOW_MODIFIER_ID,
                "aegis_ascension:millennium_echo_overflow",
                overflowDamage,
                AttributeOperation.ADDITION
        );
    }

    private static double mappedAmount(ApothicAttributeMapping mapping,
                                       PlayerPerkData data,
                                       double criticalChanceBonus,
                                       double criticalDamageBonus) {
        if (mapping.attribute().equals(CRIT_CHANCE_ATTRIBUTE)
                && mapping.customStat().equals(CRITICAL_CHANCE)) {
            return criticalChanceBonus;
        }
        if (mapping.attribute().equals(CRIT_DAMAGE_ATTRIBUTE)
                && mapping.customStat().equals(CRITICAL_DAMAGE)) {
            return criticalDamageBonus;
        }
        double amount = data.getCustomStat(mapping.customStat());
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.manuallyToggleable() || data.isTalentEnabled(perk.id())) {
                amount += perk.stat(mapping.customStat()) * entry.getValue();
            }
        }
        amount += data.getActiveSoulLinks().stream()
                .mapToDouble(link -> link.bonusStat(mapping.customStat()))
                .sum();
        return amount;
    }

    private static double overflowCriticalDamage(Player player,
                                                 double overflowChanceStep,
                                                 double damagePerOverflowStep) {
        if (overflowChanceStep <= 0.0D || damagePerOverflowStep == 0.0D) {
            return 0.0D;
        }
        if (getInstance(player, CRIT_CHANCE_ATTRIBUTE) == null) {
            return 0.0D;
        }
        double overflow = Math.max(
                0.0D,
                GeneralServerMethods.getAttributeValue(player, CRIT_CHANCE_ATTRIBUTE, 0.0D)
                        - 1.0D
        );
        double steps = Math.floor((overflow + 1.0E-9D) / overflowChanceStep);
        return steps * damagePerOverflowStep;
    }

    private static UUID modifierId(ApothicAttributeMapping mapping) {
        if (mapping.attribute().equals(CRIT_CHANCE_ATTRIBUTE)
                && mapping.customStat().equals(CRITICAL_CHANCE)) {
            return CRIT_CHANCE_MODIFIER_ID;
        }
        if (mapping.attribute().equals(CRIT_DAMAGE_ATTRIBUTE)
                && mapping.customStat().equals(CRITICAL_DAMAGE)) {
            return CRIT_DAMAGE_MODIFIER_ID;
        }
        String key = "aegis_ascension:custom_stat/" + mapping.customStat()
                + "/" + mapping.attribute() + "/" + mapping.operation().name();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isCriticalDamageMapping(ApothicAttributeMapping mapping) {
        return mapping.attribute().equals(CRIT_DAMAGE_ATTRIBUTE)
                && mapping.customStat().equals(CRITICAL_DAMAGE);
    }

    private static void updateModifier(Player player, ResourceLocation attributeId,
                                       UUID modifierId, String name, double amount,
                                       AttributeOperation operation) {
        AttributeInstance instance = getInstance(player, attributeId);
        if (instance == null) {
            return;
        }
        AttributeModifier current = GeneralServerMethods.getAttributeModifier(
                player, attributeId, modifierId
        );
        if (current != null) {
            if (Math.abs(current.getAmount() - amount) < 1.0E-9D
                    && GeneralServerMethods.getAttributeOperation(current) == operation) {
                return;
            }
            GeneralServerMethods.removeAttributeModifier(player, attributeId, modifierId);
        }
        if (Math.abs(amount) >= 1.0E-9D) {
            GeneralServerMethods.addAttributeModifier(
                    player, attributeId, modifierId, name, amount, operation
            );
        }
    }

    private static AttributeInstance getInstance(Player player, ResourceLocation attributeId) {
        return GeneralServerMethods.getAttributeInstance(player, attributeId);
    }
}
