package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;

/** Periodically converts discrete current-shield steps into a live Damage Bonus. */
public final class DominusLapidis {
    private DominusLapidis() {
    }

    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (!data.owns(PERK_DOMINUS_LAPIDIS)) {
            data.setCustomStat(DOMINUS_SHIELD_DAMAGE_BONUS, 0.0D);
            return;
        }
        Perk perk = Perk.byId(PERK_DOMINUS_LAPIDIS).orElseThrow();
        int interval = Math.max(1, (int) Math.round(
                perk.stat(SHIELD_DAMAGE_BONUS_INTERVAL_SECONDS) * 20.0D
        ));
        if (player.tickCount % interval != 0) {
            return;
        }
        double shieldPerStep = perk.stat(SHIELD_PER_DAMAGE_BONUS_STEP);
        double bonus = shieldPerStep <= 0.0D
                ? 0.0D
                : Math.floor(ShieldMechanic.totalShield(player) / shieldPerStep)
                * perk.stat(DAMAGE_BONUS_PER_SHIELD_STEP);
        data.setCustomStat(DOMINUS_SHIELD_DAMAGE_BONUS, Math.max(0.0D, bonus));
    }
}
