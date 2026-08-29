package com.whatever.aegis_ascension.aegis;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ActionCoreCompat;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.util.GeneralIronSpellSupportMethods;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Angel's Aegis grant driver.
 *
 * <p>This class only decides <em>when</em> a shield is granted; the shield itself
 * — decay, stacking, damage absorption, and the orbital model — is owned by
 * {@link ShieldMechanic}. While the aegis is active, an Action Core timer grants
 * one shield every {@linkplain #effectiveIntervalTicks effective interval}, whose
 * capacity is the player's primary attribute times the configured multiplier.
 * Cooldown reduction shortens the interval; nothing else about the shield lives
 * here.</p>
 */
public final class AngelsAegis {
    private static final String GRANT_TASK_PREFIX = "aegis_ascension:angels_shield_grant";

    /** How often the grant timer is re-evaluated against the current interval. */
    private static final int HEARTBEAT_TICKS = 5;

    private static final Map<UUID, TimerState> TIMERS = new ConcurrentHashMap<>();

    private AngelsAegis() {
    }

    /** Per-player Action Core timer bookkeeping. */
    private static final class TimerState {
        private String grantTaskId;
        private int grantPeriodTicks;
    }

    // ------------------------------------------------------------------
    // Interval and capacity formulas
    // ------------------------------------------------------------------

    /**
     * {@code effective_interval = max(min_shield_interval_seconds,
     * shield_interval_seconds
     * * (1 - cdr_percent * shield_interval_reduction_per_cooldown / 100))}.
     *
     * <p>Cooldown reduction is stored as a 0-1 fraction and
     * {@code shield_interval_reduction_per_cooldown} as "interval percent lost per
     * cooldown percent", so the two /100 terms in the spec's percent form cancel
     * and the fractional form is a plain product. However much cooldown reduction
     * shortens it, the effective interval is floored at
     * {@code min_shield_interval_seconds}.</p>
     */
    public static int effectiveIntervalTicks(PlayerPerkData data) {
        double baseSeconds = aegisStat(AegisConstants.SHIELD_INTERVAL_SECONDS);
        double reductionPerCooldown = aegisStat(
                AegisConstants.SHIELD_INTERVAL_REDUCTION_PER_COOLDOWN
        );
        double minSeconds = aegisStat(AegisConstants.MIN_SHIELD_INTERVAL_SECONDS);
        double cooldownReduction = TalentEffects.cooldownReduction(data);
        double scale = Math.max(0.0D, 1.0D - cooldownReduction * reductionPerCooldown);
        double effectiveSeconds = Math.max(minSeconds, baseSeconds * scale);
        return Math.max(1, (int) Math.round(effectiveSeconds * 20.0D));
    }

    /**
     * {@code Initial_Capacity = Primary_Stat * shield_primary_multiplier}.
     *
     * <p>The primary stat is the live value of the player's chosen primary skill
     * enhancement (for an "armor" primary, the player's armor), so the shield scales
     * with the actual stat. No primary chosen means no shield.</p>
     */
    public static float shieldCapacity(ServerPlayer player, PlayerPerkData data) {
        double primaryStat = GeneralIronSpellSupportMethods.primaryStat(player, data);
        double multiplier = aegisStat(AegisConstants.SHIELD_PRIMARY_MULTIPLIER);
        return (float) Math.max(0.0D, primaryStat * multiplier);
    }

    // ------------------------------------------------------------------
    // Grant timer
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (GeneralServerMethods.getEntityTickCount(player) % HEARTBEAT_TICKS != 0) {
            return;
        }
        PerkData.get(player).ifPresent(data ->
                heartbeat(player, data)
        );
    }

    private static void heartbeat(ServerPlayer player, PlayerPerkData data) {
        if (!data.isAegisEnabled(AegisConstants.ANGEL)) {
            stopTimer(player);
            return;
        }
        TimerState timer = TIMERS.computeIfAbsent(player.getUUID(), key -> new TimerState());

        // Restart the timer only when the effective interval actually moves, so a
        // cooldown-reduction change retimes the grant without resetting it every tick.
        int interval = effectiveIntervalTicks(data);
        if (timer.grantTaskId == null || timer.grantPeriodTicks != interval) {
            cancelTimer(player, timer);
            timer.grantTaskId = ActionCoreCompat.uniqueTaskId(GRANT_TASK_PREFIX);
            timer.grantPeriodTicks = interval;
            ActionCoreCompat.scheduleRepeating(
                    player,
                    timer.grantTaskId,
                    interval,
                    ActionCoreCompat.INFINITE,
                    AngelsAegis::grantShield
            );
        }
    }

    /** Timer callback: hand one Angel's shield to the shared mechanic. */
    private static void grantShield(LivingEntity living) {
        if (!(living instanceof ServerPlayer player) || player.isRemoved() || !player.isAlive()) {
            return;
        }
        PerkData.get(player).ifPresent(data -> {
            if (!data.isAegisEnabled(AegisConstants.ANGEL)) {
                return;
            }
            ShieldMechanic.addShield(player, shieldCapacity(player, data));
        });
    }

    // ------------------------------------------------------------------
    // Timer lifecycle
    // ------------------------------------------------------------------

    private static void cancelTimer(ServerPlayer player, TimerState timer) {
        if (timer.grantTaskId != null) {
            ActionCoreCompat.cancel(player, timer.grantTaskId);
            timer.grantTaskId = null;
            timer.grantPeriodTicks = 0;
        }
    }

    private static void stopTimer(ServerPlayer player) {
        TimerState timer = TIMERS.remove(player.getUUID());
        if (timer != null) {
            cancelTimer(player, timer);
        }
    }

    /** Re-arms the grant timer after logout, respawn, or dimension travel. */
    public static void resetTimer(ServerPlayer player) {
        stopTimer(player);
    }

    private static double aegisStat(String statKey) {
        return Aegis.byId(AegisConstants.ANGEL).map(aegis -> aegis.stat(statKey)).orElse(0.0D);
    }
}
