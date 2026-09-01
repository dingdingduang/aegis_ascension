package com.whatever.aegis_ascension.quest;

import com.whatever.aegis_ascension.util.GeneralConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/**
 * The arithmetic behind rolling a quest: which rarity it comes out at, which templates a
 * draw picks, how much it pays, and the saturating time addition its schedule uses.
 *
 * <p>Deliberately free of Minecraft and of {@link QuestConfig}, which cannot be loaded
 * outside a running game because it resolves a config path as it initialises. Keeping
 * these two decisions here makes them testable, and they are worth testing: a mistake in
 * either is silent. A rarity weighted wrong, or a rank gate applied to the wrong bound,
 * still produces perfectly ordinary quests, just at the wrong frequency, and that takes
 * hundreds of rolls to notice by playing.</p>
 */
public final class QuestRolling {

    private QuestRolling() {
    }

    /**
     * Scales a reward or target by a tier or cycle multiplier, never below one and never
     * past the int range. A zero base stays zero: a quest paying no experience must not
     * be scaled up into paying some.
     */
    public static int scaledValue(int base, double multiplier) {
        if (base <= 0) return 0;
        double scaled = base * multiplier;
        if (!Double.isFinite(scaled)) return Integer.MAX_VALUE;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.round(scaled)));
    }

    /**
     * Rolls one reward stack size. A fixed count is used unless either bound is set, in
     * which case the amount is drawn from the range.
     *
     * @param randomBelow returns a value in [0, bound) for the bound it is given
     */
    public static int rewardCount(int count, int countMin, int countMax,
                                  IntUnaryOperator randomBelow) {
        int fixed = Math.max(1, count);
        if (countMin <= 0 && countMax <= 0) return fixed;
        int min = Math.max(1, countMin > 0 ? countMin : fixed);
        int max = Math.max(min, countMax > 0 ? countMax : min);
        return min + (max > min ? randomBelow.applyAsInt(max - min + 1) : 0);
    }

    /** The rarer of two tier names; unrecognised names read as the commonest. */
    public static String higherTier(String left, String right) {
        int leftRank = GeneralConstants.rarityRank(GeneralConstants.rarityColor(left));
        int rightRank = GeneralConstants.rarityRank(GeneralConstants.rarityColor(right));
        return leftRank >= rightRank
                ? GeneralConstants.normalizeTier(left) : GeneralConstants.normalizeTier(right);
    }

    /**
     * Adds two values, stopping at the extremes instead of wrapping. Quest schedules are
     * game-time stamps, and a wrapped sum would land in the past and make a repeat
     * instantly ready rather than never ready.
     */
    public static long saturatedAdd(long left, long right) {
        long sum = left + right;
        // Overflow has occurred when both addends share a sign the sum does not.
        if (((left ^ sum) & (right ^ sum)) < 0) {
            return left > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return sum;
    }

    /** One rarity a template may roll at, separated from the catalogue type carrying it. */
    public record TierOption(String tier, int weight, int minimumRank) {
        public TierOption {
            tier = tier == null ? "" : tier;
        }
    }

    /**
     * Picks a rarity by weight from those the player has reached.
     *
     * @param progressionRank the player's rank; options above it are not eligible
     * @param fallback        returned when nothing is eligible, so a quest always rolls
     * @param random          a source of arbitrary longs, negative values included
     */
    public static String chooseTier(List<TierOption> options, int progressionRank,
                                    String fallback, LongSupplier random) {
        if (options == null || options.isEmpty()) return fallback;
        List<TierOption> eligible = new ArrayList<>(options.size());
        long totalWeight = 0L;
        for (TierOption option : options) {
            if (option == null || option.weight() <= 0
                    || progressionRank < Math.max(0, option.minimumRank())) {
                continue;
            }
            eligible.add(option);
            totalWeight += option.weight();
        }
        if (eligible.isEmpty() || totalWeight <= 0L) return fallback;
        long roll = Math.floorMod(random.getAsLong(), totalWeight);
        long cursor = 0L;
        for (TierOption option : eligible) {
            cursor += option.weight();
            if (roll < cursor) return option.tier();
        }
        return eligible.get(eligible.size() - 1).tier();
    }

    /**
     * Chooses which candidates a draw takes, as indices into the caller's list.
     *
     * <p>Chain continuations are taken first, in order, before the weighted draw fills
     * whatever slots remain. Finishing one stage of a story therefore always offers the
     * next, rather than leaving the rest of it to a lottery the player cannot influence.</p>
     *
     * @param weights      relative chance per candidate; zero or less is never drawn
     * @param continuation whether each candidate continues a chain already begun
     * @param count        how many to take; fewer are returned if the pool runs out
     */
    public static List<Integer> select(int[] weights, boolean[] continuation, int count,
                                       LongSupplier random) {
        List<Integer> selected = new ArrayList<>(Math.max(0, count));
        if (weights == null || count <= 0) return selected;
        boolean[] taken = new boolean[weights.length];

        for (int index = 0; index < weights.length && selected.size() < count; index++) {
            if (weights[index] > 0 && continuation != null && index < continuation.length
                    && continuation[index]) {
                selected.add(index);
                taken[index] = true;
            }
        }

        while (selected.size() < count) {
            long totalWeight = 0L;
            for (int index = 0; index < weights.length; index++) {
                if (!taken[index] && weights[index] > 0) totalWeight += weights[index];
            }
            if (totalWeight <= 0L) break;
            long roll = Math.floorMod(random.getAsLong(), totalWeight);
            long cursor = 0L;
            for (int index = 0; index < weights.length; index++) {
                if (taken[index] || weights[index] <= 0) continue;
                cursor += weights[index];
                if (roll < cursor) {
                    selected.add(index);
                    taken[index] = true;
                    break;
                }
            }
        }
        return selected;
    }
}
