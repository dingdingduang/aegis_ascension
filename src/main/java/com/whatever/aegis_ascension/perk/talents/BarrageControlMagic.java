package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.BARRAGE_DAMAGE_MULTIPLIER;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_BARRAGE_CONTROL_MAGIC;
import static com.whatever.aegis_ascension.perk.TalentConstants.PRIMARY_ATTRIBUTE_FLAT_PER_TRIGGER;
import static com.whatever.aegis_ascension.perk.TalentConstants.PRIMARY_ATTRIBUTE_GAIN_CHANCE_PER_BARRAGE;
import static com.whatever.aegis_ascension.perk.TalentConstants.PRIMARY_FLAT;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.GeneralIronSpellSupportMethods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Runtime implementation and optional-addon facade for Barrage Control Magic. */
public final class BarrageControlMagic {
    private BarrageControlMagic() {
    }

    /** Applies {@code (base damage + Primary Attribute) * configured multiplier}. */
    public static double scaleBaseDamage(LivingEntity caster, double baseDamage) {
        if (!Double.isFinite(baseDamage)) {
            return 0.0D;
        }
        if (!(caster instanceof ServerPlayer player)) {
            return Math.max(0.0D, baseDamage);
        }
        return PerkData.get(player).map(data -> {
            if (!data.owns(PERK_BARRAGE_CONTROL_MAGIC)) {
                return Math.max(0.0D, baseDamage);
            }
            Perk perk = requiredPerk();
            double primary = GeneralIronSpellSupportMethods.primaryStat(player, data);
            return Math.max(0.0D, baseDamage + primary)
                    * Math.max(0.0D, perk.stat(BARRAGE_DAMAGE_MULTIPLIER));
        }).orElse(Math.max(0.0D, baseDamage));
    }

    /** Primary Attribute addend for an immutable barrage launch snapshot. */
    public static double primaryAttributeDamage(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 0.0D;
        }
        return PerkData.get(player).map(data -> data.owns(PERK_BARRAGE_CONTROL_MAGIC)
                ? Math.max(0.0D,
                GeneralIronSpellSupportMethods.primaryStat(player, data))
                : 0.0D).orElse(0.0D);
    }

    /** Configurable final multiplier for an immutable barrage launch snapshot. */
    public static double damageMultiplier(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 1.0D;
        }
        return PerkData.get(player).map(data -> data.owns(PERK_BARRAGE_CONTROL_MAGIC)
                ? Math.max(0.0D, requiredPerk().stat(BARRAGE_DAMAGE_MULTIPLIER))
                : 1.0D).orElse(1.0D);
    }

    /** Rolls once for each successfully launched barrage volley. */
    public static boolean onBarrageFired(LivingEntity caster, String ignoredBarrageId) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        return PerkData.get(player)
                .map(data -> tryGrantPrimary(player, data))
                .orElse(false);
    }

    private static boolean tryGrantPrimary(ServerPlayer player, PlayerPerkData data) {
        if (!data.owns(PERK_BARRAGE_CONTROL_MAGIC)) {
            return false;
        }
        Perk perk = requiredPerk();
        double chance = Math.max(0.0D, Math.min(1.0D,
                perk.stat(PRIMARY_ATTRIBUTE_GAIN_CHANCE_PER_BARRAGE)));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return false;
        }
        double amount = perk.stat(PRIMARY_ATTRIBUTE_FLAT_PER_TRIGGER);
        if (!Double.isFinite(amount) || amount == 0.0D) {
            return false;
        }
        data.addCustomStat(PRIMARY_FLAT, amount);
        // Republishes the gain onto the player's attributes. No packet: this rolls on
        // every volley and never caps, so a full sync here would carry the whole quest
        // catalogue for a change the client already learns from vanilla's attribute
        // sync and the Custom Stats tab's own polling.
        TalentEffects.recalculateAttributes(player, data);
        return true;
    }

    private static Perk requiredPerk() {
        return Perk.byId(PERK_BARRAGE_CONTROL_MAGIC).orElseThrow();
    }
}
