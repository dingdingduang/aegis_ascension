package com.whatever.aegis_ascension.compat;

import static com.whatever.aegis_ascension.perk.TalentConstants.AEGIS_ATTACK_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.TalentConstants.AEGIS_ATTACK_RANGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.AEGIS_ATTACK_SPEED_MULTIPLIER;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.AegisModifiers;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional, cached ownership bridge for Iron's Spells and Ars Nouveau summons. */
public final class SummonCompat {
    public static final String ARS_NOUVEAU_MOD_ID = "ars_nouveau";

    private static final String IRON_MAGIC_SUMMON =
            "io.redspace.ironsspellbooks.entity.mobs.MagicSummon";
    private static final String IRON_MAGIC_SUMMON_CURRENT =
            "io.redspace.ironsspellbooks.entity.mobs.IMagicSummon";
    private static final String ARS_SUMMON =
            "com.hollingsworth.arsnouveau.api.entity.ISummon";

    private static final UUID SUMMON_ATTACK_SPEED_MODIFIER_ID =
            AegisModifiers.adopt("0ec28c9a-0c4d-4717-958d-036f286bdab5");
    private static final UUID SUMMON_ENTITY_REACH_MODIFIER_ID =
            AegisModifiers.adopt("f2803352-b0a3-4218-bdee-392c35dba6b4");
    private static final UUID SUMMON_FOLLOW_RANGE_MODIFIER_ID =
            AegisModifiers.adopt("ffcbdb99-f4bd-4301-a3c5-2ace2b1b034c");

    private static final Map<Class<?>, SummonAccessor> ACCESSORS =
            new ConcurrentHashMap<>();

    private SummonCompat() {
    }

    public static boolean isAnySummonModLoaded() {
        return IronSpellsCompat.isLoaded()
                || PlatformServices.mods().isLoaded(ARS_NOUVEAU_MOD_ID);
    }

    /** Reapplies current bonuses to a newly joined summon after its owner is assigned. */
    public static void updateJoinedSummon(LivingEntity summon) {
        ServerPlayer owner = findOwner(summon);
        if (owner == null) {
            return;
        }
        PerkData.get(owner).ifPresent(data ->
                updateSummonAttributes(summon, data)
        );
    }

    /** Reapplies stack values to every loaded Iron/Ars summon owned by this player. */
    public static void refreshOwnedSummons(ServerPlayer owner, PlayerPerkData data) {
        if (!isAnySummonModLoaded()) {
            return;
        }
        for (ServerLevel level : owner.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && owner.equals(findOwner(living))) {
                    updateSummonAttributes(living, data);
                }
            }
        }
    }

    /** Returns summon-caused damage after Blessing's accumulated Attack multiplier. */
    public static float applyBlessingDamage(DamageSource source, float amount) {
        ServerPlayer owner = findSummonDamageOwner(source);
        if (owner == null) {
            return amount;
        }
        return PerkData.get(owner).map(data -> {
            double multiplier = Math.max(
                    0.0D,
                    1.0D + data.getCustomStat(AEGIS_ATTACK_MULTIPLIER)
            );
            return (float) Math.min(
                    Float.MAX_VALUE,
                    amount * multiplier
            );
        }).orElse(amount);
    }

    /** Resolves the player responsible for direct, projectile, or supported summon damage. */
    public static ServerPlayer findDamageOwner(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        ServerPlayer summonOwner = findSummonDamageOwner(source);
        if (summonOwner != null) {
            return summonOwner;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static ServerPlayer findSummonDamageOwner(DamageSource source) {
        ServerPlayer owner = findOwner(source.getEntity());
        if (owner != null) {
            return owner;
        }
        if (source.getDirectEntity() instanceof Projectile projectile) {
            return findOwner(projectile.getOwner());
        }
        return null;
    }

    private static ServerPlayer findOwner(Entity summon) {
        if (summon == null || summon instanceof ServerPlayer) {
            return null;
        }
        return ACCESSORS.computeIfAbsent(
                summon.getClass(),
                SummonCompat::discoverAccessor
        ).findOwner(summon);
    }

    private static SummonAccessor discoverAccessor(Class<?> type) {
        try {
            if (IronSpellsCompat.isLoaded()
                    && (implementsNamed(type, IRON_MAGIC_SUMMON)
                    || implementsNamed(type, IRON_MAGIC_SUMMON_CURRENT))) {
                return new SummonAccessor(type.getMethod("getSummoner"), null);
            }
            if (PlatformServices.mods().isLoaded(ARS_NOUVEAU_MOD_ID)
                    && implementsNamed(type, ARS_SUMMON)) {
                return new SummonAccessor(
                        type.getMethod("getOwnerAlt"),
                        type.getMethod("getOwnerUUID")
                );
            }
        } catch (ReflectiveOperationException exception) {
            AegisAscensionMod.getLogger().warn(
                    "Could not inspect summon ownership API for {}",
                    type.getName(),
                    exception
            );
        }
        return SummonAccessor.NONE;
    }

    private static boolean implementsNamed(Class<?> type, String interfaceName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented.getName().equals(interfaceName)
                        || implementsNamed(implemented, interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void updateSummonAttributes(LivingEntity summon, PlayerPerkData data) {
        updateModifier(
                summon,
                Attributes.ATTACK_SPEED,
                SUMMON_ATTACK_SPEED_MODIFIER_ID,
                "aegis_ascension:blessing_summon_attack_speed",
                data.getCustomStat(AEGIS_ATTACK_SPEED_MULTIPLIER),
                AttributeOperation.MULTIPLY_TOTAL
        );
        double range = data.getCustomStat(AEGIS_ATTACK_RANGE);
        updateModifier(
                summon,
                GeneralServerMethods.getEntityReachAttribute(),
                SUMMON_ENTITY_REACH_MODIFIER_ID,
                "aegis_ascension:blessing_summon_entity_reach",
                range,
                AttributeOperation.ADDITION
        );
        // FOLLOW_RANGE controls how far ordinary summon AI can acquire a target.
        updateModifier(
                summon,
                Attributes.FOLLOW_RANGE,
                SUMMON_FOLLOW_RANGE_MODIFIER_ID,
                "aegis_ascension:blessing_summon_follow_range",
                range,
                AttributeOperation.ADDITION
        );
    }

    private static void updateModifier(LivingEntity entity, Attribute attribute,
                                       UUID id, String name, double amount,
                                       AttributeOperation operation) {
        AttributeInstance instance = GeneralServerMethods.getAttributeInstance(entity, attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = GeneralServerMethods.getAttributeModifier(entity, attribute, id);
        if (current != null
                && Math.abs(current.getAmount() - amount) < 1.0E-9D
                && GeneralServerMethods.getAttributeOperation(current) == operation) {
            return;
        }
        if (current != null) {
            GeneralServerMethods.removeAttributeModifier(entity, attribute, id);
        }
        if (Math.abs(amount) >= 1.0E-9D) {
            GeneralServerMethods.addAttributeModifier(
                    entity,
                    attribute,
                    id,
                    name,
                    amount,
                    operation
            );
        }
    }

    private record SummonAccessor(Method entityOwner, Method ownerUuid) {
        private static final SummonAccessor NONE = new SummonAccessor(null, null);

        private ServerPlayer findOwner(Entity summon) {
            if (entityOwner == null) {
                return null;
            }
            try {
                Object result = entityOwner.invoke(summon);
                if (result instanceof ServerPlayer player) {
                    return player;
                }
                if (ownerUuid != null
                        && ownerUuid.invoke(summon) instanceof UUID uuid
                        && summon.level() instanceof ServerLevel level) {
                    return level.getServer().getPlayerList().getPlayer(uuid);
                }
            } catch (ReflectiveOperationException exception) {
                AegisAscensionMod.getLogger().debug(
                        "Could not resolve summon owner for {}",
                        summon.getType(),
                        exception
                );
            }
            return null;
        }
    }
}
