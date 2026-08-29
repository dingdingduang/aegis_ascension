package com.whatever.aegis_ascension.command;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;

/** Administrative commands for resetting saved player progression. */
@Mod.EventBusSubscriber(modid = AegisAscensionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerkCommands {
    private PerkCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("perk")
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
     * Wipes a player's perks, Aegises and breakthroughs, then re-grants whatever their
     * current level entitles them to — the body of {@code /perk reset}.
     *
     * <p>Public so the Lethe's River Water virtual item performs the identical reset rather
     * than reimplementing it; a second copy would drift the moment either side changed.
     * Deliberately leaves virtual storage and virtual-item use counts alone, matching the
     * command's existing behaviour.</p>
     */
    public static void resetProgression(ServerPlayer target) {
        PerkData.get(target).ifPresent(data -> {
            data.resetAll();
            PlayerPerkData.PerkMilestoneAwards perkAwards =
                    data.awardMilestonesForLevel(target.experienceLevel);
            data.awardSkillEnhancementMilestonesForLevel(target.experienceLevel);
            data.awardAegisChargesForLevel(target.experienceLevel);
            int immediateBreakthroughs = perkAwards.breakthroughsToTriggerImmediately();
            if (immediateBreakthroughs > 0) {
                TalentEffects.triggerBreakthroughs(
                        target,
                        data,
                        immediateBreakthroughs
                );
            }
            data.applyChosenPerks(target);
            SummonCompat.refreshOwnedSummons(target, data);
            ModNetworking.syncTo(target);
        });
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
