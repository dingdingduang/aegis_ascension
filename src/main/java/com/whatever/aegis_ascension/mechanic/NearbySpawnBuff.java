package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.NEARBY_SPAWN_RADIUS;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.NearbySpawnBuffMapping;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.AegisModifiers;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strengthens mobs that spawn near a player whose talents declare it.
 *
 * <p>Which stat moves which attribute is declared by the {@code nearby_spawn_buffs} table
 * in talents.json, and a talent joins in by declaring one of those stats alongside a
 * {@code nearby_spawn_radius} - no talent and no attribute is named here. A new kind of
 * buff is therefore a row in that table plus a stat on a talent.</p>
 *
 * <p>Each contribution's modifier id is minted from the talent and the attribute it
 * moves, so several talents stack on one mob, one talent can move several attributes,
 * and every contribution stays recognisable later rather than being applied twice.</p>
 */
public final class NearbySpawnBuff {
    private static final String SPAWNED_MOB_BUFF = "spawned_mob_buff";

    private NearbySpawnBuff() {
    }

    public static void onMobJoined(Mob mob) {
        List<NearbySpawnBuffMapping> buffs = Perk.nearbySpawnBuffs().stream()
                .filter(NearbySpawnBuffMapping::enabled)
                .toList();
        if (buffs.isEmpty()) {
            return;
        }
        List<Perk> declaring = declaringTalents(buffs);
        if (declaring.isEmpty()) {
            return;
        }

        // Players are gathered once at the widest radius any talent asks for, rather
        // than once per talent, so adding talents costs no extra entity lookups.
        double widest = declaring.stream()
                .mapToDouble(perk -> perk.stat(NEARBY_SPAWN_RADIUS))
                .max()
                .orElse(0.0D);
        List<ServerPlayer> nearby = mob.level().getEntitiesOfClass(
                ServerPlayer.class,
                mob.getBoundingBox().inflate(widest)
        );
        if (nearby.isEmpty()) {
            return;
        }

        float maximumHealthBefore = mob.getMaxHealth();
        for (Perk perk : declaring) {
            if (!ownerNearby(mob, perk, nearby)) {
                continue;
            }
            for (NearbySpawnBuffMapping buff : buffs) {
                apply(mob, perk, buff);
            }
        }
        if (mob.getMaxHealth() > maximumHealthBefore) {
            // Topping up only once, and only when Max Health actually rose, keeps a mob
            // buffed by several talents from being healed part-way through - and keeps a
            // buff that never touched health from healing it for free.
            mob.setHealth(mob.getMaxHealth());
        }
    }

    /** Talents that declare a usable radius and at least one buff stat. */
    private static List<Perk> declaringTalents(List<NearbySpawnBuffMapping> buffs) {
        List<Perk> declaring = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            if (perk.stat(NEARBY_SPAWN_RADIUS) <= 0.0D) {
                continue;
            }
            for (NearbySpawnBuffMapping buff : buffs) {
                if (usableAmount(perk, buff) != 0.0D) {
                    declaring.add(perk);
                    break;
                }
            }
        }
        return declaring;
    }

    private static void apply(Mob mob, Perk perk, NearbySpawnBuffMapping buff) {
        double amount = usableAmount(perk, buff);
        if (amount == 0.0D
                || GeneralServerMethods.getAttributeInstance(mob, buff.attribute()) == null) {
            return;
        }
        UUID modifierId = modifierId(perk, buff);
        if (GeneralServerMethods.getAttributeModifier(
                mob, buff.attribute(), modifierId) != null) {
            return;
        }
        GeneralServerMethods.addAttributeModifier(
                mob,
                buff.attribute(),
                modifierId,
                AegisAscensionMod.MOD_ID + GeneralConstants.COLON + perk.id()
                        + GeneralConstants.SLASH + buff.stat(),
                amount,
                buff.operation()
        );
    }

    private static double usableAmount(Perk perk, NearbySpawnBuffMapping buff) {
        if (!perk.stats().containsKey(buff.stat())) {
            return 0.0D;
        }
        double amount = perk.stat(buff.stat());
        return Double.isFinite(amount) ? amount : 0.0D;
    }

    private static boolean ownerNearby(Mob mob, Perk perk, List<ServerPlayer> nearby) {
        double radius = Math.max(0.0D, perk.stat(NEARBY_SPAWN_RADIUS));
        if (radius <= 0.0D) {
            return false;
        }
        for (ServerPlayer player : nearby) {
            if (mob.distanceToSqr(player) > radius * radius) {
                continue;
            }
            boolean owns = PerkData.get(player)
                    .map(data -> owns(data.getPerkRanks(), perk))
                    .orElse(false);
            if (owns) {
                return true;
            }
        }
        return false;
    }

    private static boolean owns(Map<Perk, Integer> ranks, Perk perk) {
        Integer rank = ranks.get(perk);
        return rank != null && rank > 0;
    }

    private static UUID modifierId(Perk perk, NearbySpawnBuffMapping buff) {
        return AegisModifiers.mint(
                SPAWNED_MOB_BUFF + GeneralConstants.SLASH + perk.id()
                        + GeneralConstants.SLASH + buff.attribute()
        );
    }
}
