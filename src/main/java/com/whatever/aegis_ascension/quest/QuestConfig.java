package com.whatever.aegis_ascension.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralConstants;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/** Server-only, editable quest catalogue. The client receives rolled quests only. */
public final class QuestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths().modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("questsetting.json");
    /** Templates live one file per quest type beside the settings file. */
    private static final Path QUEST_DIRECTORY = FILE.getParent().resolve("quests");
    private static final Map<QuestType, String> TEMPLATE_FILES = templateFileNames();
    private static final String GENERATED_FILE = "generated.json";

    private static Map<QuestType, String> templateFileNames() {
        Map<QuestType, String> names = new LinkedHashMap<>();
        names.put(QuestType.DAILY, "daily.json");
        names.put(QuestType.CHALLENGE, "challenge.json");
        names.put(QuestType.COMMON, "common.json");
        names.put(QuestType.CHUNK, "chunk.json");
        names.put(QuestType.SIDE, "side.json");
        return names;
    }
    private static QuestConfig instance;

    /**
     * Upper bound on how many quests a single generation pass may roll for one type.
     * The per-type min/max below are clamped to this value, so lowering it caps every
     * quest type at once regardless of their individual settings.
     */
    public int maxRandomQuests = 5;
    public int dailyMin = 1;
    public int dailyMax = 5;
    public int dailyRefreshIntervalHours = 24;
    /** Whether newly generated daily quests should begin Active instead of Available. */
    public boolean dailyAutoAccept = true;
    public int challengeMin = 1;
    public int challengeMax = 5;
    public int challengeRefreshIntervalHours = 24;
    public int challengeSecurityDepositExperience = 250;
    /**
     * Chance (0..1) that an ordinary random reward is drawn from the Discovery shop
     * rather than the common one, at the quest's own rarity. Daily and Chunk quests never
     * do this, and neither does a quest that already guarantees a Discovery item. Zero
     * keeps every ordinary reward common.
     */
    public double discoveryRewardChance = 0.5D;
    /**
     * Chance (0..1) that a generated Challenge also offers a pick from the Discovery
     * shop's top shelf, on top of whatever it already pays. Zero disables it. A Challenge
     * that already declares its own reward choices keeps those instead.
     */
    public double challengeDiscoveryChoiceChance = 0.5D;
    /** How many distinct items that pick offers. */
    public int challengeDiscoveryChoiceCount = 3;
    /**
     * Rarity drawn for those items. Blank, the default, matches the rarity the Challenge
     * itself rolled at, so an R Challenge offers a pick of R goods and only an SSR
     * Challenge offers a pick of SSR ones. Naming a tier forces that rarity instead.
     */
    public String challengeDiscoveryChoiceTier = "";
    /**
     * Chance (0..1) that a chain stage offers the same pick. Chain stages are one-time
     * and tell a story, so at the default of 1 finishing one always ends in a choice,
     * the way a questline reward usually does. The pick always matches the stage's own
     * rarity, whatever challengeDiscoveryChoiceTier is set to.
     */
    public double chainDiscoveryChoiceChance = 1.0D;
    /**
     * Whether the procedurally composed side quests in quests/generated.json are added
     * to the catalogue. False leaves only the hand-authored templates, and takes effect
     * on the next /aegis_ascension quest reload.
     */
    public boolean generateRandomSideQuests = true;
    /**
     * Lowest rarity a Challenge must have rolled at to be offered the pick at all.
     * At R every Challenge is eligible; raise it to restrict the pick to rarer ones.
     */
    public String challengeDiscoveryChoiceMinimumTier = "R";
    /**
     * Item shown beside a profession's reputation on a side quest. Any item id works;
     * an unresolvable one falls back to the generic reward icon rather than rendering
     * nothing, so a typo here cannot blank the line.
     */
    public String reputationIcon = "minecraft:emerald";
    /**
     * How far short of a quest's reputation requirement it may still be offered, listed
     * as locked so the player can see what their standing is buying them. Quests further
     * out of reach than this are withheld entirely: with only a handful of slots per
     * draw, showing every late-game contract at zero standing would leave a new player
     * with a list they cannot act on. Zero hides every unmet quest.
     */
    public int reputationVisibleWithin = 2;
    /** Client-local SoundEvent ResourceLocation played once per completion batch. Blank disables it. */
    public String questCompleteSound =
            "aegis_ascension:quest_ui/quest_complete_sound";
    /** Default random Gold reward range for generated quests when Gold Currency is enabled. */
    public long goldRewardMin = 10L;
    public long goldRewardMax = 50L;
    public int chunkMin = 1;
    public int chunkMax = 5;
    /** Whether generated Chunk quests may use the player's auto-accept preference. */
    public boolean chunkAutoAccept = true;
    public int sideMin = 1;
    public int sideMax = 5;
    public int sideRefreshIntervalHours = 24;
    /** Whether unlocked common quests should be accepted as soon as they are generated. */
    public boolean commonAutoAccept = true;
    public List<Template> dailyTemplates = new ArrayList<>();
    public List<Template> challengeTemplates = new ArrayList<>();
    public List<Template> commonTemplates = new ArrayList<>();
    public List<Template> chunkTemplates = new ArrayList<>();
    public List<Template> sideTemplates = new ArrayList<>();
    public RewardPools rewardPools = new RewardPools();
    /**
     * How each quest rarity scales a rolled quest. A template lists the tiers it may
     * roll; the tier chosen here then multiplies its target, experience, gold and reward
     * stacks, so a harder quest is always the rarer one.
     */
    public Map<String, TierScaling> questTiers = defaultQuestTiers();
    /** Not serialised: rebuilt on demand, and discarded with the instance on reload. */
    private transient Map<String, Template> templateIndex;

    private static Map<String, TierScaling> defaultQuestTiers() {
        Map<String, TierScaling> tiers = new LinkedHashMap<>();
        tiers.put(GeneralConstants.TIER_R,
                new TierScaling(60, 1.0D, 1.0D, 1.0D, 1.0D, 0, 1.0D));
        tiers.put(GeneralConstants.TIER_SR,
                new TierScaling(32, 1.8D, 2.5D, 2.5D, 1.6D, 8, 2.0D));
        tiers.put(GeneralConstants.TIER_SSR,
                new TierScaling(8, 3.0D, 5.0D, 5.0D, 2.2D, 20, 4.0D));
        return tiers;
    }

    /** Scaling applied to any quest rolled at one rarity. */
    public static final class TierScaling {
        /** Relative chance of this tier being chosen; zero disables it. */
        public int weight = 1;
        public double targetMultiplier = 1.0D;
        public double experienceMultiplier = 1.0D;
        public double goldMultiplier = 1.0D;
        public double rewardCountMultiplier = 1.0D;
        /**
         * How much more a Challenge at this rarity stakes on acceptance. Rarity on a
         * Challenge should mean danger rather than only a bigger payout, so the wager
         * rises with it.
         */
        public double stakeMultiplier = 1.0D;
        /** Progression level below which this tier is never rolled. */
        public int minimumRank;

        public TierScaling() {}

        public TierScaling(int weight, double targetMultiplier, double experienceMultiplier,
                           double goldMultiplier, double rewardCountMultiplier,
                           int minimumRank) {
            this(weight, targetMultiplier, experienceMultiplier, goldMultiplier,
                    rewardCountMultiplier, minimumRank, 1.0D);
        }

        public TierScaling(int weight, double targetMultiplier, double experienceMultiplier,
                           double goldMultiplier, double rewardCountMultiplier,
                           int minimumRank, double stakeMultiplier) {
            this.stakeMultiplier = stakeMultiplier;
            this.weight = weight;
            this.targetMultiplier = targetMultiplier;
            this.experienceMultiplier = experienceMultiplier;
            this.goldMultiplier = goldMultiplier;
            this.rewardCountMultiplier = rewardCountMultiplier;
            this.minimumRank = minimumRank;
        }
    }

    public static QuestConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() { instance = null; }

    public long dailyRefreshTicks() { return hoursToTicks(dailyRefreshIntervalHours); }
    public long challengeRefreshTicks() { return hoursToTicks(challengeRefreshIntervalHours); }
    public long sideRefreshTicks() { return hoursToTicks(sideRefreshIntervalHours); }

    private static long hoursToTicks(int hours) {
        return Math.max(1, hours) * 60L * 60L * 20L;
    }

    public QuestDefinition definition(String id) {
        Template template = template(id);
        return template == null ? null : template.definition();
    }

    public Template template(String id) {
        String base = id == null ? "" : id;
        int marker = base.indexOf('#');
        if (marker >= 0) base = base.substring(0, marker);
        return templateIndex().get(base);
    }

    /**
     * Lazily built id-to-template map. Quest ticking looks a template up per quest per
     * player per tick, and walking allTemplates() for each of those rebuilt the whole
     * catalogue list every time. Cleared with the instance whenever the file is reloaded.
     */
    private Map<String, Template> templateIndex() {
        Map<String, Template> index = templateIndex;
        if (index == null) {
            index = new LinkedHashMap<>();
            for (Template template : allTemplates()) {
                if (template != null && template.id != null && !template.id.isBlank()) {
                    // putIfAbsent keeps the first match, as the old scan did.
                    index.putIfAbsent(template.id, template);
                }
            }
            templateIndex = index;
        }
        return index;
    }

    /** Resolves which catalogue section owns a stable or rolled quest id. */
    public QuestType typeOf(String id) {
        String base = id == null ? "" : id;
        int marker = base.indexOf('#');
        if (marker >= 0) base = base.substring(0, marker);
        for (QuestType type : QuestType.values()) {
            for (Template template : templates(type)) {
                if (template != null && template.id.equals(base)) return type;
            }
        }
        return QuestType.COMMON;
    }

    public List<Template> templates(QuestType type) {
        return switch (type) {
            case DAILY -> dailyTemplates;
            case CHALLENGE -> challengeTemplates;
            case COMMON -> commonTemplates;
            case CHUNK -> chunkTemplates;
            case SIDE -> sideTemplates;
        };
    }

    public List<Template> allTemplates() {
        List<Template> result = new ArrayList<>();
        result.addAll(dailyTemplates); result.addAll(challengeTemplates);
        result.addAll(commonTemplates); result.addAll(chunkTemplates);
        result.addAll(sideTemplates);
        return result;
    }

    public static final class RewardPools {
        public List<String> commonItems = new ArrayList<>();
        public List<String> uniqueItems = new ArrayList<>();
        public List<String> curioItems = new ArrayList<>();
    }

    public static final class Template {
        public String id = "quest";
        public QuestObjective objective = QuestObjective.KILL;
        public String targetId = "";
        public List<String> targetIds = new ArrayList<>();
        public int target = 1;
        public int targetMin;
        public int targetMax;
        public String title = "";
        public String description = "";
        public String story = "";
        public String profession = "";
        public String prerequisiteId = "";
        /** Relative chance of this template being selected; zero disables it. */
        public int weight = 1;
        /**
         * Rarities this template may roll at, e.g. {@code ["R","SR"]}. An empty list
         * keeps the template at R, so a modest errand never becomes an SSR quest.
         */
        public List<String> tiers = new ArrayList<>();
        /** Minimum configured progression level (Minecraft level or AAE rank). */
        public int minimumRank;
        /**
         * Quests of this profession the player must already have completed before this
         * one is offered. Standing with one villager is earned by working for them, so a
         * profession's harder contracts stay out of the pool until its easier ones are done.
         */
        public int minimumReputation;
        /** Optional dimension ResourceLocations in which this template may be generated. */
        public List<String> dimensions = new ArrayList<>();
        /** Optional biome ResourceLocations in which this template may be generated. */
        public List<String> biomes = new ArrayList<>();
        /** Client texture ResourceLocation used for this quest's row and detail icon. */
        public String icon = "";
        public int experience = 0;
        /** Optional per-quest Gold reward range; zero uses the catalogue defaults. */
        public long goldRewardMin;
        public long goldRewardMax;
        /**
         * Further requirements that must all be met alongside the main objective above.
         * Item-submission objectives are allowed: one press of Submit hands in every
         * outstanding requirement the quest asks for.
         */
        public List<SubObjective> alsoRequires = new ArrayList<>();
        public List<RewardSpec> rewards = new ArrayList<>();
        /**
         * Rewards the player picks exactly one of on completion, alongside the guaranteed
         * rewards above. Experience and gold are never part of the choice; only items are,
         * so completing a quest always pays something even if the choice is left pending.
         */
        public List<RewardSpec> rewardChoices = new ArrayList<>();
        /**
         * Absolute lifetime in minutes from the moment this quest is generated. Zero
         * keeps the quest alive until its type's next refresh; any positive value makes
         * it expire on its own clock, whether or not the player ever accepted it.
         */
        public int lifetimeMinutes;
        /**
         * Never offers this template again once the player has completed it. Chain
         * stages set this so a finished story is not handed out a second time.
         */
        public boolean oncePerPlayer;
        /**
         * Fails this quest if the player leaves the world while it is accepted, for
         * contracts that are meant to be finished in one sitting. Off by default, and
         * deliberately so: the game reports a crash, a dropped connection and a server
         * restart as the same event, so anything long-running should not set this.
         */
        public boolean failOnLogout;
        /**
         * Stake charged on acceptance and returned on completion; cancelling or letting
         * the quest lapse forfeits it. Zero means no stake, except on Challenges, which
         * fall back to challengeSecurityDepositExperience above.
         */
        public int securityDeposit;
        /** Fails this quest if the player dies while it is accepted. */
        public boolean failOnDeath;
        /** Fails this quest if the player takes any damage while it is accepted. */
        public boolean failOnDamageTaken;
        /**
         * Fails this quest the moment any armour is worn while it is accepted, and
         * refuses acceptance while armoured. Checked continuously rather than at
         * completion, which a player could satisfy by stripping for the final blow.
         */
        public boolean failOnArmorWorn;
        /** Reuses this stable Common quest id after every rewarded completion. */
        public boolean repeatable;
        /** Number of completions between target increases. */
        public int targetIncreaseEvery = 1;
        public int targetIncreaseAmount;
        /** Zero means no additional cap beyond Integer.MAX_VALUE. */
        public int maximumTarget;
        /** Number of completions between experience/item-count multiplier increases. */
        public int rewardIncreaseEvery = 10;
        public double rewardMultiplierIncrease = 0.5D;
        public double maximumRewardMultiplier = 1.0D;
        /** Minimum real play interval between repeatable reward grants. */
        public int minimumRewardIntervalMinutes;

        /** Constraint keys the client renders as warnings; empty when there are none. */
        public String constraintKeys() {
            StringBuilder keys = new StringBuilder();
            if (failOnDeath) keys.append("death");
            if (failOnDamageTaken) {
                if (keys.length() > 0) keys.append(',');
                keys.append("damage");
            }
            if (failOnArmorWorn) {
                if (keys.length() > 0) keys.append(',');
                keys.append("armor");
            }
            return keys.toString();
        }

        public QuestDefinition definition() {
            return new QuestDefinition(id, null, objective, title, description, targetId,
                    target, experience, 0L, resolveFixedRewards(), story, profession,
                    prerequisiteId, icon, GeneralConstants.TIER_R, List.of(), List.of());
        }

        private List<QuestDefinition.Reward> resolveFixedRewards() {
            List<QuestDefinition.Reward> result = new ArrayList<>();
            if (rewards != null) for (RewardSpec spec : rewards) {
                if (spec == null) continue;
                String kind = spec.kind == null ? "item" : spec.kind;
                if ("item".equalsIgnoreCase(kind) || "virtual".equalsIgnoreCase(kind)) {
                    result.add(new QuestDefinition.Reward(
                            "virtual".equalsIgnoreCase(kind) ? "" : spec.id,
                            "virtual".equalsIgnoreCase(kind) ? spec.id : "",
                            spec.count, spec.tier, spec.unique));
                }
            }
            return result;
        }
    }

    /** One additional requirement of a compound quest. */
    public static final class SubObjective {
        public QuestObjective objective = QuestObjective.KILL;
        public String targetId = "";
        public List<String> targetIds = new ArrayList<>();
        public int target = 1;
        public int targetMin;
        public int targetMax;

        public boolean isSubmission() {
            return objective == QuestObjective.TRADE_ITEM
                    || objective == QuestObjective.GIVE_MATERIAL;
        }
    }

    public static final class RewardSpec {
        /** item, virtual, shop_item, random_common, random_unique, or random_curio. */
        public String kind = "item";
        public String id = "minecraft:bread";
        /** Common or Discovery; used by shop_item. */
        public String source = "common";
        /** Explicit real-item fallback when a shop_item tier has no eligible candidate. */
        public String fallbackId = "";
        public int count = 1;
        /**
         * Optional random stack size. When either bound is set the granted amount is
         * rolled in [countMin, countMax] instead of using the fixed count above.
         */
        public int countMin;
        public int countMax;
        public String tier = "R";
        public boolean unique = false;
    }

    /**
     * Reports catalogue mistakes at load, where they are cheap to see. Most of these fail
     * silently at runtime rather than throwing: a misspelled prerequisiteId simply makes a
     * quest that can never be offered, which is indistinguishable from bad luck in play.
     *
     * <p>Nothing here rejects the file. A catalogue with one bad template should still
     * load the other thirty-eight.</p>
     */
    /**
     * Display data for the login catalog snapshot. Everything here is fixed by the
     * template rather than rolled, so the client can hold one copy instead of being sent
     * it again on every quest sync. It travels from the server for the same reason the
     * other catalogs do: a client's own files may be stale or edited, and the screen must
     * state the rules the server actually enforces.
     */
    public String exportCatalogJson() {
        List<CatalogEntry> entries = new ArrayList<>();
        // Walked per type rather than through allTemplates, because which section a
        // template lives in is what gives it its quest type, and the flat list loses that.
        for (QuestType type : QuestType.values()) {
            for (Template template : templates(type)) {
                if (template == null || template.id == null || template.id.isBlank()) continue;
                entries.add(new CatalogEntry(template, type));
            }
        }
        return GSON.toJson(entries);
    }

    /** One template's fixed presentation, as sent to the client. */
    public static final class CatalogEntry {
        public String id = "";
        public String title = "";
        public String description = "";
        public String story = "";
        public String profession = "";
        public String icon = "";
        public String prerequisiteId = "";
        public String constraints = "";
        public QuestObjective objective = QuestObjective.KILL;
        public QuestType type = QuestType.COMMON;
        public int minimumReputation;
        public int securityDeposit;
        /** True when finishing this quest retires it, so it is never offered again. */
        public boolean oncePerPlayer;

        public CatalogEntry() {
        }

        CatalogEntry(Template template, QuestType questType) {
            objective = template.objective == null ? QuestObjective.KILL : template.objective;
            type = questType;
            id = template.id;
            title = template.title == null ? "" : template.title;
            description = template.description == null ? "" : template.description;
            story = template.story == null ? "" : template.story;
            profession = template.profession == null ? "" : template.profession;
            icon = template.icon == null ? "" : template.icon;
            prerequisiteId = template.prerequisiteId == null ? "" : template.prerequisiteId;
            constraints = template.constraintKeys();
            oncePerPlayer = template.oncePerPlayer;
            minimumReputation = Math.max(0, template.minimumReputation);
            securityDeposit = Math.max(0, template.securityDeposit);
        }
    }

    private void validate() {
        var log = AegisAscensionMod.getLogger();
        List<Template> templates = allTemplates();
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (Template template : templates) {
            if (template == null) continue;
            if (template.id == null || template.id.isBlank()) {
                log.warn("Quest catalogue contains a template with no id; it can never roll");
                continue;
            }
            if (!ids.add(template.id)) {
                log.warn("Quest template id {} is declared more than once; only the first "
                        + "is reachable by id", template.id);
            }
        }
        for (QuestType type : QuestType.values()) {
        for (Template template : templates(type)) {
            if (template == null || template.id == null || template.id.isBlank()) continue;
            String id = template.id;

            if (template.targetMin > 0 && template.targetMax > 0
                    && template.targetMin > template.targetMax) {
                log.warn("Quest template {} has targetMin {} above targetMax {}",
                        id, template.targetMin, template.targetMax);
            }
            if (template.goldRewardMin > template.goldRewardMax
                    && template.goldRewardMax > 0L) {
                log.warn("Quest template {} has goldRewardMin above goldRewardMax", id);
            }
            for (RewardSpec spec : template.rewards == null ? List.<RewardSpec>of() : template.rewards) {
                if (spec != null && spec.countMin > 0 && spec.countMax > 0
                        && spec.countMin > spec.countMax) {
                    log.warn("Quest template {} has a reward with countMin above countMax", id);
                }
            }
            for (String tier : template.tiers == null ? List.<String>of() : template.tiers) {
                if (tier != null && (questTiers == null || !questTiers.containsKey(
                        GeneralConstants.normalizeTier(tier)))) {
                    log.warn("Quest template {} may roll tier {}, which questTiers does "
                            + "not define; it will scale as if unset", id, tier);
                }
            }
            if (template.alsoRequires != null) {
                if (template.alsoRequires.size() >= 8) {
                    log.warn("Quest template {} declares {} extra requirements; only the "
                            + "first few will survive the network limit",
                            id, template.alsoRequires.size());
                }
                for (SubObjective sub : template.alsoRequires) {
                    if (sub == null) continue;
                    if (sub.targetMin > 0 && sub.targetMax > 0
                            && sub.targetMin > sub.targetMax) {
                        log.warn("Quest template {} has an extra requirement with targetMin "
                                + "above targetMax", id);
                    }
                }
            }
            if (template.minimumReputation > 0
                    && (template.profession == null || template.profession.isBlank())) {
                log.warn("Quest template {} requires {} reputation but names no profession, "
                        + "so nothing can ever raise it and the quest is unreachable",
                        id, template.minimumReputation);
            }
            if (template.prerequisiteId != null && !template.prerequisiteId.isBlank()) {
                if (!ids.contains(template.prerequisiteId)) {
                    log.warn("Quest template {} requires {}, which no template declares; "
                            + "this quest can never be offered", id, template.prerequisiteId);
                } else if (!template.oncePerPlayer && type != QuestType.COMMON) {
                    // Common quests are maintained as a fixed ladder rather than drawn by
                    // choose(), and completed rungs are withdrawn there, so oncePerPlayer
                    // is meaningless for them and its absence is not a mistake.
                    log.warn("Quest template {} is a chain stage but is not oncePerPlayer, "
                            + "so a finished chain can be offered again", id);
                }
            }
        }
        }
        warnAboutChainCycles(templates, ids);
    }

    /** A prerequisite loop leaves every stage in it permanently unreachable. */
    private void warnAboutChainCycles(List<Template> templates, Set<String> ids) {
        Map<String, String> prerequisites = new LinkedHashMap<>();
        for (Template template : templates) {
            if (template == null || template.id == null || template.id.isBlank()) continue;
            if (template.prerequisiteId != null && !template.prerequisiteId.isBlank()
                    && ids.contains(template.prerequisiteId)) {
                prerequisites.putIfAbsent(template.id, template.prerequisiteId);
            }
        }
        for (String start : prerequisites.keySet()) {
            Set<String> seen = new java.util.LinkedHashSet<>();
            String cursor = start;
            while (cursor != null && seen.add(cursor)) {
                cursor = prerequisites.get(cursor);
            }
            if (cursor != null) {
                AegisAscensionMod.getLogger().warn(
                        "Quest template {} sits in a prerequisite cycle; no stage of it can "
                        + "ever be offered", start);
            }
        }
    }

    /**
     * Reads one template file per quest type. A file that is missing or unreadable leaves
     * that type's list untouched rather than emptying it, so one bad file costs the player
     * one quest type instead of the whole catalogue.
     */
    private void loadTemplateFiles() {
        for (Map.Entry<QuestType, String> entry : TEMPLATE_FILES.entrySet()) {
            Path path = QUEST_DIRECTORY.resolve(entry.getValue());
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                List<Template> loaded = GSON.fromJson(reader,
                        new TypeToken<List<Template>>() { }.getType());
                if (loaded == null) continue;
                loaded.removeIf(template -> template == null);
                setTemplates(entry.getKey(), loaded);
            } catch (Exception exception) {
                AegisAscensionMod.getLogger().error(
                        "Failed to read quest templates from {}; {} quests will be "
                        + "unavailable this session", path, entry.getKey(), exception);
            }
        }
    }

    private void setTemplates(QuestType type, List<Template> loaded) {
        // Any index built from the previous contents is now wrong.
        templateIndex = null;
        switch (type) {
            case DAILY -> dailyTemplates = loaded;
            case CHALLENGE -> challengeTemplates = loaded;
            case COMMON -> commonTemplates = loaded;
            case CHUNK -> chunkTemplates = loaded;
            case SIDE -> sideTemplates = loaded;
        }
    }

    /**
     * Adds the composed side quests to the catalogue, after the authored ones so a
     * hand-written template always wins an id collision.
     */
    private void expandGeneratedSideQuests() {
        if (!generateRandomSideQuests) return;
        Path path = QUEST_DIRECTORY.resolve(GENERATED_FILE);
        GeneratedQuests recipe;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            recipe = GSON.fromJson(reader, GeneratedQuests.class);
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error(
                    "Failed to read generated side quests from {}; only the authored "
                    + "templates will be offered", path, exception);
            return;
        }
        if (recipe == null) return;
        Set<String> authored = new java.util.LinkedHashSet<>();
        for (Template template : sideTemplates) {
            if (template != null && template.id != null) authored.add(template.id);
        }
        int added = 0;
        for (Template generated : recipe.expand()) {
            if (authored.contains(generated.id)) continue;
            sideTemplates.add(generated);
            added++;
        }
        AegisAscensionMod.getLogger().info(
                "Composed {} generated side quest(s) from {}", added, GENERATED_FILE);
    }

    /** Copies any default file the config directory does not already have. */
    private static void copyDefaultsIfAbsent() throws Exception {
        Files.createDirectories(FILE.getParent());
        copyResourceIfAbsent("/assets/aegis_ascension/questsetting.json", FILE);
        Files.createDirectories(QUEST_DIRECTORY);
        for (String name : TEMPLATE_FILES.values()) {
            copyResourceIfAbsent("/assets/aegis_ascension/quests/" + name,
                    QUEST_DIRECTORY.resolve(name));
        }
        copyResourceIfAbsent("/assets/aegis_ascension/quests/" + GENERATED_FILE,
                QUEST_DIRECTORY.resolve(GENERATED_FILE));
    }

    private static void copyResourceIfAbsent(String resource, Path destination)
            throws Exception {
        if (Files.exists(destination)) return;
        try (var stream = QuestConfig.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing default " + resource);
            Files.copy(stream, destination);
        }
    }

    private static QuestConfig load() {
        try {
            copyDefaultsIfAbsent();
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                QuestConfig config = GSON.fromJson(reader, QuestConfig.class);
                if (config != null) {
                    config.loadTemplateFiles();
                    if (config.dailyTemplates == null) config.dailyTemplates = new ArrayList<>();
                    if (config.challengeTemplates == null) config.challengeTemplates = new ArrayList<>();
                    if (config.commonTemplates == null) config.commonTemplates = new ArrayList<>();
                    if (config.chunkTemplates == null) config.chunkTemplates = new ArrayList<>();
                    if (config.sideTemplates == null) config.sideTemplates = new ArrayList<>();
                    if (config.rewardPools == null) config.rewardPools = new RewardPools();
                    if (config.questTiers == null || config.questTiers.isEmpty()) {
                        config.questTiers = defaultQuestTiers();
                    }
                    config.expandGeneratedSideQuests();
                    config.validate();
                    return config;
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error("Failed to read {}, using empty quest catalogue", FILE, exception);
        }
        return new QuestConfig();
    }
}
