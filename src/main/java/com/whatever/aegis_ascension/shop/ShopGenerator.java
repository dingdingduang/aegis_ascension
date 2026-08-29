package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Rolls a fresh set of shop offers from {@link ShopConfig}. Pure logic, no side effects. */
public final class ShopGenerator {
    private ShopGenerator() {
    }

    public static List<ShopOffer> roll(RandomSource random) {
        ShopConfig config = ShopConfig.get();
        List<ShopOffer> offers = new ArrayList<>();

        // --- Guaranteed slots -------------------------------------------------
        // Shuffled and drawn without replacement so a pool larger than minimumSlots
        // rotates between rerolls instead of always showing the first N entries.
        List<ShopConfig.FixedEntry> guaranteed = new ArrayList<>();
        for (ShopConfig.FixedEntry entry : config.guaranteedItems) {
            if (ShopConfig.isVirtual(entry.virtualId)) {
                if (isStockableVirtual(entry.virtualId)) {
                    guaranteed.add(entry);
                }
                continue;
            }
            Item item = ShopConfig.resolveItem(entry.item);
            if (item != null && config.isItemAllowed(item)) {
                guaranteed.add(entry);
            }
        }
        Collections.shuffle(guaranteed, new java.util.Random(random.nextLong()));
        for (ShopConfig.FixedEntry entry : guaranteed) {
            if (offers.size() >= config.minimumSlots) {
                break;
            }
            if (isStockableVirtual(entry.virtualId)) {
                offers.add(virtualOffer(entry.virtualId, entry.count, entry.experienceCost));
                continue;
            }
            Item item = ShopConfig.resolveItem(entry.item);
            if (item == null) {
                continue;
            }
            // Fixed entries are offered at their exact configured count, never randomized.
            offers.add(new ShopOffer(new ItemStack(item, entry.count), entry.experienceCost,
                    GeneralConstants.rarityColor(entry.tier)));
        }

        // --- Unlockable slots -------------------------------------------------
        // Bucketed by rarity: each slot rolls a tier first, then an entry from within that
        // tier. Weight therefore competes only against same-tier siblings, so adding a
        // common item can't dilute the SSR rate.
        Map<String, List<ShopConfig.RandomEntry>> byTier = new LinkedHashMap<>();
        for (ShopConfig.RandomEntry entry : config.randomItems) {
            if (entry.weight <= 0) {
                continue;
            }
            String tier;
            // Virtual books bypass the item blacklist/whitelist: they aren't real items, so
            // a whitelist naming only real ids would otherwise silently drop every book.
            if (ShopConfig.isVirtual(entry.virtualId)) {
                if (!isStockableVirtual(entry.virtualId)) {
                    continue;
                }
                tier = VirtualItems.byId(entry.virtualId).parsedTier();
            } else {
                Item item = ShopConfig.resolveItem(entry.item);
                if (item == null || !config.isItemAllowed(item)) {
                    continue;
                }
                tier = GeneralConstants.normalizeTier(entry.tier);
            }
            byTier.computeIfAbsent(tier, key -> new ArrayList<>()).add(entry);
        }
        if (byTier.isEmpty()) {
            return offers;
        }

        for (int slot = offers.size(); slot < config.maximumSlots; slot++) {
            if (random.nextDouble() >= config.additionalSlotChance) {
                if (config.sequentialSlotUnlock) {
                    break;
                }
                continue;
            }
            ShopConfig.RandomEntry picked = pickFromTier(config, byTier, random);
            if (picked == null) {
                continue;
            }
            if (isStockableVirtual(picked.virtualId)) {
                // Books stack freely in storage, so the count range applies without the
                // real-item max-stack clamp rollCount() would otherwise impose.
                int min = Math.max(1, picked.minCount);
                int max = Math.max(min, picked.maxCount);
                int count = min >= max ? min : min + random.nextInt(max - min + 1);
                offers.add(virtualOffer(picked.virtualId, count, picked.experienceCost));
                continue;
            }
            Item item = ShopConfig.resolveItem(picked.item);
            if (item == null) {
                continue;
            }
            offers.add(new ShopOffer(new ItemStack(item, rollCount(picked, item, random)),
                    picked.experienceCost, GeneralConstants.rarityColor(picked.tier)));
        }
        return offers;
    }

    /**
     * Count for a random entry: uniform in {@code [minCount, maxCount]}, always at least 1,
     * and clamped to the item's own max stack size so an unstackable item (a tool, armor)
     * can never be offered as a stack of 3 even if the config asks for it.
     */
    private static int rollCount(ShopConfig.RandomEntry entry, Item item, RandomSource random) {
        int max = Math.min(entry.maxCount, item.getMaxStackSize());
        int min = Math.max(1, Math.min(entry.minCount, max));
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    /** True only for a virtual id that names a book the current config actually defines. */
    private static boolean isStockableVirtual(String virtualId) {
        if (!ShopConfig.isVirtual(virtualId)) {
            return false;
        }
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        return definition != null && definition.appearsInShop;
    }

    private static ShopOffer virtualOffer(String virtualId, int count, int experienceCost) {
        VirtualItems.Definition definition = VirtualItems.byId(virtualId);
        ItemStack icon = definition.iconStack();
        icon.setCount(Math.max(1, count));
        return new ShopOffer(icon, experienceCost, virtualId, GeneralConstants.rarityColor(definition.parsedTier()));
    }

    /**
     * Rolls a rarity tier, then an entry within it.
     *
     * <p>Only tiers that actually have entries are considered, and their configured
     * chances are renormalised over that subset. Without this, a config with no SSR entries
     * would waste 2% of slots on an empty bucket and quietly produce fewer items than the
     * slot rules promised.</p>
     */
    private static ShopConfig.RandomEntry pickFromTier(
            ShopConfig config,
            Map<String, List<ShopConfig.RandomEntry>> byTier,
            RandomSource random) {
        int totalTierWeight = 0;
        for (String tier : byTier.keySet()) {
            totalTierWeight += Math.max(0, config.rarityWeights.weightOf(tier));
        }
        if (totalTierWeight <= 0) {
            return null;
        }
        int target = random.nextInt(totalTierWeight);
        for (var candidate : byTier.entrySet()) {
            target -= Math.max(0, config.rarityWeights.weightOf(candidate.getKey()));
            if (target < 0) {
                List<ShopConfig.RandomEntry> pool = candidate.getValue();
                int weight = 0;
                for (ShopConfig.RandomEntry entry : pool) {
                    weight += entry.weight;
                }
                return pickWeighted(pool, weight, random);
            }
        }
        return null;
    }

    private static ShopConfig.RandomEntry pickWeighted(List<ShopConfig.RandomEntry> pool,
                                                       int totalWeight, RandomSource random) {
        int target = random.nextInt(Math.max(1, totalWeight));
        for (ShopConfig.RandomEntry entry : pool) {
            target -= entry.weight;
            if (target < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
