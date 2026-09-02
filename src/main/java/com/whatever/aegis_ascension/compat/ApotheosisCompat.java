package com.whatever.aegis_ascension.compat;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Optional, reflection-only integration with Apotheosis boss spawning. */
public final class ApotheosisCompat {
    private static final Map<Object, Object> ORIGINAL_RULES = new HashMap<>();
    private static Object rulesMap;

    private ApotheosisCompat() {
    }

    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!ModList.get().isLoaded("apotheosis")
                || (event.getSpawnType() != MobSpawnType.NATURAL
                && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Mob mob = event.getEntity();
        try {
            Field field = Class.forName(
                    "dev.shadowsoffire.apotheosis.adventure.AdventureConfig"
            ).getDeclaredField("BOSS_SPAWN_RULES");
            field.setAccessible(true);
            Object currentRules = field.get(null);
            if (!(currentRules instanceof Map<?, ?> map)) {
                return;
            }
            if (rulesMap != currentRules) {
                ORIGINAL_RULES.clear();
                rulesMap = currentRules;
            }
            restoreOriginalRules(map);
            Object ruleKey = level.dimension().location();
            Object rule = map.get(ruleKey);
            if (rule == null) {
                return;
            }
            double bonus = nearbyBonus(level, mob);
            if (bonus <= 0.0D) {
                return;
            }
            Method left = rule.getClass().getMethod("getLeft");
            Method right = rule.getClass().getMethod("getRight");
            Object currentChance = left.invoke(rule);
            double chance = ((Number) currentChance).doubleValue() + bonus;
            Object updated = Class.forName("org.apache.commons.lang3.tuple.Pair")
                    .getMethod("of", Object.class, Object.class)
                    .invoke(null, numericValue(chance, currentChance.getClass()), right.invoke(rule));
            ORIGINAL_RULES.putIfAbsent(ruleKey, rule);
            ((Map<Object, Object>) map).put(ruleKey, updated);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Apotheosis is optional and its internal config must not break this mod.
        }
    }

    private static void restoreOriginalRules(Map<?, ?> rules) {
        if (ORIGINAL_RULES.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutableRules = (Map<Object, Object>) rules;
        mutableRules.putAll(ORIGINAL_RULES);
    }

    private static double nearbyBonus(ServerLevel level, Mob mob) {
        Perk frenzy = Perk.byId(PERK_FRENZY).orElse(null);
        Perk xiaoGreen = Perk.byId(PERK_XIAO_GREEN).orElse(null);
        double frenzyRadius = frenzy == null ? 0.0D
                : Math.max(0.0D, frenzy.stat(NEARBY_SPAWN_RADIUS));
        double xiaoRadius = xiaoGreen == null ? 0.0D
                : Math.max(0.0D, xiaoGreen.stat(NEARBY_SPAWN_RADIUS));
        double range = Math.max(frenzyRadius, xiaoRadius);
        double bonus = 0.0D;
        for (ServerPlayer player : level.getEntitiesOfClass(
                ServerPlayer.class, mob.getBoundingBox().inflate(range))) {
            double distanceSquared = mob.distanceToSqr(player);
            bonus = Math.max(bonus, PerkData.get(player).map(data -> {
                double value = 0.0D;
                if (data.owns(PERK_FRENZY) && distanceSquared <= frenzyRadius * frenzyRadius) {
                    value += stat(PERK_FRENZY, APOTHEOSIS_BOSS_SPAWN_CHANCE);
                }
                if (data.owns(PERK_XIAO_GREEN) && distanceSquared <= xiaoRadius * xiaoRadius) {
                    value += stat(PERK_XIAO_GREEN, APOTHEOSIS_BOSS_SPAWN_CHANCE);
                }
                return value;
            }).orElse(0.0D));
        }
        return bonus;
    }

    private static Object numericValue(double value, Class<?> type) {
        if (type == Float.class || type == float.class) {
            return (float) value;
        }
        return value;
    }
}
