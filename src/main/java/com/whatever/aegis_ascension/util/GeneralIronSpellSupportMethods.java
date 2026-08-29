package com.whatever.aegis_ascension.util;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.ToDoubleFunction;

public class GeneralIronSpellSupportMethods {
    public static double readCustomData(LivingEntity caster, ToDoubleFunction<PlayerPerkData> reader,
                               double fallback) {
        if (!(caster instanceof ServerPlayer player)) {
            return fallback;
        }
        return PerkData.get(player)
                .map(reader::applyAsDouble)
                .orElse(fallback);
    }

    /**
     * The live value of the caster's chosen primary skill enhancement, or 0.
     *
     * <p>This is the actual value of the mapped stat — for an "armor" primary, the
     * player's armor attribute (e.g. 8) — not {@link SkillEnhancement#amount()},
     * which is a fixed per-point coefficient (1.0 for armor) that never varies with
     * the build. Shields and wards scale off this value.</p>
     */
    public static double primaryStat(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player)) {
            return 0.0D;
        }
        return PerkData.get(player)
                .map(data -> primaryStat(player, data))
                .orElse(0.0D);
    }

    /** The chosen primary stat's value, resolved from its mapped attribute or custom stat. */
    public static double primaryStat(ServerPlayer player, PlayerPerkData data) {
        if (!data.hasChosenPrimarySkillEnhancement()) {
            return 0.0D;
        }
        SkillEnhancement primary = data.getPrimarySkillEnhancement();
        if (primary.attribute().isPresent()) {
            return GeneralServerMethods.getAttributeValue(player, primary.attribute().get());
        }
        if (primary.customStat().isPresent()) {
            return data.getCustomStat(primary.customStat().get());
        }
        return 0.0D;
    }
}
