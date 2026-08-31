package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative persisted Gold Currency used by optional Aegis economy paths. */
public final class GoldCurrency {
    public static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "aegis_ascension", "textures/gui/inventory/gold_currency.png");

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
}
