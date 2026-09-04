package com.whatever.aegis_ascension;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class AegisAscensionConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue RESET_PERKS_ON_DEATH = BUILDER
            .comment(
                    "If true, all perk data is reset when a player dies.",
                    "This includes owned talents, ranks, soul-link eligibility, unspent charges,",
                    "milestone history, pending offers, Skill Enhancements, Aegises, and stats.",
                    "Dimension travel and returning from the End do not count as death."
            )
            .define("resetPerksOnDeath", false);

    public static final ForgeConfigSpec.BooleanValue RESET_PERKS_ON_DEATH_EXCEPT_INVENTORY = BUILDER
            .comment(
                    "Only used when resetPerksOnDeath is true.",
                    "If true, the death reset spares the player's banked storage, their current",
                    "shop stock, and their virtual item use counts - only progression is lost.",
                    "This matches what the /perk reset command already does.",
                    "If false, a death wipes the storage UI along with everything else."
            )
            .define("resetPerksOnDeathExceptInventory", true);

    public static final ForgeConfigSpec.IntValue BASE_MAX_TALENT_SLOTS = BUILDER
            .comment(
                    "Maximum number of unique talents a player may own before bonus slots.",
                    "Ranks of an already-owned repeatable talent do not consume more slots.",
                    "Lowering this below a player's current count does not remove existing talents."
            )
            .defineInRange("baseMaxTalentSlots", 33, 1, 10000);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            HIDDEN_TALENT_IDS = BUILDER
            .comment(
                    "Talent IDs hidden and disabled by the server, for example:",
                    "hiddenTalentIds = [\"perk_skill_damage_conversion\", \"perk_plana\"]",
                    "Hidden talents are removed from offer pools and collection screens and",
                    "do not provide effects, use talent slots, or satisfy prerequisites.",
                    "Existing ranks remain saved and become active again if the ID is removed.",
                    "Soul Links requiring any hidden talent are displayed as Disabled."
            )
            .defineListAllowEmpty(
                    "hiddenTalentIds",
                    List.of(),
                    value -> value instanceof String id && !id.isBlank()
            );

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            MYSTERIOUS_DOLL_BANNED_OUTCOMES = BUILDER
            .comment(
                    "Mysterious Doll outcome IDs removed from its weighted reward pool.",
                    "Available IDs: random_aegis, random_item,",
                    "all_skill_enhancement_attribute, cooldown_reduction,",
                    "random_ssr, negative_damage_reduction, primary_stat_flat.",
                    "An outcome can also be disabled with enabled=false in",
                    "config/aegis_ascension/mysterious_doll_serverside.json.",
                    "The remaining positive JSON weights are automatically normalized."
            )
            .defineListAllowEmpty(
                    "mysteriousDollBannedOutcomes",
                    List.of(),
                    value -> value instanceof String id && !id.isBlank()
            );

    public static final ForgeConfigSpec.BooleanValue LIVE_CUSTOM_STATS_REFRESH = BUILDER
            .comment(
                    "Server-side permission for live Custom Stats updates.",
                    "If true, clients may request fresh stat snapshots while the Custom Stats",
                    "tab is open. packetCooldowns.livePerkDataSeconds controls the rate limit.",
                    "If false, stats update only when the collection or tab is opened."
            )
            .define("liveCustomStatsRefresh", false);

    public static final ForgeConfigSpec.IntValue SKILL_ENHANCEMENT_LEVEL_INTERVAL = BUILDER
            .comment(
                    "Experience levels required for each Skill Enhancement charge.",
                    "The default value of 2 awards charges at levels 2, 4, 6, and so on.",
                    "Changing this affects future level milestones and never removes saved charges."
            )
            .defineInRange("skillEnhancementLevelsPerCharge", 2, 1, 10000);

    public static final ForgeConfigSpec.IntValue
            MAXIMUM_SKILL_ENHANCEMENT_CHARGES_FROM_EXPERIENCE = BUILDER
            .comment(
                    "Maximum number of normal Skill Enhancement charges a player may",
                    "receive from experience-level milestones. Charges from talents,",
                    "Breakthrough, exchanges, commands, and Holy Blessing's post-cap effect",
                    "do not count toward this limit. Set to 0 to disable normal XP charges."
            )
            .defineInRange(
                    "maximumSkillEnhancementChargesFromExperience",
                    200,
                    0,
                    Integer.MAX_VALUE
            );

    public static final ForgeConfigSpec.IntValue
            SKILL_ENHANCEMENT_REFRESH_EXPERIENCE_COST = BUILDER
            .comment(
                    "Raw experience points spent to refresh the current Skill Enhancement",
                    "choices in Talent Collection. Set to 0 to make refreshes globally free.",
                    "The Logistics Combo Soul Link makes this refresh free regardless."
            )
            .defineInRange("skillEnhancementRefreshExperienceCost", 100, 0, 100000000);

    public static final ForgeConfigSpec.IntValue PERK_LEVEL_INTERVAL = BUILDER
            .comment(
                    "Experience levels required for each Aegis Ascension charge.",
                    "Every player also receives one persisted starting charge.",
                    "The default value of 10 awards charges at levels 10, 20, 30, and so on.",
                    "Changing this affects future level milestones and never removes saved charges."
            )
            .defineInRange("perkLevelsPerCharge", 10, 1, 10000);

    public static final ForgeConfigSpec.IntValue MAXIMUM_PERK_CHARGES_FROM_EXPERIENCE =
            BUILDER
                    .comment(
                            "Maximum number of Aegis Ascension charges a player may receive",
                            "from experience-level milestones. The free starting charge and",
                            "charges granted by talents, Aegises, commands, or other effects",
                            "do not count toward this limit. Set to 0 to disable XP charges."
                    )
                    .defineInRange(
                            "maximumPerkChargesFromExperience",
                            30,
                            0,
                            Integer.MAX_VALUE
                    );

    public static final ForgeConfigSpec.IntValue MAXIMUM_BREAKTHROUGHS_FROM_EXPERIENCE =
            BUILDER
                    .comment(
                            "Maximum number of base Breakthrough triggers a player may receive",
                            "from experience-level milestones. This is independent from the",
                            "Perk charge limit, so later milestones can still trigger Breakthrough",
                            "after XP-derived Perk charges reach their cap. The default is 40.",
                            "Set to 0 to disable XP-derived Breakthrough triggers."
                    )
                    .defineInRange(
                            "maximumBreakthroughTriggersFromExperience",
                            40,
                            0,
                            Integer.MAX_VALUE
                    );

    public static final ForgeConfigSpec.BooleanValue TRIGGER_BREAKTHROUGH_ON_PERK_SELECTION =
            BUILDER
                    .comment(
                            "If true, every spent Aegis Ascension charge executes one Breakthrough",
                            "and consumes one stored XP Breakthrough trigger when available.",
                            "When the Perk charge balance reaches zero or owned unique talents",
                            "reach the current talent-slot cap, every stored Breakthrough still",
                            "remaining activates automatically. A selected talent does not affect",
                            "its own Breakthrough, but affects later stored Breakthroughs.",
                            "If false, Breakthrough triggers immediately at XP milestones instead."
                    )
                    .define("triggerBreakthroughOnPerkSelection", true);

    public static final ForgeConfigSpec.BooleanValue RESET_TALENT_REFRESH_ON_BREAKTHROUGH =
            BUILDER
                    .comment(
                            "If true, every Breakthrough clears all unused Perk/Talent offer",
                            "refresh charges to zero. Refresh charges earned afterward are",
                            "unaffected. If false, unused refresh charges remain persisted."
                    )
                    .define("resetTalentRefreshOnBreakthrough", true);

    public static final ForgeConfigSpec.IntValue MAXIMUM_PERK_OPTIONS = BUILDER
            .comment(
                    "Maximum number of distinct options that may appear in one",
                    "Aegis Ascension offer. Luck and talent option bonuses cannot exceed",
                    "this server-side limit. Lowering it also trims saved pending offers."
            )
            .defineInRange("maximumPerkOptions", 16, 1, 10000);

    public static final ForgeConfigSpec.IntValue SKILL_ENHANCEMENT_CHARGES_PER_PERK_EXCHANGE =
            BUILDER
                    .comment(
                            "Skill Enhancement charges granted when a player gives up one",
                            "current Aegis Ascension charge from the perk selection screen."
                    )
                    .defineInRange(
                            "skillEnhancementChargesPerPerkExchange",
                            2,
                            1,
                            10000
                    );

    public static final ForgeConfigSpec.IntValue AEGIS_LEVEL_INTERVAL = BUILDER
            .comment(
                    "Experience levels required for each Aegis Selection charge after",
                    "the free starting charge. The default awards at levels 60, 120, 180, etc."
            )
            .defineInRange("aegisLevelsPerCharge", 60, 1, 10000);

    public static final ForgeConfigSpec.IntValue MAXIMUM_AEGIS_CHARGES = BUILDER
            .comment(
                    "Maximum number of level-progression Aegis charges a player may ever earn,",
                    "including the free starting charge. This normally also limits the number",
                    "of manually selected Aegises. Bonus effects such as Miracle may exceed it.",
                    "Lowering this never removes already-earned charges or owned Aegises."
            )
            .defineInRange("maximumAegisCharges", 3, 1, 10000);

    public static final ForgeConfigSpec.IntValue AEGIS_REFRESH_CHARGES_PER_CHARGE = BUILDER
            .comment(
                    "Aegis offer refresh charges granted for each newly awarded Aegis",
                    "Selection charge, including the free starting charge. Set to 0 to disable."
            )
            .defineInRange("aegisRefreshChargesPerCharge", 1, 0, 10000);

    public static final ForgeConfigSpec.BooleanValue
            DEVOUR_ALLOW_AGAIN_AFTER_DISCARD = BUILDER
            .comment(
                    "If true, an item may be devoured again after its saved bonuses are",
                    "discarded from the Devoured Items screen. An item can never be",
                    "devoured twice while its previous bonuses are still active.",
                    "If false, discarding bonuses permanently retains the consumed item ID."
            )
            .define("devourAllowAgainAfterDiscard", false);

    public static final ForgeConfigSpec.BooleanValue
            DEVOUR_CONVERT_FLAT_ATTACK_SPEED_TO_PERCENTAGE = BUILDER
            .comment(
                    "If true, flat Attack Speed modifiers inherited by Devour Aegis are",
                    "converted into total percentage modifiers using Attack Speed's default",
                    "base value (4.0 in vanilla). For example, -2.4 becomes -60%, leaving",
                    "40% of the base Attack Speed. This also updates already-devoured items",
                    "the next time player attributes are recalculated.",
                    "If false, the item's original flat Attack Speed modifier is preserved."
            )
            .define("devourConvertFlatAttackSpeedToPercentage", true);

    static {
        BUILDER.push("progression");
    }

    public static final ForgeConfigSpec.BooleanValue USE_MINECRAFT_DEFAULT_LEVEL = BUILDER
            .comment(
                    "If true, Aegis Ascension progression uses Minecraft's normal experience level.",
                    "If false, Perks, Aegises, Skill Enhancements, and Breakthrough milestones use",
                    "the separate Aegis Ascension Experience rank instead. Vanilla XP remains usable",
                    "for enchanting, anvils, and other vanilla systems; mod costs may optionally",
                    "use Gold Currency through useGoldCurrency."
            )
            .define("useMinecraftDefaultLevel", false);

    public static final ForgeConfigSpec.BooleanValue USE_GOLD_CURRENCY = BUILDER
            .comment(
                    "If true, Aegis Ascension's shop, paid refreshes, storage sales,",
                    "challenge deposits, and quest rewards use the mod's persisted Gold",
                    "Currency in addition to Aegis Ascension Experience. If false, all",
                    "of those systems keep their existing XP behavior. Vanilla villager",
                    "trades and vanilla experience remain unchanged."
            )
            .define("useGoldCurrency", true);

    public static final ForgeConfigSpec.LongValue AEGIS_ASCENSION_BASE_XP = BUILDER
            .comment(
                    "Aegis Ascension Experience required to advance from Rank 1 to Rank 2.",
                    "The next-rank requirement is BaseXP * (GrowthRate ^ (CurrentRank - 1)).",
                    "Sized against quest income: at 500 a rank costs roughly one good side",
                    "quest, and the rank 20 gate that governs SSR quests takes about",
                    "twenty-five quests. At the old default of 100 a single Challenge",
                    "carried a new player past every rank gate in the catalogue at once."
            )
            .defineInRange("aegisAscensionBaseXP", 500L, 1L, 100_000_000L);

    public static final ForgeConfigSpec.DoubleValue AEGIS_ASCENSION_GROWTH_RATE = BUILDER
            .comment(
                    "Exponential growth factor for Aegis Ascension Experience requirements.",
                    "Values near 1.01 are recommended for the 1000-rank cap; the bounded range",
                    "keeps the calculated long-valued requirements practical."
            )
            .defineInRange("aegisAscensionGrowthRate", 1.01D, 1.0D, 1.02D);

    public static final ForgeConfigSpec.IntValue AEGIS_ASCENSION_MAXIMUM_RANK = BUILDER
            .comment(
                    "Highest Aegis Ascension rank a player can reach. The server supports at most",
                    "rank 1000 as requested; lowering this only affects future rank-ups."
            )
            .defineInRange("aegisAscensionMaximumRank", 1000, 1, 1000);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            DEVOUR_ATTRIBUTE_BLACKLIST = BUILDER
            .comment(
                    "Attribute IDs that Devour Aegis must never inherit.",
                    "Entity and block reach are blacklisted by default to prevent extreme range.",
                    "Examples: [\"forge:entity_reach\", \"forge:block_reach\"]"
            )
            .defineListAllowEmpty(
                    "devourAttributeBlacklist",
                    List.of("forge:entity_reach", "forge:block_reach"),
                    value -> value instanceof String id
                            && ResourceLocation.tryParse(id.trim()) != null
            );

    static {
        BUILDER.push("packetCooldowns");
    }

    public static final ForgeConfigSpec.DoubleValue STORAGE_MUTATION_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds shared by storage deposits, extraction, use,",
                            "sale, discard, and inventory-slot storage packets. 0 disables it."
                    )
                    .defineInRange("storageMutationSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue STORAGE_VIEW_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds for storage sync and integrated-inventory open",
                            "requests. Each request type has its own bucket. 0 disables it."
                    )
                    .defineInRange("storageViewSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue TOGGLE_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds shared by talent/Aegis toggles, constellation",
                            "unlocks, primary-skill selection, and shop purchases. 0 disables it."
                    )
                    .defineInRange("toggleAndPurchaseSeconds", 1.0D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue REFRESH_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds shared by Perk, Aegis, Skill Enhancement, and",
                            "shop manual-refresh packets. 0 disables it."
                    )
                    .defineInRange("refreshSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue DEVOUR_ITEM_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment("Cooldown in seconds for Devour-item requests. 0 disables it.")
                    .defineInRange("devourItemSeconds", 0.25D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue DEVOUR_DATA_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment("Cooldown in seconds for Devour-data sync requests. 0 disables it.")
                    .defineInRange("devourDataSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue DISCARD_DEVOUR_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment("Cooldown in seconds for discarding Devoured bonuses. 0 disables it.")
                    .defineInRange("discardDevouredSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue PERK_DATA_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds for ordinary Perk/stat data requests.",
                            "0 disables it."
                    )
                    .defineInRange("perkDataSeconds", 0.25D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue LIVE_PERK_DATA_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds for live Custom Stats data requests.",
                            "0 disables it; liveCustomStatsRefresh must still be enabled."
                    )
                    .defineInRange("livePerkDataSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue SHARED_FORTUNE_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds for Shared Fortune bind/unbind packets.",
                            "This is separate from the talent's gameplay rebind cooldown."
                    )
                    .defineInRange("sharedFortuneSeconds", 0.5D, 0.0D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue QUEST_PROGRESS_SYNC_INTERVAL_SECONDS =
            BUILDER.comment(
                            "Interval in seconds for batching ordinary quest progress updates.",
                            "Completions and structural quest changes still synchronize immediately.",
                            "The minimum of 0.05 seconds limits progress traffic to once per tick."
                    )
                    .defineInRange("questProgressSyncIntervalSeconds", 0.5D, 0.05D, 60.0D);

    public static final ForgeConfigSpec.DoubleValue QUEST_VIEW_PACKET_COOLDOWN_SECONDS =
            BUILDER.comment(
                            "Cooldown in seconds for client requests for the full Quest Center",
                            "snapshot. Quest actions use the separate toggle/action limiter."
                    )
                    .defineInRange("questViewSeconds", 0.5D, 0.0D, 60.0D);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private AegisAscensionConfig() {
    }
}
