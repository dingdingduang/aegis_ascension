package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.BREAKTHROUGH_GOLD_MAX;
import static com.whatever.aegis_ascension.perk.TalentConstants.BREAKTHROUGH_GOLD_MIN;
import static com.whatever.aegis_ascension.perk.TalentConstants.IMMEDIATE_GOLD;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Gold paid out by talents: a one-off grant when the talent is taken, and a rolled
 * windfall on every Breakthrough.
 *
 * <p>Both halves exist only on a server running the economies they spend: the Aegis
 * Ascension Experience track for progression and Gold Currency for the payout. On a
 * vanilla-level server nothing pays Gold at all, so a talent's remaining effects stand
 * alone rather than paying a currency the player can never use.</p>
 *
 * <p>Driven by the {@code immediate_gold} and {@code breakthrough_gold_min}/{@code _max}
 * stats rather than by talent id, so a new talent joins by declaring them in
 * talents.json.</p>
 */
public final class TalentGoldRewards {
    private TalentGoldRewards() {
    }

    /** Whether this server pays talent Gold at all. */
    public static boolean active() {
        return GoldCurrency.enabled()
                && !AegisExperienceSystem.usesMinecraftDefaultLevel();
    }

    /** Pays a newly acquired talent's one-off Gold grant. Returns the rolled amount. */
    public static long grantImmediate(PlayerPerkData data, Perk perk) {
        if (!active() || perk == null) {
            return 0L;
        }
        long amount = clampToGold(perk.stat(IMMEDIATE_GOLD));
        GoldCurrency.grantReward(data, amount);
        return amount;
    }

    /**
     * Pays every owned talent's Breakthrough Gold, scaled by the Breakthrough
     * multiplier. Returns the total rolled before Gold reward bonuses amplify it.
     */
    public static long grantBreakthrough(ServerPlayer player, PlayerPerkData data,
                                         double breakthroughMultiplier) {
        if (!active()) {
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<Perk, Integer> entry : data.getPerkRanks().entrySet()) {
            Perk perk = entry.getKey();
            if (!perk.stats().containsKey(BREAKTHROUGH_GOLD_MIN)
                    && !perk.stats().containsKey(BREAKTHROUGH_GOLD_MAX)) {
                continue;
            }
            if (perk.manuallyToggleable() && !data.isTalentEnabled(perk.id())) {
                continue;
            }
            // Each rank rolls separately, so a multi-rank talent cannot be reduced to
            // one lucky or one unlucky roll.
            for (int rank = 0; rank < Math.max(0, entry.getValue()); rank++) {
                long amount = roll(
                        player.getRandom().nextDouble(),
                        perk.stat(BREAKTHROUGH_GOLD_MIN),
                        perk.stat(BREAKTHROUGH_GOLD_MAX),
                        breakthroughMultiplier
                );
                GoldCurrency.grantReward(data, amount);
                total = saturatingAdd(total, amount);
            }
        }
        return total;
    }

    /**
     * The Gold one roll pays. A reversed or malformed range is treated as the single
     * value that survives it rather than rolling something nonsensical.
     */
    public static long roll(double sample, double minimum, double maximum,
                            double breakthroughMultiplier) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || !Double.isFinite(breakthroughMultiplier) || !Double.isFinite(sample)) {
            return 0L;
        }
        double low = Math.max(0.0D, Math.min(minimum, maximum));
        double high = Math.max(0.0D, Math.max(minimum, maximum));
        double rolled = low + Math.min(1.0D, Math.max(0.0D, sample)) * (high - low);
        return clampToGold(rolled * Math.max(0.0D, breakthroughMultiplier));
    }

    private static long clampToGold(double amount) {
        if (!Double.isFinite(amount)) {
            return amount > 0.0D ? Long.MAX_VALUE : 0L;
        }
        return (long) Math.min(Long.MAX_VALUE, Math.max(0.0D, Math.round(amount)));
    }

    private static long saturatingAdd(long left, long right) {
        long sum = left + right;
        return ((left ^ sum) & (right ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }
}
