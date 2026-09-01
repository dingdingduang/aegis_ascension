package com.whatever.aegis_ascension.virtualitem;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralCommonMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.whatever.aegis_ascension.util.GeneralCommonMethods.formatPercent;

/**
 * Consumable "books" that live only inside the virtual storage: they are never real
 * {@link ItemStack}s in the world, cannot be extracted, and are spent from the Inventory
 * screen to buy a permanent stat increase.
 *
 * <p>Each definition's {@code maxUses} is a <em>lifetime cap per player</em>, not a stack
 * limit — banking twenty HP books does not let a player exceed twenty uses, and the cap
 * survives spending, rebuying, and relogging because the use counter is stored on the
 * player rather than on the item row. Progression-owned rewards are deliberate exceptions:
 * Trinity Tea Party Swiss Rolls and Devour Aegis Cores reset together with progression.</p>
 *
 * <p>A player's bonus is always recomputed as {@code uses * amount} rather than being
 * banked as a number when the book is consumed. That keeps the config authoritative: retune
 * {@code amount} and every existing player's bonus follows on the next recalculation, with
 * no migration step and no way for a saved bonus to drift out of sync with the config that
 * produced it.</p>
 */
public final class VirtualItems {
    public static final String SWISS_ROLL = "swiss_roll";
    public static final String DEVOUR_AEGIS_CORE_I = "devour_aegis_core_i";
    public static final String DEVOUR_AEGIS_CORE_II = "devour_aegis_core_ii";
    public static final String DEVOUR_AEGIS_CORE_III = "devour_aegis_core_iii";
    private static final List<String> DEVOUR_AEGIS_CORES = List.of(
            DEVOUR_AEGIS_CORE_I,
            DEVOUR_AEGIS_CORE_II,
            DEVOUR_AEGIS_CORE_III
    );

    /** Config keys understood by multi-stat virtual items such as the Swiss Roll. */
    public static final String ATTACK_DAMAGE = "attack_damage";
    public static final String ATTACK_SPEED_MULTIPLIER = "attack_speed_multiplier";
    public static final String MAX_HEALTH_MULTIPLIER = "max_health_multiplier";
    public static final String PRIMARY_ATTRIBUTE_FLAT = "primary_attribute_flat";
    public static final String ALL_SKILL_ENHANCEMENT_ATTRIBUTE =
            "all_skill_enhancement_attribute";
    public static final String DAMAGE_BONUS = "damage_bonus";
    public static final String FINAL_DAMAGE = "final_damage";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CATALOG_ENTRIES = 512;
    private static final int MAX_WIRE_ID_LENGTH = 128;
    private static final Path FILE = PlatformServices.paths()
            .modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("virtual_item_setting.json");

    private static volatile Map<String, Definition> localDefinitions;
    private static volatile Map<String, Definition> syncedDefinitions;

    private VirtualItems() {
    }

    /** What a book permanently raises when consumed. */
    public enum Effect {
        /** One point of the player's chosen Skill Enhancement primary, before its coefficient. */
        PRIMARY_STAT,
        /** Flat maximum health, in half-hearts (Minecraft's own unit). */
        MAX_HEALTH,
        /** Flat attack damage. */
        ATTACK_DAMAGE,
        /** Several permanent bonuses declared in the definition's {@code bonuses} map. */
        COMPOSITE_STATS,
        /** Extra distinct item-type slots in the virtual storage, on top of the config cap. */
        STORAGE_SLOTS,
        /** One of three unique, sequential upgrades to the Devour Aegis item limit. */
        DEVOUR_AEGIS_CORE,
        /**
         * One-shot action, not a passive bonus: clears every Devoured item and its
         * inherited attributes. Contributes nothing to {@link #bonus}.
         */
        RESET_DEVOURED,
        /**
         * One-shot action: the full {@code /perk reset} — clears perks, Aegises and
         * breakthroughs, then re-grants what the player's level entitles them to.
         */
        RESET_PROGRESSION;

        /**
         * Action effects fire once on consumption instead of accumulating a bonus, so they
         * consume exactly one item per use and ignore {@code amount}.
         */
        public boolean isAction() {
            return this == RESET_DEVOURED || this == RESET_PROGRESSION;
        }
    }

    /** One configured book. {@code amount} is per use; {@code maxUses} is the lifetime cap. */
    public static final class Definition {
        public String id = "";
        /**
         * Texture drawn as this book's icon, as a resource location
         * ({@code aegis_ascension:textures/gui/virtual_item/virtual_item_book_0.png}).
         * A book is not an item, so it has no item model to borrow — the UI blits this
         * directly, auto-detecting the texture's native size via
         * {@code GeneralClientMethods.detectTextureSize} so mixed resolutions all render whole.
         */
        public String icon = "";
        /**
         * Rarity band used by the daily shop's tier roll ("R", "SR", "SSR"). Declared here
         * rather than per shop listing so a book's rarity is a property of the book.
         */
        public String tier = "SR";
        /**
         * Whether a shopsetting.json entry is allowed to stock this virtual item. The
         * shop entry still controls its weight, count, and price; this is the item's
         * convenient master switch.
         */
        public boolean appearsInShop = true;
        /** Once bought, this item cannot be stocked again until progression is reset. */
        public boolean uniquePurchase = false;
        /**
         * Require an explicit confirmation before this book is consumed. Meant for the
         * irreversible ones (a full progression wipe); an ordinary stat book doesn't need
         * a dialog in the way.
         */
        public boolean requiresConfirmation = false;
        /**
         * Lang key for the display name, following talents.json's convention of naming the
         * key explicitly rather than deriving it. Blank falls back to
         * {@code virtual_item.aegis_ascension.<id>.name}.
         */
        public String name = "";
        /**
         * Lang key for the description. Receives {@link #descriptionArgs()} as format
         * arguments, so the text can state the exact bonus and use cap from this config
         * instead of hardcoding numbers that would silently drift when retuned.
         */
        public String description = "";
        public Effect effect = Effect.PRIMARY_STAT;
        public double amount = 1.0D;
        public int maxUses = 1;
        /** Per-use bonuses for {@link Effect#COMPOSITE_STATS}. */
        public Map<String, Double> bonuses = new LinkedHashMap<>();

        /** The icon texture, or null when unset/malformed so callers can fall back. */
        public ResourceLocation iconTexture() {
            return PlatformServices.resources().tryParse(icon == null ? "" : icon.trim());
        }

        /**
         * A non-empty placeholder stack, used only so storage bookkeeping (row identity,
         * the drop-empty-rows check on load) has something valid to hold. It is never
         * rendered and never handed to a player — every render site checks
         * {@code isVirtual()} first and blits {@link #iconTexture()} instead.
         */
        public ItemStack iconStack() {
            return new ItemStack(Items.PAPER);
        }

        public String nameKey() {
            return name == null || name.isBlank()
                    ? "virtual_item.aegis_ascension." + id + ".name"
                    : name;
        }

        public String descriptionKey() {
            return description == null || description.isBlank()
                    ? "virtual_item.aegis_ascension." + id + ".description"
                    : description;
        }

        /**
         * Format arguments for {@link #descriptionKey()}: the per-use bonus (trimmed of a
         * trailing ".0") and the lifetime use cap, in that order.
         */
        /** Normalised {@link #tier}, defaulting to R when blank or unrecognised. */
        public String parsedTier() {
            return com.whatever.aegis_ascension.util.GeneralConstants.normalizeTier(tier);
        }

        public Object[] descriptionArgs() {
            if (effect == Effect.COMPOSITE_STATS) {
                return new Object[]{
                        GeneralCommonMethods.compact(stat(ATTACK_DAMAGE)),
                        formatPercent(stat(ATTACK_SPEED_MULTIPLIER)),
                        formatPercent(stat(MAX_HEALTH_MULTIPLIER)),
                        GeneralCommonMethods.compact(stat(PRIMARY_ATTRIBUTE_FLAT)),
                        formatPercent(stat(ALL_SKILL_ENHANCEMENT_ATTRIBUTE)),
                        formatPercent(stat(DAMAGE_BONUS)),
                        formatPercent(stat(FINAL_DAMAGE))
                };
            }
            if (effect == Effect.DEVOUR_AEGIS_CORE) {
                int targetLevel = devourCoreTargetLevel(id);
                return new Object[]{targetLevel, DevourAegis.itemLimitForLevel(targetLevel)};
            }
            return new Object[]{GeneralCommonMethods.compact(amount), maxUses};
        }

        /** One configured composite bonus per use, or zero when absent. */
        public double stat(String key) {
            if (key == null || bonuses == null) {
                return 0.0D;
            }
            Double value = bonuses.get(key);
            return value == null || !Double.isFinite(value) ? 0.0D : value;
        }

        /** The description, already resolved with this definition's own numbers. */
        public net.minecraft.network.chat.Component descriptionComponent() {
            return GeneralTextMethods.getTranslatableString(descriptionKey(), descriptionArgs());
        }
    }

    private static final class Catalog {
        private List<Definition> items = new ArrayList<>();
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> synced = syncedDefinitions;
        if (synced != null) {
            return synced;
        }
        return localDefinitions();
    }

    private static Map<String, Definition> localDefinitions() {
        Map<String, Definition> local = localDefinitions;
        if (local == null) {
            synchronized (VirtualItems.class) {
                local = localDefinitions;
                if (local == null) {
                    local = load();
                    localDefinitions = local;
                }
            }
        }
        return local;
    }

    /** Drops the cached catalog so the next lookup re-reads the file. */
    public static void reload() {
        localDefinitions = null;
    }

    public static List<Definition> all() {
        return List.copyOf(definitions().values());
    }

    public static Definition byId(String id) {
        return id == null ? null : definitions().get(id);
    }

    public static boolean exists(String id) {
        return byId(id) != null;
    }

    /** Serializes the server's effective virtual-item definitions for the login snapshot. */
    public static String exportCatalogJson() {
        Catalog catalog = new Catalog();
        catalog.items = new ArrayList<>(localDefinitions().values());
        return GSON.toJson(catalog);
    }

    /** Installs a server-authoritative catalog in client memory without touching local files. */
    public static void installSyncedCatalog(String json) {
        Catalog catalog = Objects.requireNonNull(
                GSON.fromJson(Objects.requireNonNull(json, "json"), Catalog.class),
                "Synchronized virtual item catalog was empty"
        );
        Objects.requireNonNull(catalog.items, "Missing items");
        syncedDefinitions = normalize(catalog);
    }

    /** Restores this installation's own catalog after leaving a remote server. */
    public static void resetSyncedCatalog() {
        syncedDefinitions = null;
    }

    private static Map<String, Definition> load() {
        Catalog catalog = new Catalog();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = VirtualItems.class.getResourceAsStream(
                        "/assets/aegis_ascension/virtual_item_setting.json")) {
                    if (stream == null) {
                        throw new IllegalStateException(
                                "Missing default assets/aegis_ascension/virtual_item_setting.json");
                    }
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                Catalog loaded = GSON.fromJson(reader, Catalog.class);
                if (loaded != null && loaded.items != null) {
                    catalog = loaded;
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error(
                    "Failed to read {}, using built-in virtual item defaults", FILE, exception);
            catalog = new Catalog();
            catalog.items = defaults();
        }

        return normalize(catalog);
    }

    private static Map<String, Definition> normalize(Catalog catalog) {
        Objects.requireNonNull(catalog.items, "Missing virtual items");
        if (catalog.items.size() > MAX_CATALOG_ENTRIES) {
            throw new IllegalStateException("Too many virtual items: " + catalog.items.size());
        }
        Map<String, Definition> byId = new LinkedHashMap<>();
        for (Definition definition : catalog.items) {
            if (definition == null || definition.id == null || definition.id.isBlank()) {
                throw new IllegalStateException("Virtual item is missing an id");
            }
            if (definition.id.length() > MAX_WIRE_ID_LENGTH) {
                throw new IllegalStateException(
                        "Virtual item id exceeds " + MAX_WIRE_ID_LENGTH + " characters"
                );
            }
            if (definition.effect == null) {
                throw new IllegalStateException("Virtual item " + definition.id + " has no effect");
            }
            if (!Double.isFinite(definition.amount)) {
                throw new IllegalStateException(
                        "Virtual item " + definition.id + " has a non-finite amount"
                );
            }
            if (definition.maxUses < 0) {
                throw new IllegalStateException(
                        "Virtual item " + definition.id + " has a negative maxUses"
                );
            }
            if (definition.bonuses == null) {
                definition.bonuses = new LinkedHashMap<>();
            }
            definition.bonuses.forEach((key, value) -> {
                if (key == null || key.isBlank() || key.length() > MAX_WIRE_ID_LENGTH
                        || value == null || !Double.isFinite(value)) {
                    throw new IllegalStateException(
                            "Virtual item " + definition.id + " has invalid bonus "
                                    + key + "=" + value
                    );
                }
            });
            if (byId.put(definition.id, definition) != null) {
                throw new IllegalStateException("Duplicate virtual item id: " + definition.id);
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private static List<Definition> defaults() {
        List<Definition> list = new ArrayList<>();
        String base = "aegis_ascension:textures/gui/virtual_item/virtual_item_book_";
        list.add(define("hp_stat_book", base + "0.png", Effect.MAX_HEALTH, 2.0D, 20));
        list.add(define("primary_stat_book", base + "1.png", Effect.PRIMARY_STAT, 1.0D, 10));
        list.add(define("atk_stat_book", base + "2.png", Effect.ATTACK_DAMAGE, 1.0D, 5));
        list.add(define("expand_inventory_book",
                "aegis_ascension:textures/gui/virtual_item/virtual_item_expand_inventory_0.png",
                Effect.STORAGE_SLOTS, 2.0D, 30));
        // maxUses 0: uncapped, since these grant nothing permanent to cap.
        Definition lethe = define("lethes_river_water",
                "aegis_ascension:textures/gui/virtual_item/virtual_item_lethes_river_water.png",
                Effect.RESET_PROGRESSION, 0.0D, 0);
        lethe.requiresConfirmation = true;
        list.add(lethe);
        list.add(define("devour_reset_book",
                "aegis_ascension:textures/gui/virtual_item/virtual_item_devour_aegis_reset.png",
                Effect.RESET_DEVOURED, 0.0D, 0));
        Definition swissRoll = define(SWISS_ROLL,
                "aegis_ascension:textures/gui/virtual_item/virtual_item_swiss_roll.png",
                Effect.COMPOSITE_STATS, 0.0D, 0);
        swissRoll.tier = "SSR";
        swissRoll.bonuses.put(ATTACK_DAMAGE, 5.0D);
        swissRoll.bonuses.put(ATTACK_SPEED_MULTIPLIER, 0.05D);
        swissRoll.bonuses.put(MAX_HEALTH_MULTIPLIER, 0.05D);
        swissRoll.bonuses.put(PRIMARY_ATTRIBUTE_FLAT, 5.0D);
        swissRoll.bonuses.put(ALL_SKILL_ENHANCEMENT_ATTRIBUTE, 0.03D);
        swissRoll.bonuses.put(DAMAGE_BONUS, 0.05D);
        swissRoll.bonuses.put(FINAL_DAMAGE, 0.05D);
        list.add(swissRoll);
        list.addAll(devourCoreDefaults());
        return list;
    }

    private static List<Definition> devourCoreDefaults() {
        List<Definition> cores = new ArrayList<>();
        Definition coreI = define(
                DEVOUR_AEGIS_CORE_I,
                "aegis_ascension:textures/gui/virtual_item/virtual_item_devour_aegis_core_i.png",
                Effect.DEVOUR_AEGIS_CORE,
                1.0D,
                1
        );
        coreI.tier = "SR";
        coreI.uniquePurchase = true;
        cores.add(coreI);

        Definition coreII = define(
                DEVOUR_AEGIS_CORE_II,
                "aegis_ascension:textures/gui/virtual_item/virtual_item_devour_aegis_core_ii.png",
                Effect.DEVOUR_AEGIS_CORE,
                1.0D,
                1
        );
        coreII.tier = "SSR";
        coreII.uniquePurchase = true;
        cores.add(coreII);

        Definition coreIII = define(
                DEVOUR_AEGIS_CORE_III,
                "aegis_ascension:textures/gui/virtual_item/virtual_item_devour_aegis_core_iii.png",
                Effect.DEVOUR_AEGIS_CORE,
                1.0D,
                1
        );
        coreIII.tier = "SSR";
        coreIII.uniquePurchase = true;
        cores.add(coreIII);
        return cores;
    }

    private static Definition define(String id, String icon, Effect effect,
                                     double amount, int maxUses) {
        Definition definition = new Definition();
        definition.id = id;
        definition.icon = icon;
        definition.name = "virtual_item.aegis_ascension." + id + ".name";
        definition.description = "virtual_item.aegis_ascension." + id + ".description";
        definition.effect = effect;
        definition.amount = amount;
        definition.maxUses = maxUses;
        return definition;
    }

    // ------------------------------------------------------------------
    // Applied bonuses
    // ------------------------------------------------------------------

    /** Total permanent bonus a player has earned for one effect, across every book. */
    public static double bonus(PlayerPerkData data, Effect effect) {
        double total = 0.0D;
        if (effect.isAction()) {
            return 0.0D;
        }
        for (Definition definition : definitions().values()) {
            if (definition.effect == effect) {
                total += definition.amount * data.getVirtualItemUses(definition.id);
            }
        }
        return total;
    }

    /** {@link #bonus} rounded to a whole number, for effects measured in discrete units. */
    public static int bonusInt(PlayerPerkData data, Effect effect) {
        return (int) Math.round(bonus(data, effect));
    }

    /** Total permanent value for one key across every consumed composite virtual item. */
    public static double statBonus(PlayerPerkData data, String statKey) {
        double total = 0.0D;
        for (Definition definition : definitions().values()) {
            if (definition.effect == Effect.COMPOSITE_STATS) {
                total += definition.stat(statKey) * data.getVirtualItemUses(definition.id);
            }
        }
        return total;
    }

    /**
     * Uses still available before the lifetime cap. A {@code maxUses} of 0 or less means
     * uncapped — the only limit is how many of the item the player actually owns, which is
     * what a purely consumable item (one that grants no permanent stat) wants.
     */
    public static int remainingUses(PlayerPerkData data, String id) {
        Definition definition = byId(id);
        if (definition == null) {
            return 0;
        }
        if (definition.maxUses <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, definition.maxUses - data.getVirtualItemUses(id));
    }

    /** True when this book has no lifetime cap; used to pick the right description wording. */
    public static boolean isUncapped(String id) {
        Definition definition = byId(id);
        return definition != null && definition.maxUses <= 0;
    }

    public static List<String> devourCoreIds() {
        return DEVOUR_AEGIS_CORES;
    }

    public static boolean isDevourAegisCore(String id) {
        return DEVOUR_AEGIS_CORES.contains(id);
    }

    /** Level a specific numbered core grants; zero means the id is not a core. */
    public static int devourCoreTargetLevel(String id) {
        if (DEVOUR_AEGIS_CORE_I.equals(id)) {
            return 1;
        }
        if (DEVOUR_AEGIS_CORE_II.equals(id)) {
            return 2;
        }
        if (DEVOUR_AEGIS_CORE_III.equals(id)) {
            return 3;
        }
        return 0;
    }

    /** Number of distinct sequential Devour Cores consumed, clamped to levels 0-3. */
    public static int devourCoreLevel(PlayerPerkData data) {
        int level = 0;
        for (String coreId : DEVOUR_AEGIS_CORES) {
            level += Math.min(1, data.getVirtualItemUses(coreId));
        }
        return Math.min(3, level);
    }

    /** Server-authoritative eligibility for a virtual item to enter this player's shop. */
    public static boolean canAppearInShop(PlayerPerkData data, String id) {
        Definition definition = byId(id);
        if (definition == null || !definition.appearsInShop) {
            return false;
        }
        if (definition.uniquePurchase && data.isUniqueVirtualItemAcquired(id)) {
            return false;
        }
        if (definition.effect == Effect.DEVOUR_AEGIS_CORE) {
            return data.hasAegis(AegisConstants.DEVOUR)
                    && devourCoreTargetLevel(id) == devourCoreLevel(data) + 1;
        }
        return true;
    }
}
