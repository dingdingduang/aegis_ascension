package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative persisted Gold Currency used by optional Aegis economy paths. */
public final class GoldCurrency {
    public static final ResourceLocation ICON =
            GeneralClientMethods.fromNamespaceAndPath("aegis_ascension", "textures/gui/inventory/gold_currency.png");

    private GoldCurrency() {
    }

    public static boolean enabled() {
        return PlatformServices.config().useGoldCurrency();
    }

    public static boolean canAfford(PlayerPerkData data, long amount) {
        return data != null && amount >= 0L && data.getGoldCurrency() >= amount;
    }

    public static boolean trySpend(PlayerPerkData data, long amount) {
        return data != null && amount >= 0L && data.trySpendGoldCurrency(amount);
    }

    public static void grant(PlayerPerkData data, long amount) {
        if (data != null && amount > 0L) {
            data.addGoldCurrency(amount);
        }
    }

    /**
     * Grants Gold earned as a reward, amplified by the owned talents' Gold reward bonus.
     *
     * <p>Kept apart from {@link #grant} on purpose: refunds, released deposits, and sale
     * payouts hand back Gold the player already had, so amplifying those would turn a
     * buy-and-sell cycle into free money.</p>
     */
    public static void grantReward(PlayerPerkData data, long amount) {
        if (data == null || amount <= 0L) {
            return;
        }
        double multiplier = 1.0D + TalentEffects.goldRewardBonus(data);
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            return;
        }
        double amplified = amount * multiplier;
        grant(data, !Double.isFinite(amplified)
                ? Long.MAX_VALUE
                : (long) Math.min(Long.MAX_VALUE, Math.max(0.0D, Math.round(amplified))));
    }
}
