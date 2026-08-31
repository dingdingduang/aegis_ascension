package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.command.AegisAscensionCommands;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.aegis.DevourAegis;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.whatever.aegis_ascension.shop.ShopConfig;
import com.whatever.aegis_ascension.storage.PlayerStorage;
import com.whatever.aegis_ascension.storage.StorageConfig;
import com.whatever.aegis_ascension.storage.StoredItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Every mutating storage request, folded into one packet keyed by {@link Action}. They
 * share identical validation (row exists, amount is sane, clamp to what's actually banked),
 * so splitting them into four near-identical classes would duplicate that logic four times
 * — the place where a subtle divergence could become a duping bug.
 *
 * <p>Rows are addressed by <em>identity</em> — the item and NBT, or the virtual id — never
 * by position. That is what lets the client sort, filter, page and drag-reorder freely: a
 * request built against one view can't act on the wrong row just because the list moved
 * underneath it. A stale key either resolves to the right row or to none.</p>
 *
 * <p>{@code amount} is advisory: the server clamps it to the row's real count, so a client
 * asking to extract 9999 of a 96-stack gets 96, never a negative or inflated row.</p>
 */
public record StorageActionPacket(Action action, ItemStack key, String virtualId, long amount) {
    public enum Action {
        EXTRACT,
        DISCARD,
        SELL,
        USE
    }

    /** Addresses a row by its identity, which is what makes client-side ordering safe. */
    public StorageActionPacket(Action action, StoredItem row, long amount) {
        this(action, row.prototype(), row.virtualId(), amount);
    }

    public static void encode(StorageActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeItem(packet.key);
        buffer.writeUtf(packet.virtualId, 128);
        buffer.writeVarLong(Math.max(0L, packet.amount));
    }

    public static StorageActionPacket decode(FriendlyByteBuf buffer) {
        return new StorageActionPacket(buffer.readEnum(Action.class), buffer.readItem(),
                buffer.readUtf(128), buffer.readVarLong());
    }

    public static void handle(StorageActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!StorageRequestLimiter.tryAcquireMutation(player)) {
                // Do not answer rejected spam with a full storage sync. The accepted
                // request already produces the authoritative sync the screen needs.
                return;
            }

            PerkData.get(player).ifPresent(data -> {
                PlayerStorage storage = data.getStorage();

                // Resolved by identity, so it doesn't matter how the client had the list
                // ordered when the player clicked.
                int index = storage.indexOf(packet.key, packet.virtualId);
                StoredItem row = storage.get(index);
                if (row == null) {
                    ModNetworking.syncStorageTo(player);
                    return;
                }
                long amount = Math.min(Math.max(0L, packet.amount), row.count());
                if (amount <= 0L) {
                    ModNetworking.syncStorageTo(player);
                    return;
                }

                switch (packet.action) {
                    case EXTRACT -> {
                        if (row.isVirtual()) {
                            player.displayClientMessage(getTranslatableString(
                                    "message.aegis_ascension.storage.not_extractable"), true);
                            break;
                        }
                        storage.extractToPlayer(index, amount, player);
                    }
                    case USE -> consumeVirtual(player, data, storage, index, row, amount);
                    case DISCARD -> storage.remove(index, amount);
                    case SELL -> {
                        if (row.isVirtual()) {
                            // A book has no shop price, so selling it would destroy a
                            // capped, hard-to-replace consumable for nothing.
                            player.displayClientMessage(getTranslatableString(
                                    "message.aegis_ascension.storage.not_sellable"), true);
                            break;
                        }
                        // An item the shop doesn't stock has no price, so it sells for 0 —
                        // the sale still goes through and consumes the items. The UI shows
                        // the 0 value up front and routes every sale through the quantity
                        // prompt, so this can't happen on a single stray click.
                        int unit = ShopConfig.get().sellUnitExperience(row.prototype().getItem());
                        long removed = storage.remove(index, amount);
                        long payout = Math.round(removed * unit
                                * StorageConfig.get().sellExperienceRatio);
                        if (payout > 0L) {
                            if (GoldCurrency.enabled()) {
                                GoldCurrency.grant(data, payout);
                            } else {
                                // giveExperiencePoints takes an int; a payout past that range
                                // would silently wrap negative and delete the player's XP.
                                player.giveExperiencePoints(
                                        (int) Math.min(Integer.MAX_VALUE, payout));
                            }
                        }
                        player.displayClientMessage(getTranslatableString(
                                GoldCurrency.enabled()
                                        ? "message.aegis_ascension.storage.sold_gold"
                                        : "message.aegis_ascension.storage.sold",
                                removed, row.prototype().getHoverName(), payout), true);
                    }
                    default -> {
                    }
                }
                ModNetworking.syncStorageTo(player);
                if (GoldCurrency.enabled()) ModNetworking.syncPerkDataTo(player);
            });
        });
        context.setPacketHandled(true);
    }

    /**
     * Spends virtual books one at a time, stopping at whichever runs out first: the banked
     * count, the requested amount, or the player's remaining lifetime uses. Consuming fewer
     * than requested is normal, not an error — the cap is the point.
     */
    private static void consumeVirtual(ServerPlayer player, PlayerPerkData data,
                                       PlayerStorage storage, int index,
                                       StoredItem row, long amount) {
        if (!row.isVirtual()) {
            return;
        }
        VirtualItems.Definition definition = VirtualItems.byId(row.virtualId());
        if (definition == null) {
            return;
        }
        if (definition.effect == VirtualItems.Effect.DEVOUR_AEGIS_CORE) {
            if (!data.hasAegis(AegisConstants.DEVOUR)) {
                player.displayClientMessage(getTranslatableString(
                        "message.aegis_ascension.devour.core.no_aegis"), true);
                return;
            }
            int expectedLevel = VirtualItems.devourCoreLevel(data) + 1;
            int targetLevel = VirtualItems.devourCoreTargetLevel(row.virtualId());
            if (targetLevel != expectedLevel) {
                player.displayClientMessage(getTranslatableString(
                        "message.aegis_ascension.devour.core.wrong_order",
                        expectedLevel), true);
                return;
            }
        }
        int remaining = VirtualItems.remainingUses(data, row.virtualId());
        if (remaining <= 0) {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.storage.use_cap",
                    row.displayComponent(), definition.maxUses), true);
            return;
        }

        if (definition.effect.isAction()) {
            consumeAction(player, data, storage, index, row, definition);
            return;
        }

        long consumed = Math.min(amount, remaining);
        consumed = storage.remove(index, consumed);
        if (consumed <= 0L) {
            return;
        }
        data.addVirtualItemUse(row.virtualId(), (int) consumed);
        if (definition.effect == VirtualItems.Effect.DEVOUR_AEGIS_CORE) {
            int level = VirtualItems.devourCoreLevel(data);
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.devour.core.used",
                    level,
                    DevourAegis.itemLimit(data)
            ), true);
            ModNetworking.syncTo(player);
            return;
        }
        TalentEffects.recalculateAttributes(player, data);
        // The bonus lands on max health as a modifier; without this the player keeps the
        // old current-health value and the new hearts read as already-missing.
        if (definition.effect == VirtualItems.Effect.MAX_HEALTH) {
            player.heal((float) (definition.amount * consumed));
        }
        if (VirtualItems.isUncapped(row.virtualId())) {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.storage.used_uncapped",
                    consumed, row.displayComponent(),
                    data.getVirtualItemUses(row.virtualId())), true);
        } else {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.storage.used",
                    consumed, row.displayComponent(),
                    data.getVirtualItemUses(row.virtualId()), definition.maxUses), true);
        }
        ModNetworking.syncTo(player);
    }

    /**
     * Fires a one-shot action book. Two differences from a stat book, both deliberate:
     * exactly one is spent no matter how many were requested (a second reset in the same
     * breath would do nothing), and the effect is attempted <em>before</em> the item is
     * removed — so a book that would be a no-op is refused rather than silently burned.
     */
    private static void consumeAction(ServerPlayer player, PlayerPerkData data,
                                      PlayerStorage storage, int index,
                                      StoredItem row, VirtualItems.Definition definition) {
        boolean applied;
        switch (definition.effect) {
            case RESET_DEVOURED -> applied = data.resetDevouredItems();
            case RESET_PROGRESSION -> {
                AegisAscensionCommands.resetProgression(player);
                // Always applicable: even with nothing chosen, the reset re-grants the
                // selection charges the player's level entitles them to.
                applied = true;
            }
            default -> applied = false;
        }
        if (!applied) {
            player.displayClientMessage(getTranslatableString(
                    "message.aegis_ascension.storage.no_effect",
                    row.displayComponent()), true);
            return;
        }
        // RESET_PROGRESSION may remove banked Devour Cores before this action item.
        // Resolve the row again by identity so an index shift cannot consume another item.
        int currentIndex = storage.indexOf(row.prototype(), row.virtualId());
        if (storage.remove(currentIndex, 1L) <= 0L) {
            return;
        }
        data.addVirtualItemUse(row.virtualId(), 1);
        // Mirrors DiscardDevouredItemPacket: applyChosenPerks re-derives everything the
        // removed inherited attributes were feeding, then both views are resynced.
        data.applyChosenPerks(player);
        player.displayClientMessage(getTranslatableString(
                "message.aegis_ascension.storage.used_action",
                row.displayComponent()), true);
        ModNetworking.syncTo(player);
        ModNetworking.syncDevourDataTo(player);
    }
}
