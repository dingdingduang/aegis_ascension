package com.whatever.aegis_ascension.compat;

import static com.whatever.aegis_ascension.perk.TalentConstants.*;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.Perk;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Iron's Spells-linked cooldown reset hook, loaded only when that mod is present. */
public final class HomuraBlessingHandler {
    private HomuraBlessingHandler() {
    }

    @SubscribeEvent
    public static void onSpellCooldownAdded(SpellCooldownAddedEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PerkData.get(player).ifPresent(data -> {
            if (!data.owns(SR_HOMURAS_BLESSING)) {
                return;
            }
            double chance = Perk.byId(SR_HOMURAS_BLESSING)
                    .orElseThrow()
                    .stat(RESET_SPELL_COOLDOWN_CHANCE);
            if (player.getRandom().nextDouble()
                    < net.minecraft.util.Mth.clamp(chance, 0.0D, 1.0D)) {
                event.setEffectiveCooldown(0);
            }
        });
    }
}
