package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.network.SyncShopDataPacket;
import com.whatever.aegis_ascension.shop.ShopOffer;
import com.whatever.aegis_ascension.shop.ShopType;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side mirrors of both server-authoritative shop views. Display state only: every
 * purchase and reroll is still revalidated against the selected server-side shop.
 */
public final class ClientShopState {
    private static final Map<ShopType, Snapshot> SNAPSHOTS = new EnumMap<>(ShopType.class);

    static {
        clear();
    }

    private ClientShopState() {
    }

    private record Snapshot(
            boolean enabled,
            List<ShopOffer> offers,
            int refreshExperienceCost,
            int remainingRefreshes,
            long ticksUntilReset,
            long syncedAtMillis
    ) {
    }

    public static void accept(SyncShopDataPacket packet) {
        SNAPSHOTS.put(packet.shopType(), new Snapshot(
                packet.enabled(),
                List.copyOf(packet.offers()),
                packet.refreshExperienceCost(),
                packet.remainingRefreshes(),
                packet.ticksUntilReset(),
                System.currentTimeMillis()
        ));
    }

    public static void clear() {
        SNAPSHOTS.clear();
        for (ShopType shopType : ShopType.values()) {
            // Treat an unsynchronised tab as available so it can be selected and requested.
            SNAPSHOTS.put(shopType, new Snapshot(true, List.of(), 0, 0, 0L, 0L));
        }
    }

    private static Snapshot snapshot(ShopType shopType) {
        ShopType resolved = shopType == null ? ShopType.COMMON : shopType;
        return SNAPSHOTS.get(resolved);
    }

    public static boolean isEnabled(ShopType shopType) {
        return snapshot(shopType).enabled();
    }

    public static List<ShopOffer> getOffers(ShopType shopType) {
        return snapshot(shopType).offers();
    }

    public static int getSlotCount(ShopType shopType) {
        return snapshot(shopType).offers().size();
    }

    public static ShopOffer getOffer(ShopType shopType, int slotIndex) {
        List<ShopOffer> offers = snapshot(shopType).offers();
        return slotIndex >= 0 && slotIndex < offers.size() ? offers.get(slotIndex) : null;
    }

    /** Whether the local player can buy this stocked, unsold, affordable slot. */
    public static boolean canPurchase(ShopType shopType, int slotIndex) {
        ShopOffer offer = getOffer(shopType, slotIndex);
        return isEnabled(shopType)
                && offer != null
                && !offer.purchased()
                && (ClientPerkState.usesGoldCurrency()
                ? getPlayerGold() >= offer.experienceCost()
                : getPlayerExperience() >= offer.experienceCost());
    }

    public static int getRefreshExperienceCost(ShopType shopType) {
        return snapshot(shopType).refreshExperienceCost();
    }

    public static int getRemainingRefreshes(ShopType shopType) {
        return snapshot(shopType).remainingRefreshes();
    }

    public static boolean canRefresh(ShopType shopType) {
        return isEnabled(shopType) && getRemainingRefreshes(shopType) > 0;
    }

    public static boolean canAffordRefresh(ShopType shopType) {
        return ClientPerkState.usesGoldCurrency()
                ? getPlayerGold() >= getRefreshExperienceCost(shopType)
                : getPlayerExperience() >= getRefreshExperienceCost(shopType);
    }

    /** Ages the selected shop's server countdown locally at twenty ticks per second. */
    public static long getTicksUntilReset(ShopType shopType) {
        Snapshot snapshot = snapshot(shopType);
        if (snapshot.syncedAtMillis() <= 0L) {
            return snapshot.ticksUntilReset();
        }
        long elapsedTicks = (System.currentTimeMillis() - snapshot.syncedAtMillis()) / 50L;
        return Math.max(0L, snapshot.ticksUntilReset() - elapsedTicks);
    }

    /** The local player's raw spendable vanilla experience, used when Gold mode is off. */
    public static int getPlayerExperience() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : player.totalExperience;
    }

    public static long getPlayerGold() {
        return ClientPerkState.getGoldCurrency();
    }
}
