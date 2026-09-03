package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.MYSTERIOUS_DOLL_REWARD_SOURCE_PREFIX;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.compact;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.formatPercent;
import static com.whatever.aegis_ascension.util.GeneralCommonMethods.nonNegativeCount;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.OutcomeAnnouncement;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
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

/** Server-authoritative weighted reward loaded from mysterious_doll.json. */
public final class MysteriousDoll {
    public static final String CUSTOM_STAT = "custom_stat";
    public static final String RANDOM_AEGIS = "random_aegis";
    public static final String RANDOM_ITEM = "random_item";
    public static final String RANDOM_TALENT = "random_talent";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CATALOG_ENTRIES = 256;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    private static final Catalog LOCAL_CATALOG = loadCatalog();
    private static final List<Outcome> LOCAL_OUTCOMES = buildOutcomes(LOCAL_CATALOG);
    private static volatile List<Outcome> activeOutcomes = LOCAL_OUTCOMES;
    private static volatile boolean usingSyncedCatalog;

    private MysteriousDoll() {
    }

    /** Rolls and immediately grants exactly one non-banned, available outcome. */
    public static void roll(ServerPlayer player, PlayerPerkData data) {
        List<Item> itemPool = randomItemPool(player);
        List<Outcome> available = activeOutcomes.stream()
                .filter(Outcome::enabled)
                .filter(outcome -> outcome.weight() > 0.0D)
                .filter(outcome -> !PlatformServices.config().isMysteriousDollOutcomeBanned(
                        outcome.id()
                ))
                .filter(outcome -> isAvailable(outcome, data, itemPool))
                .toList();
        double totalWeight = available.stream().mapToDouble(Outcome::weight).sum();
        if (totalWeight <= 0.0D) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.mysterious_doll.no_outcomes"
            ));
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
        grant(selected, player, data, itemPool);
        data.applyChosenPerks(player);
        announce(player, label(selected));
    }

    private static void announce(ServerPlayer player, Component outcome) {
        OutcomeAnnouncement.announce(
                player,
                getTranslatableString("message.aegis_ascension.mysterious_doll.title"),
                outcome,
                "message.aegis_ascension.mysterious_doll.broadcast"
        );
    }

    /**
     * The short form of an outcome for the banner and the broadcast: the same pieces the
     * tooltip shows, minus the weight, which nobody wants read out when they win.
     */
    private static Component label(Outcome outcome) {
        String key = "message.aegis_ascension.mysterious_doll.label." + outcome.type();
        return switch (outcome.type()) {
            case CUSTOM_STAT -> getTranslatableString(
                    key, statName(outcome), formatSignedAmount(outcome));
            case RANDOM_AEGIS, RANDOM_ITEM -> getTranslatableString(
                    key, nonNegativeCount(outcome.amount()));
            case RANDOM_TALENT -> getTranslatableString(
                    key, nonNegativeCount(outcome.amount()), outcome.tier());
            default -> getLiteralString(outcome.id());
        };
    }

    /** Builds the talent tooltip from the same JSON entries used by the server roll. */
    public static Component description() {
        MutableComponent description = getTranslatableString(
                "perk.aegis_ascension.perk_mysterious_doll.description"
        );
        for (Outcome outcome : activeOutcomes.stream()
                .filter(Outcome::enabled)
                .filter(candidate -> usingSyncedCatalog
                        || !PlatformServices.config().isMysteriousDollOutcomeBanned(candidate.id()))
                .toList()) {
            description.append("\n").append(describe(outcome));
        }
        return description.append("\n").append(getTranslatableString(
                "perk.aegis_ascension.perk_mysterious_doll.description.footer"
        ));
    }

    public static List<Outcome> outcomes() {
        return activeOutcomes;
    }

    /** Serializes the server-effective outcome pool, including common-config bans. */
    public static String exportCatalogJson() {
        Catalog effective = GSON.fromJson(GSON.toJson(LOCAL_CATALOG), Catalog.class);
        for (OutcomeJson definition : effective.outcomes) {
            if (definition != null
                    && PlatformServices.config().isMysteriousDollOutcomeBanned(definition.id)) {
                definition.enabled = false;
            }
        }
        return GSON.toJson(effective);
    }

    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized Mysterious Doll catalog was empty"
        );
        activeOutcomes = buildOutcomes(catalog);
        usingSyncedCatalog = true;
    }

    public static void resetSyncedCatalog() {
        activeOutcomes = LOCAL_OUTCOMES;
        usingSyncedCatalog = false;
    }

    private static boolean isAvailable(Outcome outcome, PlayerPerkData data,
                                       List<Item> itemPool) {
        int count = nonNegativeCount(outcome.amount());
        return switch (outcome.type()) {
            case CUSTOM_STAT -> Math.abs(outcome.amount()) > 1.0E-9D;
            case RANDOM_AEGIS -> count > 0 && data.hasAvailableRandomAegis();
            case RANDOM_ITEM -> count > 0 && !itemPool.isEmpty();
            case RANDOM_TALENT -> count > 0 && data.hasAvailableTalentOfTier(
                    Perk.Tier.valueOf(outcome.tier())
            );
            default -> false;
        };
    }

    private static void grant(Outcome outcome, ServerPlayer player, PlayerPerkData data,
                              List<Item> itemPool) {
        switch (outcome.type()) {
            case CUSTOM_STAT -> grantCustomStat(outcome, player, data);
            case RANDOM_AEGIS -> grantAegises(
                    player, data, nonNegativeCount(outcome.amount())
            );
            case RANDOM_ITEM -> grantItem(
                    player, itemPool, nonNegativeCount(outcome.amount())
            );
            case RANDOM_TALENT -> grantTalents(
                    player,
                    data,
                    Perk.Tier.valueOf(outcome.tier()),
                    nonNegativeCount(outcome.amount())
            );
            default -> throw new IllegalStateException(
                    "Unsupported Mysterious Doll outcome type: " + outcome.type()
            );
        }
    }

    private static void grantCustomStat(Outcome outcome, ServerPlayer player,
                                        PlayerPerkData data) {
        data.addCustomStat(outcome.stat(), outcome.amount());
        data.addCustomStat(
                MYSTERIOUS_DOLL_REWARD_SOURCE_PREFIX + outcome.stat(),
                outcome.amount()
        );
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.mysterious_doll.custom_stat",
                statName(outcome),
                formatSignedAmount(outcome)
        ));
    }

    private static void grantAegises(ServerPlayer player, PlayerPerkData data, int count) {
        for (int index = 0; index < count; index++) {
            Aegis granted = data.grantRandomUnownedAegis(player).orElse(null);
            if (granted == null) {
                break;
            }
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.mysterious_doll.random_aegis",
                    granted.title()
            ));
        }
    }

    private static void grantTalents(ServerPlayer player, PlayerPerkData data,
                                     Perk.Tier tier, int count) {
        for (int index = 0; index < count; index++) {
            Perk granted = data.grantRandomTalentOfTier(player, tier).orElse(null);
            if (granted == null) {
                break;
            }
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.mysterious_doll.random_talent",
                    tier.name(),
                    granted.title()
            ));
        }
    }

    private static void grantItem(ServerPlayer player, List<Item> itemPool, int count) {
        Item item = itemPool.get(player.getRandom().nextInt(itemPool.size()));
        ItemStack displayStack = new ItemStack(item);
        int remaining = count;
        while (remaining > 0) {
            int batchSize = Math.min(
                    remaining,
                    Math.max(1, displayStack.getMaxStackSize())
            );
            ItemStack stack = new ItemStack(item, batchSize);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false, false);
            }
            remaining -= batchSize;
        }
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.mysterious_doll.random_item",
                count,
                displayStack.getHoverName()
        ));
    }

    private static List<Item> randomItemPool(ServerPlayer player) {
        List<Item> items = new ArrayList<>();
        for (Item item : GeneralServerMethods.getAllItems()) {
            if (item != Items.AIR && item.isEnabled(player.level().enabledFeatures())) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static Component describe(Outcome outcome) {
        String weight = formatPercent(outcome.weight());
        return switch (outcome.type()) {
            case CUSTOM_STAT -> getTranslatableString(
                    "perk.aegis_ascension.perk_mysterious_doll.outcome.custom_stat",
                    weight,
                    statName(outcome),
                    formatSignedAmount(outcome)
            );
            case RANDOM_AEGIS -> getTranslatableString(
                    "perk.aegis_ascension.perk_mysterious_doll.outcome.random_aegis",
                    weight,
                    nonNegativeCount(outcome.amount())
            );
            case RANDOM_ITEM -> getTranslatableString(
                    "perk.aegis_ascension.perk_mysterious_doll.outcome.random_item",
                    weight,
                    nonNegativeCount(outcome.amount())
            );
            case RANDOM_TALENT -> getTranslatableString(
                    "perk.aegis_ascension.perk_mysterious_doll.outcome.random_talent",
                    weight,
                    nonNegativeCount(outcome.amount()),
                    outcome.tier()
            );
            default -> getLiteralString(outcome.id());
        };
    }

    private static Component statName(Outcome outcome) {
        String key = outcome.translationKey().isBlank()
                ? "screen.aegis_ascension.collection.stat." + outcome.stat()
                : outcome.translationKey();
        return getTranslatableString(key);
    }

    private static String formatSignedAmount(Outcome outcome) {
        String formatted = switch (outcome.format()) {
            case "percent" -> formatPercent(outcome.amount());
            case "absolute_percent" -> formatPercent(Math.abs(outcome.amount()));
            case "number" -> compact(outcome.amount());
            default -> throw new IllegalStateException(
                    "Unsupported Mysterious Doll format: " + outcome.format()
            );
        };
        return outcome.amount() > 0.0D ? "+" + formatted : formatted;
    }

    private static Catalog loadCatalog() {
        Path configPath = PlatformServices.paths()
                .modConfigDirectory(AegisAscensionMod.MOD_ID)
                .resolve("mysterious_doll.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (var stream = MysteriousDoll.class.getResourceAsStream(
                        "/assets/aegis_ascension/mysterious_doll.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/mysterious_doll.json"
                        );
                    }
                    Files.copy(stream, configPath);
                }
            }

            Catalog catalog;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                catalog = GSON.fromJson(reader, Catalog.class);
            }
            return Objects.requireNonNull(catalog, "Mysterious Doll catalog was empty");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static List<Outcome> buildOutcomes(Catalog catalog) {
        Objects.requireNonNull(catalog.outcomes, "Missing Mysterious Doll outcomes");
        if (catalog.outcomes.isEmpty()) {
            throw new IllegalStateException("Mysterious Doll outcome pool is empty");
        }
        if (catalog.outcomes.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException(
                    "Too many Mysterious Doll outcomes: " + catalog.outcomes.size()
            );
        }

        List<Outcome> outcomes = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (OutcomeJson definition : catalog.outcomes) {
            Outcome outcome = validate(Objects.requireNonNull(
                    definition,
                    "Null Mysterious Doll outcome"
            ));
            if (!ids.add(outcome.id())) {
                throw new IllegalStateException(
                        "Duplicate Mysterious Doll outcome id: " + outcome.id()
                );
            }
            outcomes.add(outcome);
        }
        return List.copyOf(outcomes);
    }

    private static Outcome validate(OutcomeJson definition) {
        String id = Objects.requireNonNull(
                definition.id, "Missing Mysterious Doll outcome id"
        );
        String type = Objects.requireNonNull(
                definition.type, "Missing Mysterious Doll outcome type"
        );
        String stat = definition.stat == null ? "" : definition.stat;
        String tier = definition.tier == null ? "" : definition.tier;
        String format = definition.format == null ? "number" : definition.format;
        String translationKey = definition.translationKey == null
                ? "" : definition.translationKey;
        boolean enabled = definition.enabled == null || definition.enabled;
        if (id.isBlank()) {
            throw new IllegalStateException("Blank Mysterious Doll outcome id");
        }
        if (id.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(
                    "Mysterious Doll outcome id exceeds " + MAX_WIRE_ID_LENGTH + " characters"
            );
        }
        if (!List.of(CUSTOM_STAT, RANDOM_AEGIS, RANDOM_ITEM, RANDOM_TALENT)
                .contains(type)) {
            throw new IllegalStateException(
                    "Unknown Mysterious Doll outcome type for " + id + ": " + type
            );
        }
        if (type.equals(CUSTOM_STAT) && stat.isBlank()) {
            throw new IllegalStateException(
                    "Mysterious Doll custom_stat outcome is missing stat: " + id
            );
        }
        if (type.equals(CUSTOM_STAT) && stat.length() > MAX_WIRE_ID_LENGTH) {
            throw new IllegalStateException(
                    "Mysterious Doll custom stat id exceeds " + MAX_WIRE_ID_LENGTH
                            + " characters: " + id
            );
        }
        if (type.equals(RANDOM_TALENT)) {
            try {
                Perk.Tier.valueOf(tier);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Invalid Mysterious Doll talent tier for " + id + ": " + tier,
                        exception
                );
            }
        }
        if (!List.of("number", "percent", "absolute_percent").contains(format)) {
            throw new IllegalStateException(
                    "Invalid Mysterious Doll format for " + id + ": " + format
            );
        }
        if (!Double.isFinite(definition.weight) || definition.weight < 0.0D
                || !Double.isFinite(definition.amount)) {
            throw new IllegalStateException(
                    "Non-finite Mysterious Doll value for outcome: " + id
            );
        }
        return new Outcome(
                id,
                type,
                stat,
                tier,
                format,
                translationKey,
                enabled,
                definition.weight,
                definition.amount
        );
    }

    public record Outcome(
            String id,
            String type,
            String stat,
            String tier,
            String format,
            String translationKey,
            boolean enabled,
            double weight,
            double amount) {
    }

    private static final class Catalog {
        private List<OutcomeJson> outcomes = List.of();
    }

    private static final class OutcomeJson {
        private String id;
        private String type;
        private String stat;
        private String tier;
        private String format;
        @SerializedName("translation_key")
        private String translationKey;
        private Boolean enabled;
        private double weight;
        private double amount;
    }
}
