package com.whatever.aegis_ascension.lifecycle;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.AngelsAegis;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.ServerCatalogSync;
import com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Loader-neutral orchestration for player login, logout, respawn, and travel. */
public final class PlayerSessionLifecycle {
    private PlayerSessionLifecycle() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        repairNonFiniteHealth(player);
        ServerCatalogSync.begin(player);
        updateApplyAndSync(player, true);
    }

    public static void onPlayerLogout(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerCatalogSync.clear(serverPlayer);
            AngelsAegis.resetTimer(serverPlayer);
            ShieldMechanic.clear(serverPlayer);
            QuestManager.clearTransientState(serverPlayer);
        }
        PlayerDataLifecycle.onPlayerLogout(player.getUUID());
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        AngelsAegis.resetTimer(player);
        ShieldMechanic.clear(player);
        QuestManager.resetWalkTracking(player);
        updateApplyAndSync(player, false);
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        AngelsAegis.resetTimer(player);
        ShieldMechanic.onPlayerChangedDimension(player);
        QuestManager.resetWalkTracking(player);
        updateApplyAndSync(player, false);
    }

    private static void updateApplyAndSync(
            ServerPlayer player,
            boolean announceNewCharges
    ) {
        PerkData.get(player).ifPresent(data -> {
            AegisExperienceSystem.awardMilestones(player, data, announceNewCharges);
            SoulLinkEffects.refreshCachedState(player, data);
            data.applyChosenPerks(player);
            QuestManager.tick(player, data);
            SummonCompat.refreshOwnedSummons(player, data);
            ModNetworking.syncTo(player);
        });
    }

    /** Repairs a non-finite health value before it can permanently brick the player. */
    private static void repairNonFiniteHealth(ServerPlayer player) {
        if (GeneralServerMethods.repairNonFiniteVitals(player)) {
            AegisAscensionMod.getLogger().warn(
                    "Repaired dead-alive state for {} at login: absorption and/or health were "
                            + "non-finite. Players can also fix this themselves with /perk repair.",
                    player.getGameProfile().getName()
            );
        }
    }

}
