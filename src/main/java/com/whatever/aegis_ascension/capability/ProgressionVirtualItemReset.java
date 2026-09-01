package com.whatever.aegis_ascension.capability;

import com.whatever.aegis_ascension.virtualitem.VirtualItems;

import java.util.List;
import java.util.function.Consumer;

/** Defines which virtual-item state belongs to progression rather than inventory. */
final class ProgressionVirtualItemReset {
    private static final List<String> DEVOUR_CORES = List.of(
            VirtualItems.DEVOUR_AEGIS_CORE_I,
            VirtualItems.DEVOUR_AEGIS_CORE_II,
            VirtualItems.DEVOUR_AEGIS_CORE_III
    );

    private ProgressionVirtualItemReset() {
    }

    static void apply(Consumer<String> clearConsumed,
                      Consumer<String> clearBanked,
                      Consumer<String> clearUniquePurchase,
                      Consumer<String> reopenShopOffer) {
        // No Swiss Rolls Abuse
        clearConsumed.accept(VirtualItems.SWISS_ROLL);
        clearBanked.accept(VirtualItems.SWISS_ROLL);

        for (String coreId : DEVOUR_CORES) {
            clearConsumed.accept(coreId);
            clearBanked.accept(coreId);
            clearUniquePurchase.accept(coreId);
            reopenShopOffer.accept(coreId);
        }
    }
}
