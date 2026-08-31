package com.whatever.aegis_ascension.perk.talents;

import static com.whatever.aegis_ascension.perk.TalentConstants.COPY_TALENT_CHANCE;
import static com.whatever.aegis_ascension.perk.TalentConstants.SHARED_FORTUNE_FALLBACK_SKILL_ENHANCEMENT_CHARGES;
import static com.whatever.aegis_ascension.perk.TalentConstants.SHARED_FORTUNE_REBIND_COOLDOWN_SECONDS;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_SHARED_FORTUNE;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Server-authoritative multiplayer behavior for Shared Fortune. */
public final class SharedFortune {
    private SharedFortune() {
    }

    /** Binds the owner to one currently online player, subject to the JSON cooldown. */
    public static boolean bind(ServerPlayer owner, UUID partnerId) {
        PlayerPerkData data = PerkData.of(owner);
        if (!data.owns(PERK_SHARED_FORTUNE)) {
            owner.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shared_fortune.not_owned"
            ));
            return false;
        }
        if (owner.getUUID().equals(partnerId)) {
            owner.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shared_fortune.cannot_bind_self"
            ));
            return false;
        }

        ServerPlayer partner = owner.getServer().getPlayerList().getPlayer(partnerId);
        if (partner == null) {
            owner.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shared_fortune.partner_offline"
            ));
            return false;
        }
        if (data.getSharedFortunePartnerId().filter(partnerId::equals).isPresent()) {
            owner.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shared_fortune.already_bound",
                    partner.getDisplayName()
            ));
            return true;
        }

        int remainingSeconds = data.getSharedFortuneRebindCooldownSeconds();
        if (remainingSeconds > 0) {
            owner.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.shared_fortune.rebind_cooldown",
                    remainingSeconds
            ));
            return false;
        }

        Perk sharedFortune = Perk.byId(PERK_SHARED_FORTUNE).orElseThrow();
        long cooldownMillis = Math.max(0L, Math.round(
                sharedFortune.stat(SHARED_FORTUNE_REBIND_COOLDOWN_SECONDS) * 1_000.0D
        ));
        long now = System.currentTimeMillis();
        long rebindAt = cooldownMillis > Long.MAX_VALUE - now
                ? Long.MAX_VALUE
                : now + cooldownMillis;
        data.setSharedFortunePartner(
                partnerId,
                partner.getGameProfile().getName(),
                rebindAt
        );
        owner.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.shared_fortune.bound",
                partner.getDisplayName()
        ));
        partner.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.shared_fortune.bound_by",
                owner.getDisplayName()
        ));
        return true;
    }

    /** Removes the bond without bypassing an already-running rebind cooldown. */
    public static void unbind(ServerPlayer owner) {
        PlayerPerkData data = PerkData.of(owner);
        if (data.getSharedFortunePartnerId().isEmpty()) {
            return;
        }
        data.clearSharedFortunePartner();
        owner.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.shared_fortune.unbound"
        ));
    }

    /**
     * Called only for talents acquired directly from a paid manual choice. Copies call
     * {@link PlayerPerkData#grantSharedFortuneCopy(ServerPlayer, Perk)} directly and never
     * enter this method, preventing reciprocal or chained copy loops.
     */
    public static void onManualTalentSelected(ServerPlayer source, Perk selected) {
        for (ServerPlayer receiver : source.getServer().getPlayerList().getPlayers()) {
            if (receiver.getUUID().equals(source.getUUID())) {
                continue;
            }
            PlayerPerkData receiverData = PerkData.of(receiver);
            if (!receiverData.owns(PERK_SHARED_FORTUNE)
                    || receiverData.getSharedFortunePartnerId()
                    .filter(source.getUUID()::equals)
                    .isEmpty()) {
                continue;
            }

            Perk sharedFortune = Perk.byId(PERK_SHARED_FORTUNE).orElseThrow();
            double copyChance = Mth.clamp(
                    sharedFortune.stat(COPY_TALENT_CHANCE),
                    0.0D,
                    1.0D
            );
            if (receiver.getRandom().nextDouble() >= copyChance) {
                continue;
            }

            if (receiverData.grantSharedFortuneCopy(receiver, selected)) {
                receiver.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shared_fortune.copied",
                        source.getDisplayName(),
                        selected.title()
                ));
                source.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shared_fortune.copy_shared",
                        receiver.getDisplayName(),
                        selected.title()
                ));
            } else {
                int fallbackCharges = Math.max(0, (int) Math.min(
                        Integer.MAX_VALUE,
                        Math.round(sharedFortune.stat(
                                SHARED_FORTUNE_FALLBACK_SKILL_ENHANCEMENT_CHARGES
                        ))
                ));
                receiverData.addSkillEnhancementCharges(fallbackCharges);
                receiver.sendSystemMessage(getTranslatableString(
                        "message.aegis_ascension.shared_fortune.fallback",
                        selected.title(),
                        fallbackCharges
                ));
            }
            ModNetworking.syncTo(receiver);
        }
    }
}
