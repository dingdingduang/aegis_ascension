package com.whatever.aegis_ascension.aegis;

import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_DURATION_SECONDS;
import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_WARD_I_COUNT;
import static com.whatever.aegis_ascension.perk.TalentConstants.DIVINE_SAKURA_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.MAX_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.R_DIVINE_SAKURA_POWER;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ActionCoreCompat;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fox God's Aegis ward driver.
 *
 * <p>While the aegis is active, the three configured Iron's Spells ward spells are
 * cast once per cycle to summon stationary ward entities at the player. The ward
 * entities — not this class — own their lifespan and periodic actions; here we only
 * decide how many wards of each type to summon and how often to refresh them.</p>
 *
 * <p>{@code ward_summon_count} adds a ward of every type, Divine Sakura Power C5 adds
 * Ward Type I wards, and C2 extends the refresh cycle. Casting is routed through the
 * Action Core action pipeline, and every Iron's Spells / Action Core reference is
 * reached through an isolated compat bridge, so this class loads even without those
 * mods.</p>
 */
public final class FoxAegis {
    private static final int HEARTBEAT_TICKS = 5;
    private static final int TICKS_PER_SECOND = 20;

    /** Last game tick each player's wards were summoned. */
    private static final Map<UUID, Long> LAST_SUMMON = new ConcurrentHashMap<>();

    private FoxAegis() {
    }

    public static void tick(ServerPlayer player) {
        if (GeneralServerMethods.getEntityTickCount(player) % HEARTBEAT_TICKS != 0) {
            return;
        }
        PerkData.get(player).ifPresent(data ->
                heartbeat(player, data)
        );
    }

    private static void heartbeat(ServerPlayer player, PlayerPerkData data) {
        if (!data.isAegisEnabled(AegisConstants.FOX_GOD)) {
            LAST_SUMMON.remove(player.getUUID());
            return;
        }
        long now = GeneralServerMethods.getLevelGameTime(player.level());
        Long last = LAST_SUMMON.get(player.getUUID());
        int constellation = constellationCount(data);
        if (last == null || now - last >= durationTicks(data, constellation)) {
            summonWards(player, data, constellation);
            LAST_SUMMON.put(player.getUUID(), now);
        }
    }

    /** Casts each ward spell the configured number of times, summoning the wards. */
    private static void summonWards(ServerPlayer player, PlayerPerkData data, int constellation) {
        castWard(player, FoxAegisWards.wardI(), wardCount(FoxAegisWards.wardI(), true, constellation));
        castWard(player, FoxAegisWards.wardII(), wardCount(FoxAegisWards.wardII(), false, constellation));
        castWard(player, FoxAegisWards.wardIII(), wardCount(FoxAegisWards.wardIII(), false, constellation));
    }

    private static void castWard(ServerPlayer player, FoxAegisWards.WardType ward, int casts) {
        if (ward.spellId().isBlank() || casts <= 0) {
            return;
        }
        int level = ward.spellLevel();
        // The Iron's Spells casts are performed as an Action Core action payload.
        ActionCoreCompat.dispatch(player, actor -> {
            for (int i = 0; i < casts; i++) {
                IronSpellsCompat.castSpell(player, ward.spellId(), level);
            }
        });
    }

    // ------------------------------------------------------------------
    // Counts, cadence, and constellations
    // ------------------------------------------------------------------

    private static double aegisStat(String statKey) {
        return Aegis.byId(AegisConstants.FOX_GOD).map(aegis -> aegis.stat(statKey)).orElse(0.0D);
    }

    /** Wards summoned of a type: its config count + ward_summon_count (+ C5 for Ward I). */
    private static int wardCount(FoxAegisWards.WardType ward, boolean isWardTypeI, int constellation) {
        int count = ward.count();
        count += (int) Math.max(0.0D, aegisStat(AegisConstants.WARD_SUMMON_COUNT));
        if (isWardTypeI && constellation >= 5) {
            count += (int) Math.max(0.0D, requiredTalent().stat(CONSTELLATION_WARD_I_COUNT));
        }
        return Math.max(1, count);
    }

    /**
     * Effective constellation count for the caster, or {@code -1} when Divine Sakura
     * Power is not owned. Both unlock paths feed this — obtaining the talent again
     * (rank) and spending experience (stored counter) — capped at the talent's max.
     */
    public static int constellationCount(PlayerPerkData data) {
        Perk talent = Perk.byId(R_DIVINE_SAKURA_POWER).orElse(null);
        if (talent == null || !data.owns(talent.id())) {
            return -1;
        }
        int fromRanks = Math.max(0, data.getRank(talent) - 1);
        int fromExperience = (int) Math.max(0.0D,
                data.getCustomStat(DIVINE_SAKURA_CONSTELLATIONS));
        int max = (int) Math.max(0.0D, talent.stat(MAX_CONSTELLATIONS));
        return Math.min(max, fromRanks + fromExperience);
    }

    /** C2 extends the re-summon cycle. */
    private static int durationTicks(PlayerPerkData data, int constellation) {
        double seconds = FoxAegisWards.durationSeconds();
        if (constellation >= 2) {
            seconds += requiredTalent().stat(CONSTELLATION_DURATION_SECONDS);
        }
        return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
    }

    private static Perk requiredTalent() {
        return Perk.byId(R_DIVINE_SAKURA_POWER).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Cleanup (wards despawn themselves on their lifespan)
    // ------------------------------------------------------------------

    public static void resetSummonTimer(ServerPlayer player) {
        LAST_SUMMON.remove(player.getUUID());
    }
}
