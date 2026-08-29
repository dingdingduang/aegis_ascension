package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.R_CASHBACK;
import static com.whatever.aegis_ascension.perk.TalentConstants.TRADE_MATERIAL_REFUND_FRACTION;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** Refunds part of both material inputs after a completed NPC trade. */
public final class CashBack {
    private CashBack() {
    }

    public static void onSuccessfulTrade(ServerPlayer player, PlayerPerkData data,
                                         MerchantOffer offer) {
        if (!data.owns(R_CASHBACK)) {
            return;
        }

        Perk cashBack = Perk.byId(R_CASHBACK).orElseThrow();
        double fraction = Mth.clamp(
                cashBack.stat(TRADE_MATERIAL_REFUND_FRACTION),
                0.0D,
                1.0D
        );
        refund(player, offer.getCostA(), fraction);
        refund(player, offer.getCostB(), fraction);
    }

    private static void refund(ServerPlayer player, ItemStack paidCost, double fraction) {
        int refundCount = (int) Math.floor(paidCost.getCount() * fraction + 1.0E-9D);
        if (paidCost.isEmpty() || refundCount <= 0) {
            return;
        }

        ItemStack refunded = paidCost.copyWithCount(refundCount);
        player.getInventory().add(refunded);
        if (!refunded.isEmpty()) {
            player.drop(refunded, false, false);
        }
    }
}
