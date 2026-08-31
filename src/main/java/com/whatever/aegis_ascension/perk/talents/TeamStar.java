package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Global multiplayer Team Star bonus; deliberately ignores distance and dimension. */
public final class TeamStar {
    private TeamStar() {
    }

    public static double damageBonus(Player beneficiary) {
        MinecraftServer server = beneficiary.getServer();
        if (server == null) {
            return 0.0D;
        }
        Perk perk = Perk.byId(PERK_TEAM_STAR).orElse(null);
        if (perk == null) {
            return 0.0D;
        }
        int maximumStacks = Math.max(0, (int) Math.round(
                perk.stat(TEAM_DAMAGE_BONUS_MAX_STACKS)
        ));
        int owners = 0;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (PerkData.get(online).map(data -> data.owns(PERK_TEAM_STAR)).orElse(false)
                    && ++owners >= maximumStacks) {
                break;
            }
        }
        return Math.min(owners, maximumStacks) * perk.stat(TEAM_DAMAGE_BONUS);
    }

    /** Caches the global total for Custom Stats source attribution once per second. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (player.tickCount % 20 == 0) {
            data.setCustomStat(TEAM_DAMAGE_BONUS_ACTIVE, damageBonus(player));
        }
    }
}
