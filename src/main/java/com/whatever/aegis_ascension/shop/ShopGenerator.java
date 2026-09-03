package com.whatever.aegis_ascension.shop;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Rolls a fresh set of shop offers from {@link ShopConfig}. Pure logic, no side effects. */
public final class ShopGenerator {
    private ShopGenerator() {
    }

    public static List<ShopOffer> roll(
            RandomSource random,
            PlayerPerkData data,
            ShopType shopType
    ) {
        return shopType == ShopType.DISCOVERY
                ? rollDiscovery(random)
                : roll(random, data);
    }

    public static List<ShopOffer> roll(RandomSource random, PlayerPerkData data) {
        ShopConfig config = ShopConfig.get();
        List<ShopOffer> offers = new ArrayList<>();
        Set<String> uniqueVirtualsInRoll = new HashSet<>();

        // --- Guaranteed slots -------------------------------------------------
        // Shuffled and drawn without replacement so a pool larger than minimumSlots
        // rotates between rerolls instead of always showing the first N entries.
        List<ShopConfig.FixedEntry> guaranteed = new ArrayList<>();
        for (ShopConfig.FixedEntry entry : config.guaranteedItems) {
            if (ShopConfig.isVirtual(entry.virtualId)) {
                if (isStockableVirtual(data, entry.virtualId)) {
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
            if (isStockableVirtual(data, entry.virtualId)) {
                VirtualItems.Definition definition = VirtualItems.byId(entry.virtualId);
                if (definition.uniquePurchase
                        && !uniqueVirtualsInRoll.add(entry.virtualId)) {
                    continue;
                }
                offers.add(virtualOffer(entry.virtualId, entry.count,
                        rollPrice(entry.experienceCost, config.priceVariance, random)));
                continue;
            }
            Item item = ShopConfig.resolveItem(entry.item);
            if (item == null) {
                continue;
            }
            // Fixed entries are offered at their exact configured count, never randomized.
            offers.add(new ShopOffer(new ItemStack(item, entry.count),
                    rollPrice(entry.experienceCost, config.priceVariance, random),
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
                if (!isStockableVirtual(data, entry.virtualId)) {
                    continue;
                }
                VirtualItems.Definition definition = VirtualItems.byId(entry.virtualId);
                if (definition.uniquePurchase
                        && uniqueVirtualsInRoll.contains(entry.virtualId)) {
                    continue;
                }
                tier = definition.parsedTier();
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
            if (isStockableVirtual(data, picked.virtualId)) {
                // Books stack freely in storage, so the count range applies without the
                // real-item max-stack clamp rollCount() would otherwise impose.
                int min = Math.max(1, picked.minCount);
                int max = Math.max(min, picked.maxCount);
                int count = min >= max ? min : min + random.nextInt(max - min + 1);
                offers.add(virtualOffer(picked.virtualId, count,
                        rollPrice(picked.experienceCost, config.priceVariance, random)));
                VirtualItems.Definition definition = VirtualItems.byId(picked.virtualId);
                if (definition.uniquePurchase) {
                    uniqueVirtualsInRoll.add(picked.virtualId);
                    removeUniqueVirtualFromPool(byTier, picked.virtualId);
                }
                continue;
            }
            Item item = ShopConfig.resolveItem(picked.item);
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, rollCount(picked, item, random));
            // Enchanting raises the price, so it is applied before the variance roll.
            int basePrice = picked.experienceCost;
            if (picked.randomEnchantment) {
                basePrice = enchantRandomly(stack, basePrice, config.enchantmentRolls,
                        random);
            }
            offers.add(new ShopOffer(stack,
                    rollPrice(basePrice, config.priceVariance, random),
                    GeneralConstants.rarityColor(picked.tier)));
        }
        return offers;
    }

    /**
     * Selects one real item from the same tiered candidate pool used by a shop.
     * Quest rewards use this instead of rolling shop stock, so they share filters,
     * classification, and weights without changing or selling out a player's offers.
     */
    public static Optional<Item> rollRewardItem(RandomSource random, ShopType shopType,
                                                String requestedTier,
                                                Predicate<Item> eligibility) {
        if (random == null) return Optional.empty();
        ShopType source = shopType == null ? ShopType.COMMON : shopType;
        String tier = GeneralConstants.normalizeTier(requestedTier);
        Predicate<Item> allowed = eligibility == null ? item -> true : eligibility;
        return source == ShopType.DISCOVERY
                ? rollDiscoveryRewardItem(random, tier, allowed)
                : rollCommonRewardItem(random, tier, allowed);
    }

    private static Optional<Item> rollCommonRewardItem(RandomSource random, String tier,
                                                       Predicate<Item> eligibility) {
        ShopConfig config = ShopConfig.get();
        List<WeightedRewardItem> candidates = new ArrayList<>();
        for (ShopConfig.RandomEntry entry : config.randomItems) {
            if (entry == null || entry.weight <= 0 || ShopConfig.isVirtual(entry.virtualId)
                    || !tier.equals(GeneralConstants.normalizeTier(entry.tier))) {
                continue;
            }
            Item item = ShopConfig.resolveItem(entry.item);
            if (item != null && config.isItemAllowed(item) && eligibility.test(item)) {
                candidates.add(new WeightedRewardItem(item, entry.weight));
            }
        }
        return pickWeightedRewardItem(candidates, random);
    }

    private static Optional<Item> rollDiscoveryRewardItem(RandomSource random, String tier,
                                                          Predicate<Item> eligibility) {
        ShopConfig.DiscoveryShop config = ShopConfig.get().discoveryShop;
        if (!config.enabled) return Optional.empty();
        List<WeightedRewardItem> candidates = new ArrayList<>();
        for (Item item : GeneralServerMethods.getAllItems()) {
            if (item == null || !config.isItemAllowed(item) || !eligibility.test(item)) {
                continue;
            }
            ItemStack probe = new ItemStack(item);
            if (probe.isEmpty() || probe.getMaxStackSize() <= 0) continue;
            EquipmentPower power = equipmentPower(probe);
            ShopConfig.DiscoveryOfferSettings settings = config.settingsFor(
                    item, power.attackDamage(), power.armor());
            if (settings.selectionWeight() > 0.0D && tier.equals(settings.tier())) {
                candidates.add(new WeightedRewardItem(item, settings.selectionWeight()));
            }
        }
        return pickWeightedRewardItem(candidates, random);
    }

    private static Optional<Item> pickWeightedRewardItem(
            List<WeightedRewardItem> candidates, RandomSource random) {
        double totalWeight = 0.0D;
        for (WeightedRewardItem candidate : candidates) {
            totalWeight += candidate.weight();
        }
        if (!(totalWeight > 0.0D) || !Double.isFinite(totalWeight)) {
            return Optional.empty();
        }
        double target = random.nextDouble() * totalWeight;
        for (int index = 0; index < candidates.size(); index++) {
            WeightedRewardItem candidate = candidates.get(index);
            target -= candidate.weight();
            if (target <= 0.0D || index == candidates.size() - 1) {
                return Optional.of(candidate.item());
            }
        }
        return Optional.empty();
    }

    private record WeightedRewardItem(Item item, double weight) {
    }

    /**
     * Samples real items from the complete live item registry, including mod-owned entries.
     * Candidates are shuffled and consumed without replacement, so one restock cannot show
     * the same item twice. Filters and ordered price/count/tier rules come from
     * {@code discoveryShop} in shopsetting.json.
     */
    private static List<ShopOffer> rollDiscovery(RandomSource random) {
        ShopConfig.DiscoveryShop config = ShopConfig.get().discoveryShop;
        if (!config.enabled) {
            return List.of();
        }

        Map<String, List<DiscoveryCandidate>> candidatesByTier = new LinkedHashMap<>();
        int candidateCount = 0;
        for (Item item : GeneralServerMethods.getAllItems()) {
            if (item == null || !config.isItemAllowed(item)) {
                continue;
            }
            ItemStack probe = new ItemStack(item);
            if (probe.isEmpty() || probe.getMaxStackSize() <= 0) {
                continue;
            }
            EquipmentPower power = equipmentPower(probe);
            ShopConfig.DiscoveryOfferSettings settings = config.settingsFor(
                    item,
                    power.attackDamage(),
                    power.armor()
            );
            if (settings.selectionWeight() <= 0.0D) {
                continue;
            }
            candidatesByTier.computeIfAbsent(settings.tier(), ignored -> new ArrayList<>())
                    .add(new DiscoveryCandidate(item, settings));
            candidateCount++;
        }

        int targetSlots = Math.min(config.minimumSlots, candidateCount);
        for (int slot = config.minimumSlots;
             slot < config.maximumSlots && targetSlots < candidateCount; slot++) {
            if (random.nextDouble() < config.additionalSlotChance) {
                targetSlots++;
            } else if (config.sequentialSlotUnlock) {
                break;
            }
        }

        List<ShopOffer> offers = new ArrayList<>(targetSlots);
        for (int index = 0; index < targetSlots; index++) {
            String tier = pickDiscoveryTier(config, candidatesByTier, random);
            if (tier == null) {
                break;
            }
            List<DiscoveryCandidate> tierCandidates = candidatesByTier.get(tier);
            DiscoveryCandidate candidate = takeWeightedDiscoveryCandidate(
                    tierCandidates,
                    random
            );
            if (tierCandidates.isEmpty()) {
                candidatesByTier.remove(tier);
            }
            Item item = candidate.item();
            ShopConfig.DiscoveryOfferSettings settings = candidate.settings();
            int itemMax = Math.max(1, item.getMaxStackSize());
            int max = Math.max(1, Math.min(settings.maxCount(), itemMax));
            int min = Math.max(1, Math.min(settings.minCount(), max));
            int count = min >= max ? min : min + random.nextInt(max - min + 1);
            offers.add(new ShopOffer(
                    new ItemStack(item, count),
                    rollPrice(settings.experienceCost(), config.priceVariance, random),
                    GeneralConstants.rarityColor(settings.tier())
            ));
        }
        return offers;
    }

    private record DiscoveryCandidate(
            Item item,
            ShopConfig.DiscoveryOfferSettings settings
    ) {
    }

    private record EquipmentPower(double attackDamage, double armor) {
    }

    /** Reads the values shown when the default stack is equipped, including mod attributes. */
    private static EquipmentPower equipmentPower(ItemStack stack) {
        double attackDamage = maximumEquippedAttribute(stack, Attributes.ATTACK_DAMAGE, 1.0D);
        double armor = maximumEquippedAttribute(stack, Attributes.ARMOR, 0.0D);
        if (stack.getItem() instanceof ArmorItem armorItem) {
            armor = Math.max(armor, armorItem.getDefense());
        }
        return new EquipmentPower(
                Math.max(0.0D, attackDamage),
                Math.max(0.0D, armor)
        );
    }

    /** Highest effective value contributed in any equipment slot by one attribute. */
    private static double maximumEquippedAttribute(
            ItemStack stack,
            Attribute attribute,
            double baseValue
    ) {
        double maximum = 0.0D;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var modifiers = stack.getAttributeModifiers(slot).get(attribute);
            if (modifiers.isEmpty()) {
                continue;
            }
            double additions = 0.0D;
            double multiplyBase = 0.0D;
            double multiplyTotal = 1.0D;
            for (AttributeModifier modifier : modifiers) {
                double amount = modifier.getAmount();
                if (!Double.isFinite(amount)) {
                    continue;
                }
                switch (modifier.getOperation()) {
                    case ADDITION -> additions += amount;
                    case MULTIPLY_BASE -> multiplyBase += amount;
                    case MULTIPLY_TOTAL -> multiplyTotal *= 1.0D + amount;
                }
            }
            double value = (baseValue + additions + baseValue * multiplyBase) * multiplyTotal;
            if (Double.isFinite(value)) {
                maximum = Math.max(maximum, value);
            }
        }
        return maximum;
    }

    /** Rolls R/SR/SSR using only tiers that still contain candidates. */
    private static String pickDiscoveryTier(
            ShopConfig.DiscoveryShop config,
            Map<String, List<DiscoveryCandidate>> candidatesByTier,
            RandomSource random
    ) {
        int totalWeight = 0;
        for (String tier : candidatesByTier.keySet()) {
            totalWeight += config.rarityWeights.weightOf(tier);
        }
        if (totalWeight <= 0) {
            return null;
        }
        int target = random.nextInt(totalWeight);
        for (String tier : candidatesByTier.keySet()) {
            target -= config.rarityWeights.weightOf(tier);
            if (target < 0) {
                return tier;
            }
        }
        return null;
    }

    /** Weighted sampling without replacement inside one Discovery rarity tier. */
    private static DiscoveryCandidate takeWeightedDiscoveryCandidate(
            List<DiscoveryCandidate> candidates,
            RandomSource random
    ) {
        double totalWeight = 0.0D;
        for (DiscoveryCandidate candidate : candidates) {
            totalWeight += candidate.settings().selectionWeight();
        }
        double target = random.nextDouble() * totalWeight;
        for (int index = 0; index < candidates.size(); index++) {
            DiscoveryCandidate candidate = candidates.get(index);
            target -= candidate.settings().selectionWeight();
            if (target <= 0.0D || index == candidates.size() - 1) {
                candidates.remove(index);
                return candidate;
            }
        }
        throw new IllegalStateException("Discovery candidate pool was empty");
    }

    /**
     * Count for a random entry: uniform in {@code [minCount, maxCount]}, always at least 1,
     * and clamped to the item's own max stack size so an unstackable item (a tool, armor)
     * can never be offered as a stack of 3 even if the config asks for it.
     */
    /**
     * Varies a listed price around its configured amount so the same item is not always
     * worth the same. Rolled once when the stock is generated and carried on the offer,
     * so the price a player is shown is the price the server charges, and a cheap roll
     * rewards checking the shop rather than being a client-side illusion.
     */
    /**
     * Puts one random enchantment on a stack and returns what it should now cost.
     *
     * <p>An enchanted book stores its enchantment differently from an enchanted tool -
     * on the book it is inert data describing what it can confer, on the tool it is
     * active - so the two are written differently even though both end up as stack NBT.
     * Levels are drawn across the enchantment's whole range rather than always maxed, so
     * a shop that stocks Sharpness is not always stocking Sharpness V.</p>
     *
     * @return the adjusted base price, unchanged when nothing could be rolled
     */
    private static int enchantRandomly(ItemStack stack, int basePrice,
                                       ShopConfig.EnchantmentRolls settings,
                                       RandomSource random) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
            if (enchantment == null) continue;
            if (enchantment.isCurse() && !settings.allowCurses) continue;
            if (enchantment.isTreasureOnly() && !settings.allowTreasure) continue;
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (id != null && settings.excluded != null
                    && settings.excluded.contains(id.toString())) {
                continue;
            }
            boolean book = stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK);
            if (!book && !enchantment.canEnchant(stack)) continue;
            candidates.add(enchantment);
        }
        if (candidates.isEmpty()) return basePrice;

        Enchantment chosen = candidates.get(random.nextInt(candidates.size()));
        int maxLevel = Math.max(1, chosen.getMaxLevel());
        int level = 1 + random.nextInt(maxLevel);
        if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) {
            // A plain book becomes an enchanted one; writing the enchantment onto a
            // plain book would leave an item that displays nothing.
            stack.setCount(1);
            if (stack.is(Items.BOOK)) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.setTag(stack.getTag());
                stack = book;
            }
            EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(chosen, level));
        } else {
            stack.enchant(chosen, level);
        }
        double multiplier = 1.0D + Math.max(0.0D, settings.costPerLevel) * level;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                Math.round(basePrice * multiplier)));
    }

    private static int rollPrice(int basePrice, double variance, RandomSource random) {
        if (basePrice <= 0 || !(variance > 0.0D) || !Double.isFinite(variance)) {
            return Math.max(0, basePrice);
        }
        double spread = Math.min(0.95D, variance);
        double factor = 1.0D + (random.nextDouble() * 2.0D - 1.0D) * spread;
        long rolled = Math.round(basePrice * factor);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, rolled));
    }

    private static int rollCount(ShopConfig.RandomEntry entry, Item item, RandomSource random) {
        int max = Math.min(entry.maxCount, item.getMaxStackSize());
        int min = Math.max(1, Math.min(entry.minCount, max));
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    /** True only for a virtual id that names a book the current config actually defines. */
    private static boolean isStockableVirtual(PlayerPerkData data, String virtualId) {
        if (!ShopConfig.isVirtual(virtualId)) {
            return false;
        }
        return VirtualItems.canAppearInShop(data, virtualId);
    }

    private static void removeUniqueVirtualFromPool(
            Map<String, List<ShopConfig.RandomEntry>> byTier,
            String virtualId) {
        byTier.values().forEach(entries -> entries.removeIf(entry ->
                virtualId.equals(entry.virtualId)));
        byTier.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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
