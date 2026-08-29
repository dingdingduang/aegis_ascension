package com.whatever.aegis_ascension.perk.soullink;

import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkEffects.stat;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Grants configurable Skill Enhancement charges from successfully consumed food. */
public final class GourmetGuild {
    private GourmetGuild() {
    }

    public static void onFoodConsumed(ServerPlayer player, PlayerPerkData data,
                                      ItemStack consumedItem) {
        if (!data.hasActiveSoulLink(SOUL_GOURMET_GUILD)
                || consumedItem.isEmpty() || !consumedItem.isEdible()) {
            return;
        }
        int limit = Math.max(0, (int) Math.round(
                stat(SOUL_GOURMET_GUILD, FOOD_SKILL_CHARGE_TRIGGER_LIMIT)
        ));
        int triggers = Math.max(0, Mth.floor(
                data.getCustomStat(GOURMET_GUILD_TRIGGER_COUNT)
        ));
        if (triggers >= limit || player.getRandom().nextDouble() >= Mth.clamp(
                stat(SOUL_GOURMET_GUILD, FOOD_SKILL_CHARGE_CHANCE), 0.0D, 1.0D
        )) {
            return;
        }

        int amount = Math.max(0, (int) Math.round(
                stat(SOUL_GOURMET_GUILD, FOOD_SKILL_CHARGE_AMOUNT)
        ));
        data.setCustomStat(GOURMET_GUILD_TRIGGER_COUNT, triggers + 1.0D);
        data.addSkillEnhancementCharges(amount);
        ModNetworking.syncTo(player);
    }
}
