package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_HOMURAS_BLESSING;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Homura's Blessing absorbs the death that would have wiped the player's talents. The
 * reset is skipped in full - talents, charges, Aegises, Skill Enhancements, stats, and
 * storage all survive, so {@code resetPerksOnDeathExceptInventory} has nothing to decide
 * for that death - and the blessing is spent doing it.
 *
 * <p>The perk is not refunded and not reserved: it returns to the ordinary offer pool and
 * costs a perk selection charge to take again. That charge is the price of the negation,
 * which is why the absorption itself is unlimited.</p>
 */
public final class HomuraResetNegation {
    /**
     * Players owed the "your blessing was spent" notice. The message cannot be sent from
     * the clone event, where the replacement entity is still being assembled, so it waits
     * for the respawn.
     */
    private static final Set<UUID> PENDING_NOTICE = ConcurrentHashMap.newKeySet();

    private HomuraResetNegation() {
    }

    /**
     * Spends the blessing to cancel this death's reset, if the player has one to spend.
     *
     * @return true when the reset was absorbed and must not run
     */
    public static boolean absorbDeathReset(UUID playerId) {
        PlayerPerkData data = PerkStore.get(playerId);
        if (!data.owns(PERK_HOMURAS_BLESSING)) {
            return false;
        }
        data.removeTalent(PERK_HOMURAS_BLESSING);
        PENDING_NOTICE.add(playerId);
        return true;
    }

    /** Explains the missing perk once the respawned player can actually receive chat. */
    public static void notifyIfAbsorbed(ServerPlayer player) {
        if (PENDING_NOTICE.remove(player.getUUID())) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.homuras_blessing.reset_negated"
            ));
        }
    }

    public static void clear() {
        PENDING_NOTICE.clear();
    }
}
