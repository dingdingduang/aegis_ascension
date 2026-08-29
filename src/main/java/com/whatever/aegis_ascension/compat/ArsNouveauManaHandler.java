package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Ars-linked event subscriber. This class must only be loaded through
 * {@link ArsNouveauCompat#registerOptionalHandlers()} after Ars is present.
 */
public final class ArsNouveauManaHandler {
    private ArsNouveauManaHandler() {
    }

    @SubscribeEvent
    public static void onMaximumManaCalculated(MaxManaCalcEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PerkData.get(player).ifPresent(data -> {
            double configuredBonus = TalentEffects.magicConversionMaximumMana(data)
                    + TalentEffects.frierenMaximumMana(data);
            long roundedBonus = Math.round(Math.max(0.0D, configuredBonus));
            long combined = (long) event.getMax() + roundedBonus;
            event.setMax((int) Math.min(Integer.MAX_VALUE, combined));
        });
    }

    @SubscribeEvent
    public static void onManaRegenerationCalculated(ManaRegenCalcEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PerkData.get(player).ifPresent(data -> event.setRegen(
                event.getRegen()
                        * Math.max(0.0D, 1.0D
                        + TalentEffects.manaRegenerationMultiplier(data))
        ));
    }
}
