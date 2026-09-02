package com.whatever.aegis_ascension.event;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.AngelsAegis;
import com.whatever.aegis_ascension.lifecycle.PlayerDataLifecycle;
import com.whatever.aegis_ascension.lifecycle.PlayerSessionLifecycle;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.mechanic.ServerTickHandler;
import com.whatever.aegis_ascension.mechanic.ServerGameplayHandler;
import com.whatever.aegis_ascension.compat.ApotheosisCompat;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.mechanic.MagicBladeMechanic;
import com.whatever.aegis_ascension.network.ServerCatalogSync;
import com.whatever.aegis_ascension.quest.QuestManager;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.perk.talents.KoharuShield;
import com.whatever.aegis_ascension.perk.talents.HomuraExperienceProtection;
import com.whatever.aegis_ascension.perk.talents.HomuraResetNegation;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.Container;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AegisAscensionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    /**
     * Separates an arrow the player just loosed from one restored motionless from disk.
     * Even a minimally drawn bow launches well above this, while a stuck arrow sits at zero.
     */
    private static final double MINIMUM_LOOSED_ARROW_SPEED_SQUARED = 0.05D;

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ServerGameplayHandler.onLivingEntityJoined(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ApotheosisCompat.onFinalizeSpawn(event);
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
        HomuraResetNegation.clear();
        QuestManager.clearTransientState();
        ServerCatalogSync.clearAll();
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

        ServerTickHandler.onPlayerTick(player);
        ShieldMechanic.tick(player);
        AngelsAegis.tick(player);
        KoharuShield.tick(player);
    }


    /** Converts enabled Magic Blade player attacks before vanilla mitigation begins. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (MagicBladeMechanic.convertAttack(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

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
        // Homura's XP snapshot is captured by the LOWEST-priority handler below, after
        // every revive path has had a chance to cancel this death.
    }

    /** Counts genuine player kills after revive handlers have had a chance to cancel death. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onQuestKill(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        QuestManager.onKill(player, event.getEntity());
    }

    @SubscribeEvent
    public static void onQuestPlant(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getPlacedBlock().getBlock() instanceof CropBlock)) {
            return;
        }
        QuestManager.onPlant(player, event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onQuestChestOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof Container)) return;

        // A generated loot container keeps its LootTable tag until its first access.
        // RightClickBlock runs before vanilla unpacks that table, so this distinguishes
        // a newly discovered loot chest from placed or previously opened storage.
        boolean unopenedGeneratedLoot = blockEntity.saveWithoutMetadata().contains(
                RandomizableContainerBlockEntity.LOOT_TABLE_TAG, Tag.TAG_STRING);
        QuestManager.onChestOpened(player, event.getPos(), unopenedGeneratedLoot);
    }

    @SubscribeEvent
    public static void onQuestCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        QuestManager.onCraft(player, event.getCrafting());
    }

    @SubscribeEvent
    public static void onQuestBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        QuestManager.onBlockBroken(player, event.getState());
    }

    /**
     * Fires for every arrow that enters the level rather than for the bow release, so
     * crossbow bolts and multishot volleys count alongside ordinary bow shots.
     *
     * <p>This event also fires for arrows restored from disk, and an arrow stuck in a
     * block keeps both its owner and its saved entity, so a chunk reload would otherwise
     * re-count every spent shot. Only a moving arrow is a shot the player just took.</p>
     */
    @SubscribeEvent
    public static void onQuestArrowShot(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer player)
                || arrow.getDeltaMovement().lengthSqr() <= MINIMUM_LOOSED_ARROW_SPEED_SQUARED) {
            return;
        }
        QuestManager.onArrowShot(player, arrow);
    }

    @SubscribeEvent
    public static void onQuestArrowHit(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer player)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity victim)
                || victim == player) {
            return;
        }
        QuestManager.onArrowHit(player, victim);
    }

    /** Fails constrained quests only after revive handlers have declined to cancel. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onQuestOwnerDied(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        PerkData.get(player).ifPresent(data -> {
            if (QuestManager.onPlayerDied(data)) ModNetworking.syncQuestsTo(player);
        });
    }

    /** Cancelled damage never lands, so it must not break a no-damage constraint. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onQuestOwnerDamaged(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PerkData.get(player).ifPresent(data -> {
            if (QuestManager.onPlayerDamaged(data)) ModNetworking.syncQuestsTo(player);
        });
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


}
