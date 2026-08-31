package com.whatever.aegis_ascension.command;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.quest.QuestManager;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
     * Wipes a player's perks, Aegises, AAE rank, and breakthroughs, then re-grants whatever
     * their currently selected progression source entitles them to — the body of
     * {@code /perk reset}.
     *
     * <p>Public so the Lethe's River Water virtual item performs the identical reset rather
     * than reimplementing it; a second copy would drift the moment either side changed.
     * Deliberately leaves ordinary virtual storage and ordinary virtual-item use counts
     * alone. Devour Aegis Core levels, banked copies, and unique-purchase history reset
     * with the Aegis progression they upgrade.</p>
     */
    public static void resetProgression(ServerPlayer target) {
        PerkData.get(target).ifPresent(data -> {
            data.resetAll();
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
}
