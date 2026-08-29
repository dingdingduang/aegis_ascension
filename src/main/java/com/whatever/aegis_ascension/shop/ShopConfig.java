package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.platform.PlatformServices;
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
     * Item ids ({@code "minecraft:emerald"}) or item tags ({@code "#minecraft:planks"}),
     * interpreted per {@link #blacklistMode}.
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
    }

    /**
     * Whether an item may be stocked at all, per {@link #blacklistMode} and
     * {@link #filteredItems}. An empty filter list means "no restriction" in blacklist mode
     * and "nothing allowed" in whitelist mode — the literal reading of each, so an
     * accidentally-empty whitelist fails visibly (an empty shop) rather than silently
     * behaving like no filter at all.
     */
    public boolean isItemAllowed(Item item) {
        boolean listed = matchesFilter(item);
        return blacklistMode != listed;
    }

    private boolean matchesFilter(Item item) {
        ItemStack probe = new ItemStack(item);
        for (String entry : filteredItems) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.startsWith("#")) {
                ResourceLocation tagId = PlatformServices.resources().tryParse(
                        trimmed.substring(1)
                );
                if (tagId != null && probe.is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
            } else {
                ResourceLocation itemId = PlatformServices.resources().tryParse(trimmed);
                if (itemId != null && itemId.equals(GeneralServerMethods.getItemKey(item))) {
                    return true;
                }
            }
        }
        return false;
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
