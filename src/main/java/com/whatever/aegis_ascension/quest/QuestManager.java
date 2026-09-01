package com.whatever.aegis_ascension.quest;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.NetworkLimits;
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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
            changed |= expireQuests(player, data, now);
            state.setChallengeRefreshIndex(challengeIndex);
            state.clearChallenges();
            generateChallenges(player, data, nextBoundary(challengeIndex, challengeTicks));
            changed = true;
        } else {
            changed |= expireQuests(player, data, now);
        }

        long sideTicks = config.sideRefreshTicks();
        long sideIndex = now / sideTicks;
        if (state.sideRefreshIndex() != sideIndex) {
            state.setSideRefreshIndex(sideIndex);
            state.clearSide();
            generateSide(player, data, nextBoundary(sideIndex, sideTicks));
            changed = true;
        }
        changed |= maintainUnlockedSideChains(player, data,
                nextBoundary(sideIndex, sideTicks));

        changed |= maintainUnlockedCommon(player, data);
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
                    roll(template, QuestType.CHALLENGE, player, data), false,
                    effectiveExpiry(template, player, expiresAt)));
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

    /**
     * When a template sets its own lifetime the quest expires on that clock instead of
     * at its type's next refresh, so a short contract can come and go inside one cycle.
     */
    private static long effectiveExpiry(QuestConfig.Template template, ServerPlayer player,
                                        long refreshExpiresAt) {
        int lifetimeMinutes = Math.max(0, template.lifetimeMinutes);
        if (lifetimeMinutes <= 0) return refreshExpiresAt;
        return QuestRolling.saturatedAdd(player.serverLevel().getGameTime(),
                lifetimeMinutes * 60L * TICKS_PER_SECOND);
    }

    /**
     * Offers a chain's next stage the moment the previous one is completed, rather than
     * waiting for the side refresh. That refresh is a 24 hour cycle, so leaving it to the
     * roll would put up to a real day between two halves of one story.
     *
     * <p>Only continuations are added: a stage whose prerequisite is now met, that the
     * player has not finished, and that is not already offered. Stages are once-per-player,
     * so each chain contributes at most one at a time and this cannot flood the list.</p>
     */
    private static boolean maintainUnlockedSideChains(ServerPlayer player,
                                                      PlayerPerkData data, long expiresAt) {
        QuestConfig config = QuestConfig.get();
        QuestState state = data.getQuestState();
        Set<String> present = new LinkedHashSet<>();
        for (QuestProgress progress : state.side()) {
            present.add(templateId(progress.definition().id()));
        }
        boolean changed = false;
        for (QuestConfig.Template template : config.sideTemplates) {
            if (template == null || template.id == null || template.id.isBlank()
                    || template.prerequisiteId == null || template.prerequisiteId.isBlank()
                    || present.contains(template.id)
                    || !templateUnlocked(data, template)
                    || state.hasCompletedTemplate(template.id)) {
                continue;
            }
            state.addSide(generatedProgress(template, QuestType.SIDE, player, data,
                    expiresAt, false));
            changed = true;
        }
        return changed;
    }

    /**
     * Drives an accepted quest's counters straight to a value, for trying out completion,
     * rewards and reward choices without first doing the work. Advances the main
     * objective and every extra requirement together, since a compound quest only
     * finishes when all of them are met.
     *
     * @return false when no accepted quest of that id is currently held.
     */
    public static boolean forceProgress(ServerPlayer player, PlayerPerkData data,
                                        String questId, int amount) {
        QuestProgress quest = find(data, questId);
        if (quest == null || !quest.accepted() || quest.completed()
                || quest.cancelled() || quest.expired()) {
            return false;
        }
        QuestDefinition definition = quest.definition();
        quest.setProgress(amount <= 0 ? definition.target()
                : Math.min(definition.target(), quest.progress() + amount));
        List<QuestDefinition.Requirement> requirements = definition.extraRequirements();
        for (int index = 0; index < requirements.size(); index++) {
            int target = requirements.get(index).target();
            int next = amount <= 0 ? target
                    : Math.min(target, quest.extraProgress(index) + amount);
            quest.addExtraProgress(index, next - quest.extraProgress(index), true);
        }
        if (quest.completed()) {
            grantRewards(player, data, quest);
            syncRewardState(player);
        }
        clearPendingProgressSync(player);
        ModNetworking.syncQuestsTo(player);
        return true;
    }

    /** Forces one quest type to regenerate now; an authoring aid, not a gameplay path. */
    public static void rerollQuests(ServerPlayer player, PlayerPerkData data,
                                    QuestType type) {
        QuestConfig config = QuestConfig.get();
        QuestState state = data.getQuestState();
        long now = player.serverLevel().getGameTime();
        switch (type) {
            case DAILY -> {
                long ticks = config.dailyRefreshTicks();
                state.clearDaily();
                generateDaily(player, data, nextBoundary(now / ticks, ticks));
            }
            case CHALLENGE -> {
                long ticks = config.challengeRefreshTicks();
                state.clearChallenges();
                generateChallenges(player, data, nextBoundary(now / ticks, ticks));
            }
            case SIDE -> {
                long ticks = config.sideRefreshTicks();
                state.clearSide();
                generateSide(player, data, nextBoundary(now / ticks, ticks));
            }
            case CHUNK -> {
                state.clearChunks();
                generateChunks(player, data, player.chunkPosition().x,
                        player.chunkPosition().z);
            }
            // The ladder rebuilds itself from the catalogue on the next tick.
            case COMMON -> state.clearCommon();
        }
        clearPendingProgressSync(player);
        ModNetworking.syncQuestsTo(player);
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
        return new QuestProgress(definition, accepted,
                effectiveExpiry(template, player, expiresAt));
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

    /**
     * Places one catalogue template straight into a player's log, ignoring weights, rank,
     * location, prerequisites and once-per-player. Purely an authoring aid: it exists so a
     * rare quest can be examined without waiting for the roll that would normally offer it.
     *
     * @return the rolled quest id, or null when no template has that id.
     */
    public static String grantTemplate(ServerPlayer player, PlayerPerkData data,
                                       String templateId, String tier) {
        QuestConfig config = QuestConfig.get();
        QuestConfig.Template template = config.template(templateId);
        if (template == null) return null;
        QuestType type = config.typeOf(templateId);
        QuestDefinition definition = roll(template, type, player, data, 0, tier);
        // Replace rather than duplicate: two quests sharing a rolled id would make
        // accept and submit ambiguous.
        data.getQuestState().removeIf(
                existing -> existing.definition().id().equals(definition.id()));
        QuestProgress progress = new QuestProgress(definition, false,
                effectiveExpiry(template, player, 0L));
        QuestState state = data.getQuestState();
        switch (type) {
            case DAILY -> state.addDaily(progress);
            case CHALLENGE -> state.addChallenge(progress);
            case COMMON -> state.addCommon(progress);
            case SIDE -> state.addSide(progress);
            case CHUNK -> {
                progress.setOriginChunk(player.chunkPosition().x, player.chunkPosition().z);
                state.addChunk(progress);
            }
        }
        clearPendingProgressSync(player);
        ModNetworking.syncQuestsTo(player);
        return definition.id();
    }

    /**
     * Rereads the quest catalogue and refreshes every online player's view of it. Quests
     * already rolled keep the definition they were rolled with; what changes is the
     * template-derived detail the view resolves live, such as stakes and constraints.
     *
     * @return how many players were resynchronised.
     */
    public static int reloadCatalogue(MinecraftServer server) {
        QuestConfig.reload();
        QuestConfig.get();
        int synced = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearPendingProgressSync(player);
            ModNetworking.syncQuestsTo(player);
            synced++;
        }
        return synced;
    }

    /**
     * Reports why a template is or is not currently offered to one player. There are now
     * seven independent gates on the draw, and from inside the game an unreachable quest
     * is indistinguishable from an unlucky one, so this states which gate rejected it.
     */
    public static List<String> explainTemplate(ServerPlayer player, PlayerPerkData data,
                                               String templateId) {
        QuestConfig config = QuestConfig.get();
        QuestConfig.Template template = config.template(templateId);
        List<String> reasons = new ArrayList<>();
        if (template == null) {
            reasons.add("no template with that id exists");
            return reasons;
        }
        QuestType type = config.typeOf(templateId);
        reasons.add("type " + type + ", weight " + template.weight);
        if (templateWeight(template) <= 0) reasons.add("BLOCKED: weight is zero");

        int rank = AegisExperienceSystem.effectiveLevel(player, data);
        reasons.add("rank " + rank + " of " + Math.max(0, template.minimumRank) + " required");
        if (rank < Math.max(0, template.minimumRank)) reasons.add("BLOCKED: rank too low");

        ResourceLocation dimension = player.serverLevel().dimension().location();
        if (!matchesLocation(template.dimensions, dimension)) {
            reasons.add("BLOCKED: wrong dimension, needs " + template.dimensions);
        }
        ResourceLocation biome = player.serverLevel().getBiome(player.blockPosition())
                .unwrapKey().map(key -> key.location()).orElse(null);
        if (!matchesLocation(template.biomes, biome)) {
            reasons.add("BLOCKED: wrong biome, needs " + template.biomes);
        }
        if (!templateUnlocked(data, template)) {
            reasons.add("BLOCKED: requires " + template.prerequisiteId + " first");
        }
        if (template.oncePerPlayer
                && data.getQuestState().hasCompletedTemplate(template.id)) {
            reasons.add("BLOCKED: already completed and is once per player");
        }
        int reputation = professionReputation(data).getOrDefault(
                template.profession == null ? "" : template.profession, 0);
        if (template.minimumReputation > 0) {
            reasons.add(template.profession + " reputation " + reputation + " of "
                    + template.minimumReputation + " required");
            if (reputation < template.minimumReputation) {
                reasons.add("BLOCKED: reputation too low");
            }
        }
        int poolSize = choose(player, data, config.templates(type), 999, 999).size();
        reasons.add("the whole " + type + " pool currently offers " + poolSize
                + " template(s) to you");
        if (reasons.stream().noneMatch(line -> line.startsWith("BLOCKED"))) {
            reasons.add("nothing blocks this template; it is simply down to the weighted draw");
        }
        return reasons;
    }

    /**
     * Lifetime totals per objective for this player, for the Quest Complete summary.
     * Counted from every qualifying event rather than from quest progress, so they
     * measure what the player has done, not what a quest happened to ask for.
     */
    public static Map<QuestObjective, Integer> lifetimeTotals(PlayerPerkData data) {
        Map<QuestObjective, Integer> totals = new LinkedHashMap<>();
        QuestState state = data.getQuestState();
        for (QuestObjective objective : QuestObjective.values()) {
            int total = state.lifetime(objective);
            if (total > 0) totals.put(objective, total);
        }
        return totals;
    }

    /** Every template id in the catalogue, for command completion. */
    public static List<String> templateIds() {
        List<String> ids = new ArrayList<>();
        for (QuestConfig.Template template : QuestConfig.get().allTemplates()) {
            if (template != null && template.id != null && !template.id.isBlank()) {
                ids.add(template.id);
            }
        }
        return ids;
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
        // Built once for the whole draw: reputation is derived from completion history,
        // and resolving that per candidate template would rescan it for every one of them.
        Map<String, Integer> reputation = professionReputation(data);
        pool.removeIf(template -> template == null || template.id == null
                || template.id.isBlank() || templateWeight(template) <= 0
                || progressionRank < Math.max(0, template.minimumRank)
                || !matchesLocation(template.dimensions, dimension)
                || !matchesLocation(template.biomes, biome)
                // A stage the player has not unlocked is withheld rather than shown
                // locked, so a chain reveals itself one step at a time.
                || !templateUnlocked(data, template)
                || (template.oncePerPlayer
                && data.getQuestState().hasCompletedTemplate(template.id))
                // Offered while within reach and locked, rather than hidden outright, so
                // standing has something visible to work towards. Acceptance is still
                // refused until the requirement is actually met.
                || reputation.getOrDefault(template.profession == null
                ? "" : template.profession, 0)
                < Math.max(0, template.minimumReputation)
                - Math.max(0, QuestConfig.get().reputationVisibleWithin));
        if (pool.isEmpty()) return List.of();
        int cap = Math.max(1, QuestConfig.get().maxRandomQuests);
        int max = Math.min(pool.size(), Math.min(cap, Math.max(1, configuredMax)));
        int min = Math.min(max, Math.min(cap, Math.max(1, configuredMin)));
        int count = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
        // The pool has already dropped locked and finished stages, so anything left
        // holding a prerequisite is an unlocked continuation, and the selection places
        // those before the weighted draw.
        int[] weights = new int[pool.size()];
        boolean[] continuation = new boolean[pool.size()];
        for (int index = 0; index < pool.size(); index++) {
            QuestConfig.Template template = pool.get(index);
            weights[index] = templateWeight(template);
            continuation[index] = template.prerequisiteId != null
                    && !template.prerequisiteId.isBlank();
        }
        List<QuestConfig.Template> selected = new ArrayList<>(count);
        for (int index : QuestRolling.select(weights, continuation, count,
                () -> player.getRandom().nextLong())) {
            selected.add(pool.get(index));
        }
        return selected;
    }

    /**
     * How many quests the player has completed for each profession. Reputation is derived
     * from completion history rather than stored separately, so it can never drift out of
     * step with what the player has actually done.
     */
    private static Map<String, Integer> professionReputation(PlayerPerkData data) {
        QuestConfig config = QuestConfig.get();
        Map<String, Integer> reputation = new LinkedHashMap<>();
        for (String questId : data.getQuestState().completedQuestIds()) {
            QuestConfig.Template template = config.template(questId);
            if (template == null || template.profession == null
                    || template.profession.isBlank()) {
                continue;
            }
            int completions = data.getQuestState().completionCount(questId);
            if (completions <= 0) continue;
            reputation.merge(template.profession, completions,
                    (current, added) -> current > Integer.MAX_VALUE - added
                            ? Integer.MAX_VALUE : current + added);
        }
        return reputation;
    }

    /** A template is offered only once every stage it depends on has been completed. */
    private static boolean templateUnlocked(PlayerPerkData data,
                                            QuestConfig.Template template) {
        String prerequisite = template.prerequisiteId;
        return prerequisite == null || prerequisite.isBlank()
                || data.getQuestState().hasCompletedTemplate(prerequisite);
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

    /**
     * Resolves one list of reward specs into rolled rewards. Shared by a quest's
     * guaranteed rewards and by the alternatives it offers a choice between, so both
     * honour tier inheritance, random stack sizes, and the unique-item reservations that
     * stop the same one-off being promised twice in a single roll.
     */
    /**
     * Caps a reward at a single stack of whatever it is.
     *
     * <p>Reward counts are rolled from a range and then scaled by rarity, which suits
     * materials but not equipment: armour, tools and weapons all stack to one, so the
     * same arithmetic that pays out thirty-three iron ingots was paying out nine elytra.
     * Since a stack limit is exactly the game's own statement of how many of a thing is
     * a sensible quantity, that is what this defers to.</p>
     */
    private static int withinOneStack(String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 1) return Math.max(1, count);
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        Item item = location == null ? null : GeneralServerMethods.resolveItem(location);
        if (item == null) return count;
        return Math.max(1, Math.min(count, item.getMaxStackSize()));
    }

    /** Whether a rolled rarity is at least as rare as the one required. */
    private static boolean atLeastTier(String tier, String minimum) {
        return GeneralConstants.rarityRank(GeneralConstants.rarityColor(tier))
                >= GeneralConstants.rarityRank(GeneralConstants.rarityColor(minimum));
    }

    /**
     * Draws distinct Discovery items to be offered as a choice of one.
     *
     * <p>They are deliberately not marked unique. A unique reward the player has already
     * claimed is skipped when it is granted, so a choice offered as unique could be
     * picked and pay out nothing; a choice has to be honoured once it is shown. The
     * attempt limit bounds the work when the shop pool is smaller than the number asked
     * for, and the seen set stops the same item filling every slot, which would make the
     * choice no choice at all.</p>
     */
    private static List<QuestDefinition.Reward> rollDiscoveryChoices(
            ServerPlayer player, PlayerPerkData data, Set<String> reservedUniqueItems,
            int count, String tier) {
        List<QuestDefinition.Reward> choices = new ArrayList<>();
        int wanted = Math.max(0, Math.min(NetworkLimits.MAX_QUEST_REWARD_CHOICES, count));
        Set<String> seen = new LinkedHashSet<>();
        for (int attempt = 0; attempt < wanted * 6 && choices.size() < wanted; attempt++) {
            String id = pickShopReward(player, data, reservedUniqueItems,
                    ShopType.DISCOVERY, tier, false);
            if (id.isBlank() || !seen.add(id)) continue;
            choices.add(new QuestDefinition.Reward(id, "", 1, tier, false));
        }
        return choices;
    }

    /** Whether any of a template's rewards or choices already guarantees a Discovery item. */
    private static boolean declaresUniqueReward(QuestConfig.Template template) {
        return declaresUnique(template.rewards) || declaresUnique(template.rewardChoices);
    }

    private static boolean declaresUnique(List<QuestConfig.RewardSpec> specs) {
        if (specs == null) return false;
        for (QuestConfig.RewardSpec spec : specs) {
            if (spec != null && "random_unique".equalsIgnoreCase(spec.kind)) return true;
        }
        return false;
    }

    /**
     * @param discoveryEligible whether an ordinary reward may instead be drawn from the
     *                          Discovery shop, decided once for the whole quest
     */
    private static List<QuestDefinition.Reward> resolveRewards(
            List<QuestConfig.RewardSpec> specs, String tier, double rewardMultiplier,
            ServerPlayer player, PlayerPerkData data, Set<String> reservedUniqueItems,
            boolean discoveryEligible) {
        List<QuestDefinition.Reward> rewards = new ArrayList<>();
        if (specs == null) return rewards;
        for (QuestConfig.RewardSpec spec : specs) {
            if (spec == null) continue;

            String kind = spec.kind == null ? "item"
                    : spec.kind.toLowerCase(java.util.Locale.ROOT);
            String id = spec.id == null ? "" : spec.id;
            // A rarer quest never pays out in commoner goods, but a reward that already
            // asks for something rarer than the quest keeps its own tier.
            String rewardTier = QuestRolling.higherTier(spec.tier, tier);
            boolean unique = spec.unique || "random_unique".equals(kind);
            boolean fallbackUsed = false;
            if ("random_common".equals(kind)) {
                // Half of these are drawn from Discovery instead, at the quest's own
                // rarity, so an ordinary reward line can still turn up something rare.
                boolean fromDiscovery = discoveryEligible
                        && player.getRandom().nextDouble()
                        < QuestConfig.get().discoveryRewardChance;
                id = pickShopReward(player, data, reservedUniqueItems,
                        fromDiscovery ? ShopType.DISCOVERY : ShopType.COMMON,
                        rewardTier, unique);
                if (id.isBlank() && fromDiscovery) {
                    // Discovery may hold nothing at this rarity the player can still be
                    // given; fall back to the common shop rather than paying nothing.
                    id = pickShopReward(player, data, reservedUniqueItems,
                            ShopType.COMMON, rewardTier, unique);
                }
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
                        source, rewardTier, unique);
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
            boolean virtual = "virtual".equals(kind);
            int count = QuestRolling.scaledValue(randomRewardCount(spec, player),
                    rewardMultiplier);
            rewards.add(new QuestDefinition.Reward(
                    virtual ? "" : id, virtual ? id : "",
                    virtual ? count : withinOneStack(id, count),
                    rewardTier, rewardUnique));
        }
        return rewards;
    }

    private static QuestDefinition roll(QuestConfig.Template template, QuestType type,
                                        ServerPlayer player, PlayerPerkData data,
                                        int completedCycles) {
        return roll(template, type, player, data, completedCycles, null);
    }

    /** @param forcedTier a rarity to roll at, bypassing weights and rank; null to choose. */
    private static QuestDefinition roll(QuestConfig.Template template, QuestType type,
                                        ServerPlayer player, PlayerPerkData data,
                                        int completedCycles, String forcedTier) {
        String tier = forcedTier == null
                ? chooseTier(template, player, data)
                : GeneralConstants.normalizeTier(forcedTier);
        QuestConfig.TierScaling scaling = tierScaling(tier);
        // A repeatable quest's cycle bonus applies to everything it pays out. The tier
        // knobs are then applied one each, so rewardCountMultiplier moves stack sizes
        // without also inflating experience and gold behind the catalogue's back.
        double cycleMultiplier = rewardMultiplier(template, completedCycles);
        double rewardMultiplier = cycleMultiplier
                * Math.max(0.0D, scaling.rewardCountMultiplier);
        Set<String> reservedUniqueItems = reservedUniqueItemKeys(data);
        // Decided once per quest: the everyday types never pay out Discovery goods, and
        // a quest that already guarantees one does not get further chances at another.
        boolean discoveryEligible = type != QuestType.DAILY && type != QuestType.CHUNK
                && !declaresUniqueReward(template);
        List<QuestDefinition.Reward> rewards = resolveRewards(
                template.rewards, tier, rewardMultiplier, player, data,
                reservedUniqueItems, discoveryEligible);
        List<QuestDefinition.Reward> rewardChoices = resolveRewards(
                template.rewardChoices, tier, rewardMultiplier, player, data,
                reservedUniqueItems, discoveryEligible);
        // A Challenge may additionally offer a pick from the Discovery shop's top shelf.
        // Only when the template declares no choices of its own, so an authored set is
        // never silently replaced by a generated one.
        QuestConfig config = QuestConfig.get();
        if (rewardChoices.isEmpty()) {
            // Two cases offer a pick from the Discovery shop. A Challenge earns one by
            // being dangerous, and only above the rarity floor; a chain stage earns one
            // by ending a piece of a story, at any rarity. Either way the template's own
            // choices win if it declares them.
            boolean isChainStage = template.oncePerPlayer;
            boolean offered = type == QuestType.CHALLENGE
                    ? atLeastTier(tier, config.challengeDiscoveryChoiceMinimumTier)
                    && player.getRandom().nextDouble() < config.challengeDiscoveryChoiceChance
                    : isChainStage
                    && player.getRandom().nextDouble() < config.chainDiscoveryChoiceChance;
            if (offered) {
                // A chain stage always pays at its own rarity; a Challenge may be forced
                // to a fixed one by configuration.
                String choiceTier = isChainStage && type != QuestType.CHALLENGE
                        || config.challengeDiscoveryChoiceTier == null
                        || config.challengeDiscoveryChoiceTier.isBlank()
                        ? tier
                        : GeneralConstants.normalizeTier(config.challengeDiscoveryChoiceTier);
                rewardChoices = rollDiscoveryChoices(player, data, reservedUniqueItems,
                        config.challengeDiscoveryChoiceCount, choiceTier);
            }
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
        // Rarity does not multiply how many landmarks a quest asks for. For a counted
        // objective a multiplier means proportionally more work, but the cost of reaching
        // a structure is the search, not the arrival, so three ancient cities is not three
        // times one - it is an expedition. Rarity is expressed in the reward and the stake
        // for those instead.
        if (template.objective != QuestObjective.REACH_LOCATION) {
            target = QuestRolling.scaledValue(target,
                    Math.max(1.0D, scaling.targetMultiplier));
        }
        int experience = QuestRolling.scaledValue(template.experience,
                cycleMultiplier * Math.max(0.0D, scaling.experienceMultiplier));
        long goldReward = randomGoldReward(template,
                cycleMultiplier * Math.max(0.0D, scaling.goldMultiplier), player);
        List<QuestDefinition.Requirement> extraRequirements = new ArrayList<>();
        if (template.alsoRequires != null) {
            for (QuestConfig.SubObjective sub : template.alsoRequires) {
                if (sub == null || sub.objective == null) continue;
                String subTargetId = sub.targetId == null ? "" : sub.targetId;
                if (sub.targetIds != null && !sub.targetIds.isEmpty()) {
                    subTargetId = pick(sub.targetIds, player);
                }
                int subTarget = Math.max(1, sub.target);
                if (sub.targetMin > 0 || sub.targetMax > 0) {
                    int min = Math.max(1, sub.targetMin > 0 ? sub.targetMin : subTarget);
                    int max = Math.max(min, sub.targetMax > 0 ? sub.targetMax : min);
                    subTarget = min + (max > min ? player.getRandom().nextInt(max - min + 1) : 0);
                }
                if (sub.objective != QuestObjective.REACH_LOCATION) {
                    subTarget = QuestRolling.scaledValue(subTarget,
                            Math.max(1.0D, scaling.targetMultiplier));
                }
                extraRequirements.add(new QuestDefinition.Requirement(
                        sub.objective, subTargetId, subTarget));
            }
        }
        return new QuestDefinition(rolledId(template, type), type,
                template.objective, template.title, template.description, targetId,
                target, experience, goldReward, rewards, template.story,
                template.profession, template.prerequisiteId, template.icon, tier,
                extraRequirements, rewardChoices);
    }

    /**
     * Picks the rarity this quest rolls at. A template lists the tiers it allows; any
     * tier the player has not yet reached, or that the catalogue has disabled, is
     * dropped, so an unranked player never draws a quest they cannot finish.
     */
    private static String chooseTier(QuestConfig.Template template, ServerPlayer player,
                                     PlayerPerkData data) {
        List<String> allowed = template.tiers;
        if (allowed == null || allowed.isEmpty()) return GeneralConstants.TIER_R;
        List<QuestRolling.TierOption> options = new ArrayList<>(allowed.size());
        for (String candidate : allowed) {
            String tier = GeneralConstants.normalizeTier(candidate);
            QuestConfig.TierScaling scaling = tierScaling(tier);
            options.add(new QuestRolling.TierOption(tier, scaling.weight,
                    scaling.minimumRank));
        }
        return QuestRolling.chooseTier(options,
                AegisExperienceSystem.effectiveLevel(player, data),
                GeneralConstants.TIER_R, () -> player.getRandom().nextLong());
    }


    private static QuestConfig.TierScaling tierScaling(String tier) {
        Map<String, QuestConfig.TierScaling> tiers = QuestConfig.get().questTiers;
        QuestConfig.TierScaling scaling = tiers == null ? null
                : tiers.get(GeneralConstants.normalizeTier(tier));
        return scaling == null ? new QuestConfig.TierScaling() : scaling;
    }

    /**
     * Rolls one reward stack size. A template may give a fixed count or a
     * [countMin, countMax] range; the range wins whenever either bound is set.
     */
    private static int randomRewardCount(QuestConfig.RewardSpec spec, ServerPlayer player) {
        return QuestRolling.rewardCount(spec.count, spec.countMin, spec.countMax,
                bound -> player.getRandom().nextInt(bound));
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

    /**
     * Counts the whole crafted stack rather than the craft action, so shift-crafting
     * sixty-four sticks advances a "craft any items" quest by sixty-four.
     */
    public static boolean onCraft(ServerPlayer player, ItemStack crafted) {
        return advance(player, QuestObjective.CRAFT_ITEM,
                canonicalItemId(crafted.getItem()), crafted.getCount());
    }

    public static boolean onBlockBroken(ServerPlayer player, BlockState state) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock());
        return advance(player, QuestObjective.BREAK_BLOCK, key.toString(), 1);
    }

    public static boolean onArrowShot(ServerPlayer player, AbstractArrow arrow) {
        return advance(player, QuestObjective.SHOOT_ARROW,
                GeneralServerMethods.getEntityTypeKey(arrow.getType()).toString(), 1);
    }

    public static boolean onArrowHit(ServerPlayer player, LivingEntity victim) {
        return advance(player, QuestObjective.HIT_ARROW,
                GeneralServerMethods.getEntityTypeKey(victim.getType()).toString(), 1);
    }

    /**
     * Credits arrival at any structure an active quest is asking for. Only structures a
     * quest actually names are tested, so a player with no location quest pays nothing
     * beyond the objective check.
     */
    public static boolean onLocationVisited(ServerPlayer player, PlayerPerkData data) {
        if (!hasActiveObjective(data, QuestObjective.REACH_LOCATION)) return false;
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        Set<String> tested = new LinkedHashSet<>();
        boolean changed = false;
        for (QuestProgress progress : allQuests(data.getQuestState())) {
            QuestDefinition definition = progress.definition();
            if (!progress.accepted() || progress.completed() || progress.cancelled()
                    || progress.expired()
                    || definition.objective() != QuestObjective.REACH_LOCATION
                    || definition.targetId().isBlank()
                    || !tested.add(definition.targetId())) {
                continue;
            }
            StructureStart start = structureAt(level, position, definition.targetId());
            if (start == null || !start.isValid()) continue;
            String instance = level.dimension().location() + "|" + definition.targetId()
                    + "|" + start.getChunkPos();
            // Two quests may name the same structure, and each has to record this
            // arrival for itself, so the credit is taken per quest rather than once here.
            changed |= advance(player, QuestObjective.REACH_LOCATION,
                    definition.targetId(), 1, false,
                    candidate -> candidate.creditInstance(instance));
        }
        return changed;
    }

    private static StructureStart structureAt(ServerLevel level, BlockPos position,
                                              String structureId) {
        ResourceLocation location = ResourceLocation.tryParse(structureId);
        if (location == null) return null;
        Registry<Structure> registry = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        Structure structure = registry.get(location);
        return structure == null ? null
                : level.structureManager().getStructureAt(position, structure);
    }

    private static boolean advance(ServerPlayer player, QuestObjective objective,
                                   String targetId, int amount) {
        return advance(player, objective, targetId, amount, false);
    }

    private static boolean advance(ServerPlayer player, QuestObjective objective,
                                   String targetId, int amount,
                                   boolean unopenedGeneratedLoot) {
        return advance(player, objective, targetId, amount, unopenedGeneratedLoot, null);
    }

    /**
     * @param gate optional per-quest test applied after every other filter, for events
     *             that a quest may only count once. It is consulted exactly once per
     *             matching quest and may record that the event was taken.
     */
    private static boolean advance(ServerPlayer player, QuestObjective objective,
                                   String targetId, int amount,
                                   boolean unopenedGeneratedLoot,
                                   java.util.function.Predicate<QuestProgress> gate) {
        if (amount <= 0) return false;
        PlayerPerkData data = PerkData.of(player);
        QuestState state = data.getQuestState();
        // Counted whether or not a quest wanted it: these are lifetime totals for the
        // player, not quest progress. The map was already saved and loaded; nothing had
        // ever filled it.
        state.incrementLifetime(objective, amount);
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
            boolean mainMatches = definition.objective() == objective
                    && matches(definition.targetId(), targetId);
            // One event can feed the main objective and any number of extras, so every
            // part that names it advances rather than only the first match.
            List<Integer> matchedExtras = new ArrayList<>();
            List<QuestDefinition.Requirement> requirements = definition.extraRequirements();
            for (int index = 0; index < requirements.size(); index++) {
                QuestDefinition.Requirement requirement = requirements.get(index);
                if (requirement.objective() == objective
                        && matches(requirement.targetId(), targetId)) {
                    matchedExtras.add(index);
                }
            }
            if (!mainMatches && matchedExtras.isEmpty()) continue;
            if (gate != null && !gate.test(progress)) continue;
            boolean beforeComplete = progress.completed();
            int before = progress.progress();
            int[] beforeExtras = progress.extraProgressSnapshot();
            boolean repeatable = isRepeatable(definition);
            boolean rewardReady = !repeatable
                    || player.serverLevel().getGameTime() >= progress.nextRepeatRewardAt();
            if (mainMatches) progress.addProgress(amount, rewardReady);
            for (int index : matchedExtras) {
                progress.addExtraProgress(index, amount, rewardReady);
            }
            boolean counterMoved = progress.progress() != before
                    || !java.util.Arrays.equals(beforeExtras, progress.extraProgressSnapshot());
            if (counterMoved || progress.completed() != beforeComplete) {
                changed = true;
            }
            if (progress.completed() && !beforeComplete) {
                grantRewards(player, data, progress);
                rewarded = true;
            } else if (counterMoved) {
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
                ignored -> new PendingProgressSync(QuestRolling.saturatedAdd(
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
    /**
     * Fails accepted quests whose template asked to be finished in one sitting. Run on
     * logout; quests that were never accepted are left alone, since letting an untouched
     * offer sit unclaimed is not a failure.
     */
    /**
     * Fails every accepted quest whose template declares the triggered constraint. A
     * constraint is enforced the moment it is broken rather than tested at completion,
     * which a player could always satisfy for the single instant that was measured.
     */
    private static boolean failConstrainedQuests(PlayerPerkData data,
            java.util.function.Predicate<QuestConfig.Template> triggered) {
        QuestConfig config = QuestConfig.get();
        boolean changed = false;
        for (QuestProgress progress : allQuests(data.getQuestState())) {
            if (!progress.accepted() || progress.completed() || progress.cancelled()
                    || progress.expired()) {
                continue;
            }
            QuestConfig.Template template = config.template(progress.definition().id());
            if (template == null || !triggered.test(template)) continue;
            progress.expire();
            changed = true;
        }
        return changed;
    }

    public static boolean onPlayerDied(PlayerPerkData data) {
        return failConstrainedQuests(data, template -> template.failOnDeath);
    }

    public static boolean onPlayerDamaged(PlayerPerkData data) {
        return failConstrainedQuests(data, template -> template.failOnDamageTaken);
    }

    /** Run on a slow cadence; equipping armour is not an event this mod can hook. */
    public static boolean onArmorChecked(ServerPlayer player, PlayerPerkData data) {
        if (!isWearingArmor(player)) return false;
        return failConstrainedQuests(data, template -> template.failOnArmorWorn);
    }

    private static boolean isWearingArmor(ServerPlayer player) {
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }


    public static boolean failQuestsOnLogout(ServerPlayer player, PlayerPerkData data) {
        QuestConfig config = QuestConfig.get();
        boolean changed = false;
        for (QuestProgress progress : allQuests(data.getQuestState())) {
            if (!progress.accepted() || progress.completed() || progress.cancelled()
                    || progress.expired()) {
                continue;
            }
            QuestConfig.Template template = config.template(progress.definition().id());
            if (template == null || !template.failOnLogout) continue;
            progress.expire();
            changed = true;
        }
        return changed;
    }

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
        QuestConfig.Template template = QuestConfig.get().template(quest.definition().id());
        // Enforced here as well as in the draw: a quest may now be offered while still
        // short of its requirement, and the client cannot be trusted to hold that line.
        if (template != null && template.minimumReputation > 0) {
            int standing = professionReputation(data).getOrDefault(
                    template.profession == null ? "" : template.profession, 0);
            if (standing < template.minimumReputation) {
                player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                        "message.aegis_ascension.quest.reputation_required",
                        template.minimumReputation, standing));
                return false;
            }
        }
        if (template != null && template.failOnArmorWorn && isWearingArmor(player)) {
            player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                    "message.aegis_ascension.quest.armor_required_off"));
            return false;
        }
        int deposit = securityDeposit(quest.definition());
        if (deposit > 0) {
            if (GoldCurrency.enabled()) {
                if (!GoldCurrency.canAfford(data, deposit)) return false;
                GoldCurrency.trySpend(data, deposit);
            } else {
                if (player.totalExperience < deposit) return false;
                player.giveExperiencePoints(-deposit);
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
    /** Objectives fulfilled by handing items in rather than by an event in the world. */
    private static boolean isSubmission(QuestObjective objective) {
        return objective == QuestObjective.TRADE_ITEM
                || objective == QuestObjective.GIVE_MATERIAL;
    }

    /**
     * Hands in every outstanding item this quest asks for, across its main objective and
     * any requirement that is also handed in.
     *
     * <p>One press settles the whole quest rather than one requirement, because the
     * detail panel scrolls while its buttons do not: a button per requirement would drift
     * away from the row it belongs to. Each requirement is taken independently, so a
     * player holding one of two asked-for items submits that one and is told what is
     * still missing, instead of the press doing nothing.</p>
     */
    public static boolean submit(ServerPlayer player, String id) {
        PlayerPerkData data = PerkData.of(player);
        QuestProgress quest = find(data, id);
        if (quest == null || !quest.accepted() || quest.completed()
                || quest.cancelled() || quest.expired()
                || (quest.expiresAt() > 0L
                && quest.expiresAt() <= player.serverLevel().getGameTime())) {
            return false;
        }
        QuestDefinition definition = quest.definition();
        boolean submitted = false;

        if (isSubmission(definition.objective())) {
            int taken = takeRequestedItems(player, data, definition.targetId(),
                    Math.max(0, definition.target() - quest.progress()));
            if (taken > 0) {
                quest.addProgress(taken);
                submitted = true;
            }
        }
        List<QuestDefinition.Requirement> requirements = definition.extraRequirements();
        for (int index = 0; index < requirements.size(); index++) {
            QuestDefinition.Requirement requirement = requirements.get(index);
            if (!isSubmission(requirement.objective())) continue;
            int taken = takeRequestedItems(player, data, requirement.targetId(),
                    Math.max(0, requirement.target() - quest.extraProgress(index)));
            if (taken > 0) {
                quest.addExtraProgress(index, taken, true);
                submitted = true;
            }
        }
        if (!submitted) return false;

        if (quest.completed()) {
            grantRewards(player, data, quest);
            syncRewardState(player);
        }
        // Successful submission is a structural change and is synchronized here so
        // callers can avoid sending a second identical full snapshot.
        ModNetworking.syncQuestsTo(player);
        return true;
    }

    /**
     * Consumes one requirement's outstanding items from Storage first, then the player's
     * inventory.
     *
     * @return the amount taken, or zero when nothing is outstanding or the player is
     *         short, in which case they are told which item they lack.
     */
    private static int takeRequestedItems(ServerPlayer player, PlayerPerkData data,
                                          String targetId, int required) {
        if (required <= 0) return 0;
        ResourceLocation itemId = ResourceLocation.tryParse(targetId);
        Item item = itemId == null ? null : GeneralServerMethods.resolveItem(itemId);
        if (item == null) return 0;
        PlayerStorage storage = data.getStorage();
        long available = QuestRolling.saturatedAdd(storage.countItem(item), countInventory(player, item));
        if (available < required) {
            player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                    "message.aegis_ascension.quest.side.insufficient_items",
                    required, new ItemStack(item).getHoverName(), available));
            return 0;
        }
        long removedFromStorage = storage.removeItem(item, required);
        int remaining = (int) Math.max(0L, required - removedFromStorage);
        if (remaining > 0) removeFromInventory(player, item, remaining);
        return required;
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
    /**
     * Counters for the compact progress sync: index zero is the main objective, and any
     * further entries are the quest's extra requirements in their definition order.
     */
    public static Map<String, List<Integer>> progressValues(PlayerPerkData data,
                                                            Set<String> questIds) {
        if (questIds == null || questIds.isEmpty()) return Map.of();
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            String id = quest.definition().id();
            if (!questIds.contains(id)) continue;
            List<Integer> counters = new ArrayList<>();
            counters.add(quest.progress());
            for (int value : quest.extraProgressSnapshot()) counters.add(value);
            result.put(id, counters);
        }
        return result;
    }

    private static boolean allTerminal(List<QuestProgress> quests) {
        return !quests.isEmpty() && quests.stream().allMatch(quest ->
                quest.completed() || quest.cancelled() || quest.expired());
    }

    /**
     * Keeps the Common ladder showing exactly the rungs the player has reached. A stage
     * appears the moment its prerequisite is met, and one that is still locked is
     * withheld rather than listed greyed out, so a ladder reveals itself a rung at a time.
     *
     * <p>Withdrawing a locked stage cannot lose anything: a locked quest can never have
     * been accepted, so it can never carry progress. Worlds created before this
     * behaviour existed are tidied the same way on their next tick.</p>
     */
    private static boolean maintainUnlockedCommon(ServerPlayer player,
                                                  PlayerPerkData data) {
        QuestConfig config = QuestConfig.get();
        QuestState state = data.getQuestState();
        // Collected from the catalogue list already being walked below, so the filter
        // never calls QuestConfig#template, which rebuilds the whole catalogue per call.
        Set<String> repeatableIds = new LinkedHashSet<>();
        for (QuestConfig.Template template : config.commonTemplates) {
            if (template != null && template.repeatable && template.id != null) {
                repeatableIds.add(template.id);
            }
        }
        boolean changed = state.removeCommonIf(progress -> {
            QuestDefinition definition = progress.definition();
            // A finished one-off is already recorded in the Quest Complete tab, so it
            // leaves the ladder rather than sitting there as a climbed rung. Repeatables
            // must stay: their next cycle is started from this list once reset time passes.
            if (progress.completed()) {
                return !repeatableIds.contains(templateId(definition.id()));
            }
            // A locked stage can never have been accepted, so it can never hold progress.
            return !progress.accepted() && progress.progress() <= 0
                    && !prerequisiteMet(data, definition);
        });
        Set<String> present = new LinkedHashSet<>();
        for (QuestProgress progress : state.common()) {
            present.add(templateId(progress.definition().id()));
        }
        for (QuestConfig.Template template : config.commonTemplates) {
            if (template == null || template.id == null || template.id.isBlank()
                    || present.contains(template.id)
                    || !templateUnlocked(data, template)
                    // Without this a finished one-off would be withdrawn above and
                    // immediately handed back with its progress reset.
                    || (!template.repeatable
                    && data.getQuestState().hasCompletedTemplate(template.id))) {
                continue;
            }
            state.addCommon(generatedProgress(template, QuestType.COMMON, player,
                    data, 0L, config.commonAutoAccept));
            changed = true;
        }
        return changed;
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

    /**
     * Expires every quest whose deadline has passed, so a template with its own lifetime
     * dies on its own clock rather than surviving until the next refresh. Only an
     * accepted Challenge carries a penalty: letting an untouched side contract lapse
     * should cost the player nothing.
     */
    private static boolean expireQuests(ServerPlayer player, PlayerPerkData data,
                                        long now) {
        boolean changed = false;
        boolean applyPenalty = false;
        for (QuestProgress quest : allQuests(data.getQuestState())) {
            if (quest.completed() || quest.cancelled() || quest.expired()
                    || quest.expiresAt() <= 0L || now < quest.expiresAt()) continue;
            quest.expire();
            changed = true;
            if (quest.accepted() && quest.definition().type() == QuestType.CHALLENGE) {
                applyPenalty = true;
            }
        }
        if (applyPenalty) {
            data.getQuestState().setPenalty(true);
            data.applyChosenPerks(player);
        }
        return changed;
    }

    /**
     * The stake this quest charges on acceptance. A template may set its own; Challenges
     * fall back to the catalogue-wide deposit so their behaviour is unchanged.
     */
    /**
     * The stake a quest charges on acceptance and returns on completion, scaled by the
     * rarity it rolled at so a rarer Challenge risks proportionally more.
     */
    private static int securityDeposit(QuestDefinition definition) {
        QuestConfig config = QuestConfig.get();
        QuestConfig.Template template = config.template(definition.id());
        if (template != null && template.securityDeposit > 0) {
            return QuestRolling.scaledValue(template.securityDeposit,
                    Math.max(0.0D, tierScaling(definition.tier()).stakeMultiplier));
        }
        int base = definition.type() == QuestType.CHALLENGE
                ? Math.max(0, config.challengeSecurityDepositExperience) : 0;
        return QuestRolling.scaledValue(base,
                Math.max(0.0D, tierScaling(definition.tier()).stakeMultiplier));
    }

    private static boolean prerequisiteMet(PlayerPerkData data, QuestDefinition definition) {
        return definition.prerequisiteId().isBlank()
                || data.getQuestState().hasCompletedTemplate(definition.prerequisiteId());
    }

    private static void grantRewards(ServerPlayer player, PlayerPerkData data,
                                     QuestProgress quest) {
        QuestDefinition definition = quest.definition();
        data.getQuestState().recordCompletion(definition.id(), definition.experience());
        // A repeatable quest whose reward is still unpicked must not restart, or the
        // next cycle would overwrite the choice before the player could take it.
        if (isRepeatable(definition) && definition.rewardChoices().isEmpty()) {
            long now = player.serverLevel().getGameTime();
            quest.setNextRepeatRewardAt(QuestRolling.saturatedAdd(now,
                    repeatRewardIntervalTicks(definition)));
            // Preserve Completed for the packet sent below; the following server tick
            // starts the next cycle under the same stable quest id.
            quest.scheduleRepeatReset(QuestRolling.saturatedAdd(now, 1L));
        }
        autoAcceptNewlyUnlockedCommon(data, definition);
        if (definition.type() == QuestType.CHALLENGE) {
            data.clearChallengePenalty();
        }
        // Any staked quest returns its stake on completion, not only a Challenge.
        int refund = quest.releaseSecurityDeposit();
        if (refund > 0) {
            if (GoldCurrency.enabled()) GoldCurrency.grant(data, refund);
            else player.giveExperiencePoints(refund);
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
        // Offered rewards wait for the player to pick one. Experience, gold and the
        // guaranteed rewards above are paid now regardless, so finishing a quest always
        // pays something even if the choice is left sitting.
        grantRewardItems(player, data, storage, definition.rewards());
        data.applyChosenPerks(player);
    }

    private static void grantRewardItems(ServerPlayer player, PlayerPerkData data,
                                         PlayerStorage storage,
                                         List<QuestDefinition.Reward> rewards) {
        for (QuestDefinition.Reward reward : rewards) {
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
    }

    /**
     * Hands over the reward the player picked from a finished quest's alternatives. The
     * choice is recorded before anything is granted, so a duplicated packet cannot pay
     * out twice.
     */
    public static boolean chooseReward(ServerPlayer player, String questId, int index) {
        PlayerPerkData data = PerkData.of(player);
        QuestProgress quest = find(data, questId);
        if (quest == null || !quest.awaitingRewardChoice()
                || !quest.chooseReward(index)) {
            return false;
        }
        grantRewardItems(player, data, data.getStorage(),
                List.of(quest.definition().rewardChoices().get(index)));
        data.applyChosenPerks(player);
        syncRewardState(player);
        ModNetworking.syncQuestsTo(player);
        return true;
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
            result.add(new QuestCompletionView(id, state.completionCount(id),
                    state.completionExperience(id)));
        }
        return result;
    }

    /** Offered rewards as display strings, in the same format as the reward summary. */
    private static List<String> rewardChoiceSummaries(QuestDefinition definition) {
        List<String> summaries = new ArrayList<>();
        for (QuestDefinition.Reward reward : definition.rewardChoices()) {
            summaries.add(QuestRewardSummary.entry(
                    reward.isVirtual() ? reward.virtualId() : reward.itemId(),
                    reward.count()));
        }
        return summaries;
    }

    private static List<QuestView.Requirement> requirementViews(QuestDefinition definition,
                                                                QuestProgress progress) {
        List<QuestDefinition.Requirement> requirements = definition.extraRequirements();
        if (requirements.isEmpty()) return List.of();
        List<QuestView.Requirement> views = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            QuestDefinition.Requirement requirement = requirements.get(index);
            views.add(new QuestView.Requirement(requirement.objective(),
                    requirement.targetId(), progress.extraProgress(index),
                    requirement.target()));
        }
        return views;
    }

    private static QuestView view(PlayerPerkData data, QuestProgress progress) {
        QuestDefinition definition = progress.definition();
        // Resolved once and reused: the view needs several template-derived values, and
        // each lookup would otherwise repeat the same catalogue search.
        QuestConfig.Template template = QuestConfig.get().template(definition.id());
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
            QuestRewardSummary.append(rewards,
                    reward.isVirtual() ? reward.virtualId() : reward.itemId(),
                    reward.count());
        }
        return new QuestView(definition.id(), definition.type(), definition.objective(),
                definition.targetId(),
                progress.progress(), definition.target(), progress.accepted(),
                progress.completed(), progress.cancelled(), progress.expired(),
                progress.expiresAt(), definition.experience(), definition.goldReward(),
                rewards.toString(),
                prerequisiteMet(data, definition), progress.securityDepositPaid(),
                repeatable, cycle,
                progress.nextRepeatRewardAt(), definition.tier(),
                // Resolved here rather than read from the catalog: the stake scales with
                // the rarity this quest rolled at, so it is not a fixed template value.
                progress.securityDepositPaid() > 0
                        ? progress.securityDepositPaid() : securityDeposit(definition),
                requirementViews(definition, progress),
                // Sent until a choice is taken, so the reward line can advertise the
                // options before the quest is finished, not only once it is claimable.
                progress.chosenRewardIndex() < 0
                        ? rewardChoiceSummaries(definition) : List.of());
    }
}
