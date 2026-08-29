package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;

/** Runtime state for Sakuya's short-lived Perfection combat stacks. */
public final class PerfectAndElegantServant {
    private PerfectAndElegantServant() {
    }

    public static void onDamageDealt(ServerPlayer player, PlayerPerkData data,
                                     float damage) {
        if (damage <= 0.0F || !data.owns(R_PERFECT_AND_ELEGANT_SERVANT)) {
            return;
        }
        Perk perk = perk();
        int maximum = Math.max(0, (int) Math.round(
                perk.stat(PERFECTION_MAX_STACKS)
        ));
        if (maximum == 0) {
            clear(player, data);
            return;
        }
        int stacks = Math.min(maximum, Math.max(0, (int) Math.floor(
                data.getCustomStat(PERFECTION_STACKS)
        )) + 1);
        long durationTicks = Math.max(1L, Math.round(
                perk.stat(PERFECTION_DURATION_SECONDS) * 20.0D
        ));
        data.setCustomStat(PERFECTION_STACKS, stacks);
        data.setCustomStat(
                PERFECTION_EXPIRES_AT_TICK,
                player.level().getGameTime() + durationTicks
        );
        TalentEffects.recalculateAttributes(player, data);
    }

    public static void onDamageReceived(ServerPlayer player, PlayerPerkData data,
                                        float damage) {
        if (damage > 0.0F) {
            clear(player, data);
        }
    }

    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (data.getCustomStat(PERFECTION_STACKS) <= 0.0D) {
            return;
        }
        if (!data.owns(R_PERFECT_AND_ELEGANT_SERVANT)
                || player.level().getGameTime()
                >= (long) data.getCustomStat(PERFECTION_EXPIRES_AT_TICK)) {
            clear(player, data);
        }
    }

    public static double criticalChance(PlayerPerkData data) {
        return perStack(data, PERFECTION_CRITICAL_CHANCE_PER_STACK);
    }

    public static double criticalDamage(PlayerPerkData data) {
        return perStack(data, PERFECTION_CRITICAL_DAMAGE_PER_STACK);
    }

    public static double finalDamage(PlayerPerkData data) {
        return perStack(data, PERFECTION_FINAL_DAMAGE_PER_STACK);
    }

    private static double perStack(PlayerPerkData data, String stat) {
        if (!data.owns(R_PERFECT_AND_ELEGANT_SERVANT)) {
            return 0.0D;
        }
        return Math.max(0.0D, data.getCustomStat(PERFECTION_STACKS))
                * perk().stat(stat);
    }

    private static void clear(ServerPlayer player, PlayerPerkData data) {
        if (data.getCustomStat(PERFECTION_STACKS) == 0.0D
                && data.getCustomStat(PERFECTION_EXPIRES_AT_TICK) == 0.0D) {
            return;
        }
        data.setCustomStat(PERFECTION_STACKS, 0.0D);
        data.setCustomStat(PERFECTION_EXPIRES_AT_TICK, 0.0D);
        TalentEffects.recalculateAttributes(player, data);
    }

    private static Perk perk() {
        return Perk.byId(R_PERFECT_AND_ELEGANT_SERVANT).orElseThrow();
    }
}
