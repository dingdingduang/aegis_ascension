package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.INTERVAL_SECONDS;
import static com.whatever.aegis_ascension.perk.TalentConstants.SHIELD_GAIN;
import static com.whatever.aegis_ascension.perk.TalentConstants.SHIELD_GAIN_PER_LEVEL;
import static com.whatever.aegis_ascension.perk.TalentConstants.SR_KOHARU_SPRITE;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.GeneralIronSpellSupportMethods;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;

/**
 * Koharu Sprite shield driver.
 *
 * <p>The parallel of {@code AngelsAegis} for the Koharu Sprite perk: it decides
 * <em>when</em> to grant a shield and hands it to the shared
 * {@link ShieldMechanic}, which owns decay, stacking, and absorption.</p>
 *
 * <p>While the perk is owned, the player periodically gains a shield equal to the
 * configured fraction of the player's Primary Attribute, plus Spring Blossom's
 * configured fraction per experience level. The shield uses the shared model.
 * The cadence reads {@code interval_seconds} from the perk when present, otherwise
 * {@link #DEFAULT_INTERVAL_SECONDS}, so it stays tunable from talents.json.</p>
 */
public final class KoharuShield {
    /** Fallback cadence when the perk defines no {@code interval_seconds} stat. */
    private static final double DEFAULT_INTERVAL_SECONDS = 2.0D;

    private KoharuShield() {
    }

    public static void tick(ServerPlayer player) {
        PerkData.get(player).ifPresent(data ->
                grant(player, data)
        );
    }

    private static void grant(ServerPlayer player, PlayerPerkData data) {
        Perk koharu = Perk.byId(SR_KOHARU_SPRITE).orElse(null);
        if (koharu == null || !data.owns(koharu.id())
                || !data.hasChosenPrimarySkillEnhancement()) {
            return;
        }
        double intervalSeconds = koharu.stat(INTERVAL_SECONDS);
        if (intervalSeconds <= 0.0D) {
            intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        }
        int interval = Math.max(1, (int) Math.round(intervalSeconds * 20.0D));
        if (GeneralServerMethods.getEntityTickCount(player) % interval != 0) {
            return;
        }
        int rank = Math.max(1, data.getRank(koharu));
        int level = Math.max(0, player.experienceLevel);
        double shieldRatio = (koharu.stat(SHIELD_GAIN)
                + koharu.stat(SHIELD_GAIN_PER_LEVEL) * level) * rank;
        float amount = (float) Math.max(0.0D,
                GeneralIronSpellSupportMethods.primaryStat(player, data) * shieldRatio);

        // Koharu's two formula stats also participate in the shared Shield Gain total.
        // Exclude this perk only for its own grant so the requested fraction is not
        // counted a second time; other bonuses and Alya's multiplier still apply.
        ShieldMechanic.addShieldExcludingPerkGain(player, amount, koharu.id());
    }
}
