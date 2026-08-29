package com.whatever.aegis_ascension.aegis;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.compat.ActionCoreCompat;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Iron's Spells-only implementation of Wisdom Aegis double and triple casting. */
public final class WisdomAegis {
    private static final String TASK_PREFIX = "aegis_ascension:wisdom_extra_cast";
    private static final double DEFAULT_INTERVAL_SECONDS = 0.5D;
    private static final double MAX_INTERVAL_SECONDS = 60.0D;
    private static final int TICKS_PER_SECOND = 20;

    private WisdomAegis() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PerkData.get(player).ifPresent(data -> {
            if (!data.isAegisEnabled(AegisConstants.WISDOM)) {
                return;
            }

            Aegis wisdom = Aegis.byId(AegisConstants.WISDOM).orElseThrow();
            int extraCasts = rollExtraCasts(player, wisdom);
            if (extraCasts == 0) {
                return;
            }

            String spellId = event.getSpellId();
            if (SpellRegistry.getSpell(spellId) == SpellRegistry.none()) {
                return;
            }

            // Action Core paces the repeats on its own entity-bound timer, so the
            // boosted casts land one interval apart instead of stacking onto the
            // tick that produced the original cast.
            int spellLevel = event.getSpellLevel();
            CastSource castSource = event.getCastSource();
            ActionCoreCompat.scheduleRepeating(
                    player,
                    ActionCoreCompat.uniqueTaskId(TASK_PREFIX),
                    intervalTicks(wisdom),
                    extraCasts,
                    living -> extraCast(living, spellId, spellLevel, castSource)
            );
        });
    }

    /** Rolls the configured triple-cast chance first, then the double-cast chance. */
    private static int rollExtraCasts(ServerPlayer player, Aegis wisdom) {
        double tripleChance = Mth.clamp(
                wisdom.stat(AegisConstants.EXTRA_CAST_TWO_CHANCE),
                0.0D,
                1.0D
        );
        double doubleChance = Mth.clamp(
                wisdom.stat(AegisConstants.EXTRA_CAST_ONE_CHANCE),
                0.0D,
                1.0D - tripleChance
        );
        double roll = player.getRandom().nextDouble();
        return roll < tripleChance
                ? 2
                : roll < tripleChance + doubleChance ? 1 : 0;
    }

    /**
     * Reads the configured gap between boosted casts. A missing or non-positive
     * value keeps the default half-second cadence so older configs stay valid.
     */
    private static int intervalTicks(Aegis wisdom) {
        double seconds = wisdom.stat(AegisConstants.EXTRA_CAST_INTERVAL_SECONDS);
        if (!(seconds > 0.0D)) {
            seconds = DEFAULT_INTERVAL_SECONDS;
        }
        seconds = Math.min(seconds, MAX_INTERVAL_SECONDS);
        return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
    }

    /** Repeats the spell's effect without extra mana, cooldown, or recast cost. */
    private static void extraCast(LivingEntity living, String spellId,
                                  int spellLevel, CastSource castSource) {
        if (!(living instanceof ServerPlayer player)
                || player.isRemoved()
                || !player.isAlive()) {
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == SpellRegistry.none()) {
            return;
        }
        // Direct onCast repeats the effect without recursively posting
        // SpellOnCastEvent, and the level is re-resolved so a dimension change
        // between repeats still targets the player's current world.
        spell.onCast(
                player.serverLevel(),
                spellLevel,
                player,
                castSource,
                MagicData.getPlayerMagicData(player)
        );
    }
}
