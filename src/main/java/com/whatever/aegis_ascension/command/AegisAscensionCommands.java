package com.whatever.aegis_ascension.command;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.quest.QuestManager;
import com.whatever.aegis_ascension.quest.QuestType;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.whatever.aegis_ascension.perk.TalentConstants.EXTRA_TALENT_SLOTS;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;

/** Administrative commands for saved progression, testing, and resets. */
@Mod.EventBusSubscriber(modid = AegisAscensionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AegisAscensionCommands {
    private AegisAscensionCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("aegis_ascension")
                        .then(Commands.literal("givevirtual")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            VirtualItems.all().forEach(
                                                    definition -> builder.suggest(definition.id));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> giveVirtual(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "id"),
                                                1
                                        ))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> giveVirtual(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                ))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> giveVirtual(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "id"),
                                                                IntegerArgumentType.getInteger(context, "count")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("quest")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("reload")
                                        .executes(context -> reloadQuests(context.getSource())))
                                .then(Commands.literal("reroll")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (QuestType type : QuestType.values()) {
                                                        builder.suggest(type.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> rerollQuests(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "type")))))
                                .then(Commands.literal("advance")
                                        .then(Commands.argument("quest", StringArgumentType.word())
                                                .executes(context -> advanceQuest(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "quest"), 0))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> advanceQuest(
                                                                context.getSource(),
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "quest"),
                                                                IntegerArgumentType.getInteger(context, "amount"))))))
                                .then(Commands.literal("why")
                                        .then(Commands.argument("template", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    QuestManager.templateIds()
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> explainQuest(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "template")))))
                                .then(Commands.literal("grant")
                                        .then(Commands.argument("template", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    QuestManager.templateIds()
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> grantQuest(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "template"),
                                                        null))
                                                .then(Commands.argument("tier", StringArgumentType.word())
                                                        .suggests((context, builder) -> {
                                                            builder.suggest(GeneralConstants.TIER_R);
                                                            builder.suggest(GeneralConstants.TIER_SR);
                                                            builder.suggest(GeneralConstants.TIER_SSR);
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> grantQuest(
                                                                context.getSource(),
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "template"),
                                                                StringArgumentType.getString(context, "tier")))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(context -> grantQuest(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "template"),
                                                                        StringArgumentType.getString(context, "tier"))))))))
                        .then(Commands.literal("stat")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("list")
                                        .executes(context -> statList(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> statList(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("get")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(STAT_KEYS)
                                                .executes(context -> statGet(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "key")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> statGet(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "key"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(STAT_KEYS)
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(context -> statWrite(
                                                                context.getSource(),
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "key"),
                                                                DoubleArgumentType.getDouble(context, "value"),
                                                                false))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(context -> statWrite(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "key"),
                                                                        DoubleArgumentType.getDouble(context, "value"),
                                                                        false))))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(STAT_KEYS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                        .executes(context -> statWrite(
                                                                context.getSource(),
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "key"),
                                                                DoubleArgumentType.getDouble(context, "amount"),
                                                                true))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(context -> statWrite(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "key"),
                                                                        DoubleArgumentType.getDouble(context, "amount"),
                                                                        true))))))
                        )
                        .then(Commands.literal("talent")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("talent", StringArgumentType.word())
                                                .suggests(TALENT_IDS)
                                                .executes(context -> talentAdd(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "talent")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> talentAdd(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "talent"))))))
                        )
                        .then(Commands.literal("aegis")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("aegis", StringArgumentType.word())
                                                .suggests(AEGIS_IDS)
                                                .executes(context -> aegisAdd(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "aegis")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> aegisAdd(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "aegis"))))))
                        )
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> resetAll(
                                        context.getSource(),
                                        context.getSource().getPlayerOrException()
                                ))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> resetAll(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("repair")
                                .executes(context -> repair(
                                        context.getSource(),
                                        context.getSource().getPlayerOrException()
                                ))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> repair(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("experience")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("get")
                                        .executes(context -> experienceGet(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> experienceGet(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                                .executes(context -> experienceAdd(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        LongArgumentType.getLong(context, "amount")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> experienceAdd(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                LongArgumentType.getLong(context, "amount"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                                .executes(context -> experienceSet(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        LongArgumentType.getLong(context, "amount")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> experienceSet(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                LongArgumentType.getLong(context, "amount"))))))
                        )
                        .then(Commands.literal("rank")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 1000))
                                                .executes(context -> rankSet(
                                                        context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "rank")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> rankSet(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                IntegerArgumentType.getInteger(context, "rank"))))))
                        )
                );
    }

    /**
     * Clears the dead-alive state (non-finite absorption or health) for a player.
     *
     * <p>Deliberately usable without an operator level. It is a no-op unless the player is
     * genuinely stuck, so it cannot be abused as a heal, and the stuck player is exactly the
     * one who needs it - on a server they may have no other way out, since NaN health can
     * neither die nor be healed.</p>
     */
    private static int repair(CommandSourceStack source, ServerPlayer target) {
        String name = target.getGameProfile().getName();
        if (!GeneralServerMethods.repairNonFiniteVitals(target)) {
            source.sendSuccess(
                    () -> getLiteralString("Nothing to repair: " + name
                            + " has valid health and absorption."),
                    false
            );
            return 0;
        }
        ModNetworking.syncTo(target);
        source.sendSuccess(
                () -> getLiteralString("Repaired the dead-alive state for " + name
                        + " (health " + target.getHealth() + ", absorption cleared)."),
                true
        );
        AegisAscensionMod.getLogger().warn("Repaired dead-alive state for {} via /perk repair", name);
        return 1;
    }

    /** Rereads questsetting.json without a restart, so catalogue edits can be tried live. */
    private static int reloadQuests(CommandSourceStack source) {
        int synced;
        try {
            synced = QuestManager.reloadCatalogue(source.getServer());
        } catch (RuntimeException exception) {
            // A malformed file must not take the server down mid-command.
            source.sendFailure(getLiteralString(
                    "Failed to reload the quest catalogue: " + exception.getMessage()));
            AegisAscensionMod.getLogger().error("Quest catalogue reload failed", exception);
            return 0;
        }
        source.sendSuccess(() -> getLiteralString(
                "Reloaded the quest catalogue and resynchronised " + synced + " player(s)."),
                true);
        return 1;
    }

    /** Drives an accepted quest's counters, to try completion without doing the work. */
    private static int advanceQuest(CommandSourceStack source, ServerPlayer target,
                                    String questId, int amount) {
        boolean[] advanced = {false};
        PerkData.get(target).ifPresent(data ->
                advanced[0] = QuestManager.forceProgress(target, data, questId, amount));
        if (!advanced[0]) {
            source.sendFailure(getLiteralString(
                    "No accepted, unfinished quest with id " + questId
                            + ". Rolled ids look like side_armorer_forge#side."));
            return 0;
        }
        source.sendSuccess(() -> getLiteralString(amount <= 0
                ? "Completed " + questId + "." : "Advanced " + questId + " by " + amount + "."),
                true);
        return 1;
    }

    /** Reports which gate is keeping a template out of a player's draw. */
    private static int explainQuest(CommandSourceStack source, ServerPlayer target,
                                    String templateId) {
        PerkData.get(target).ifPresent(data -> {
            for (String reason : QuestManager.explainTemplate(target, data, templateId)) {
                source.sendSuccess(() -> getLiteralString("  " + reason), false);
            }
        });
        return 1;
    }

    /** Regenerates one quest type immediately instead of waiting for its refresh. */
    private static int rerollQuests(CommandSourceStack source, ServerPlayer target,
                                    String typeName) {
        QuestType type;
        try {
            type = QuestType.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            source.sendFailure(getLiteralString("Unknown quest type: " + typeName));
            return 0;
        }
        PerkData.get(target).ifPresent(
                data -> QuestManager.rerollQuests(target, data, type));
        String name = target.getGameProfile().getName();
        source.sendSuccess(() -> getLiteralString("Rerolled " + type.name().toLowerCase()
                + " quests for " + name + ". Progress on the replaced quests is gone."),
                true);
        return 1;
    }

    /** Places a catalogue template straight into a log, bypassing the normal roll. */
    private static int grantQuest(CommandSourceStack source, ServerPlayer target,
                                  String templateId, String tier) {
        String[] granted = {null};
        PerkData.get(target).ifPresent(data ->
                granted[0] = QuestManager.grantTemplate(target, data, templateId, tier));
        if (granted[0] == null) {
            source.sendFailure(getLiteralString("Unknown quest template id: " + templateId));
            return 0;
        }
        String name = target.getGameProfile().getName();
        String rarity = tier == null ? "its own rolled rarity"
                : GeneralConstants.normalizeTier(tier);
        source.sendSuccess(() -> getLiteralString(
                "Granted " + granted[0] + " at " + rarity + " to " + name + "."), true);
        return 1;
    }

    private static int giveVirtual(CommandSourceStack source, ServerPlayer target,
                                   String id, int count) {
        if (!VirtualItems.exists(id)) {
            source.sendFailure(getLiteralString("Unknown virtual item id: " + id));
            return 0;
        }
        boolean[] stored = {false};
        PerkData.get(target).ifPresent(data ->
                stored[0] = data.getStorage().addVirtual(id, count));
        if (!stored[0]) {
            source.sendFailure(getLiteralString(
                    "Storage is full; could not give " + id + " to " + target.getGameProfile().getName()));
            return 0;
        }
        ModNetworking.syncStorageTo(target);
        source.sendSuccess(() -> getLiteralString(
                "Gave " + count + "x " + id + " to " + target.getGameProfile().getName()), true);
        return count;
    }

    /**
     * Wipes a player's perks, Aegises, and breakthroughs, then re-grants whatever their
     * currently selected progression source entitles them to — the body of
     * {@code /perk reset}.
     *
     * <p>A respec, not a punishment: the AAE rank and banked experience survive, so the
     * selection charges the player had spent all come back. Wiping the rank here would
     * make the re-grant vacuous under the default progression source, and would contradict
     * Lethe's River Water, which promises to restore the charges the player's level grants.
     * Only a death reset takes the progression track itself.</p>
     *
     * <p>Public so the Lethe's River Water virtual item performs the identical reset rather
     * than reimplementing it; a second copy would drift the moment either side changed.
     * Deliberately leaves ordinary virtual storage and ordinary virtual-item use counts
     * alone. Trinity Tea Party Swiss Rolls and Devour Aegis Cores reset with the progression
     * that awards them; Core unique-purchase history resets as well.</p>
     */
    public static void resetProgression(ServerPlayer target) {
        PerkData.get(target).ifPresent(data -> {
            data.resetChoices();
            QuestManager.tick(target, data);
            AegisExperienceSystem.awardMilestones(target, data, false);
            data.applyChosenPerks(target);
            SummonCompat.refreshOwnedSummons(target, data);
            ModNetworking.syncTo(target);
            ModNetworking.syncStorageTo(target);
            ModNetworking.syncShopTo(target);
        });
    }

    private static int experienceGet(CommandSourceStack source, ServerPlayer target) {
        PlayerPerkData data = PerkData.of(target);
        AegisExperienceSystem.Snapshot snapshot =
                AegisExperienceSystem.snapshot(target, data);
        source.sendSuccess(() -> getLiteralString(
                target.getGameProfile().getName() + " — Aegis Rank "
                        + snapshot.aegisAscensionRank() + ", "
                        + snapshot.aegisAscensionExperience() + "/"
                        + snapshot.experienceToNextRank() + " AAE"
                        + (snapshot.usesMinecraftDefaultLevel()
                        ? " (Minecraft level " + snapshot.progressionLevel() + " is active)"
                        : "")), false);
        return 1;
    }

    private static int experienceAdd(CommandSourceStack source, ServerPlayer target,
                                     long amount) {
        PlayerPerkData data = PerkData.of(target);
        AegisExperienceSystem.AwardResult result =
                AegisExperienceSystem.addExperience(data, amount);
        AegisExperienceSystem.awardMilestones(target, data, false);
        ModNetworking.syncTo(target);
        source.sendSuccess(() -> getLiteralString(
                "Added " + amount + " AAE to " + target.getGameProfile().getName()
                        + " (Rank " + result.currentRank() + ")."), true);
        return 1;
    }

    private static int experienceSet(CommandSourceStack source, ServerPlayer target,
                                     long amount) {
        PlayerPerkData data = PerkData.of(target);
        data.setAegisAscensionProgress(1, 0L);
        AegisExperienceSystem.addExperience(data, amount);
        AegisExperienceSystem.awardMilestones(target, data, false);
        ModNetworking.syncTo(target);
        source.sendSuccess(() -> getLiteralString(
                "Set Aegis Ascension Experience for " + target.getGameProfile().getName()
                        + " to " + amount + "."), true);
        return 1;
    }

    private static int rankSet(CommandSourceStack source, ServerPlayer target, int rank) {
        PlayerPerkData data = PerkData.of(target);
        AegisExperienceSystem.setRank(data, rank);
        AegisExperienceSystem.awardMilestones(target, data, false);
        ModNetworking.syncTo(target);
        source.sendSuccess(() -> getLiteralString(
                "Set Aegis Rank for " + target.getGameProfile().getName()
                        + " to " + data.getAegisAscensionRank() + "."), true);
        return 1;
    }

    private static int resetAll(CommandSourceStack source, ServerPlayer target) {
        resetProgression(target);
        source.sendSuccess(
                () -> getLiteralString(
                        "Reset all Aegis Ascension data for "
                                + target.getGameProfile().getName()
                                + "."
                ),
                true
        );
        return 1;
    }

    /**
     * Every stat key the catalogs can produce, plus whatever the target already carries so
     * a hand-set key completes next time. Suggestions are advisory: any key is accepted,
     * because addon catalogs and runtime-only stats are not enumerable here.
     */
    private static final SuggestionProvider<CommandSourceStack> TALENT_IDS =
            (context, builder) -> {
                Perk.values().stream()
                        .map(Perk::id)
                        .sorted()
                        .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> AEGIS_IDS =
            (context, builder) -> {
                Aegis.values().stream()
                        .map(Aegis::id)
                        .sorted()
                        .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    /**
     * Grants one talent outright, spending no selection charge and ignoring the offer
     * roll. Acquisition rules still apply: a talent at max rank, locked behind a Soul
     * Link, hidden by config, or blocked for want of a talent slot is refused with the
     * reason rather than forced into a state the mod could not otherwise reach.
     */
    private static int talentAdd(CommandSourceStack source, ServerPlayer target,
                                 String talentId) {
        Perk perk = Perk.byId(talentId).orElse(null);
        if (perk == null) {
            source.sendFailure(getLiteralString("Unknown talent: " + talentId));
            return 0;
        }
        PlayerPerkData data = PerkData.of(target);
        String name = target.getGameProfile().getName();
        if (!data.canAcquireTalent(perk)) {
            source.sendFailure(getLiteralString(
                    name + " cannot acquire " + perk.id() + ": " + refusalReason(data, perk)
            ));
            return 0;
        }
        if (!data.grantTalent(target, perk)) {
            source.sendFailure(getLiteralString(
                    "Failed to grant " + perk.id() + " to " + name + "."));
            return 0;
        }
        SummonCompat.refreshOwnedSummons(target, data);
        ModNetworking.syncTo(target);
        int rank = data.getRank(perk);
        source.sendSuccess(() -> getLiteralString(
                "Granted " + perk.id() + " to " + name
                        + (perk.maxRank() > 1 ? " (rank " + rank + "/" + perk.maxRank() + ")" : "")
                        + "."), true);
        return 1;
    }

    /** The specific rule that stopped an acquisition, for a message worth reading. */
    private static String refusalReason(PlayerPerkData data, Perk perk) {
        if (PlatformServices.config().isTalentHidden(perk.id())) {
            return "it is hidden by the server configuration";
        }
        if (!perk.isUnlockedForPool(data)) {
            return "its pool requirements are unmet";
        }
        if (!perk.canAcquire(data.getRank(perk))) {
            return "it is already at max rank " + perk.maxRank();
        }
        if (data.getRank(perk) == 0
                && data.getUniqueTalentCount() >= data.getMaxTalentSlots()
                && perk.stat(EXTRA_TALENT_SLOTS) <= 0.0D) {
            return "every talent slot is full (" + data.getUniqueTalentCount()
                    + "/" + data.getMaxTalentSlots() + ")";
        }
        return "the talent is not currently acquirable";
    }

    /** Grants one Aegis outright, spending no Aegis charge and ignoring the offer roll. */
    private static int aegisAdd(CommandSourceStack source, ServerPlayer target,
                                String aegisId) {
        Aegis aegis = Aegis.byId(aegisId).orElse(null);
        if (aegis == null) {
            source.sendFailure(getLiteralString("Unknown Aegis: " + aegisId));
            return 0;
        }
        PlayerPerkData data = PerkData.of(target);
        String name = target.getGameProfile().getName();
        if (!data.grantAegis(target, aegis)) {
            source.sendFailure(getLiteralString(
                    name + " already owns " + aegis.id() + "."));
            return 0;
        }
        SummonCompat.refreshOwnedSummons(target, data);
        ModNetworking.syncTo(target);
        source.sendSuccess(() -> getLiteralString(
                "Granted " + aegis.id() + " to " + name + "."), true);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> STAT_KEYS =
            (context, builder) -> {
                knownStatKeys(context.getSource()).stream()
                        .filter(key -> key.startsWith(builder.getRemainingLowerCase()))
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static Set<String> knownStatKeys(CommandSourceStack source) {
        Set<String> keys = new TreeSet<>();
        Perk.values().forEach(perk -> keys.addAll(perk.stats().keySet()));
        Aegis.values().forEach(aegis -> keys.addAll(aegis.stats().keySet()));
        SkillEnhancement.values().forEach(
                enhancement -> enhancement.customStat().ifPresent(keys::add));
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            PerkData.get(player).ifPresent(
                    data -> keys.addAll(data.getCustomStats().keySet()));
        }
        return keys;
    }

    private static int statList(CommandSourceStack source, ServerPlayer target) {
        PlayerPerkData data = PerkData.of(target);
        Map<String, Double> stats = new TreeMap<>(data.getCustomStats());
        if (stats.isEmpty()) {
            source.sendSuccess(() -> getLiteralString(
                    target.getGameProfile().getName() + " has no custom stats set."), false);
            return 0;
        }
        StringBuilder text = new StringBuilder(
                target.getGameProfile().getName() + " - " + stats.size() + " custom stat(s):");
        stats.forEach((key, value) -> text.append("\n  ").append(key).append(" = ").append(value));
        source.sendSuccess(() -> getLiteralString(text.toString()), false);
        return stats.size();
    }

    private static int statGet(CommandSourceStack source, ServerPlayer target, String key) {
        double value = PerkData.of(target).getCustomStat(key);
        source.sendSuccess(() -> getLiteralString(
                target.getGameProfile().getName() + " - " + key + " = " + value), false);
        return 1;
    }

    /**
     * Writes a custom stat for testing. Values near zero are dropped by
     * {@link PlayerPerkData#setCustomStat}, so {@code set <key> 0} clears the entry.
     * Stats their own systems recompute - walk damage, frostbite - are overwritten again
     * on the next tick; the ones read straight from the map, such as skill_damage, stick.
     */
    private static int statWrite(CommandSourceStack source, ServerPlayer target,
                                 String key, double value, boolean add) {
        PlayerPerkData data = PerkData.of(target);
        if (add) {
            data.addCustomStat(key, value);
        } else {
            data.setCustomStat(key, value);
        }
        data.applyChosenPerks(target);
        ModNetworking.syncTo(target);
        double updated = data.getCustomStat(key);
        source.sendSuccess(() -> getLiteralString(
                (add ? "Added " + value + " to " : "Set ") + key + " for "
                        + target.getGameProfile().getName() + "; now " + updated + "."), true);
        return 1;
    }
}
