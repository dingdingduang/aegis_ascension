package com.whatever.aegis_ascension.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server-only, editable quest catalogue. The client receives rolled quests only. */
public final class QuestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformServices.paths().modConfigDirectory(AegisAscensionMod.MOD_ID)
            .resolve("questsetting.json");
    private static QuestConfig instance;

    public int dailyMin = 1;
    public int dailyMax = 5;
    public int dailyRefreshIntervalHours = 24;
    /** Whether newly generated daily quests should begin Active instead of Available. */
    public boolean dailyAutoAccept = true;
    public int challengeMin = 1;
    public int challengeMax = 5;
    public int challengeRefreshIntervalHours = 24;
    public int challengeSecurityDepositExperience = 250;
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
        for (Template template : allTemplates()) {
            if (template != null && template.id.equals(base)) return template;
        }
        return null;
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
        /** Minimum configured progression level (Minecraft level or AAE rank). */
        public int minimumRank;
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
        public List<RewardSpec> rewards = new ArrayList<>();
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

        public QuestDefinition definition() {
            return new QuestDefinition(id, null, objective, title, description, targetId,
                    target, experience, 0L, resolveFixedRewards(), story, profession,
                    prerequisiteId, icon);
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

    public static final class RewardSpec {
        /** item, virtual, shop_item, random_common, random_unique, or random_curio. */
        public String kind = "item";
        public String id = "minecraft:bread";
        /** Common or Discovery; used by shop_item. */
        public String source = "common";
        /** Explicit real-item fallback when a shop_item tier has no eligible candidate. */
        public String fallbackId = "";
        public int count = 1;
        public String tier = "R";
        public boolean unique = false;
    }

    private static QuestConfig load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (var stream = QuestConfig.class.getResourceAsStream("/assets/aegis_ascension/questsetting.json")) {
                    if (stream == null) throw new IllegalStateException("Missing default questsetting.json");
                    Files.copy(stream, FILE);
                }
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                QuestConfig config = GSON.fromJson(reader, QuestConfig.class);
                if (config != null) {
                    if (config.dailyTemplates == null) config.dailyTemplates = new ArrayList<>();
                    if (config.challengeTemplates == null) config.challengeTemplates = new ArrayList<>();
                    if (config.commonTemplates == null) config.commonTemplates = new ArrayList<>();
                    if (config.chunkTemplates == null) config.chunkTemplates = new ArrayList<>();
                    if (config.sideTemplates == null) config.sideTemplates = new ArrayList<>();
                    if (config.rewardPools == null) config.rewardPools = new RewardPools();
                    return config;
                }
            }
        } catch (Exception exception) {
            AegisAscensionMod.getLogger().error("Failed to read {}, using empty quest catalogue", FILE, exception);
        }
        return new QuestConfig();
    }
}
