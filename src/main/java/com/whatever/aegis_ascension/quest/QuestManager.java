package com.whatever.aegis_ascension.quest;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.shop.ShopGenerator;
import com.whatever.aegis_ascension.shop.ShopType;
import com.whatever.aegis_ascension.storage.PlayerStorage;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative generation, progress, deposits, penalties, and rewards. */
public final class QuestManager {
    private static final int MAX_RANDOM_QUESTS = 5;
    private static final int MAX_TEMPLATE_WEIGHT = 100_000;
    private static final int TICKS_PER_SECOND = 20;
    /** Rejects teleports and other discontinuous position changes as walked distance. */
    private static final double MAX_WALK_DISTANCE_PER_TICK = 8.0D;
    /** Last end-of-tick server position sampled by this mod, keyed by player UUID. */
    private static final Map<UUID, WalkSample> WALK_SAMPLES = new HashMap<>();
    /** Fractional blocks walked between server ticks, keyed by player UUID. */
    private static final Map<UUID, Double> WALK_REMAINDERS = new HashMap<>();
    /** Progress-only changes waiting for their next compact per-player flush. */
    private static final Map<UUID, PendingProgressSync> PENDING_PROGRESS_SYNCS =
            new HashMap<>();

    private QuestManager() {}

    public static boolean tick(ServerPlayer player, PlayerPerkData data) {
        QuestConfig config = QuestConfig.get();
        QuestState state = data.getQuestState();
        long now = player.serverLevel().getGameTime();
        boolean changed = false;

        long dailyTicks = config.dailyRefreshTicks();
        long dailyIndex = now / dailyTicks;
        if (state.dayIndex() != dailyIndex) {
            state.setDayIndex(dailyIndex);
            state.clearDaily();
            generateDaily(player, data, nextBoundary(dailyIndex, dailyTicks));
            changed = true;
        }

        long challengeTicks = config.challengeRefreshTicks();
        long challengeIndex = now / challengeTicks;
        if (state.challengeRefreshIndex() != challengeIndex) {
            changed |= expireChallenges(player, data, now);
            state.setChallengeRefreshIndex(challengeIndex);
            state.clearChallenges();
            generateChallenges(player, data, nextBoundary(challengeIndex, challengeTicks));
            changed = true;
        } else {
            changed |= expireChallenges(player, data, now);
        }

        long sideTicks = config.sideRefreshTicks();
        long sideIndex = now / sideTicks;
        if (state.sideRefreshIndex() != sideIndex) {
            state.setSideRefreshIndex(sideIndex);
            state.clearSide();
            generateSide(player, data, nextBoundary(sideIndex, sideTicks));
            changed = true;
        }

        if (state.common().isEmpty() && !config.commonTemplates.isEmpty()) {
            for (QuestConfig.Template template : config.commonTemplates) {
                state.addCommon(generatedProgress(template, QuestType.COMMON, player,
                        data, 0L, config.commonAutoAccept));
            }
            changed = true;
        }
        changed |= maintainRepeatableCommon(player, data, now);

        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        if (state.chunks().isEmpty()) {
            generateChunks(player, data, chunkX, chunkZ);
            changed = true;
        } else if (allTerminal(state.chunks())
                && (state.lastChunkX() == Long.MIN_VALUE
                || Math.max(Math.abs(chunkX - state.lastChunkX()),
                Math.abs(chunkZ - state.lastChunkZ())) >= 16)) {
            state.clearChunks();
            generateChunks(player, data, chunkX, chunkZ);
            changed = true;
        }
        return changed;
    }

    private static long nextBoundary(long index, long intervalTicks) {
        return (index + 1L) * intervalTicks;
    }

    private static void generateDaily(ServerPlayer player, PlayerPerkData data,
                                      long expiresAt) {
        QuestConfig config = QuestConfig.get();
        for (QuestConfig.Template template : choose(player, data, config.dailyTemplates,
                config.dailyMin, config.dailyMax)) {
            data.getQuestState().addDaily(generatedProgress(template, QuestType.DAILY, player,
                    data, expiresAt, config.dailyAutoAccept));
        }
    }

    private static void generateChallenges(ServerPlayer player, PlayerPerkData data,
                                           long expiresAt) {
        QuestConfig config = QuestConfig.get();
        for (QuestConfig.Template template : choose(player, data, config.challengeTemplates,
                config.challengeMin, config.challengeMax)) {
            data.getQuestState().addChallenge(new QuestProgress(
                    roll(template, QuestType.CHALLENGE, player, data), false, expiresAt));
        }
    }

    private static void generateChunks(ServerPlayer player, PlayerPerkData data,
                                       int chunkX, int chunkZ) {
        QuestConfig config = QuestConfig.get();
        for (QuestConfig.Template template : choose(player, data, config.chunkTemplates,
                config.chunkMin, config.chunkMax)) {
            QuestProgress progress = generatedProgress(template, QuestType.CHUNK,
                    player, data, 0L, config.chunkAutoAccept);
            progress.setOriginChunk(chunkX, chunkZ);
            data.getQuestState().addChunk(progress);
        }
        data.getQuestState().setLastChunk(chunkX, chunkZ);
    }

    private static void generateSide(ServerPlayer player, PlayerPerkData data,
                                     long expiresAt) {
        QuestConfig config = QuestConfig.get();
        for (QuestConfig.Template template : choose(player, data, config.sideTemplates,
                config.sideMin, config.sideMax)) {
            data.getQuestState().addSide(generatedProgress(template, QuestType.SIDE, player,
                    data, expiresAt, false));
        }
    }

    private static QuestProgress generatedProgress(QuestConfig.Template template,
                                                   QuestType type, ServerPlayer player,
                                                   PlayerPerkData data, long expiresAt,
                                                   boolean autoAccept) {
        int completions = template.repeatable && type == QuestType.COMMON
                ? data.getQuestState().completionCount(rolledId(template, type)) : 0;
        QuestDefinition definition = roll(template, type, player, data, completions);
        // Auto-accept applies only when the quest is currently unlocked. Prerequisite
        // chains therefore keep later common/side stages locked until their prior stage
        // has been completed, while unlocked quests start Active immediately.
        boolean accepted = autoAccept && autoAcceptEnabled(data, type)
                && prerequisiteMet(data, definition);
        return new QuestProgress(definition, accepted, expiresAt);
    }

    private static boolean autoAcceptEnabled(PlayerPerkData data, QuestType type) {
        if (!data.getQuestState().autoAcceptEligibleQuests()) return false;
        QuestConfig config = QuestConfig.get();
        return switch (type) {
            case DAILY -> config.dailyAutoAccept;
            case COMMON -> config.commonAutoAccept;
            case CHUNK -> config.chunkAutoAccept;
            case CHALLENGE, SIDE -> false;
        };
    }

    /** Updates one player's server-owned preference without abandoning active quests. */
    public static boolean setAutoAcceptEligibleQuests(ServerPlayer player, boolean enabled) {
        PlayerPerkData data = PerkData.of(player);
        QuestState state = data.getQuestState();
        if (state.autoAcceptEligibleQuests() == enabled) return false;
        state.setAutoAcceptEligibleQuests(enabled);
        if (enabled) acceptAvailableEligibleQuests(player, data);
        return true;
    }

    private static void acceptAvailableEligibleQuests(ServerPlayer player,
                                                      PlayerPerkData data) {
        long now = player.serverLevel().getGameTime();
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            if (!autoAcceptEnabled(data, quest.definition().type())
                    || quest.accepted() || quest.completed() || quest.cancelled()
                    || quest.expired()
                    || (quest.expiresAt() > 0L && quest.expiresAt() <= now)
                    || !prerequisiteMet(data, quest.definition())) {
                continue;
            }
            quest.accept();
        }
    }

    private static List<QuestConfig.Template> choose(ServerPlayer player,
                                                     PlayerPerkData data,
                                                     List<QuestConfig.Template> templates,
                                                     int configuredMin, int configuredMax) {
        List<QuestConfig.Template> pool = new ArrayList<>(
                templates == null ? List.of() : templates);
        int progressionRank = AegisExperienceSystem.effectiveLevel(player, data);
        ResourceLocation dimension = player.serverLevel().dimension().location();
        ResourceLocation biome = player.serverLevel().getBiome(player.blockPosition())
                .unwrapKey().map(key -> key.location()).orElse(null);
        pool.removeIf(template -> template == null || template.id == null
                || template.id.isBlank() || templateWeight(template) <= 0
                || progressionRank < Math.max(0, template.minimumRank)
                || !matchesLocation(template.dimensions, dimension)
                || !matchesLocation(template.biomes, biome));
        if (pool.isEmpty()) return List.of();
        int max = Math.min(pool.size(), Math.min(MAX_RANDOM_QUESTS,
                Math.max(1, configuredMax)));
        int min = Math.min(max, Math.min(MAX_RANDOM_QUESTS,
                Math.max(1, configuredMin)));
        int count = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
        List<QuestConfig.Template> selected = new ArrayList<>(count);
        while (selected.size() < count && !pool.isEmpty()) {
            selected.add(removeWeighted(player, pool));
        }
        return selected;
    }

    private static QuestConfig.Template removeWeighted(ServerPlayer player,
                                                       List<QuestConfig.Template> pool) {
        long totalWeight = 0L;
        for (QuestConfig.Template template : pool) totalWeight += templateWeight(template);
        long roll = Math.floorMod(player.getRandom().nextLong(), totalWeight);
        long cursor = 0L;
        for (int index = 0; index < pool.size(); index++) {
            cursor += templateWeight(pool.get(index));
            if (roll < cursor) return pool.remove(index);
        }
        return pool.remove(pool.size() - 1);
    }

    private static int templateWeight(QuestConfig.Template template) {
        return Math.max(0, Math.min(MAX_TEMPLATE_WEIGHT, template.weight));
    }

    private static boolean matchesLocation(List<String> configured,
                                           ResourceLocation current) {
        if (configured == null || configured.isEmpty()) return true;
        if (current == null) return false;
        for (String value : configured) {
            if (value == null || value.isBlank()) continue;
            ResourceLocation location = ResourceLocation.tryParse(value);
            if (current.equals(location)) return true;
        }
        return false;
    }

    private static QuestDefinition roll(QuestConfig.Template template, QuestType type,
                                        ServerPlayer player, PlayerPerkData data) {
        return roll(template, type, player, data, 0);
    }

    private static QuestDefinition roll(QuestConfig.Template template, QuestType type,
                                        ServerPlayer player, PlayerPerkData data,
                                        int completedCycles) {
        double rewardMultiplier = rewardMultiplier(template, completedCycles);
        List<QuestDefinition.Reward> rewards = new ArrayList<>();
        Set<String> reservedUniqueItems = reservedUniqueItemKeys(data);
        if (template.rewards != null) for (QuestConfig.RewardSpec spec : template.rewards) {
            if (spec == null) continue;
            String kind = spec.kind == null ? "item"
                    : spec.kind.toLowerCase(java.util.Locale.ROOT);
            String id = spec.id == null ? "" : spec.id;
            boolean unique = spec.unique || "random_unique".equals(kind);
            boolean fallbackUsed = false;
            if ("random_common".equals(kind)) {
                id = pickShopReward(player, data, reservedUniqueItems,
                        ShopType.COMMON, spec.tier, unique);
                if (id.isBlank()) {
                    id = pickLegacyReward(QuestConfig.get().rewardPools.commonItems,
                            player, data, reservedUniqueItems, unique);
                }
            } else if ("random_unique".equals(kind)) {
                id = pickShopReward(player, data, reservedUniqueItems,
                        ShopType.DISCOVERY, GeneralConstants.TIER_SSR, true);
                if (id.isBlank()) {
                    id = pickLegacyReward(QuestConfig.get().rewardPools.uniqueItems,
                            player, data, reservedUniqueItems, true);
                }
            } else if ("shop_item".equals(kind)) {
                ShopType source = "discovery".equalsIgnoreCase(spec.source)
                        ? ShopType.DISCOVERY : ShopType.COMMON;
                id = pickShopReward(player, data, reservedUniqueItems,
                        source, spec.tier, unique);
                if (id.isBlank()) {
                    id = canonicalItemId(spec.fallbackId);
                    fallbackUsed = !id.isBlank();
                }
            } else if ("random_curio".equals(kind)) {
                id = pickLegacyReward(QuestConfig.get().rewardPools.curioItems,
                        player, data, reservedUniqueItems, unique);
            }
            if (id.isBlank()) continue;
            // fallbackId is a guaranteed consolation item rather than another unique
            // draw; otherwise a previously claimed fallback could still yield nothing.
            boolean rewardUnique = unique && !fallbackUsed;
            if (rewardUnique && !"virtual".equals(kind)) {
                String key = "item:" + id;
                if (data.getQuestState().hasClaimedUnique(key)
                        || !reservedUniqueItems.add(key)) {
                    continue;
                }
            }
            rewards.add(new QuestDefinition.Reward(
                    "virtual".equals(kind) ? "" : id,
                    "virtual".equals(kind) ? id : "",
                    scaledValue(spec.count, rewardMultiplier), spec.tier, rewardUnique));
        }

        String targetId = template.targetId == null ? "" : template.targetId;
        if (template.targetIds != null && !template.targetIds.isEmpty()) {
            targetId = pick(template.targetIds, player);
        }
        int target = Math.max(1, template.target);
        if (template.targetMin > 0 || template.targetMax > 0) {
            int min = Math.max(1, template.targetMin > 0 ? template.targetMin : target);
            int max = Math.max(min, template.targetMax > 0 ? template.targetMax : min);
            target = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
        }
        if (template.repeatable && type == QuestType.COMMON) {
            int increaseEvery = Math.max(1, template.targetIncreaseEvery);
            long increases = Math.max(0, completedCycles) / increaseEvery;
            long scaledTarget = target + increases * Math.max(0L,
                    template.targetIncreaseAmount);
            int maximumTarget = template.maximumTarget > 0
                    ? Math.max(target, template.maximumTarget) : Integer.MAX_VALUE;
            target = (int) Math.min(maximumTarget, Math.min(Integer.MAX_VALUE, scaledTarget));
        }
        int experience = scaledValue(template.experience, rewardMultiplier);
        long goldReward = randomGoldReward(template, rewardMultiplier, player);
        return new QuestDefinition(rolledId(template, type), type,
                template.objective, template.title, template.description, targetId,
                target, experience, goldReward, rewards, template.story,
                template.profession, template.prerequisiteId, template.icon);
    }

    private static long randomGoldReward(QuestConfig.Template template,
                                         double rewardMultiplier, ServerPlayer player) {
        QuestConfig config = QuestConfig.get();
        long min = template.goldRewardMin > 0L
                ? template.goldRewardMin : config.goldRewardMin;
        long max = template.goldRewardMax > 0L
                ? template.goldRewardMax : config.goldRewardMax;
        min = Math.max(0L, min);
        max = Math.max(min, max);
        if (max == 0L) return 0L;
        long value;
        if (max == min) {
            value = min;
        } else {
            long range = max - min;
            long offset = range >= Integer.MAX_VALUE
                    ? (long) Math.floor(player.getRandom().nextDouble()
                    * (double) range)
                    : player.getRandom().nextInt((int) range + 1);
            value = min + Math.min(range, Math.max(0L, offset));
        }
        if (rewardMultiplier <= 1.0D) return value;
        double scaled = value * rewardMultiplier;
        return !Double.isFinite(scaled) || scaled >= Long.MAX_VALUE
                ? Long.MAX_VALUE : Math.max(0L, Math.round(scaled));
    }

    private static String rolledId(QuestConfig.Template template, QuestType type) {
        return template.id + "#" + type.name().toLowerCase();
    }

    private static double rewardMultiplier(QuestConfig.Template template,
                                           int completedCycles) {
        if (!template.repeatable) return 1.0D;
        int increaseEvery = Math.max(1, template.rewardIncreaseEvery);
        int increases = Math.max(0, completedCycles) / increaseEvery;
        double multiplier = 1.0D + increases
                * Math.max(0.0D, template.rewardMultiplierIncrease);
        double maximum = Math.max(1.0D, template.maximumRewardMultiplier);
        return Math.min(maximum, multiplier);
    }

    private static int scaledValue(int base, double multiplier) {
        if (base <= 0) return 0;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                Math.round(base * multiplier)));
    }

    private static String pick(List<String> values, ServerPlayer player) {
        return values == null || values.isEmpty() ? ""
                : values.get(player.getRandom().nextInt(values.size()));
    }

    private static String pickShopReward(ServerPlayer player, PlayerPerkData data,
                                         Set<String> reservedUniqueItems,
                                         ShopType source, String tier, boolean unique) {
        return ShopGenerator.rollRewardItem(player.getRandom(), source, tier, item ->
                        !unique || isUniqueItemAvailable(data, reservedUniqueItems, item))
                .map(QuestManager::canonicalItemId)
                .orElse("");
    }

    private static String pickLegacyReward(List<String> values, ServerPlayer player,
                                           PlayerPerkData data,
                                           Set<String> reservedUniqueItems,
                                           boolean unique) {
        if (values == null || values.isEmpty()) return "";
        List<String> eligible = new ArrayList<>();
        for (String value : values) {
            ResourceLocation location = ResourceLocation.tryParse(value);
            Item item = location == null ? null : GeneralServerMethods.resolveItem(location);
            if (item == null || (unique
                    && !isUniqueItemAvailable(data, reservedUniqueItems, item))) {
                continue;
            }
            eligible.add(canonicalItemId(item));
        }
        return pick(eligible, player);
    }

    private static boolean isUniqueItemAvailable(PlayerPerkData data,
                                                 Set<String> reservedUniqueItems,
                                                 Item item) {
        String id = canonicalItemId(item);
        if (id.isBlank()) return false;
        String key = "item:" + id;
        return !data.getQuestState().hasClaimedUnique(key)
                && !reservedUniqueItems.contains(key);
    }

    private static Set<String> reservedUniqueItemKeys(PlayerPerkData data) {
        Set<String> result = new LinkedHashSet<>();
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            if (quest.completed() || quest.cancelled() || quest.expired()) continue;
            for (QuestDefinition.Reward reward : quest.definition().rewards()) {
                if (reward.unique() && !reward.isVirtual() && !reward.itemId().isBlank()) {
                    result.add("item:" + reward.itemId());
                }
            }
        }
        return result;
    }

    private static String canonicalItemId(String configuredId) {
        ResourceLocation location = ResourceLocation.tryParse(
                configuredId == null ? "" : configuredId.trim());
        Item item = location == null ? null : GeneralServerMethods.resolveItem(location);
        return item == null ? "" : canonicalItemId(item);
    }

    private static String canonicalItemId(Item item) {
        ResourceLocation location = item == null ? null : GeneralServerMethods.getItemKey(item);
        return location == null ? "" : location.toString();
    }

    public static boolean onKill(ServerPlayer player, LivingEntity killed) {
        return advance(player, QuestObjective.KILL,
                GeneralServerMethods.getEntityTypeKey(killed.getType()).toString(), 1);
    }

    public static boolean onPlant(ServerPlayer player, BlockState state) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock());
        return advance(player, QuestObjective.PLANT, key.toString(), 1);
    }

    /**
     * Samples the player's authoritative server position once at the end of each tick.
     *
     * <p>{@code Entity#xo}/{@code zo} cannot be used here: normal client movement packets
     * may update those fields before Forge's END player-tick event runs, making ordinary
     * walking appear stationary. Keeping our own previous sample makes horizontal walking
     * reliable without requiring a client movement packet. Dimension changes and large
     * discontinuities only rebase the sample, so they do not advance a quest.</p>
     */
    public static boolean sampleWalkMovement(ServerPlayer player, PlayerPerkData data) {
        UUID playerId = player.getUUID();
        if (!hasActiveObjective(data, QuestObjective.WALK)) {
            WALK_SAMPLES.remove(playerId);
            WALK_REMAINDERS.remove(playerId);
            return false;
        }

        WalkSample current = new WalkSample(player.serverLevel().dimension().location(),
                player.getX(), player.getZ());
        WalkSample previous = WALK_SAMPLES.put(playerId, current);
        if (previous == null || !previous.dimension().equals(current.dimension())) {
            WALK_REMAINDERS.remove(playerId);
            return false;
        }

        double distance = Math.hypot(current.x() - previous.x(),
                current.z() - previous.z());
        if (!Double.isFinite(distance) || !(distance > 0.0D)
                || distance > MAX_WALK_DISTANCE_PER_TICK) {
            if (!Double.isFinite(distance) || distance > MAX_WALK_DISTANCE_PER_TICK) {
                WALK_REMAINDERS.remove(playerId);
            }
            return false;
        }

        double total = WALK_REMAINDERS.getOrDefault(playerId, 0.0D) + distance;
        int wholeBlocks = (int) Math.floor(total);
        WALK_REMAINDERS.put(playerId, total - wholeBlocks);
        return wholeBlocks > 0 && advance(player, QuestObjective.WALK, "", wholeBlocks);
    }

    private static boolean hasActiveObjective(PlayerPerkData data,
                                              QuestObjective objective) {
        for (QuestProgress progress : allQuests(data.getQuestState())) {
            if (progress.accepted() && !progress.completed() && !progress.cancelled()
                    && !progress.expired() && progress.definition().objective() == objective) {
                return true;
            }
        }
        return false;
    }

    public static boolean onChestOpened(ServerPlayer player, BlockPos pos,
                                        boolean unopenedGeneratedLoot) {
        return advance(player, QuestObjective.OPEN_CHEST, "", 1,
                unopenedGeneratedLoot);
    }

    public static boolean onBiomeVisited(ServerPlayer player) {
        String id = player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        return advance(player, QuestObjective.EXPLORE_BIOME, id, 1);
    }

    private static boolean advance(ServerPlayer player, QuestObjective objective,
                                   String targetId, int amount) {
        return advance(player, objective, targetId, amount, false);
    }

    private static boolean advance(ServerPlayer player, QuestObjective objective,
                                   String targetId, int amount,
                                   boolean unopenedGeneratedLoot) {
        if (amount <= 0) return false;
        PlayerPerkData data = PerkData.of(player);
        QuestState state = data.getQuestState();
        boolean changed = false;
        boolean rewarded = false;
        for (QuestProgress progress : allQuests(state)) {
            QuestDefinition definition = progress.definition();
            if (!progress.accepted() || progress.completed()
                    || progress.cancelled() || progress.expired()) continue;
            if (definition.type() == QuestType.CHUNK
                    && Math.max(Math.abs(player.chunkPosition().x - progress.originChunkX()),
                    Math.abs(player.chunkPosition().z - progress.originChunkZ())) > 8) {
                continue;
            }
            if ((definition.type() == QuestType.DAILY
                    || definition.type() == QuestType.CHUNK)
                    && definition.objective() == QuestObjective.OPEN_CHEST
                    && !unopenedGeneratedLoot) {
                continue;
            }
            if (definition.objective() != objective
                    || !matches(definition.targetId(), targetId)) continue;
            boolean beforeComplete = progress.completed();
            int before = progress.progress();
            boolean repeatable = isRepeatable(definition);
            boolean rewardReady = !repeatable
                    || player.serverLevel().getGameTime() >= progress.nextRepeatRewardAt();
            progress.addProgress(amount, rewardReady);
            if (progress.progress() != before || progress.completed() != beforeComplete) {
                changed = true;
            }
            if (progress.completed() && !beforeComplete) {
                grantRewards(player, data, progress);
                rewarded = true;
            } else if (progress.progress() != before) {
                queueProgressSync(player, definition.id());
            }
        }
        if (rewarded) {
            syncRewardState(player);
            // Build the full mirror after every matching quest has advanced, so one event
            // can never send a completion snapshot that omits a later quest's new counter.
            ModNetworking.syncQuestsTo(player);
        }
        return changed;
    }

    private static boolean matches(String configured, String actual) {
        return configured == null || configured.isBlank() || configured.equals(actual);
    }

    /**
     * Marks one quest counter for a compact, delayed sync. Multiple hot events during
     * the interval collapse into one update containing only the latest values.
     */
    private static void queueProgressSync(ServerPlayer player, String questId) {
        if (questId == null || questId.isBlank()) return;
        UUID playerId = player.getUUID();
        PendingProgressSync pending = PENDING_PROGRESS_SYNCS.computeIfAbsent(playerId,
                ignored -> new PendingProgressSync(saturatedAdd(
                        player.serverLevel().getGameTime(), progressSyncIntervalTicks())));
        pending.questIds.add(questId);
    }

    private static long progressSyncIntervalTicks() {
        double seconds = PlatformServices.config().questProgressSyncIntervalSeconds();
        if (!Double.isFinite(seconds)) seconds = 0.5D;
        return Math.max(1L, (long) Math.ceil(Math.max(0.0D, seconds)
                * TICKS_PER_SECOND));
    }

    /** Flushes this player's pending counter batch when its configurable deadline arrives. */
    public static void flushPendingProgressSync(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PendingProgressSync pending = PENDING_PROGRESS_SYNCS.get(playerId);
        if (pending == null
                || player.serverLevel().getGameTime() < pending.flushAt) return;
        PENDING_PROGRESS_SYNCS.remove(playerId);
        ModNetworking.syncQuestProgressTo(player, Set.copyOf(pending.questIds));
    }

    /** A full quest snapshot supersedes every counter waiting in the compact queue. */
    public static void clearPendingProgressSync(ServerPlayer player) {
        PENDING_PROGRESS_SYNCS.remove(player.getUUID());
    }

    /** Rebases quest movement after respawning or crossing dimensions. */
    public static void resetWalkTracking(ServerPlayer player) {
        UUID playerId = player.getUUID();
        WALK_SAMPLES.remove(playerId);
        WALK_REMAINDERS.remove(playerId);
    }

    /** Frees per-session movement and packet-batching state when a player leaves. */
    public static void clearTransientState(ServerPlayer player) {
        UUID playerId = player.getUUID();
        resetWalkTracking(player);
        PENDING_PROGRESS_SYNCS.remove(playerId);
    }

    /** Frees transient state when the logical server stops. */
    public static void clearTransientState() {
        WALK_SAMPLES.clear();
        WALK_REMAINDERS.clear();
        PENDING_PROGRESS_SYNCS.clear();
    }

    public static boolean accept(ServerPlayer player, String id) {
        PlayerPerkData data = PerkData.of(player);
        QuestProgress quest = find(data, id);
        if (quest == null || quest.accepted() || quest.completed()
                || quest.cancelled() || quest.expired()
                || (quest.expiresAt() > 0L
                && quest.expiresAt() <= player.serverLevel().getGameTime())
                || !prerequisiteMet(data, quest.definition())) return false;
        if (quest.definition().type() == QuestType.CHALLENGE) {
            int deposit = Math.max(0, QuestConfig.get().challengeSecurityDepositExperience);
            if (GoldCurrency.enabled()) {
                if (!GoldCurrency.canAfford(data, deposit)) return false;
                if (deposit > 0) GoldCurrency.trySpend(data, deposit);
            } else {
                if (player.totalExperience < deposit) return false;
                if (deposit > 0) player.giveExperiencePoints(-deposit);
            }
            quest.accept(deposit);
        } else {
            quest.accept();
        }
        return true;
    }

    public static boolean cancel(ServerPlayer player, String id) {
        PlayerPerkData data = PerkData.of(player);
        QuestProgress quest = find(data, id);
        if (quest == null || !quest.accepted() || quest.completed()
                || quest.cancelled() || quest.expired()) return false;
        if (quest.definition().type() == QuestType.COMMON) {
            quest.makeAvailableAgain();
        } else {
            // Challenge deposits were already paid on acceptance and are deliberately
            // not returned by cancellation. Other refreshable quests are abandoned.
            quest.cancel();
        }
        return true;
    }

    /** Atomically consumes a Side Quest's requested item from Storage and inventory. */
    public static boolean submit(ServerPlayer player, String id) {
        PlayerPerkData data = PerkData.of(player);
        QuestProgress quest = find(data, id);
        if (quest == null || !quest.accepted() || quest.completed()
                || quest.cancelled() || quest.expired()
                || (quest.definition().objective() != QuestObjective.TRADE_ITEM
                && quest.definition().objective() != QuestObjective.GIVE_MATERIAL)) {
            return false;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(quest.definition().targetId());
        Item item = itemId == null ? null : GeneralServerMethods.resolveItem(itemId);
        if (item == null) return false;
        int required = Math.max(0, quest.definition().target() - quest.progress());
        PlayerStorage storage = data.getStorage();
        long available = saturatedAdd(storage.countItem(item), countInventory(player, item));
        if (available < required) {
            player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                    "message.aegis_ascension.quest.side.insufficient_items",
                    required, new ItemStack(item).getHoverName(), available));
            return false;
        }

        long removedFromStorage = storage.removeItem(item, required);
        int remaining = (int) Math.max(0L, required - removedFromStorage);
        if (remaining > 0) removeFromInventory(player, item, remaining);
        quest.addProgress(required);
        if (quest.completed()) {
            grantRewards(player, data, quest);
            syncRewardState(player);
        }
        // Successful submission is a structural change and is synchronized here so
        // callers can avoid sending a second identical full snapshot.
        ModNetworking.syncQuestsTo(player);
        return true;
    }

    private static long countInventory(ServerPlayer player, Item item) {
        long count = 0L;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void removeFromInventory(ServerPlayer player, Item item, int amount) {
        int remaining = Math.max(0, amount);
        for (int slot = 0; slot < player.getInventory().getContainerSize()
                && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }
        player.getInventory().setChanged();
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static QuestProgress find(PlayerPerkData data, String id) {
        if (id == null) return null;
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            if (id.equals(quest.definition().id())) return quest;
        }
        return null;
    }

    private static List<QuestProgress> allQuests(QuestState state) {
        List<QuestProgress> result = new ArrayList<>();
        result.addAll(state.daily());
        result.addAll(state.challenges());
        result.addAll(state.common());
        result.addAll(state.chunks());
        result.addAll(state.side());
        return result;
    }

    /** Resolves current authoritative counters for a compact delta packet. */
    public static Map<String, Integer> progressValues(PlayerPerkData data,
                                                      Set<String> questIds) {
        if (questIds == null || questIds.isEmpty()) return Map.of();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            String id = quest.definition().id();
            if (questIds.contains(id)) result.put(id, quest.progress());
        }
        return result;
    }

    private static boolean allTerminal(List<QuestProgress> quests) {
        return !quests.isEmpty() && quests.stream().allMatch(quest ->
                quest.completed() || quest.cancelled() || quest.expired());
    }

    private static boolean maintainRepeatableCommon(ServerPlayer player,
                                                    PlayerPerkData data,
                                                    long now) {
        boolean structuralChanged = false;
        boolean rewarded = false;
        QuestState state = data.getQuestState();
        for (QuestProgress progress : state.common()) {
            QuestConfig.Template template = QuestConfig.get()
                    .template(progress.definition().id());
            if (template == null || !template.repeatable) continue;

            if (progress.completed()) {
                if (now < progress.repeatResetAt()) continue;
                int completions = state.completionCount(progress.definition().id());
                QuestDefinition next = roll(template, QuestType.COMMON,
                        player, data, completions);
                boolean accepted = autoAcceptEnabled(data, QuestType.COMMON)
                        && prerequisiteMet(data, next);
                progress.restart(next, accepted);
                structuralChanged = true;
                continue;
            }

            if (progress.accepted() && !progress.cancelled() && !progress.expired()
                    && progress.progress() >= progress.definition().target()
                    && now >= progress.nextRepeatRewardAt()
                    && progress.completeIfReady()) {
                grantRewards(player, data, progress);
                structuralChanged = true;
                rewarded = true;
            }
        }
        if (rewarded) syncRewardState(player);
        return structuralChanged;
    }

    private static boolean isRepeatable(QuestDefinition definition) {
        QuestConfig.Template template = QuestConfig.get().template(definition.id());
        return template != null && template.repeatable
                && definition.type() == QuestType.COMMON;
    }

    private static long repeatRewardIntervalTicks(QuestDefinition definition) {
        QuestConfig.Template template = QuestConfig.get().template(definition.id());
        if (template == null || !template.repeatable) return 0L;
        return Math.max(0L, template.minimumRewardIntervalMinutes) * 60L * 20L;
    }

    private static boolean expireChallenges(ServerPlayer player, PlayerPerkData data,
                                            long now) {
        boolean changed = false;
        boolean applyPenalty = false;
        for (QuestProgress quest : data.getQuestState().challenges()) {
            if (quest.completed() || quest.cancelled() || quest.expired()
                    || quest.expiresAt() <= 0L || now < quest.expiresAt()) continue;
            quest.expire();
            changed = true;
            if (quest.accepted()) applyPenalty = true;
        }
        if (applyPenalty) {
            data.getQuestState().setPenalty(true);
            data.applyChosenPerks(player);
        }
        return changed;
    }

    private static boolean prerequisiteMet(PlayerPerkData data, QuestDefinition definition) {
        return definition.prerequisiteId().isBlank()
                || data.getQuestState().hasCompletedTemplate(definition.prerequisiteId());
    }

    private static void grantRewards(ServerPlayer player, PlayerPerkData data,
                                     QuestProgress quest) {
        QuestDefinition definition = quest.definition();
        data.getQuestState().recordCompletion(definition.id(), definition.experience());
        if (isRepeatable(definition)) {
            long now = player.serverLevel().getGameTime();
            quest.setNextRepeatRewardAt(saturatedAdd(now,
                    repeatRewardIntervalTicks(definition)));
            // Preserve Completed for the packet sent below; the following server tick
            // starts the next cycle under the same stable quest id.
            quest.scheduleRepeatReset(saturatedAdd(now, 1L));
        }
        autoAcceptNewlyUnlockedCommon(data, definition);
        if (definition.type() == QuestType.CHALLENGE) {
            data.clearChallengePenalty();
            int refund = quest.releaseSecurityDeposit();
            if (refund > 0) {
                if (GoldCurrency.enabled()) GoldCurrency.grant(data, refund);
                else player.giveExperiencePoints(refund);
            }
        }
        if (GoldCurrency.enabled() && definition.goldReward() > 0L) {
            GoldCurrency.grant(data, definition.goldReward());
        }
        if (definition.experience() > 0) {
            AegisExperienceSystem.grantQuestExperience(
                    player, data, definition.experience());
            AegisExperienceSystem.awardMilestones(player, data, true);
        }
        PlayerStorage storage = data.getStorage();
        for (QuestDefinition.Reward reward : definition.rewards()) {
            String uniqueKey = reward.isVirtual() ? "virtual:" + reward.virtualId()
                    : "item:" + reward.itemId();
            if (reward.unique() && data.getQuestState().hasClaimedUnique(uniqueKey)) continue;
            boolean delivered;
            if (reward.isVirtual()) {
                delivered = storage.addVirtual(reward.virtualId(), reward.count());
            } else {
                ResourceLocation itemId = ResourceLocation.tryParse(reward.itemId());
                Item item = itemId == null ? null : GeneralServerMethods.resolveItem(itemId);
                if (item == null) {
                    delivered = false;
                } else {
                    ItemStack stack = new ItemStack(item, reward.count());
                    if (stack.isEmpty()) {
                        delivered = false;
                    } else if (storage.add(stack, GeneralConstants.rarityColor(reward.tier()))) {
                        delivered = true;
                    } else if (player.getInventory().add(stack)) {
                        delivered = true;
                    } else {
                        player.drop(stack, false);
                        delivered = true;
                    }
                }
            }
            if (delivered && reward.unique()) data.getQuestState().claimUnique(uniqueKey);
        }
        data.applyChosenPerks(player);
    }

    private static void syncRewardState(ServerPlayer player) {
        ModNetworking.syncStorageTo(player);
        // Quest synchronization is performed once by the mutation path after every
        // matching quest has been updated. Keep this reward sync focused on the state
        // that actually changed here.
        ModNetworking.syncPerkDataTo(player);
    }

    /** Auto-accepts only the next Common stage unlocked by this exact completion. */
    private static void autoAcceptNewlyUnlockedCommon(PlayerPerkData data,
                                                      QuestDefinition completed) {
        if (!autoAcceptEnabled(data, QuestType.COMMON)) return;
        String completedTemplateId = templateId(completed.id());
        for (QuestProgress candidate : data.getQuestState().common()) {
            QuestDefinition definition = candidate.definition();
            if (!completedTemplateId.equals(definition.prerequisiteId())
                    || candidate.accepted() || candidate.completed()
                    || candidate.cancelled() || candidate.expired()
                    || !prerequisiteMet(data, definition)) {
                continue;
            }
            candidate.accept();
        }
    }

    private static String templateId(String rolledId) {
        if (rolledId == null) return "";
        int marker = rolledId.indexOf('#');
        return marker < 0 ? rolledId : rolledId.substring(0, marker);
    }

    private static final class PendingProgressSync {
        private final Set<String> questIds = new LinkedHashSet<>();
        private final long flushAt;

        private PendingProgressSync(long flushAt) {
            this.flushAt = flushAt;
        }
    }

    private record WalkSample(ResourceLocation dimension, double x, double z) {}

    public static List<QuestView> views(ServerPlayer player, PlayerPerkData data) {
        List<QuestView> result = new ArrayList<>();
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            result.add(view(data, quest));
        }
        return result;
    }

    public static List<QuestCompletionView> completionViews(PlayerPerkData data) {
        List<QuestCompletionView> result = new ArrayList<>();
        QuestState state = data.getQuestState();
        for (String id : state.completedQuestIds()) {
            QuestDefinition definition = QuestConfig.get().definition(id);
            String title = definition == null || definition.title().isBlank()
                    ? id : definition.title();
            QuestType type = QuestConfig.get().typeOf(id);
            QuestObjective objective = definition == null
                    ? QuestObjective.KILL : definition.objective();
            String icon = definition == null ? "" : definition.icon();
            String profession = definition == null ? "" : definition.profession();
            result.add(new QuestCompletionView(id, title, type, objective,
                    icon, profession,
                    state.completionCount(id), state.completionExperience(id)));
        }
        return result;
    }

    private static QuestView view(PlayerPerkData data, QuestProgress progress) {
        QuestDefinition definition = progress.definition();
        boolean repeatable = isRepeatable(definition);
        int completedCycles = data.getQuestState().completionCount(definition.id());
        int cycle = !repeatable ? 0 : progress.completed()
                ? Math.max(1, completedCycles)
                : completedCycles == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : completedCycles + 1;
        StringBuilder rewards = new StringBuilder();
        if (definition.experience() > 0) {
            rewards.append(definition.experience()).append(' ')
                    .append(AegisExperienceSystem.experienceLabel(
                            PlatformServices.config().useMinecraftDefaultLevel()));
        }
        if (GoldCurrency.enabled() && definition.goldReward() > 0L) {
            if (rewards.length() > 0) rewards.append(", ");
            rewards.append(definition.goldReward()).append(" Gold");
        }
        for (QuestDefinition.Reward reward : definition.rewards()) {
            if (rewards.length() > 0) rewards.append(", ");
            rewards.append(reward.isVirtual() ? reward.virtualId() : reward.itemId());
        }
        String prerequisiteTitle = "";
        if (!definition.prerequisiteId().isBlank()) {
            QuestDefinition prerequisite = QuestConfig.get()
                    .definition(definition.prerequisiteId());
            prerequisiteTitle = prerequisite == null || prerequisite.title().isBlank()
                    ? definition.prerequisiteId() : prerequisite.title();
        }
        return new QuestView(definition.id(), definition.type(), definition.objective(),
                definition.title(), definition.description(), definition.targetId(),
                progress.progress(), definition.target(), progress.accepted(),
                progress.completed(), progress.cancelled(), progress.expired(),
                progress.expiresAt(), definition.experience(), definition.goldReward(),
                rewards.toString(),
                definition.story(), definition.profession(), prerequisiteTitle,
                prerequisiteMet(data, definition), progress.securityDepositPaid(),
                definition.icon(), repeatable, cycle,
                progress.nextRepeatRewardAt());
    }
}
