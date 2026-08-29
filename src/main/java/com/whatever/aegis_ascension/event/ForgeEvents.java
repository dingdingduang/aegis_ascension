package com.whatever.aegis_ascension.event;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.AngelsAegis;
import com.whatever.aegis_ascension.aegis.FoxAegis;
import com.whatever.aegis_ascension.lifecycle.PlayerDataLifecycle;
import com.whatever.aegis_ascension.lifecycle.PlayerSessionLifecycle;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.mechanic.ServerTickHandler;
import com.whatever.aegis_ascension.mechanic.ServerGameplayHandler;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.perk.talents.KoharuShield;
import com.whatever.aegis_ascension.perk.talents.HomuraExperienceProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AegisAscensionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ServerGameplayHandler.onLivingEntityJoined(living);
        }
    }

    /**
     * Perk data is keyed by UUID, so a rebuilt player entity
     * carries nothing across and there is nothing to copy here. All that remains is the
     * deliberate wipe when {@code resetPerksOnDeath} is enabled.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerDataLifecycle.onPlayerClone(
                event.getEntity().getUUID(),
                event.isWasDeath(),
                PlatformServices.config().resetPerksOnDeath(),
                PlatformServices.config().preserveInventoryOnDeathReset()
        );
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            HomuraExperienceProtection.restore(player);
        }
    }

    /** Reads the player's perk file during login, before they are added to the world. */
    @SubscribeEvent
    public static void onPlayerLoadFromFile(PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataLifecycle.onPlayerLoad(player);
        }
    }

    /** Writes the player's perk file alongside every vanilla player save. */
    @SubscribeEvent
    public static void onPlayerSaveToFile(PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataLifecycle.onPlayerSave(player);
        }
    }

    /** Frees the in-memory map once the world unloads, so it cannot leak into the next one. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HomuraExperienceProtection.clear();
        PlayerDataLifecycle.onServerStopped();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSessionLifecycle.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerSessionLifecycle.onPlayerLogout(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSessionLifecycle.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSessionLifecycle.onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public static void onExperienceGain(PlayerXpEvent.XpChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setAmount(ServerGameplayHandler.modifyExperienceGain(
                    player,
                    event.getAmount()
            ));
        }
    }

    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerGameplayHandler.onVillagerTrade(player, event.getMerchantOffer());
    }

    @SubscribeEvent
    public static void onFoodConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerGameplayHandler.onFoodConsumed(player, event.getItem());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        ServerTickHandler.onLivingTick(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

//        logPlayerState("Abnormal player tick", player, true);
        ServerTickHandler.onPlayerTick(player);
        ShieldMechanic.tick(player);
        AngelsAegis.tick(player);
        FoxAegis.tick(player);
        KoharuShield.tick(player);
    }

    // ------------------------------------------------------------------
    // TEMPORARY damage bracket, paired with the [ReviveDebug] trace.
    // /kill deals Float.MAX_VALUE and the player's health lands on NaN before any
    // LivingDeathEvent fires. These four handlers straddle every mod handler on both damage
    // events, so the log shows the amount entering and leaving the chain - which tells us
    // whether this mod produces the NaN or merely receives it from something else.
    // ------------------------------------------------------------------

//    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
//    public static void traceHurtIn(LivingHurtEvent event) {
//        traceDamage("LivingHurt IN ", event.getEntity(), event.getAmount(), event.isCanceled());
//    }
//
//    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
//    public static void traceHurtOut(LivingHurtEvent event) {
//        traceDamage("LivingHurt OUT", event.getEntity(), event.getAmount(), event.isCanceled());
//    }
//
//    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
//    public static void traceDamageIn(LivingDamageEvent event) {
//        traceDamage("LivingDamage IN ", event.getEntity(), event.getAmount(), event.isCanceled());
//    }
//
//    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
//    public static void traceDamageOut(LivingDamageEvent event) {
//        traceDamage("LivingDamage OUT", event.getEntity(), event.getAmount(), event.isCanceled());
//    }

//    private static void traceDamage(String stage, Entity entity, float amount, boolean canceled) {
//        if (!(entity instanceof ServerPlayer player)) {
//            return;
//        }
//        // Silent while everything is finite. The dead-alive bug is intermittent and its
//        // trigger is still unknown, so this stays in as a watchdog rather than a trace: it
//        // says nothing during normal play and names the exact hit if a value ever flips.
//        if (Float.isFinite(amount)
//                && Float.isFinite(player.getHealth())
//                && Float.isFinite(player.getAbsorptionAmount())) {
//            return;
//        }
//        AegisAscensionMod.LOGGER.warn(
//                "[ReviveDebug] {}: player={}, amount={}, finiteAmount={}, canceled={}, health={}, "
//                        + "finiteHealth={}, maxHealth={}, absorption={}, finiteAbsorption={}",
//                stage,
//                player.getGameProfile().getName(),
//                amount,
//                Float.isFinite(amount),
//                canceled,
//                player.getHealth(),
//                Float.isFinite(player.getHealth()),
//                player.getMaxHealth(),
//                player.getAbsorptionAmount(),
//                Float.isFinite(player.getAbsorptionAmount())
//        );
//    }

    /** Captures base damage before Apothic Attributes' HIGH-priority crit handler. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtCapture(LivingHurtEvent event) {
        TalentEffects.captureLivingHurt(
                event.getEntity(),
                event.getSource(),
                event.getAmount()
        );
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        event.setAmount(TalentEffects.onLivingHurt(
                event.getEntity(),
                event.getSource(),
                event.getAmount()
        ));
    }

    /** Prevents a canceled/zeroed hit from leaving transient conversion state behind. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurtCleanup(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            TalentEffects.clearLivingHurt(event.getEntity(), event.getSource());
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (TalentEffects.shouldCancelLivingHeal(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        event.setAmount(TalentEffects.onLivingDamage(
                event.getEntity(),
                event.getSource(),
                event.getAmount()
        ));
    }

    /** Applies shield absorption after ordinary damage modifiers but before observers. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onShieldLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) {
            return;
        }
        float incoming = event.getAmount();
        float remaining = ShieldMechanic.absorbDamage(player, incoming);
        if (remaining >= incoming) {
            return;
        }
        if (remaining <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(remaining);
        }
    }

    /** Observes only health damage left after ShieldMechanic's LOW-priority absorption. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFinalLivingDamageForPerfection(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        TalentEffects.onFinalLivingDamage(
                event.getEntity(),
                event.getSource(),
                event.getAmount()
        );
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (TalentEffects.onLivingDeath(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            AegisAscensionMod.LOGGER.info(
                    "[ReviveDebug] ForgeEvents after TalentEffects.onLivingDeath: player={}, "
                            + "eventCanceled={}, health={}, maxHealth={}, removed={}, "
                            + "removalReason={}, deadOrDying={}, deathTime={}, hurtTime={}, "
                            + "invulnerableTime={}",
                    player.getGameProfile().getName(),
                    event.isCanceled(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.isRemoved(),
                    player.getRemovalReason(),
                    player.isDeadOrDying(),
                    player.deathTime,
                    player.hurtTime,
                    player.invulnerableTime
            );
        }
        // Homura's XP snapshot is captured by the LOWEST-priority handler below, after
        // every revive path has had a chance to cancel this death.
    }

    /** Captures only genuine deaths after revive/cancel handlers have finished. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void captureHomuraExperience(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            HomuraExperienceProtection.capture(player);
        }
    }

    /** Prevents the retained experience from also entering the world as XP orbs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void suppressHomuraExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && HomuraExperienceProtection.isPending(player.getUUID())) {
            event.setDroppedExperience(0);
        }
    }

//    private static void logPlayerState(String stage, ServerPlayer player, boolean abnormalOnly) {
//        boolean abnormal = player.getHealth() <= 0.0F
//                || !Float.isFinite(player.getHealth())
//                || !Float.isFinite(player.getMaxHealth())
//                || player.isRemoved();
//        if (abnormalOnly && !abnormal) {
//            LAST_ABNORMAL_STATE_LOG_TICK.remove(player.getUUID());
//            return;
//        }
//
//        if (abnormalOnly) {
//            long gameTime = player.serverLevel().getGameTime();
//            Long previous = LAST_ABNORMAL_STATE_LOG_TICK.get(player.getUUID());
//            if (previous != null && gameTime - previous < ABNORMAL_STATE_LOG_INTERVAL_TICKS) {
//                return;
//            }
//            LAST_ABNORMAL_STATE_LOG_TICK.put(player.getUUID(), gameTime);
//        }
//
//        String damageSource = player.getLastDamageSource() == null
//                ? "none"
//                : player.getLastDamageSource().getMsgId();
//        String message = "[ReviveDebug] {}: player={}, health={}, maxHealth={}, finiteHealth={}, "
//                + "finiteMaxHealth={}, removed={}, removalReason={}, alive={}, deadOrDying={}, "
//                + "deathTime={}, hurtTime={}, invulnerableTime={}, lastDamageSource={}, gameTime={}";
//        if (abnormal) {
//            AegisAscensionMod.LOGGER.warn(
//                    message,
//                    stage,
//                    player.getGameProfile().getName(),
//                    player.getHealth(),
//                    player.getMaxHealth(),
//                    Float.isFinite(player.getHealth()),
//                    Float.isFinite(player.getMaxHealth()),
//                    player.isRemoved(),
//                    player.getRemovalReason(),
//                    player.isAlive(),
//                    player.isDeadOrDying(),
//                    player.deathTime,
//                    player.hurtTime,
//                    player.invulnerableTime,
//                    damageSource,
//                    player.serverLevel().getGameTime()
//            );
//        } else {
//            AegisAscensionMod.LOGGER.info(
//                    message,
//                    stage,
//                    player.getGameProfile().getName(),
//                    player.getHealth(),
//                    player.getMaxHealth(),
//                    Float.isFinite(player.getHealth()),
//                    Float.isFinite(player.getMaxHealth()),
//                    player.isRemoved(),
//                    player.getRemovalReason(),
//                    player.isAlive(),
//                    player.isDeadOrDying(),
//                    player.deathTime,
//                    player.hurtTime,
//                    player.invulnerableTime,
//                    damageSource,
//                    player.serverLevel().getGameTime()
//            );
//        }
//    }

}
