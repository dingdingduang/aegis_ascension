package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side, data-driven configuration for the daily shop, loaded from
 * {@code config/aegis_ascension/shopsetting.json}.
 *
     * <p>Seeded from the bundled {@code assets/aegis_ascension/shopsetting.json} on first
     * run and read back from disk thereafter — the same copy-then-read flow as
     * {@link com.whatever.aegis_ascension.aegis.Aegis}'s catalog. The shipped JSON is the
     * source of truth for the defaults; the field initialisers below only fill in keys a
     * hand-edited file omits, and are never serialised back out to create the file.</p>
 *
 *
 * <p>This is authoritative on the server only. The client never reads this file; the
 * numbers a client needs to render the shop (each slot's stack and XP price, the manual
 * refresh price, the reset countdown) travel in
 * {@link com.whatever.aegis_ascension.network.SyncShopDataPacket} instead, so a client with a
 * stale or hand-edited copy can't desync prices or fabricate a cheaper purchase.</p>
 */
public final class ShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("shopsetting.json");

    private static ShopConfig instance;

    /**
     * Real-world minutes between automatic restocks. 60 = hourly. Converted to ticks at
     * 20/second, and measured against {@code Level#getGameTime} (which counts steadily)
     * rather than {@code getDayTime} (which a {@code /time set} can jump arbitrarily).
     */
    public int autoRefreshIntervalMinutes = 60;
    /** Paid manual rerolls allowed between automatic restocks; each restock refills them. */
    public int maxManualRefreshes = 3;
    /** Raw experience points (not levels) charged per manual reroll. */
    public int manualRefreshExperienceCost = 200;
    /** Slots always filled, drawn from {@link #guaranteedItems}. */
    /**
     * How far a listed price may drift from its configured amount, as a fraction. At
     * 0.15 an item costing 100 is stocked somewhere between 85 and 115. The roll happens
     * once when the stock is generated, so a price is fixed for as long as that offer
     * stands and waiting for a refresh is a real choice. Zero disables the variation.
     */
    public double priceVariance = 0.15D;
    public int minimumSlots = 3;
    /** Hard ceiling on total slots, including the guaranteed ones. */
    public int maximumSlots = 16;
    /** Probability (0..1) that any one slot past {@link #minimumSlots} unlocks. */
    public double additionalSlotChance = 0.30D;
    /**
     * false (default): every slot past the minimum rolls independently, so a failed roll
     * doesn't stop later slots — this is the "independent" reading of the 30% rule and
     * averages about {@code min + (max - min) * chance} slots. true: roll sequentially and
     * stop at the first failure, so unlocking slot N requires every slot before it to have
     * succeeded, which makes large shops exponentially rare.
     */
    public boolean sequentialSlotUnlock = false;

    /**
     * true: {@link #filteredItems} is a blacklist — every listed item/tag is excluded.
     * false: it is a whitelist — only listed items/tags may appear. Applies to the
     * guaranteed pool and the random pool alike.
     */
    public boolean blacklistMode = true;
    /**
     * Item ids ({@code "minecraft:emerald"}), item tags ({@code "#minecraft:planks"}),
     * or namespaces ({@code "@example_mod"}), interpreted per {@link #blacklistMode}.
     */
    public List<String> filteredItems = new ArrayList<>();

    /** Always-stocked entries; each is offered at its exact {@code count}, never randomized. */
    public List<FixedEntry> guaranteedItems = new ArrayList<>();
    /** Weighted pool for the unlockable slots; stackable entries get a randomized count. */
    public List<RandomEntry> randomItems = new ArrayList<>();
    /**
     * Chance of each rarity tier per unlockable slot, in the same R/SR/SSR vocabulary as
     * talents.json. Rolled first; the entry is then picked by {@code weight} from within
     * the chosen tier, so an entry's weight competes only against its own tier rather than
     * against the whole pool.
     */
    public RarityWeights rarityWeights = new RarityWeights();
    /** Registry-backed shop with its own stock, refresh schedule, filters, and price rules. */
    public DiscoveryShop discoveryShop = new DiscoveryShop();

    /** Relative tier chances. Defaults to 88 / 10 / 2. */
    public static final class RarityWeights {
        @SerializedName("R")
        public int r = 88;
        @SerializedName("SR")
        public int sr = 10;
        @SerializedName("SSR")
        public int ssr = 2;

        public int weightOf(String tier) {
            return switch (GeneralConstants.normalizeTier(tier)) {
                case GeneralConstants.TIER_SSR -> ssr;
                case GeneralConstants.TIER_SR -> sr;
                default -> r;
            };
        }
    }

    private ShopConfig() {
    }

    /** A guaranteed slot: fixed item, fixed count, fixed price. */
    public static final class FixedEntry {
        /** Display rarity only — guaranteed slots bypass the tier roll by definition. */
        public String tier = "R";
        public String item = "minecraft:stone";
        /** Non-empty to stock a {@link com.whatever.aegis_ascension.virtualitem.VirtualItems} book instead of {@code item}. */
        public String virtualId = "";
        public int count = 1;
        public int experienceCost = 10;
    }

    /** A random slot candidate: weighted pick, count rolled in {@code [minCount, maxCount]}. */
    public static final class RandomEntry {
        /**
         * Rarity band this entry competes in: "R", "SR", or "SSR". Ignored for a virtual
         * entry, whose tier is declared on the book itself in virtual_item_setting.json so
         * a book's rarity travels with the book rather than with each shop listing of it.
         */
        public String tier = "R";
        public String item = "minecraft:stone";
        /** Non-empty to stock a {@link com.whatever.aegis_ascension.virtualitem.VirtualItems} book instead of {@code item}. */
        public String virtualId = "";
        public int weight = 1;
        public int minCount = 1;
        public int maxCount = 1;
        public int experienceCost = 10;
    }

    /** Settings for the registry-backed Discovery Shop. */
    public static final class DiscoveryShop {
        public boolean enabled = true;
        /** As the common shop's, applied to this shop's own rarity prices. */
        public double priceVariance = 0.15D;
        public int autoRefreshIntervalMinutes = 60;
        public int maxManualRefreshes = 3;
        public int manualRefreshExperienceCost = 500;
        public int minimumSlots = 4;
        public int maximumSlots = 10;
        public double additionalSlotChance = 0.40D;
        public boolean sequentialSlotUnlock = false;
        public int defaultMinCount = 1;
        public int defaultMaxCount = 1;
        /** Fallback price when an item has no rarity (or an invalid rarity). */
        public int defaultExperienceCost = 500;
        public String defaultTier = "R";
        /** Default prices for items resolved into the corresponding rarity tier. */
        public RarityExperienceCosts rarityExperienceCosts = new RarityExperienceCosts();
        /** Tier odds are rolled before an item is selected from that equipment tier. */
        public RarityWeights rarityWeights = new RarityWeights();
        public boolean autoClassifyEquipmentTier = true;
        /** Default thresholds keep all ordinary vanilla weapons in R. */
        public double srAttackDamageThreshold = 12.0D;
        public double ssrAttackDamageThreshold = 30.0D;
        /** Default thresholds keep all ordinary vanilla armor pieces in R. */
        public double srArmorThreshold = 10.0D;
        public double ssrArmorThreshold = 20.0D;
        /** Extra continuous falloff inside a tier; 0 disables it. */
        public double highPowerFalloffExponent = 2.0D;
        /** Floor for automatic power falloff before a rule multiplier is applied. */
        public double minimumHighPowerWeight = 0.001D;
        public boolean blacklistMode = true;
        /** Exact item ids, item tags prefixed with '#', or whole namespaces prefixed with '@'. */
        public List<String> filteredItems = new ArrayList<>(List.of(
                "minecraft:air",
                "minecraft:bedrock",
                "minecraft:barrier",
                "minecraft:command_block",
                "minecraft:chain_command_block",
                "minecraft:repeating_command_block",
                "minecraft:command_block_minecart",
                "minecraft:structure_block",
                "minecraft:structure_void",
                "minecraft:jigsaw",
                "minecraft:light",
                "minecraft:debug_stick",
                "minecraft:knowledge_book",
                "minecraft:spawner",
                "minecraft:end_portal_frame",
                "#forge:technical_items"
        ));
        /** First matching rule overrides the defaults for an item. */
        public List<DiscoveryRule> rules = new ArrayList<>();

        public long autoRefreshIntervalTicks() {
            return Math.max(1L, autoRefreshIntervalMinutes) * 60L * 20L;
        }

        public boolean isItemAllowed(Item item) {
            boolean listed = matchesAny(item, filteredItems);
            return blacklistMode != listed;
        }

        /** Resolves the first matching override, then fills every omitted value from defaults. */
        public DiscoveryOfferSettings settingsFor(
                Item item,
                double attackDamage,
                double armor
        ) {
            DiscoveryRule matched = null;
            for (DiscoveryRule rule : rules) {
                if (rule != null && matches(item, rule.match)) {
                    matched = rule;
                    break;
                }
            }
            int minCount = matched == null || matched.minCount < 0
                    ? defaultMinCount : matched.minCount;
            int maxCount = matched == null || matched.maxCount < 0
                    ? defaultMaxCount : matched.maxCount;
            String tier = matched == null || matched.tier == null || matched.tier.isBlank()
                    ? automaticTier(attackDamage, armor)
                    : matched.tier;
            boolean explicitTier = matched != null && matched.tier != null
                    && !matched.tier.isBlank();
            boolean automaticallyClassified = autoClassifyEquipmentTier
                    && (attackDamage > 0.0D || armor > 0.0D);
            int cost = matched != null && matched.experienceCost >= 0
                    ? matched.experienceCost
                    : (explicitTier || automaticallyClassified
                            ? rarityExperienceCost(tier) : defaultExperienceCost);
            double ruleWeight = matched == null || matched.selectionWeightMultiplier < 0.0D
                    ? 1.0D : matched.selectionWeightMultiplier;
            return new DiscoveryOfferSettings(
                    Math.max(1, minCount),
                    Math.max(Math.max(1, minCount), maxCount),
                    Math.max(0, cost),
                    GeneralConstants.normalizeTier(tier),
                    Math.max(0.0D, ruleWeight) * automaticPowerWeight(attackDamage, armor)
            );
        }

        private int rarityExperienceCost(String tier) {
            if (rarityExperienceCosts == null) {
                return defaultExperienceCost;
            }
            return rarityExperienceCosts.costOf(tier, defaultExperienceCost);
        }

        private String automaticTier(double attackDamage, double armor) {
            if (!autoClassifyEquipmentTier
                    || (attackDamage <= 0.0D && armor <= 0.0D)) {
                return defaultTier;
            }
            if (meetsThreshold(attackDamage, ssrAttackDamageThreshold)
                    || meetsThreshold(armor, ssrArmorThreshold)) {
                return GeneralConstants.TIER_SSR;
            }
            if (meetsThreshold(attackDamage, srAttackDamageThreshold)
                    || meetsThreshold(armor, srArmorThreshold)) {
                return GeneralConstants.TIER_SR;
            }
            return GeneralConstants.TIER_R;
        }

        private double automaticPowerWeight(double attackDamage, double armor) {
            if (highPowerFalloffExponent <= 0.0D) {
                return 1.0D;
            }
            double ratio = Math.max(
                    powerRatio(attackDamage, srAttackDamageThreshold),
                    powerRatio(armor, srArmorThreshold)
            );
            if (ratio <= 1.0D) {
                return 1.0D;
            }
            return Math.max(
                    minimumHighPowerWeight,
                    Math.pow(ratio, -highPowerFalloffExponent)
            );
        }

        private static boolean meetsThreshold(double value, double threshold) {
            return threshold > 0.0D && value >= threshold;
        }

        private static double powerRatio(double value, double threshold) {
            return threshold <= 0.0D || value <= threshold ? 1.0D : value / threshold;
        }

        private void sanitize() {
            autoRefreshIntervalMinutes = Math.max(1, autoRefreshIntervalMinutes);
            maxManualRefreshes = Math.max(0, maxManualRefreshes);
            manualRefreshExperienceCost = Math.max(0, manualRefreshExperienceCost);
            maximumSlots = Math.max(1, Math.min(54, maximumSlots));
            minimumSlots = Math.max(0, Math.min(maximumSlots, minimumSlots));
            additionalSlotChance = Math.max(0.0D, Math.min(1.0D, additionalSlotChance));
            defaultMinCount = Math.max(1, defaultMinCount);
            defaultMaxCount = Math.max(defaultMinCount, defaultMaxCount);
            defaultExperienceCost = Math.max(0, defaultExperienceCost);
            defaultTier = GeneralConstants.normalizeTier(defaultTier);
            if (rarityExperienceCosts == null) {
                rarityExperienceCosts = new RarityExperienceCosts();
            }
            rarityExperienceCosts.sanitize(defaultExperienceCost);
            if (rarityWeights == null) {
                rarityWeights = new RarityWeights();
            }
            rarityWeights.r = Math.max(0, rarityWeights.r);
            rarityWeights.sr = Math.max(0, rarityWeights.sr);
            rarityWeights.ssr = Math.max(0, rarityWeights.ssr);
            srAttackDamageThreshold = finiteNonNegative(srAttackDamageThreshold, 12.0D);
            ssrAttackDamageThreshold = Math.max(
                    srAttackDamageThreshold,
                    finiteNonNegative(ssrAttackDamageThreshold, 30.0D)
            );
            srArmorThreshold = finiteNonNegative(srArmorThreshold, 10.0D);
            ssrArmorThreshold = Math.max(
                    srArmorThreshold,
                    finiteNonNegative(ssrArmorThreshold, 20.0D)
            );
            highPowerFalloffExponent = finiteNonNegative(highPowerFalloffExponent, 2.0D);
            minimumHighPowerWeight = Double.isFinite(minimumHighPowerWeight)
                    ? Math.max(0.0D, Math.min(1.0D, minimumHighPowerWeight))
                    : 0.001D;
            if (filteredItems == null) {
                filteredItems = new ArrayList<>();
            }
            if (rules == null) {
                rules = new ArrayList<>();
            }
            for (DiscoveryRule rule : rules) {
                if (rule == null) {
                    continue;
                }
                rule.match = rule.match == null ? "" : rule.match.trim();
                rule.minCount = Math.max(-1, rule.minCount);
                rule.maxCount = Math.max(-1, rule.maxCount);
                if (rule.minCount >= 0 && rule.maxCount >= 0) {
                    rule.maxCount = Math.max(rule.minCount, rule.maxCount);
                }
                rule.experienceCost = Math.max(-1, rule.experienceCost);
                rule.tier = rule.tier == null ? "" : rule.tier.trim();
                rule.selectionWeightMultiplier =
                        Double.isFinite(rule.selectionWeightMultiplier)
                                ? Math.max(-1.0D, Math.min(1_000_000.0D,
                                rule.selectionWeightMultiplier))
                                : -1.0D;
            }
        }

        private static double finiteNonNegative(double value, double fallback) {
            return Double.isFinite(value) ? Math.max(0.0D, value) : fallback;
        }
    }

    /** An ordered exact-id, tag, or namespace override for Discovery Shop offers. */
    public static final class DiscoveryRule {
        public String match = "";
        /** -1 inherits {@link DiscoveryShop#defaultMinCount}. */
        public int minCount = -1;
        /** -1 inherits {@link DiscoveryShop#defaultMaxCount}. */
        public int maxCount = -1;
        /** -1 inherits the matching rarity default, or {@link DiscoveryShop#defaultExperienceCost}
         * when no rarity is available. */
        public int experienceCost = -1;
        /** Blank inherits {@link DiscoveryShop#defaultTier}. */
        public String tier = "";
        /** -1 inherits 1.0; 0 removes matching items from rolls; higher values make them likelier. */
        public double selectionWeightMultiplier = -1.0D;
    }

    /** Default Discovery Shop prices by resolved rarity. */
    public static final class RarityExperienceCosts {
        @SerializedName("R")
        public int r = 500;
        @SerializedName("SR")
        public int sr = 1500;
        @SerializedName("SSR")
        public int ssr = 5000;

        public int costOf(String tier, int fallback) {
            if (tier == null) {
                return Math.max(0, fallback);
            }
            return switch (tier.trim().toUpperCase(java.util.Locale.ROOT)) {
                case GeneralConstants.TIER_R -> r;
                case GeneralConstants.TIER_SR -> sr;
                case GeneralConstants.TIER_SSR -> ssr;
                default -> Math.max(0, fallback);
            };
        }

        private void sanitize(int fallback) {
            int safeFallback = Math.max(0, fallback);
            // Keep hand-edited negative values from becoming a free offer while allowing
            // an explicitly configured zero price when desired.
            r = r < 0 ? safeFallback : r;
            sr = sr < 0 ? safeFallback : sr;
            ssr = ssr < 0 ? safeFallback : ssr;
        }
    }

    /** Fully resolved count, price, and rarity for one Discovery Shop candidate. */
    public record DiscoveryOfferSettings(
            int minCount,
            int maxCount,
            int experienceCost,
            String tier,
            double selectionWeight
    ) {
    }

    public static ShopConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Drops the cached instance so the next {@link #get()} re-reads the file from disk. */
    public static void reload() {
        instance = null;
    }

    private static ShopConfig load() {
        ShopConfig config = new ShopConfig();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = ShopConfig.class.getResourceAsStream(
                        "/assets/aegis_ascension/shopsetting.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/shopsetting.json");
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                ShopConfig loaded = GSON.fromJson(reader, ShopConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception exception) {
            // A broken or hand-mangled shop config must not take the whole mod down at
            // load time (unlike the Aegis catalog, which is load-bearing for gameplay) —
            // fall back to the in-code defaults and say so loudly.
            AegisAscensionMod.getLogger().error("Failed to read {}, using built-in shop defaults", FILE, exception);
            config = new ShopConfig();
        }
        config.sanitize();
        return config;
    }

    /** {@link #autoRefreshIntervalMinutes} in game ticks. */
    public long autoRefreshIntervalTicks() {
        return Math.max(1L, autoRefreshIntervalMinutes) * 60L * 20L;
    }

    /** Clamps hand-edited values into workable ranges so a bad file can't break generation. */
    /**
     * The tier the shop assigns a real item, or R when it stocks no such item. Used when
     * banking something the shop never sold (the put-into-storage keybind), so a hand-stored
     * diamond still shows the same rarity as a bought one.
     */
    public String tierOf(Item item) {
        for (RandomEntry entry : randomItems) {
            if (entry.virtualId.isEmpty() && resolveItem(entry.item) == item) {
                return GeneralConstants.normalizeTier(entry.tier);
            }
        }
        for (FixedEntry entry : guaranteedItems) {
            if (entry.virtualId.isEmpty() && resolveItem(entry.item) == item) {
                return GeneralConstants.normalizeTier(entry.tier);
            }
        }
        return GeneralConstants.TIER_R;
    }

    private void sanitize() {
        autoRefreshIntervalMinutes = Math.max(1, autoRefreshIntervalMinutes);
        maxManualRefreshes = Math.max(0, maxManualRefreshes);
        manualRefreshExperienceCost = Math.max(0, manualRefreshExperienceCost);
        maximumSlots = Math.max(1, Math.min(54, maximumSlots));
        minimumSlots = Math.max(0, Math.min(maximumSlots, minimumSlots));
        additionalSlotChance = Math.max(0.0D, Math.min(1.0D, additionalSlotChance));
        if (filteredItems == null) {
            filteredItems = new ArrayList<>();
        }
        if (guaranteedItems == null) {
            guaranteedItems = new ArrayList<>();
        }
        if (randomItems == null) {
            randomItems = new ArrayList<>();
        }
        if (rarityWeights == null) {
            rarityWeights = new RarityWeights();
        }
        if (discoveryShop == null) {
            discoveryShop = new DiscoveryShop();
        }
        rarityWeights.r = Math.max(0, rarityWeights.r);
        rarityWeights.sr = Math.max(0, rarityWeights.sr);
        rarityWeights.ssr = Math.max(0, rarityWeights.ssr);
        for (FixedEntry entry : guaranteedItems) {
            if (entry.virtualId == null) {
                entry.virtualId = "";
            }
            entry.count = Math.max(1, entry.count);
            entry.experienceCost = Math.max(0, entry.experienceCost);
        }
        for (RandomEntry entry : randomItems) {
            if (entry.virtualId == null) {
                entry.virtualId = "";
            }
            entry.weight = Math.max(0, entry.weight);
            entry.minCount = Math.max(1, entry.minCount);
            entry.maxCount = Math.max(entry.minCount, entry.maxCount);
            entry.experienceCost = Math.max(0, entry.experienceCost);
        }
        discoveryShop.sanitize();
    }

    /**
     * Whether an item may be stocked at all, per {@link #blacklistMode} and
     * {@link #filteredItems}. An empty filter list means "no restriction" in blacklist mode
     * and "nothing allowed" in whitelist mode — the literal reading of each, so an
     * accidentally-empty whitelist fails visibly (an empty shop) rather than silently
     * behaving like no filter at all.
     */
    public boolean isItemAllowed(Item item) {
        boolean listed = matchesAny(item, filteredItems);
        return blacklistMode != listed;
    }

    private static boolean matchesAny(Item item, List<String> filters) {
        if (filters == null) {
            return false;
        }
        for (String entry : filters) {
            if (matches(item, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Item item, String expression) {
        if (item == null || expression == null || expression.isBlank()) {
            return false;
        }
        ItemStack probe = new ItemStack(item);
        String trimmed = expression.trim();
        ResourceLocation actualId = GeneralServerMethods.getItemKey(item);
        if (trimmed.startsWith("#")) {
            ResourceLocation tagId = PlatformServices.resources().tryParse(trimmed.substring(1));
            return tagId != null && probe.is(TagKey.create(Registries.ITEM, tagId));
        }
        if (trimmed.startsWith("@")) {
            return actualId != null && actualId.getNamespace().equals(trimmed.substring(1));
        }
        ResourceLocation itemId = PlatformServices.resources().tryParse(trimmed);
        return itemId != null && itemId.equals(actualId);
    }

    public boolean isEnabled(ShopType shopType) {
        return shopType == ShopType.COMMON || discoveryShop.enabled;
    }

    public long autoRefreshIntervalTicks(ShopType shopType) {
        return shopType == ShopType.DISCOVERY
                ? discoveryShop.autoRefreshIntervalTicks()
                : autoRefreshIntervalTicks();
    }

    public int maxManualRefreshes(ShopType shopType) {
        return shopType == ShopType.DISCOVERY
                ? discoveryShop.maxManualRefreshes
                : maxManualRefreshes;
    }

    public int manualRefreshExperienceCost(ShopType shopType) {
        return shopType == ShopType.DISCOVERY
                ? discoveryShop.manualRefreshExperienceCost
                : manualRefreshExperienceCost;
    }

    /**
     * Experience value of a single unit of an item, for selling it back out of storage.
     * Returns 0 for anything the shop doesn't stock (which the UI surfaces as "unsellable").
     *
     * <p>Takes the <em>lowest</em> per-unit price across every matching entry, and for a
     * random entry divides by {@code maxCount} rather than {@code minCount}. Both choices
     * close the same exploit: a random entry charges one flat price for a rolled range, so
     * pricing a sale off the small end of that range would let a player buy the big roll
     * and sell it back for more than they paid. Costing every unit at the cheapest rate the
     * shop ever offers it guarantees a sale can never out-earn the purchase.</p>
     */
    public int sellUnitExperience(Item item) {
        double best = Double.MAX_VALUE;
        for (FixedEntry entry : guaranteedItems) {
            if (isVirtual(entry.virtualId)) {
                continue;
            }
            if (resolveItem(entry.item) == item) {
                best = Math.min(best, entry.experienceCost / (double) Math.max(1, entry.count));
            }
        }
        for (RandomEntry entry : randomItems) {
            if (isVirtual(entry.virtualId)) {
                continue;
            }
            if (resolveItem(entry.item) == item) {
                best = Math.min(best, entry.experienceCost / (double) Math.max(1, entry.maxCount));
            }
        }
        return best == Double.MAX_VALUE ? 0 : (int) Math.floor(best);
    }

    /** Whether a configured entry stocks a virtual book rather than a real item. */
    public static boolean isVirtual(String virtualId) {
        return virtualId != null && !virtualId.isBlank();
    }

    /** Resolves an entry's item id, or null if it names an item this instance doesn't have. */
    public static Item resolveItem(String id) {
        ResourceLocation location = PlatformServices.resources().tryParse(
                id == null ? "" : id.trim()
        );
        return location == null ? null : GeneralServerMethods.resolveItem(location);
    }
}
