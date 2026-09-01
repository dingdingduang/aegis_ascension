package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.AegisModifiers;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Talent multipliers and the timed, non-renewing Armor Shred debuff. */
public final class MakeUpWorkClub {
    private static final String STACKS_TAG = "aegis_ascension.make_up_armor_shred_stacks";
    private static final String EXPIRES_TAG = "aegis_ascension.make_up_armor_shred_expires";
    private static final UUID FLAT_ARMOR_MODIFIER_ID =
            AegisModifiers.adopt("c87d0ba5-b58c-47bb-a11e-b46b0ce1f1cb");
    private static final UUID ZERO_ARMOR_MODIFIER_ID =
            AegisModifiers.adopt("0373ae1c-d9dd-4649-baa3-f1c59d20cb66");

    private MakeUpWorkClub() {
    }

    public static double collectorMultiplier(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MAKE_UP_WORK_CLUB)
                ? Math.max(0.0D, stat(SOUL_MAKE_UP_WORK_CLUB, COLLECTOR_EFFECT_MULTIPLIER))
                : 1.0D;
    }

    public static double hanakoMultiplier(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MAKE_UP_WORK_CLUB)
                ? Math.max(0.0D, stat(SOUL_MAKE_UP_WORK_CLUB, HANAKO_EFFECT_MULTIPLIER))
                : 1.0D;
    }

    public static boolean negatesKoharuPenalty(PlayerPerkData data) {
        return data.hasActiveSoulLink(SOUL_MAKE_UP_WORK_CLUB)
                && stat(SOUL_MAKE_UP_WORK_CLUB, NEGATE_KOHARU_CRITICAL_PENALTY) > 0.0D;
    }

    public static void onDamageDealt(PlayerPerkData data, LivingEntity target) {
        if (!data.hasActiveSoulLink(SOUL_MAKE_UP_WORK_CLUB)) {
            return;
        }
        AttributeInstance armor = GeneralServerMethods.getAttributeInstance(target, Attributes.ARMOR);
        if (armor == null) {
            return;
        }

        CompoundTag persistentData = PlatformServices.entityData().persistentData(target);
        long now = target.level().getGameTime();
        if (persistentData.getLong(EXPIRES_TAG) <= now) {
            clear(target);
        }

        int maximum = Math.max(1, (int) Math.round(
                stat(SOUL_MAKE_UP_WORK_CLUB, ARMOR_SHRED_MAX_STACKS)
        ));
        int stacks = Math.min(maximum, persistentData.getInt(STACKS_TAG) + 1);
        if (!persistentData.contains(EXPIRES_TAG)) {
            long durationTicks = Math.max(1L, Math.round(
                    stat(SOUL_MAKE_UP_WORK_CLUB, ARMOR_SHRED_DURATION_SECONDS) * 20.0D
            ));
            persistentData.putLong(EXPIRES_TAG, now + durationTicks);
        }
        persistentData.putInt(STACKS_TAG, stacks);
        applyModifiers(target, armor, stacks, maximum);
    }

    public static void tick(LivingEntity entity) {
        CompoundTag persistentData = PlatformServices.entityData().persistentData(entity);
        if (persistentData.contains(EXPIRES_TAG)
                && persistentData.getLong(EXPIRES_TAG) <= entity.level().getGameTime()) {
            clear(entity);
        }
    }

    private static void applyModifiers(LivingEntity entity, AttributeInstance armor,
                                       int stacks, int maximum) {
        GeneralServerMethods.removeAttributeModifier(entity, Attributes.ARMOR, FLAT_ARMOR_MODIFIER_ID);
        GeneralServerMethods.removeAttributeModifier(entity, Attributes.ARMOR, ZERO_ARMOR_MODIFIER_ID);

        double armorPerStack = Math.abs(stat(
                SOUL_MAKE_UP_WORK_CLUB, ARMOR_SHRED_PER_STACK
        ));
        GeneralServerMethods.addTransientAttributeModifier(
                entity,
                Attributes.ARMOR,
                FLAT_ARMOR_MODIFIER_ID,
                "aegis_ascension:make_up_work_club_armor_shred",
                -armorPerStack * stacks,
                AttributeOperation.ADDITION
        );
        if (stacks >= maximum && stat(
                SOUL_MAKE_UP_WORK_CLUB, FORCE_ZERO_ARMOR_AT_MAX_STACKS
        ) > 0.0D) {
            GeneralServerMethods.addTransientAttributeModifier(
                    entity,
                    Attributes.ARMOR,
                    ZERO_ARMOR_MODIFIER_ID,
                    "aegis_ascension:make_up_work_club_zero_armor",
                    -1.0D,
                    AttributeOperation.MULTIPLY_TOTAL
            );
        }
    }

    private static void clear(LivingEntity entity) {
        CompoundTag persistentData = PlatformServices.entityData().persistentData(entity);
        persistentData.remove(STACKS_TAG);
        persistentData.remove(EXPIRES_TAG);
        AttributeInstance armor = GeneralServerMethods.getAttributeInstance(entity, Attributes.ARMOR);
        if (armor != null) {
            GeneralServerMethods.removeAttributeModifier(entity, Attributes.ARMOR, FLAT_ARMOR_MODIFIER_ID);
            GeneralServerMethods.removeAttributeModifier(entity, Attributes.ARMOR, ZERO_ARMOR_MODIFIER_ID);
        }
    }
}
