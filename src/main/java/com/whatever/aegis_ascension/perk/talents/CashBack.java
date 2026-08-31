package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_CASHBACK;
import static com.whatever.aegis_ascension.perk.TalentConstants.TRADE_MATERIAL_REFUND_FRACTION;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** Refunds part of trade inputs, enabled only while Gold Currency mode is active. */
public final class CashBack {
    private CashBack() {
    }

    public static void onSuccessfulTrade(ServerPlayer player, PlayerPerkData data,
                                         MerchantOffer offer) {
        // Cashback is part of the optional Gold economy. Keeping this gate server-side
        // means changing the config cannot be bypassed by a client packet.
        if (!GoldCurrency.enabled() || !data.owns(PERK_CASHBACK)) {
            return;
        }

        Perk cashBack = Perk.byId(PERK_CASHBACK).orElseThrow();
        double fraction = Mth.clamp(
                cashBack.stat(TRADE_MATERIAL_REFUND_FRACTION),
                0.0D,
                1.0D
        );
        refund(player, offer.getCostA(), fraction);
        refund(player, offer.getCostB(), fraction);
    }

    /** Refunds a fraction of an Aegis Gold purchase when the perk is owned. */
    public static long refundGold(PlayerPerkData data, long paidAmount) {
        if (!GoldCurrency.enabled() || !data.owns(PERK_CASHBACK) || paidAmount <= 0L) {
            return 0L;
        }
        Perk cashBack = Perk.byId(PERK_CASHBACK).orElseThrow();
        double fraction = Mth.clamp(
                cashBack.stat(TRADE_MATERIAL_REFUND_FRACTION), 0.0D, 1.0D);
        double refund = paidAmount * fraction;
        if (!Double.isFinite(refund) || refund <= 0.0D) return 0L;
        return Math.min(Long.MAX_VALUE, Math.max(0L, (long) Math.floor(refund + 1.0E-9D)));
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
