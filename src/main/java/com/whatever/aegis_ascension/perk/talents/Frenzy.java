package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Applies Frenzy's one-time permanent Max Health modifier to nearby spawned mobs. */
public final class Frenzy {
    private static final UUID MAX_HEALTH_MODIFIER_ID =
            UUID.fromString("e9e8df40-5d23-4094-95c1-f182cd97039c");

    private Frenzy() {
    }

    public static void onMobJoined(Mob mob) {
        Perk frenzy = Perk.byId(PERK_FRENZY).orElse(null);
        if (frenzy == null) {
            return;
        }
        double radius = Math.max(0.0D, frenzy.stat(NEARBY_SPAWN_RADIUS));
        boolean ownerNearby = false;
        for (ServerPlayer player : mob.level().getEntitiesOfClass(
                ServerPlayer.class,
                mob.getBoundingBox().inflate(radius)
        )) {
            if (mob.distanceToSqr(player) <= radius * radius
                    && PerkData.get(player).map(data -> data.owns(PERK_FRENZY))
                    .orElse(false)) {
                ownerNearby = true;
                break;
            }
        }
        if (!ownerNearby) {
            return;
        }
        if (GeneralServerMethods.getAttributeInstance(mob, Attributes.MAX_HEALTH) == null
                || GeneralServerMethods.getAttributeModifier(
                mob, Attributes.MAX_HEALTH, MAX_HEALTH_MODIFIER_ID
        ) != null) {
            return;
        }
        GeneralServerMethods.addAttributeModifier(
                mob,
                Attributes.MAX_HEALTH,
                MAX_HEALTH_MODIFIER_ID,
                "aegis_ascension:frenzy_spawned_mob_health",
                frenzy.stat(SPAWNED_MOB_MAX_HEALTH_MULTIPLIER),
                AttributeOperation.MULTIPLY_TOTAL
        );
        mob.setHealth(mob.getMaxHealth());
    }
}
