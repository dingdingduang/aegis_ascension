package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's live shop: the current stock, which slots they've already bought, how many
 * paid rerolls they've spent, and when the next free restock is due.
 *
 * <p>Restocks are scheduled against {@code Level#getGameTime}, which advances one tick per
 * tick and is never rewritten by {@code /time set} — unlike {@code getDayTime}, where a time
 * command could push the next restock hours away or fire a burst of them. An overdue
 * deadline is re-based to {@code now + interval} rather than advanced by whole intervals, so
 * a long server downtime produces a single catch-up restock instead of one per interval
 * missed.</p>
 */
public final class ShopState {
    private static final String OFFERS_TAG = "Offers";
    private static final String NEXT_REFRESH_TAG = "NextAutoRefreshGameTime";
    private static final String REFRESH_COUNT_TAG = "RefreshCount";
    private static final long UNSCHEDULED = Long.MIN_VALUE;

    private final ShopType shopType;
    private List<ShopOffer> offers = new ArrayList<>();
    private long nextAutoRefreshGameTime = UNSCHEDULED;
    private int refreshCount;

    public ShopState() {
        this(ShopType.COMMON);
    }

    public ShopState(ShopType shopType) {
        this.shopType = shopType == null ? ShopType.COMMON : shopType;
    }

    public ShopType shopType() {
        return shopType;
    }

    public List<ShopOffer> getOffers() {
        return List.copyOf(offers);
    }

    public int getRefreshCount() {
        return refreshCount;
    }

    /** Ticks until the next automatic restock, for the GUI's countdown. */
    public long ticksUntilReset(Level level) {
        if (nextAutoRefreshGameTime == UNSCHEDULED) {
            return ShopConfig.get().autoRefreshIntervalTicks(shopType);
        }
        return Math.max(0L, nextAutoRefreshGameTime - level.getGameTime());
    }

    /** Manual rerolls still available before the next automatic restock refills them. */
    public int getRemainingManualRefreshes() {
        if (!ShopConfig.get().isEnabled(shopType)) {
            return 0;
        }
        return Math.max(0, ShopConfig.get().maxManualRefreshes(shopType) - refreshCount);
    }

    public boolean canManualRefresh() {
        return getRemainingManualRefreshes() > 0;
    }

    /**
     * Restocks if the scheduled time has passed (or nothing was ever rolled).
     *
     * @return true if the stock changed and the owner needs a resync.
     */
    public boolean tickAutoRefresh(Level level, PlayerPerkData data) {
        if (!ShopConfig.get().isEnabled(shopType)) {
            boolean changed = !offers.isEmpty() || refreshCount != 0
                    || nextAutoRefreshGameTime != UNSCHEDULED;
            offers = new ArrayList<>();
            refreshCount = 0;
            nextAutoRefreshGameTime = UNSCHEDULED;
            return changed;
        }
        long now = level.getGameTime();
        long interval = ShopConfig.get().autoRefreshIntervalTicks(shopType);
        if (nextAutoRefreshGameTime != UNSCHEDULED && now < nextAutoRefreshGameTime) {
            return false;
        }
        reroll(level.getRandom(), data);
        // The automatic restock is free, so it also refills the paid-reroll allowance.
        refreshCount = 0;
        nextAutoRefreshGameTime = now + interval;
        return true;
    }

    /** Unconditional reroll, used by the automatic restock and by a paid manual refresh. */
    public void reroll(RandomSource random, PlayerPerkData data) {
        offers = new ArrayList<>(ShopGenerator.roll(random, data, shopType));
    }

    /**
     * Paid manual reroll. Refuses once the allowance is spent, so the cap is enforced here
     * rather than only in the UI. XP is charged by the caller, and only when this returns
     * true. Does not move the automatic restock deadline — paying for a reroll shouldn't
     * postpone the free one.
     *
     * @return false if no manual refreshes remain.
     */
    public boolean manualRefresh(RandomSource random, PlayerPerkData data) {
        if (!ShopConfig.get().isEnabled(shopType) || !canManualRefresh()) {
            return false;
        }
        reroll(random, data);
        refreshCount++;
        return true;
    }

    public boolean isValidSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < offers.size();
    }

    public ShopOffer getOffer(int slotIndex) {
        return isValidSlot(slotIndex) ? offers.get(slotIndex) : null;
    }

    /** Flags a slot sold out for this player until the next reroll. */
    public void markPurchased(int slotIndex) {
        if (isValidSlot(slotIndex)) {
            offers.set(slotIndex, offers.get(slotIndex).asPurchased());
        }
    }

    /** Reopens retained slots for a unique virtual item whose acquisition was reset. */
    public void reopenVirtualOffer(String virtualId) {
        if (virtualId == null || virtualId.isBlank()) {
            return;
        }
        for (int index = 0; index < offers.size(); index++) {
            ShopOffer offer = offers.get(index);
            if (offer.purchased() && virtualId.equals(offer.virtualId())) {
                offers.set(index, offer.asAvailable());
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        offers.forEach(offer -> list.add(offer.save()));
        tag.put(OFFERS_TAG, list);
        tag.putLong(NEXT_REFRESH_TAG, nextAutoRefreshGameTime);
        tag.putInt(REFRESH_COUNT_TAG, refreshCount);
        return tag;
    }

    public void load(CompoundTag tag) {
        offers = new ArrayList<>();
        ListTag list = tag.getList(OFFERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            offers.add(ShopOffer.load(list.getCompound(i)));
        }
        nextAutoRefreshGameTime = tag.contains(NEXT_REFRESH_TAG, Tag.TAG_LONG)
                ? tag.getLong(NEXT_REFRESH_TAG)
                : UNSCHEDULED;
        refreshCount = Math.max(0, tag.getInt(REFRESH_COUNT_TAG));
    }
}
