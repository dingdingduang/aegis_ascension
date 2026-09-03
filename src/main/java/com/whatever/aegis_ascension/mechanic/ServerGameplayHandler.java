package com.whatever.aegis_ascension.mechanic;

import static com.whatever.aegis_ascension.perk.TalentConstants.EXPERIENCE_GAINED;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.compat.ApothicAttributesCompat;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.perk.soullink.GourmetGuild;
import com.whatever.aegis_ascension.perk.talents.CashBack;
import com.whatever.aegis_ascension.perk.talents.FairTrade;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** Loader-neutral entry points for ordinary server gameplay notifications. */
public final class ServerGameplayHandler {
    private ServerGameplayHandler() {
    }

    /** Defers summon ownership and spawned-mob checks until entity insertion is complete. */
    public static void onLivingEntityJoined(LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }
        MinecraftServer server = living.level().getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (living.isRemoved()) {
                return;
            }
            SummonCompat.updateJoinedSummon(living);
            if (living instanceof Mob mob) {
                NearbySpawnBuff.onMobJoined(mob);
            }
        });
    }

    /** Returns the effective positive XP change after Aegis and talent bonuses. */
    public static int modifyExperienceGain(ServerPlayer player, int originalAmount) {
        if (originalAmount <= 0) {
            return originalAmount;
        }
        return PerkData.get(player).map(data -> {
            double bonus = 0.0D;
            if (data.isAegisEnabled(AegisConstants.HOLY_BLESSING)) {
                bonus += Aegis.byId(AegisConstants.HOLY_BLESSING)
                        .orElseThrow()
                        .stat(AegisConstants.EXPERIENCE_GAIN_MULTIPLIER);
            }
            if (!ApothicAttributesCompat.handlesMappedAttribute(player, EXPERIENCE_GAINED)) {
                bonus += TalentEffects.experienceGainBonus(data);
            }
            double multiplier = Math.max(0.0D, 1.0D + bonus);
            return (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(0L, Math.round(originalAmount * multiplier))
            );
        }).orElse(originalAmount);
    }

    public static void onVillagerTrade(ServerPlayer player, MerchantOffer offer) {
        PerkData.get(player).ifPresent(data -> {
            if (FairTrade.onSuccessfulTrade(data)) {
                ModNetworking.syncTo(player);
            }
            CashBack.onSuccessfulTrade(player, data, offer);
        });
    }

    public static void onFoodConsumed(ServerPlayer player, ItemStack consumedItem) {
        PerkData.get(player).ifPresent(data ->
                GourmetGuild.onFoodConsumed(player, data, consumedItem)
        );
    }
}
