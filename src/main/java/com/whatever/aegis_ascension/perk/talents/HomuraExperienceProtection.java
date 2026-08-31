package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_HOMURAS_BLESSING;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures Homura's Blessing XP before vanilla death processing and restores it
 * to the replacement player entity. Restoration sets an exact snapshot rather
 * than adding XP, while the matching XP-drop event is suppressed, preventing
 * duplication with keepInventory or another XP-preservation mod.
 */
public final class HomuraExperienceProtection {
    private static final Map<UUID, ExperienceSnapshot> PENDING =
            new ConcurrentHashMap<>();

    private HomuraExperienceProtection() {
    }

    public static void capture(ServerPlayer player) {
        boolean ownsBlessing = PerkData.get(player)
                .map(data -> data.owns(PERK_HOMURAS_BLESSING))
                .orElse(false);
        if (!ownsBlessing) {
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.put(player.getUUID(), new ExperienceSnapshot(
                player.experienceLevel,
                player.experienceProgress,
                player.totalExperience,
                GeneralServerMethods.getTotalExperience(player)
        ));
    }

    public static boolean isPending(UUID playerId) {
        return PENDING.containsKey(playerId);
    }

    public static void restore(ServerPlayer player) {
        ExperienceSnapshot snapshot = PENDING.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }

        int currentSpendableExperience = GeneralServerMethods.getTotalExperience(player);
        if (currentSpendableExperience < snapshot.spendableExperience()) {
            player.experienceLevel = snapshot.level();
            player.experienceProgress = snapshot.progress();
        }
        // totalExperience is consulted by several shop paths. Never add the snapshot
        // to a value another mod restored; only raise a lower value to the old total.
        player.totalExperience = Math.max(
                player.totalExperience,
                snapshot.totalExperience()
        );
    }

    public static void clear() {
        PENDING.clear();
    }

    private record ExperienceSnapshot(
            int level,
            float progress,
            int totalExperience,
            int spendableExperience
    ) {
    }
}
