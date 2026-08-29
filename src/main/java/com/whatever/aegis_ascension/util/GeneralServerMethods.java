package com.whatever.aegis_ascension.util;

import com.whatever.aegis_ascension.platform.AttributeAccess;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Minecraft/Forge helpers used by gameplay and the logical server.
 *
 * <p>This class must never acquire a client-only dependency. Its implementation
 * can be replaced per target, for example by Forge 1.16.5 and NeoForge 26.1
 * source sets, while gameplay code keeps the same intent.</p>
 */
public final class GeneralServerMethods {
    private static final AttributeAccess ATTRIBUTES = PlatformServices.attributes();

    private GeneralServerMethods() {
    }

    public static int getEntityTickCount(Entity entity) {
        return entity.tickCount;
    }

    public static long getLevelGameTime(Level level) {
        return level.getGameTime();
    }

    public static double getPlayerArmor(LivingEntity entity) {
        return getAttributeValue(entity, Attributes.ARMOR);
    }

    public static double getAttributeValue(LivingEntity entity, Attribute attribute) {
        return ATTRIBUTES.getValue(entity, attribute, 0.0D);
    }

    public static double getAttributeValue(LivingEntity entity, ResourceLocation attributeId,
                                           double fallback) {
        return ATTRIBUTES.getValue(entity, attributeId, fallback);
    }

    public static double getAttributeBaseValue(LivingEntity entity, Attribute attribute,
                                               double fallback) {
        return ATTRIBUTES.getBaseValue(entity, attribute, fallback);
    }

    public static Attribute resolveAttribute(ResourceLocation attributeId) {
        return ATTRIBUTES.resolve(attributeId);
    }

    public static ResourceLocation getAttributeKey(Attribute attribute) {
        return ATTRIBUTES.keyOf(attribute);
    }

    public static Iterable<Attribute> getAllAttributes() {
        return ATTRIBUTES.allAttributes();
    }

    public static Attribute getEntityReachAttribute() {
        return ATTRIBUTES.entityReach();
    }

    public static Item resolveItem(ResourceLocation itemId) {
        return PlatformServices.registries().resolveItem(itemId);
    }

    public static ResourceLocation getItemKey(Item item) {
        return PlatformServices.registries().itemKey(item);
    }

    public static Iterable<Item> getAllItems() {
        return PlatformServices.registries().allItems();
    }

    public static ResourceLocation getEntityTypeKey(EntityType<?> entityType) {
        return PlatformServices.registries().entityTypeKey(entityType);
    }

    public static AttributeInstance getAttributeInstance(LivingEntity entity, Attribute attribute) {
        return ATTRIBUTES.getInstance(entity, attribute);
    }

    public static AttributeInstance getAttributeInstance(LivingEntity entity,
                                                         ResourceLocation attributeId) {
        return ATTRIBUTES.getInstance(entity, attributeId);
    }

    public static AttributeModifier getAttributeModifier(LivingEntity entity, Attribute attribute,
                                                         UUID modifierId) {
        return ATTRIBUTES.getModifier(entity, attribute, modifierId);
    }

    public static AttributeModifier getAttributeModifier(LivingEntity entity,
                                                         ResourceLocation attributeId,
                                                         UUID modifierId) {
        return ATTRIBUTES.getModifier(
                entity,
                ATTRIBUTES.resolve(attributeId),
                modifierId
        );
    }

    public static AttributeOperation getAttributeOperation(AttributeModifier modifier) {
        return ATTRIBUTES.operationOf(modifier);
    }

    public static Iterable<AttributeModifier> getAttributeModifiers(LivingEntity entity,
                                                                     Attribute attribute) {
        return ATTRIBUTES.getModifiers(entity, attribute);
    }

    public static Iterable<AttributeModifier> getAttributeModifiers(
            LivingEntity entity, Attribute attribute, AttributeOperation operation) {
        return ATTRIBUTES.getModifiers(entity, attribute, operation);
    }

    public static void removeAttributeModifier(LivingEntity entity, Attribute attribute,
                                               UUID modifierId) {
        ATTRIBUTES.removeModifier(entity, attribute, modifierId);
    }

    public static void removeAttributeModifier(LivingEntity entity, ResourceLocation attributeId,
                                               UUID modifierId) {
        ATTRIBUTES.removeModifier(entity, ATTRIBUTES.resolve(attributeId), modifierId);
    }

    public static void addAttributeModifier(LivingEntity entity, Attribute attribute,
                                            UUID modifierId, String name, double amount,
                                            AttributeOperation operation) {
        ATTRIBUTES.addPermanentModifier(entity, attribute, modifierId, name, amount, operation);
    }

    public static void addAttributeModifier(LivingEntity entity, ResourceLocation attributeId,
                                            UUID modifierId, String name, double amount,
                                            AttributeOperation operation) {
        ATTRIBUTES.addPermanentModifier(
                entity,
                ATTRIBUTES.resolve(attributeId),
                modifierId,
                name,
                amount,
                operation
        );
    }

    public static void addTransientAttributeModifier(LivingEntity entity, Attribute attribute,
                                                     UUID modifierId, String name, double amount,
                                                     AttributeOperation operation) {
        ATTRIBUTES.addTransientModifier(entity, attribute, modifierId, name, amount, operation);
    }

    public static boolean repairNonFiniteVitals(LivingEntity entity) {
        boolean badAbsorption = !Float.isFinite(entity.getAbsorptionAmount());
        boolean badHealth = !Float.isFinite(entity.getHealth());
        if (!badAbsorption && !badHealth) {
            return false;
        }
        if (badAbsorption) {
            entity.setAbsorptionAmount(0.0F);
        }
        if (badHealth) {
            entity.setHealth(Float.isFinite(entity.getMaxHealth()) ? entity.getMaxHealth() : 20.0F);
        }
        return true;
    }

    public static float getAbsorptionAmount(LivingEntity entity) {
        return entity.getAbsorptionAmount();
    }

    public static void setAbsorptionAmount(LivingEntity entity, float amount) {
        entity.setAbsorptionAmount(Float.isFinite(amount) ? Math.max(0.0F, amount) : 0.0F);
    }

    public static Level getEntityLevel(Entity entity) {
        return entity.level();
    }

    public static ServerLevel getServerLevel(ServerPlayer player) {
        return player.serverLevel();
    }

    public static void playSoundAt(LivingEntity entity, SoundEvent sound, SoundSource source,
                                   float volume, float pitch) {
        if (entity == null || sound == null) {
            return;
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                source, volume, pitch);
    }

    public static void spawnParticlesAt(ServerLevel level, ParticleOptions particle,
                                        double x, double y, double z, int count,
                                        double spread, double speed) {
        if (level == null || particle == null || count <= 0) {
            return;
        }
        level.sendParticles(particle, x, y, z, count, spread, spread, spread, speed);
    }

    public static int getTotalExperience(Player player) {
        int level = player.experienceLevel;
        int base;
        if (level <= 16) {
            base = level * level + 6 * level;
        } else if (level <= 31) {
            base = (int) (2.5D * level * level - 40.5D * level + 360.0D);
        } else {
            base = (int) (4.5D * level * level - 162.5D * level + 2220.0D);
        }
        int toNext = xpToNextLevel(level);
        return base + Math.round(player.experienceProgress * toNext);
    }

    private static int xpToNextLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    public static boolean consumeExperience(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (getTotalExperience(player) < cost) {
            return false;
        }
        player.giveExperiencePoints(-cost);
        return true;
    }
}
