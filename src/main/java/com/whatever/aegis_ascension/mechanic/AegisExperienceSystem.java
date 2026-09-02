package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative progression bridge for vanilla experience and Aegis Ascension
 * Experience (AAE). AAE is deliberately a non-spendable progression track: vanilla XP
 * pays mod costs when Gold Currency is disabled; GoldCurrency owns those payments when
 * the optional economy is enabled.
 */
public final class AegisExperienceSystem {
    private static final long MAX_SAFE_EXPERIENCE = Long.MAX_VALUE;

    private AegisExperienceSystem() {
    }

    public record AwardResult(long requested, int previousRank, int currentRank) {
        public boolean rankChanged() {
            return previousRank != currentRank;
        }
    }

    public record MilestoneResult(PlayerPerkData.PerkMilestoneAwards perkAwards,
                                  int skillEnhancementsGranted,
                                  int aegisGranted,
                                  boolean progressionChanged) {
        public boolean changed() {
            return progressionChanged
                    || perkAwards.chargesGranted() > 0
                    || perkAwards.breakthroughsTriggered() > 0
                    || skillEnhancementsGranted > 0
                    || aegisGranted > 0;
        }
    }

    public record Snapshot(boolean usesMinecraftDefaultLevel,
                           int progressionLevel,
                           int aegisAscensionRank,
                           long aegisAscensionExperience,
                           long experienceToNextRank,
                           int maximumRank) {
    }

    public static boolean usesMinecraftDefaultLevel() {
        return PlatformServices.config().useMinecraftDefaultLevel();
    }

    /** Returns the level used by every Aegis Ascension progression/scaling path. */
    public static int effectiveLevel(Player player, PlayerPerkData data) {
        if (usesMinecraftDefaultLevel()) {
            return Math.max(0, player.experienceLevel);
        }
        normalize(data);
        return data.getAegisAscensionRank();
    }

    /** Returns the exact integer XP threshold for the supplied current rank. */
    public static long experienceToNextRank(int currentRank) {
        int rank = Math.max(1, currentRank);
        int maximum = PlatformServices.config().aegisAscensionMaximumRank();
        if (rank >= maximum) {
            return 0L;
        }
        double requirement = PlatformServices.config().aegisAscensionBaseXp()
                * Math.pow(PlatformServices.config().aegisAscensionGrowthRate(), rank - 1L);
        if (!Double.isFinite(requirement) || requirement >= MAX_SAFE_EXPERIENCE) {
            return MAX_SAFE_EXPERIENCE;
        }
        return Math.max(1L, (long) Math.ceil(requirement));
    }

    /** Adds AAE and performs as many rank-ups as the reward provides. */
    public static AwardResult addExperience(PlayerPerkData data, long amount) {
        int previousRank = data.getAegisAscensionRank();
        normalize(data);
        if (amount <= 0L || data.getAegisAscensionRank()
                >= PlatformServices.config().aegisAscensionMaximumRank()) {
            return new AwardResult(Math.max(0L, amount), previousRank,
                    data.getAegisAscensionRank());
        }

        long remaining = amount;
        int rank = data.getAegisAscensionRank();
        long experience = data.getAegisAscensionExperience();
        int maximum = PlatformServices.config().aegisAscensionMaximumRank();
        while (remaining > 0L && rank < maximum) {
            long needed = experienceToNextRank(rank);
            if (needed <= 0L) {
                rank = maximum;
                experience = 0L;
                break;
            }
            long combined = saturatingAdd(experience, remaining);
            if (combined < needed) {
                experience = combined;
                remaining = 0L;
            } else {
                remaining = combined - needed;
                rank++;
                experience = 0L;
            }
        }
        if (rank >= maximum) {
            rank = maximum;
            experience = 0L;
        }
        data.setAegisAscensionProgress(rank, experience);
        return new AwardResult(Math.max(0L, amount), previousRank, rank);
    }

    /** Clamps config changes and consumes any already-saved XP that crosses a threshold. */
    public static boolean normalize(PlayerPerkData data) {
        int oldRank = data.getAegisAscensionRank();
        long oldExperience = data.getAegisAscensionExperience();
        int rank = Math.max(1, Math.min(
                PlatformServices.config().aegisAscensionMaximumRank(), oldRank));
        long experience = Math.max(0L, oldExperience);
        int maximum = PlatformServices.config().aegisAscensionMaximumRank();
        while (rank < maximum) {
            long needed = experienceToNextRank(rank);
            if (needed <= 0L || experience < needed) {
                break;
            }
            experience -= needed;
            rank++;
        }
        if (rank >= maximum) {
            rank = maximum;
            experience = 0L;
        }
        if (rank != oldRank || experience != oldExperience) {
            data.setAegisAscensionProgress(rank, experience);
            return true;
        }
        return false;
    }

    /**
     * Grants experience in the currently selected progression currency. Every reward that
     * calls itself "Experience" must come through here: handing out vanilla XP while the
     * server runs on Aegis Ascension Experience moves nothing the player is progressing.
     */
    public static AwardResult grantExperience(ServerPlayer player,
                                              PlayerPerkData data,
                                              long amount) {
        long reward = Math.max(0L, amount);
        if (usesMinecraftDefaultLevel()) {
            if (reward > 0L) {
                player.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, reward));
            }
            return new AwardResult(reward, data.getAegisAscensionRank(),
                    data.getAegisAscensionRank());
        }
        // The vanilla branch above is already amplified by ServerGameplayHandler as the
        // XP orbs arrive, so the AAE bonus belongs here and only here.
        return addExperience(data, amplifyAegisExperience(data, reward));
    }

    /**
     * Grants whole levels of progression. On the Aegis Ascension track a "level" is a
     * rank, so this raises the rank directly and keeps the experience already banked
     * toward the next one - a reward must never cost the player progress they earned.
     *
     * <p>Charges for the new ranks are handed out by the milestone pass that already runs
     * each server tick. Awarding them inline would let a rank reward trigger a
     * Breakthrough that grants more ranks, and recurse.</p>
     */
    public static AwardResult grantLevels(ServerPlayer player, PlayerPerkData data,
                                          int levels) {
        int granted = Math.max(0, levels);
        if (usesMinecraftDefaultLevel()) {
            if (granted > 0) {
                player.giveExperienceLevels(granted);
            }
            return new AwardResult(granted, data.getAegisAscensionRank(),
                    data.getAegisAscensionRank());
        }
        normalize(data);
        int previousRank = data.getAegisAscensionRank();
        int maximum = PlatformServices.config().aegisAscensionMaximumRank();
        int newRank = Math.min(maximum, previousRank + granted);
        // normalize() holds banked experience at zero once the cap is reached; match it.
        long banked = newRank >= maximum ? 0L : data.getAegisAscensionExperience();
        data.setAegisAscensionProgress(newRank, banked);
        return new AwardResult(granted, previousRank, newRank);
    }

    /** Applies the owned talents' Aegis Ascension Experience bonus to a reward. */
    private static long amplifyAegisExperience(PlayerPerkData data, long reward) {
        if (reward <= 0L) {
            return 0L;
        }
        double multiplier = 1.0D + TalentEffects.aegisExperienceGainBonus(data);
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            return 0L;
        }
        double amplified = reward * multiplier;
        return !Double.isFinite(amplified)
                ? MAX_SAFE_EXPERIENCE
                : (long) Math.min(MAX_SAFE_EXPERIENCE, Math.max(0.0D, Math.round(amplified)));
    }

    /** Grants a quest's experience in the currently selected progression currency. */
    public static AwardResult grantQuestExperience(ServerPlayer player,
                                                   PlayerPerkData data,
                                                   long amount) {
        return grantExperience(player, data, amount);
    }

    /** Applies all level-based mod milestones using the selected progression source. */
    public static MilestoneResult awardMilestones(ServerPlayer player,
                                                  PlayerPerkData data,
                                                  boolean announce) {
        boolean progressionChanged = !usesMinecraftDefaultLevel() && normalize(data);
        int level = effectiveLevel(player, data);
        PlayerPerkData.PerkMilestoneAwards perkAwards =
                data.awardMilestonesForLevel(level);
        int skillEnhancementsGranted =
                data.awardSkillEnhancementMilestonesForLevel(level);
        int aegisGranted = data.awardAegisChargesForLevel(level);
        int immediateBreakthroughs = perkAwards.breakthroughsToTriggerImmediately();
        if (immediateBreakthroughs > 0) {
            TalentEffects.triggerBreakthroughs(player, data, immediateBreakthroughs);
        }
        if (skillEnhancementsGranted > 0) {
            TalentEffects.recalculateAttributes(player, data);
        }
        if (announce) {
            if (perkAwards.chargesGranted() > 0) {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.charge_awarded",
                        perkAwards.chargesGranted(), data.getSelectionCharges()));
            }
            if (skillEnhancementsGranted > 0) {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.skill_enhancement_charge_awarded",
                        skillEnhancementsGranted, data.getSkillEnhancementCharges()));
            }
            if (aegisGranted > 0) {
                player.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.aegis_charge_awarded",
                        aegisGranted, data.getAegisSelectionCharges()));
            }
        }
        return new MilestoneResult(perkAwards, skillEnhancementsGranted, aegisGranted,
                progressionChanged);
    }

    public static Snapshot snapshot(Player player, PlayerPerkData data) {
        boolean vanilla = usesMinecraftDefaultLevel();
        int rank = data.getAegisAscensionRank();
        int level = effectiveLevel(player, data);
        return new Snapshot(vanilla, level, rank,
                vanilla ? 0L : data.getAegisAscensionExperience(),
                vanilla ? 0L : experienceToNextRank(rank),
                PlatformServices.config().aegisAscensionMaximumRank());
    }

    public static void setRank(PlayerPerkData data, int rank) {
        int maximum = PlatformServices.config().aegisAscensionMaximumRank();
        data.setAegisAscensionProgress(Math.max(1, Math.min(maximum, rank)), 0L);
    }

    public static String experienceLabel(boolean vanilla) {
        return vanilla ? "XP" : "AAE";
    }

    private static long saturatingAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }
}
