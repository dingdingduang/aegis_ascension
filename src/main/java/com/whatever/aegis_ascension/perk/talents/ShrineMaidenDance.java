package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.formatPercent;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.nonNegativeCount;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.soullink.MadokaWithHomura;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Server-authoritative Shrine Maiden Dance roll loaded from its own JSON file. */
public final class ShrineMaidenDance {
    private static final String HOSTILE_ZOMBIE = "hostile_zombie";
    private static final String BREAKTHROUGH_EFFECT = "breakthrough_effect";
    private static final String DIVINE_PUNISHMENT = "divine_punishment";
    private static final String ALL_SKILL_PENALTY = "all_skill_penalty";
    private static final String RANDOM_ITEMS = "random_items";
    private static final String DAMAGE_REDUCTION_PENALTY =
            "damage_reduction_penalty";
    private static final String SKILL_ENHANCEMENT_CHARGES =
            "skill_enhancement_charges";
    private static final Set<String> NEGATED_BY_LAW_OF_CYCLE = Set.of(
            HOSTILE_ZOMBIE,
            DIVINE_PUNISHMENT,
            ALL_SKILL_PENALTY,
            DAMAGE_REDUCTION_PENALTY
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Catalog CATALOG = loadCatalog();

    private ShrineMaidenDance() {
    }

    /** Forces creation and validation of the server-editable JSON during startup. */
    public static void initialize() {
        CATALOG.outcomes.size();
    }

    public static void roll(ServerPlayer player, PlayerPerkData data) {
        List<Outcome> available = CATALOG.outcomes.stream()
                .filter(Outcome::enabled)
                .filter(outcome -> outcome.weight() > 0.0D)
                .toList();
        double totalWeight = available.stream().mapToDouble(Outcome::weight).sum();
        if (totalWeight <= 0.0D) {
            return;
        }
        double roll = player.getRandom().nextDouble() * totalWeight;
        Outcome selected = available.get(available.size() - 1);
        for (Outcome outcome : available) {
            roll -= outcome.weight();
            if (roll < 0.0D) {
                selected = outcome;
                break;
            }
        }

        if (data.owns(PERK_LAW_OF_THE_CYCLE)
                && NEGATED_BY_LAW_OF_CYCLE.contains(selected.id())) {
            if (MadokaWithHomura.isActive(data)) {
                convertNegatedOutcome(selected.id(), player, data);
                data.applyChosenPerks(player);
            } else {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.negated"
                ));
            }
            return;
        }

        grant(selected.id(), player, data);
        data.applyChosenPerks(player);
    }

    private static void convertNegatedOutcome(String outcomeId, ServerPlayer player,
                                              PlayerPerkData data) {
        switch (outcomeId) {
            case ALL_SKILL_PENALTY -> {
                double amount = MadokaWithHomura.convertPercentagePenalty(
                        data,
                        CATALOG.settings.allSkillEnhancementAttributePenalty
                );
                data.addAttributedCustomStat(PERK_SHRINE_MAIDEN_DANCE,
                        ALL_SKILL_ENHANCEMENT_ATTRIBUTE, amount);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.madoka_homura.converted_percentage",
                        amount
                ));
            }
            case DAMAGE_REDUCTION_PENALTY -> {
                double amount = MadokaWithHomura.convertDamageReductionPenalty(
                        data,
                        CATALOG.settings.damageReductionPenalty
                );
                data.addAttributedCustomStat(PERK_SHRINE_MAIDEN_DANCE,
                        DAMAGE_REDUCTION, amount);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.madoka_homura.converted_damage_reduction",
                        amount
                ));
            }
            case HOSTILE_ZOMBIE, DIVINE_PUNISHMENT ->
                    MadokaWithHomura.grantNonnumericReward(player, data);
            default -> throw new IllegalStateException(
                    "Unsupported converted Shrine Maiden outcome: " + outcomeId
            );
        }
    }

    public static Component description() {
        MutableComponent description = getTranslatableString(
                "perk.aegis_ascension.perk_shrine_maiden_dance.description"
        );
        for (Outcome outcome : CATALOG.outcomes.stream()
                .filter(Outcome::enabled)
                .toList()) {
            description.append("\n").append(describe(outcome));
        }
        return description.append("\n").append(getTranslatableString(
                "perk.aegis_ascension.perk_shrine_maiden_dance.description.footer"
        ));
    }

    private static void grant(String outcomeId, ServerPlayer player,
                              PlayerPerkData data) {
        Settings settings = CATALOG.settings;
        switch (outcomeId) {
            case HOSTILE_ZOMBIE -> {
                spawnHostileZombie(player, settings);
                notify(player, "zombie");
            }
            case BREAKTHROUGH_EFFECT -> {
                data.addCustomStat(
                        BREAKTHROUGH_EFFECT_MULTIPLIER_BONUS,
                        settings.breakthroughEffectBonus
                );
                notify(player, "breakthrough");
            }
            case DIVINE_PUNISHMENT -> {
                double maxHealth = Math.max(1.0D, player.getMaxHealth());
                float health = player.getHealth();
                if (health > maxHealth * settings.healthHalvingThreshold) {
                    player.setHealth((float) Math.max(
                            1.0D,
                            health * settings.healthMultiplierAboveThreshold
                    ));
                } else {
                    player.setHealth((float) Math.max(1.0D, settings.fallbackHealth));
                }
                strikeLightning(player);
                notify(player, "lightning");
            }
            case ALL_SKILL_PENALTY -> {
                data.addAttributedCustomStat(
                        PERK_SHRINE_MAIDEN_DANCE,
                        ALL_SKILL_ENHANCEMENT_ATTRIBUTE,
                        settings.allSkillEnhancementAttributePenalty
                );
                notify(player, "all_skill_penalty");
            }
            case RANDOM_ITEMS -> {
                grantRandomItems(player, Math.max(0, settings.randomItemRolls));
                notify(player, "items");
            }
            case DAMAGE_REDUCTION_PENALTY -> {
                data.addAttributedCustomStat(
                        PERK_SHRINE_MAIDEN_DANCE,
                        DAMAGE_REDUCTION,
                        settings.damageReductionPenalty
                );
                notify(player, "damage_reduction");
            }
            case SKILL_ENHANCEMENT_CHARGES -> {
                int minimum = Math.max(0, settings.skillEnhancementChargesMin);
                int maximum = Math.max(minimum, settings.skillEnhancementChargesMax);
                int charges = minimum + player.getRandom().nextInt(
                        maximum - minimum + 1
                );
                data.addSkillEnhancementCharges(charges);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.skill_charges",
                        charges
                ));
            }
            default -> throw new IllegalStateException(
                    "Unsupported Shrine Maiden Dance outcome: " + outcomeId
            );
        }
    }

    private static void spawnHostileZombie(ServerPlayer player, Settings settings) {
        ServerLevel level = player.serverLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return;
        }
        zombie.moveTo(player.getX() + 1.5D, player.getY(), player.getZ() + 1.5D,
                player.getYRot(), 0.0F);
        setBaseValue(GeneralServerMethods.getAttributeInstance(zombie, Attributes.MAX_HEALTH),
                Math.max(1.0D, settings.zombieMaxHealth));
        setBaseValue(GeneralServerMethods.getAttributeInstance(zombie, Attributes.ATTACK_DAMAGE), Math.max(
                settings.zombieMinimumAttackDamage,
                GeneralServerMethods.getAttributeValue(player, Attributes.ATTACK_DAMAGE)
        ));
        setBaseValue(GeneralServerMethods.getAttributeInstance(zombie, Attributes.ATTACK_SPEED),
                Math.max(0.0D, settings.zombieAttackSpeed));
        zombie.setHealth(zombie.getMaxHealth());
        zombie.setPersistenceRequired();
        zombie.setTarget(player);
        level.addFreshEntity(zombie);
    }

    private static void setBaseValue(AttributeInstance attribute, double value) {
        if (attribute != null && Double.isFinite(value)) {
            attribute.setBaseValue(value);
        }
    }

    private static void strikeLightning(ServerPlayer player) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(player.serverLevel());
        if (lightning == null) {
            return;
        }
        lightning.moveTo(player.position());
        lightning.setCause(player);
        player.serverLevel().addFreshEntity(lightning);
    }

    private static void grantRandomItems(ServerPlayer player, int rolls) {
        List<Item> itemPool = new ArrayList<>();
        for (Item item : GeneralServerMethods.getAllItems()) {
            if (item != Items.AIR && item.isEnabled(player.level().enabledFeatures())) {
                itemPool.add(item);
            }
        }
        if (itemPool.isEmpty()) {
            return;
        }
        for (int index = 0; index < rolls; index++) {
            Item item = itemPool.get(player.getRandom().nextInt(itemPool.size()));
            ItemStack stack = new ItemStack(item);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false, false);
            }
        }
    }

    private static void notify(ServerPlayer player, String outcome) {
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.shrine_maiden." + outcome
        ));
    }

    private static Component describe(Outcome outcome) {
        Settings settings = CATALOG.settings;
        String key = "perk.aegis_ascension.perk_shrine_maiden_dance.outcome."
                + outcome.id();
        String weight = formatPercent(outcome.weight());
        return switch (outcome.id()) {
            case HOSTILE_ZOMBIE -> getTranslatableString(
                    key, weight, compact(settings.zombieMaxHealth),
                    compact(settings.zombieMinimumAttackDamage),
                    compact(settings.zombieAttackSpeed)
            );
            case BREAKTHROUGH_EFFECT -> getTranslatableString(
                    key, weight, formatPercent(settings.breakthroughEffectBonus)
            );
            case DIVINE_PUNISHMENT -> getTranslatableString(
                    key, weight, formatPercent(settings.healthHalvingThreshold),
                    formatPercent(settings.healthMultiplierAboveThreshold),
                    compact(settings.fallbackHealth)
            );
            case ALL_SKILL_PENALTY -> getTranslatableString(
                    key, weight,
                    formatPercent(settings.allSkillEnhancementAttributePenalty)
            );
            case RANDOM_ITEMS -> getTranslatableString(
                    key, weight, settings.randomItemRolls
            );
            case DAMAGE_REDUCTION_PENALTY -> getTranslatableString(
                    key, weight, formatPercent(settings.damageReductionPenalty)
            );
            case SKILL_ENHANCEMENT_CHARGES -> getTranslatableString(
                    key, weight, settings.skillEnhancementChargesMin,
                    settings.skillEnhancementChargesMax
            );
            default -> getLiteralString(outcome.id());
        };
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("shrine_maiden_dance.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = ShrineMaidenDance.class.getResourceAsStream(
                        "/assets/aegis_ascension/shrine_maiden_dance.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default Shrine Maiden Dance JSON"
                        );
                    }
                    Files.copy(stream, configPath);
                }
            }
            Catalog catalog;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                catalog = GSON.fromJson(reader, Catalog.class);
            }
            validate(catalog);
            return catalog;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void validate(Catalog catalog) {
        Objects.requireNonNull(catalog, "Shrine Maiden Dance catalog was empty");
        Objects.requireNonNull(catalog.settings, "Missing Shrine Maiden Dance settings");
        Objects.requireNonNull(catalog.outcomes, "Missing Shrine Maiden Dance outcomes");
        if (catalog.outcomes.isEmpty()) {
            throw new IllegalStateException("Shrine Maiden Dance outcome pool is empty");
        }
        Set<String> supported = Set.of(
                HOSTILE_ZOMBIE, BREAKTHROUGH_EFFECT, DIVINE_PUNISHMENT,
                ALL_SKILL_PENALTY, RANDOM_ITEMS, DAMAGE_REDUCTION_PENALTY,
                SKILL_ENHANCEMENT_CHARGES
        );
        Set<String> ids = new LinkedHashSet<>();
        for (Outcome outcome : catalog.outcomes) {
            Objects.requireNonNull(outcome.id(), "Missing Shrine Maiden Dance outcome id");
            if (!supported.contains(outcome.id())) {
                throw new IllegalStateException(
                        "Unknown Shrine Maiden Dance outcome: " + outcome.id()
                );
            }
            if (!ids.add(outcome.id())) {
                throw new IllegalStateException(
                        "Duplicate Shrine Maiden Dance outcome: " + outcome.id()
                );
            }
            if (!Double.isFinite(outcome.weight())) {
                throw new IllegalStateException(
                        "Non-finite Shrine Maiden Dance weight: " + outcome.id()
                );
            }
        }
    }

    private record Outcome(String id, boolean enabled, double weight) {
    }

    private static final class Catalog {
        private Settings settings = new Settings();
        private List<Outcome> outcomes = new ArrayList<>();
    }

    private static final class Settings {
        @SerializedName("zombie_max_health")
        private double zombieMaxHealth = 10000.0D;
        @SerializedName("zombie_minimum_attack_damage")
        private double zombieMinimumAttackDamage = 100.0D;
        @SerializedName("zombie_attack_speed")
        private double zombieAttackSpeed = 4.0D;
        @SerializedName("breakthrough_effect_bonus")
        private double breakthroughEffectBonus = 0.5D;
        @SerializedName("health_halving_threshold")
        private double healthHalvingThreshold = 0.5D;
        @SerializedName("health_multiplier_above_threshold")
        private double healthMultiplierAboveThreshold = 0.5D;
        @SerializedName("fallback_health")
        private double fallbackHealth = 1.0D;
        @SerializedName("all_skill_enhancement_attribute_penalty")
        private double allSkillEnhancementAttributePenalty = -0.3D;
        @SerializedName("random_item_rolls")
        private int randomItemRolls = 3;
        @SerializedName("damage_reduction_penalty")
        private double damageReductionPenalty = -1.0D;
        @SerializedName("skill_enhancement_charges_min")
        private int skillEnhancementChargesMin = 1;
        @SerializedName("skill_enhancement_charges_max")
        private int skillEnhancementChargesMax = 10;
    }
}
