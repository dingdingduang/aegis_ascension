package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_LAW_OF_THE_CYCLE;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_SHRINE_MAIDEN_DANCE;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.formatPercent;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.nonNegativeCount;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.OutcomeAnnouncement;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.Optional;
import java.util.Set;

/**
 * Server-authoritative Shrine Maiden Dance roll loaded from its own JSON file.
 *
 * <p>Outcomes are typed and self-contained, the same shape Mysterious Doll uses: each
 * entry carries its own parameters instead of reaching into a shared settings block, and
 * says for itself whether Law of Cycle negates it. A new outcome of an existing type is a
 * JSON edit with no Java change.</p>
 */
public final class ShrineMaidenDance {
    public static final String CUSTOM_STAT = "custom_stat";
    public static final String RANDOM_AEGIS = "random_aegis";
    public static final String RANDOM_ITEM = "random_item";
    public static final String CHARGE_GRANT = "charge_grant";
    public static final String SPAWN_HOSTILE = "spawn_hostile";
    public static final String HEALTH_STRIKE = "health_strike";

    private static final List<String> TYPES = List.of(
            CUSTOM_STAT, RANDOM_AEGIS, RANDOM_ITEM, CHARGE_GRANT, SPAWN_HOSTILE, HEALTH_STRIKE
    );

    private static final String CHARGE_SKILL_ENHANCEMENT = "skill_enhancement";
    private static final String CHARGE_PERK = "perk";
    private static final String CHARGE_PERK_REFRESH = "perk_refresh";
    private static final String CHARGE_AEGIS = "aegis";
    private static final List<String> CHARGES = List.of(
            CHARGE_SKILL_ENHANCEMENT, CHARGE_PERK, CHARGE_PERK_REFRESH, CHARGE_AEGIS
    );

    /** How Madoka with Homura turns a negated outcome into a benefit. */
    private static final String CONVERSION_PERCENTAGE = "percentage";
    private static final String CONVERSION_DAMAGE_REDUCTION = "damage_reduction";
    private static final String CONVERSION_NONNUMERIC = "nonnumeric";
    private static final List<String> CONVERSIONS = List.of(
            CONVERSION_PERCENTAGE, CONVERSION_DAMAGE_REDUCTION, CONVERSION_NONNUMERIC
    );

    private static final List<String> FORMATS = List.of("number", "percent", "absolute_percent");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CATALOG_ENTRIES = 256;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static volatile Catalog activeCatalog = LOCAL_CATALOG;

    private ShrineMaidenDance() {
    }

    /** Forces creation and validation of the server-editable JSON during startup. */
    public static void initialize() {
        activeCatalog.outcomes.size();
    }

    public static int outcomeCount() {
        return activeCatalog.outcomes.size();
    }

    public static String exportCatalogJson() {
        return GSON.toJson(LOCAL_CATALOG);
    }

    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized Shrine Maiden Dance catalog was empty"
        );
        validate(catalog);
        activeCatalog = catalog;
    }

    public static void resetSyncedCatalog() {
        activeCatalog = LOCAL_CATALOG;
    }

    public static void roll(ServerPlayer player, PlayerPerkData data) {
        Catalog catalog = activeCatalog;
        List<Item> itemPool = randomItemPool(player);
        List<Outcome> available = catalog.outcomes.stream()
                .filter(outcome -> outcome.enabled)
                .filter(outcome -> outcome.weight > 0.0D)
                .filter(outcome -> isAvailable(outcome, data, itemPool))
                .toList();
        double totalWeight = available.stream().mapToDouble(outcome -> outcome.weight).sum();
        if (totalWeight <= 0.0D) {
            return;
        }
        double roll = player.getRandom().nextDouble() * totalWeight;
        Outcome selected = available.get(available.size() - 1);
        for (Outcome outcome : available) {
            roll -= outcome.weight;
            if (roll < 0.0D) {
                selected = outcome;
                break;
            }
        }

        if (selected.negatedByLawOfCycle && data.owns(PERK_LAW_OF_THE_CYCLE)) {
            Component negated;
            if (MadokaWithHomura.isActive(data)) {
                negated = convertNegatedOutcome(selected, player, data);
                data.applyChosenPerks(player);
            } else {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.negated"
                ));
                negated = label("negated");
            }
            announce(player, negated);
            return;
        }

        Component outcome = grant(selected, player, data, itemPool);
        data.applyChosenPerks(player);
        announce(player, outcome);
    }

    private static boolean isAvailable(Outcome outcome, PlayerPerkData data,
                                       List<Item> itemPool) {
        return switch (outcome.type) {
            case RANDOM_AEGIS -> nonNegativeCount(outcome.amount) > 0
                    && data.hasAvailableRandomAegis();
            case RANDOM_ITEM -> nonNegativeCount(outcome.amount) > 0 && !itemPool.isEmpty();
            case CUSTOM_STAT -> Math.abs(outcome.amount) > 1.0E-9D;
            default -> true;
        };
    }

    private static Component grant(Outcome outcome, ServerPlayer player,
                                   PlayerPerkData data, List<Item> itemPool) {
        switch (outcome.type) {
            case CUSTOM_STAT -> {
                addStat(data, outcome, outcome.amount);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.custom_stat",
                        statName(outcome), signedAmount(outcome, outcome.amount)));
                return label(outcome);
            }
            case RANDOM_AEGIS -> {
                int count = nonNegativeCount(outcome.amount);
                List<Component> granted = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    Aegis aegis = data.grantRandomUnownedAegis(player).orElse(null);
                    if (aegis == null) {
                        break;
                    }
                    granted.add(aegis.title());
                    player.sendSystemMessage(getTranslatableString(
                            "message.aegis_ascension.shrine_maiden.random_aegis",
                            aegis.title()));
                }
                if (granted.isEmpty()) {
                    player.sendSystemMessage(getTranslatableString(
                            "message.aegis_ascension.shrine_maiden.random_aegis_none"));
                    return label("random_aegis_none");
                }
                return getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.label." + RANDOM_AEGIS,
                        granted.get(0));
            }
            case RANDOM_ITEM -> {
                grantRandomItems(player, itemPool, nonNegativeCount(outcome.amount));
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.items"));
                return label(outcome);
            }
            case CHARGE_GRANT -> {
                int charges = outcome.min + player.getRandom().nextInt(
                        Math.max(1, outcome.max - outcome.min + 1));
                grantCharges(data, outcome.charge, charges);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.charges",
                        charges, chargeName(outcome)));
                return getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.label." + CHARGE_GRANT,
                        charges, chargeName(outcome));
            }
            case SPAWN_HOSTILE -> {
                spawnHostile(player, outcome);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.zombie"));
                return label(outcome);
            }
            case HEALTH_STRIKE -> {
                double maxHealth = Math.max(1.0D, player.getMaxHealth());
                float health = player.getHealth();
                if (health > maxHealth * outcome.threshold) {
                    player.setHealth((float) Math.max(1.0D, health * outcome.multiplierAbove));
                } else {
                    player.setHealth((float) Math.max(1.0D, outcome.fallbackHealth));
                }
                if (outcome.lightning) {
                    strikeLightning(player);
                }
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shrine_maiden.lightning"));
                return label(outcome);
            }
            default -> throw new IllegalStateException(
                    "Unsupported Shrine Maiden Dance outcome: " + outcome.id
            );
        }
    }

    /**
     * Law of Cycle cancelled the outcome and Madoka with Homura pays it back. Numeric
     * conversions write the converted value to the same stat the punishment targeted, so
     * a new negatable stat outcome needs no Java change.
     */
    private static Component convertNegatedOutcome(Outcome outcome, ServerPlayer player,
                                                   PlayerPerkData data) {
        switch (outcome.negatedConversion) {
            case CONVERSION_PERCENTAGE -> {
                double amount = MadokaWithHomura.convertPercentagePenalty(data, outcome.amount);
                addStat(data, outcome, amount);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.madoka_homura.converted_percentage", amount));
                return label("converted");
            }
            case CONVERSION_DAMAGE_REDUCTION -> {
                double amount = MadokaWithHomura.convertDamageReductionPenalty(
                        data, outcome.amount);
                addStat(data, outcome, amount);
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.madoka_homura.converted_damage_reduction",
                        amount));
                return label("converted");
            }
            case CONVERSION_NONNUMERIC -> {
                MadokaWithHomura.grantNonnumericReward(player, data);
                return label("converted");
            }
            default -> throw new IllegalStateException(
                    "Unsupported Shrine Maiden Dance conversion: " + outcome.id
            );
        }
    }

    private static void addStat(PlayerPerkData data, Outcome outcome, double amount) {
        if (outcome.attributed) {
            data.addAttributedCustomStat(PERK_SHRINE_MAIDEN_DANCE, outcome.stat, amount);
        } else {
            data.addCustomStat(outcome.stat, amount);
        }
    }

    private static void grantCharges(PlayerPerkData data, String charge, int amount) {
        switch (charge) {
            case CHARGE_SKILL_ENHANCEMENT -> data.addSkillEnhancementCharges(amount);
            case CHARGE_PERK -> data.addSelectionCharges(amount);
            case CHARGE_PERK_REFRESH -> data.addPerkRefreshCharges(amount);
            case CHARGE_AEGIS -> data.addAegisSelectionCharges(amount);
            default -> throw new IllegalStateException(
                    "Unsupported Shrine Maiden Dance charge: " + charge
            );
        }
    }

    private static void announce(ServerPlayer player, Component outcome) {
        OutcomeAnnouncement.announce(
                player,
                getTranslatableString("message.aegis_ascension.shrine_maiden.title"),
                outcome,
                "message.aegis_ascension.shrine_maiden.broadcast"
        );
    }

    /** The short form of an outcome, for the banner and the broadcast. */
    private static Component label(String suffix) {
        return getTranslatableString("message.aegis_ascension.shrine_maiden.label." + suffix);
    }

    private static Component label(Outcome outcome) {
        String key = "message.aegis_ascension.shrine_maiden.label." + outcome.type;
        return switch (outcome.type) {
            case CUSTOM_STAT -> getTranslatableString(
                    key, statName(outcome), signedAmount(outcome, outcome.amount));
            case RANDOM_ITEM -> getTranslatableString(key, nonNegativeCount(outcome.amount));
            case SPAWN_HOSTILE -> getTranslatableString(key, entityName(outcome));
            case HEALTH_STRIKE -> getTranslatableString(key);
            default -> getLiteralString(outcome.id);
        };
    }

    public static Component description() {
        Catalog catalog = activeCatalog;
        MutableComponent description = getTranslatableString(
                "perk.aegis_ascension.perk_shrine_maiden_dance.description"
        );
        for (Outcome outcome : catalog.outcomes.stream()
                .filter(candidate -> candidate.enabled)
                .toList()) {
            description.append("\n").append(describe(outcome));
        }
        return description.append("\n").append(getTranslatableString(
                "perk.aegis_ascension.perk_shrine_maiden_dance.description.footer"
        ));
    }

    private static Component describe(Outcome outcome) {
        String key = "perk.aegis_ascension.perk_shrine_maiden_dance.outcome." + outcome.type;
        String weight = formatPercent(outcome.weight);
        return switch (outcome.type) {
            case CUSTOM_STAT -> getTranslatableString(
                    key, weight, statName(outcome), signedAmount(outcome, outcome.amount));
            case RANDOM_AEGIS, RANDOM_ITEM -> getTranslatableString(
                    key, weight, nonNegativeCount(outcome.amount));
            case CHARGE_GRANT -> getTranslatableString(
                    key, weight, outcome.min, outcome.max, chargeName(outcome));
            case SPAWN_HOSTILE -> getTranslatableString(
                    key, weight, entityName(outcome), compact(outcome.maxHealth),
                    compact(outcome.minimumAttackDamage), compact(outcome.attackSpeed));
            case HEALTH_STRIKE -> getTranslatableString(
                    key, weight, formatPercent(outcome.threshold),
                    formatPercent(outcome.multiplierAbove), compact(outcome.fallbackHealth));
            default -> getLiteralString(outcome.id);
        };
    }

    private static Component statName(Outcome outcome) {
        String key = outcome.translationKey.isBlank()
                ? "screen.aegis_ascension.collection.stat." + outcome.stat
                : outcome.translationKey;
        return getTranslatableString(key);
    }

    private static Component chargeName(Outcome outcome) {
        return getTranslatableString(
                "message.aegis_ascension.shrine_maiden.charge." + outcome.charge);
    }

    private static Component entityName(Outcome outcome) {
        return entityType(outcome).<Component>map(EntityType::getDescription)
                .orElseGet(() -> getLiteralString(outcome.entity));
    }

    private static Optional<EntityType<?>> entityType(Outcome outcome) {
        return EntityType.byString(outcome.entity);
    }

    private static String signedAmount(Outcome outcome, double amount) {
        String formatted = switch (outcome.format) {
            case "percent" -> formatPercent(amount);
            case "absolute_percent" -> formatPercent(Math.abs(amount));
            default -> compact(amount);
        };
        return amount > 0.0D ? "+" + formatted : formatted;
    }

    private static void spawnHostile(ServerPlayer player, Outcome outcome) {
        ServerLevel level = player.serverLevel();
        Entity spawned = entityType(outcome).map(type -> type.create(level)).orElse(null);
        if (spawned == null) {
            return;
        }
        spawned.moveTo(player.getX() + 1.5D, player.getY(), player.getZ() + 1.5D,
                player.getYRot(), 0.0F);
        if (spawned instanceof LivingEntity living) {
            setBaseValue(GeneralServerMethods.getAttributeInstance(living, Attributes.MAX_HEALTH),
                    Math.max(1.0D, outcome.maxHealth));
            setBaseValue(
                    GeneralServerMethods.getAttributeInstance(living, Attributes.ATTACK_DAMAGE),
                    Math.max(outcome.minimumAttackDamage, GeneralServerMethods
                            .getAttributeValue(player, Attributes.ATTACK_DAMAGE)));
            setBaseValue(GeneralServerMethods.getAttributeInstance(living, Attributes.ATTACK_SPEED),
                    Math.max(0.0D, outcome.attackSpeed));
            living.setHealth(living.getMaxHealth());
        }
        if (spawned instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setTarget(player);
        }
        level.addFreshEntity(spawned);
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

    private static List<Item> randomItemPool(ServerPlayer player) {
        List<Item> itemPool = new ArrayList<>();
        for (Item item : GeneralServerMethods.getAllItems()) {
            if (item != Items.AIR && item.isEnabled(player.level().enabledFeatures())) {
                itemPool.add(item);
            }
        }
        return itemPool;
    }

    private static void grantRandomItems(ServerPlayer player, List<Item> itemPool, int rolls) {
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

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("shrine_maiden_dance_serverside.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = ShrineMaidenDance.class.getResourceAsStream(
                        "/assets/aegis_ascension/shrine_maiden_dance_serverside.json")) {
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
        Objects.requireNonNull(catalog.outcomes, "Missing Shrine Maiden Dance outcomes");
        if (catalog.outcomes.isEmpty()) {
            throw new IllegalStateException("Shrine Maiden Dance outcome pool is empty");
        }
        if (catalog.outcomes.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException(
                    "Too many Shrine Maiden Dance outcomes: " + catalog.outcomes.size()
            );
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Outcome outcome : catalog.outcomes) {
            validate(Objects.requireNonNull(outcome, "Null Shrine Maiden Dance outcome"));
            if (!ids.add(outcome.id)) {
                throw new IllegalStateException(
                        "Duplicate Shrine Maiden Dance outcome: " + outcome.id
                );
            }
        }
    }

    /** Fills defaults in place, then rejects anything the roll could not act on. */
    private static void validate(Outcome outcome) {
        String id = Objects.requireNonNull(outcome.id, "Missing Shrine Maiden Dance outcome id");
        if (id.isBlank() || id.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException("Invalid Shrine Maiden Dance outcome id: " + id);
        }
        outcome.type = Objects.requireNonNull(
                outcome.type, "Missing Shrine Maiden Dance outcome type: " + id);
        if (!TYPES.contains(outcome.type)) {
            throw new IllegalStateException(
                    "Unknown Shrine Maiden Dance outcome type for " + id + ": " + outcome.type);
        }
        outcome.stat = outcome.stat == null ? "" : outcome.stat;
        outcome.format = outcome.format == null ? "number" : outcome.format;
        outcome.translationKey = outcome.translationKey == null ? "" : outcome.translationKey;
        outcome.charge = outcome.charge == null ? "" : outcome.charge;
        outcome.entity = outcome.entity == null ? "" : outcome.entity;
        outcome.negatedConversion = outcome.negatedConversion == null
                ? CONVERSION_NONNUMERIC : outcome.negatedConversion;

        if (!FORMATS.contains(outcome.format)) {
            throw new IllegalStateException(
                    "Invalid Shrine Maiden Dance format for " + id + ": " + outcome.format);
        }
        if (!Double.isFinite(outcome.weight) || outcome.weight < 0.0D
                || !Double.isFinite(outcome.amount)) {
            throw new IllegalStateException(
                    "Non-finite Shrine Maiden Dance value for outcome: " + id);
        }
        if (outcome.negatedByLawOfCycle && !CONVERSIONS.contains(outcome.negatedConversion)) {
            throw new IllegalStateException("Invalid Shrine Maiden Dance negated_conversion for "
                    + id + ": " + outcome.negatedConversion);
        }
        switch (outcome.type) {
            case CUSTOM_STAT -> {
                if (outcome.stat.isBlank() || outcome.stat.length() > MAX_WIRE_ID_LENGTH) {
                    throw new IllegalStateException(
                            "Shrine Maiden Dance custom_stat outcome needs a stat: " + id);
                }
            }
            case CHARGE_GRANT -> {
                if (!CHARGES.contains(outcome.charge)) {
                    throw new IllegalStateException(
                            "Invalid Shrine Maiden Dance charge for " + id + ": " + outcome.charge);
                }
                if (outcome.min < 0 || outcome.max < outcome.min || outcome.max > 1_000_000) {
                    throw new IllegalStateException(
                            "Invalid Shrine Maiden Dance charge range: " + id);
                }
            }
            case SPAWN_HOSTILE -> {
                if (EntityType.byString(outcome.entity).isEmpty()) {
                    throw new IllegalStateException("Unknown Shrine Maiden Dance entity for "
                            + id + ": " + outcome.entity);
                }
                requireFinite(outcome.maxHealth, id, "max_health");
                requireFinite(outcome.minimumAttackDamage, id, "minimum_attack_damage");
                requireFinite(outcome.attackSpeed, id, "attack_speed");
                if (outcome.maxHealth <= 0.0D || outcome.minimumAttackDamage < 0.0D
                        || outcome.attackSpeed < 0.0D) {
                    throw new IllegalStateException(
                            "Out-of-range Shrine Maiden Dance hostile stats: " + id);
                }
            }
            case HEALTH_STRIKE -> {
                requireFinite(outcome.threshold, id, "threshold");
                requireFinite(outcome.multiplierAbove, id, "multiplier_above");
                requireFinite(outcome.fallbackHealth, id, "fallback_health");
                if (outcome.threshold < 0.0D || outcome.threshold > 1.0D
                        || outcome.multiplierAbove < 0.0D || outcome.fallbackHealth <= 0.0D) {
                    throw new IllegalStateException(
                            "Out-of-range Shrine Maiden Dance health_strike values: " + id);
                }
            }
            default -> {
            }
        }
    }

    private static void requireFinite(double value, String id, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(
                    "Non-finite Shrine Maiden Dance " + field + " for outcome: " + id);
        }
    }

    private static final class Catalog {
        private List<Outcome> outcomes = new ArrayList<>();
    }

    private static final class Outcome {
        private String id;
        private String type;
        private boolean enabled = true;
        private double weight;

        private String stat;
        private double amount;
        private String format;
        private boolean attributed;
        @SerializedName("translation_key")
        private String translationKey;

        private String charge;
        private int min;
        private int max;

        private String entity;
        @SerializedName("max_health")
        private double maxHealth;
        @SerializedName("minimum_attack_damage")
        private double minimumAttackDamage;
        @SerializedName("attack_speed")
        private double attackSpeed;

        private double threshold;
        @SerializedName("multiplier_above")
        private double multiplierAbove;
        @SerializedName("fallback_health")
        private double fallbackHealth;
        private boolean lightning;

        @SerializedName("negated_by_law_of_cycle")
        private boolean negatedByLawOfCycle;
        @SerializedName("negated_conversion")
        private String negatedConversion;
    }
}
