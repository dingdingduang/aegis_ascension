package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.network.SyncShopDataPacket;
import com.whatever.aegis_ascension.shop.ShopOffer;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Client-side mirror of the server's shop view, populated only by
 * {@link SyncShopDataPacket}. Display state exclusively — the GUI reads prices and
 * availability from here, but every purchase is re-validated server-side, so a stale or
 * edited value here can only make the UI wrong, never grant an item or a discount.
 */
public final class ClientShopState {
    private static List<ShopOffer> offers = List.of();
    private static int refreshExperienceCost;
    private static int refreshCount;
    private static long ticksUntilReset;
    /** Wall-clock time of the last sync, used to age {@link #ticksUntilReset} between syncs. */
    private static long ticksUntilResetSyncedAtMillis;

    private ClientShopState() {
    }

    public static void accept(SyncShopDataPacket packet) {
        offers = List.copyOf(packet.offers());
        refreshExperienceCost = packet.refreshExperienceCost();
        refreshCount = packet.refreshCount();
        ticksUntilReset = packet.ticksUntilReset();
        ticksUntilResetSyncedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        offers = List.of();
        refreshExperienceCost = 0;
        refreshCount = 0;
        ticksUntilReset = 0L;
        ticksUntilResetSyncedAtMillis = 0L;
    }

    // ------------------------------------------------------------------
    // GUI integration hooks
    // ------------------------------------------------------------------

    /** Every stocked slot, in slot-index order. */
    public static List<ShopOffer> getOffers() {
        return offers;
    }

    public static int getSlotCount() {
        return offers.size();
    }

    public static ShopOffer getOffer(int slotIndex) {
        return slotIndex >= 0 && slotIndex < offers.size() ? offers.get(slotIndex) : null;
    }

    /** Experience price of one slot, or 0 if the index isn't stocked. */
    public static int getSlotCost(int slotIndex) {
        ShopOffer offer = getOffer(slotIndex);
        return offer == null ? 0 : offer.experienceCost();
    }

    /** Whether the local player can actually buy this slot right now (stocked, unsold, affordable). */
    public static boolean canPurchase(int slotIndex) {
        ShopOffer offer = getOffer(slotIndex);
        return offer != null && !offer.purchased() && getPlayerExperience() >= offer.experienceCost();
    }

    public static boolean isPurchased(int slotIndex) {
        ShopOffer offer = getOffer(slotIndex);
        return offer != null && offer.purchased();
    }

    public static int getRefreshExperienceCost() {
        return refreshExperienceCost;
    }

    /** Manual rerolls left before the next automatic restock refills them. */
    public static int getRemainingRefreshes() {
        return refreshCount;
    }

    public static boolean canRefresh() {
        return refreshCount > 0;
    }

    public static boolean canAffordRefresh() {
        return getPlayerExperience() >= refreshExperienceCost;
    }

    /**
     * Ticks left until the daily reroll, aged locally so the countdown moves while the shop
     * screen is open instead of sitting frozen until the next sync packet.
     *
     * <p>Elapsed time comes from the wall clock at 20 ticks/second. That tracks the server
     * because the shop screen doesn't pause the game ({@code isPauseScreen() == false});
     * any drift is corrected by the next sync, and the value is floored at 0 so an
     * over-run reads as "due" rather than counting backwards.</p>
     */
    public static long getTicksUntilReset() {
        if (ticksUntilResetSyncedAtMillis <= 0L) {
            return ticksUntilReset;
        }
        long elapsedTicks = (System.currentTimeMillis() - ticksUntilResetSyncedAtMillis) / 50L;
        return Math.max(0L, ticksUntilReset - elapsedTicks);
    }

    /** The local player's spendable experience — raw points, matching what the server charges. */
    public static int getPlayerExperience() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : player.totalExperience;
    }
}
