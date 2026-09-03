package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.ARROW_DAMAGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.INDEPENDENT_ARROW_DAMAGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.ARROW_VELOCITY;
import static com.whatever.aegis_ascension.perk.TalentConstants.DRAW_SPEED;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.compat.ApothicAttributesCompat;
import com.whatever.aegis_ascension.data.PerkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;

/**
 * Native handling for the three archery stats talents accumulate on a kill trigger.
 *
 * <p>Each is published to Apothic Attributes when that mod is present, and Apothic then
 * owns the effect - so every method here stands down once the matching attribute is
 * live, exactly as the dodge roll does. Without Apothic these would otherwise be numbers
 * that accumulate and do nothing.</p>
 */
public final class ArcheryStats {
    /** A bow barely drawn is not a shot; matches the loosed-arrow check in ForgeEvents. */
    private static final double MINIMUM_LOOSED_SPEED_SQUARED = 0.05D;
    /** Marks an arrow already sped up, so re-entering a level cannot compound it. */
    private static final String VELOCITY_APPLIED_TAG =
            "aegis_ascension:arrow_velocity_applied";

    private ArcheryStats() {
    }

    /**
     * Multiplier for a hit this player's own arrow is delivering.
     *
     * <p>Independent Arrow Damage is a separate multiplier rather than another entry in
     * the Arrow Damage bucket, so it multiplies whatever that bucket already grants. It
     * is this mod's own stat and is never published to Apothic, so - unlike Arrow Damage
     * itself - it keeps applying when that mod owns the attribute.</p>
     */
    public static double arrowDamageMultiplier(ServerPlayer attacker, PlayerPerkData data,
                                               DamageSource source) {
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)
                || arrow.getOwner() != attacker) {
            return 1.0D;
        }
        double multiplier = 1.0D + Math.max(-1.0D, independentArrowDamage(data));
        if (!ApothicAttributesCompat.handlesMappedAttribute(attacker, ARROW_DAMAGE)) {
            multiplier *= 1.0D + Math.max(-1.0D, data.getCustomStat(ARROW_DAMAGE));
        }
        return multiplier;
    }

    /** Independent Arrow Damage from talents, Soul Links, and accumulated stats. */
    public static double independentArrowDamage(PlayerPerkData data) {
        return data.getCustomStat(INDEPENDENT_ARROW_DAMAGE)
                + TalentStatService.sumOwnedStat(data, INDEPENDENT_ARROW_DAMAGE)
                + data.getActiveSoulLinks().stream()
                .mapToDouble(link -> link.bonusStat(INDEPENDENT_ARROW_DAMAGE))
                .sum();
    }

    /**
     * Speeds up an arrow the moment it is loosed. Vanilla scales an arrow's damage by
     * its speed, so a faster arrow hits harder for the same reason a fully drawn bow
     * does - that is the stat's meaning in Apothic too, and this keeps the two agreeing.
     */
    public static void onArrowLoosed(AbstractArrow arrow) {
        CompoundTag projectileData = arrow.getPersistentData();
        // An arrow re-enters its level on chunk reload and on dimension change, and it
        // is still in flight when it does. Without this the same shot would be sped up
        // again every time, compounding for as long as it lives.
        if (projectileData.getBoolean(VELOCITY_APPLIED_TAG)
                || !(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        Vec3 velocity = arrow.getDeltaMovement();
        if (velocity.lengthSqr() <= MINIMUM_LOOSED_SPEED_SQUARED
                || ApothicAttributesCompat.handlesMappedAttribute(player, ARROW_VELOCITY)) {
            return;
        }
        projectileData.putBoolean(VELOCITY_APPLIED_TAG, true);
        PerkData.get(player).ifPresent(data -> {
            double bonus = data.getCustomStat(ARROW_VELOCITY);
            if (!Double.isFinite(bonus) || bonus <= 0.0D) {
                return;
            }
            arrow.setDeltaMovement(velocity.scale(1.0D + bonus));
        });
    }

    /**
     * Extra progress on a bow or crossbow pull. A fractional bonus is spent as a chance
     * at one extra tick, so a small bonus averages out over a draw instead of rounding
     * away to nothing on every single tick.
     *
     * @return the ticks to remove from the remaining use duration.
     */
    public static int drawSpeedTickBonus(ServerPlayer player, ItemStack usedItem) {
        if (!(usedItem.getItem() instanceof BowItem)
                && !(usedItem.getItem() instanceof CrossbowItem)) {
            return 0;
        }
        if (ApothicAttributesCompat.handlesMappedAttribute(player, DRAW_SPEED)) {
            return 0;
        }
        return PerkData.get(player).map(data -> {
            double bonus = data.getCustomStat(DRAW_SPEED);
            if (!Double.isFinite(bonus) || bonus <= 0.0D) {
                return 0;
            }
            int whole = (int) bonus;
            double fraction = bonus - whole;
            return whole + (player.getRandom().nextDouble() < fraction ? 1 : 0);
        }).orElse(0);
    }
}
