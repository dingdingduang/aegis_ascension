package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.perk.TalentConstants.CONSTELLATION_XP_COST;
import static com.whatever.aegis_ascension.perk.TalentConstants.DIVINE_SAKURA_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.MAX_CONSTELLATIONS;
import static com.whatever.aegis_ascension.perk.TalentConstants.PERK_DIVINE_SAKURA_POWER;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import com.whatever.aegis_ascension.util.GeneralTextMethods;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client request to spend experience and unlock the next constellation of a
 * constellation-bearing talent (currently Divine Sakura Power), sent when the
 * player clicks the talent in the Owned Talents tab.
 */
public record UnlockConstellationPacket(String perkId) {
    public static void encode(UnlockConstellationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.perkId, 128);
    }

    public static UnlockConstellationPacket decode(FriendlyByteBuf buffer) {
        return new UnlockConstellationPacket(buffer.readUtf(128));
    }

    public static void handle(UnlockConstellationPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PERK_DIVINE_SAKURA_POWER.equals(packet.perkId)) {
                return;
            }
            if (!ToggleRequestLimiter.tryAcquire(player)) {
                return;
            }
            Perk.byId(PERK_DIVINE_SAKURA_POWER).ifPresent(talent ->
                    PerkData.get(player).ifPresent(data -> {
                        if (!data.owns(talent.id())) {
                            return;
                        }
                        int max = (int) Math.max(0.0D, talent.stat(MAX_CONSTELLATIONS));
                        int fromRanks = Math.max(0, data.getRank(talent) - 1);
                        int fromExperience = (int) Math.max(0.0D,
                                data.getCustomStat(DIVINE_SAKURA_CONSTELLATIONS));
                        if (fromRanks + fromExperience >= max) {
                            player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                                    "message.aegis_ascension.constellation.max"));
                            return;
                        }
                        int cost = (int) Math.max(0.0D, talent.stat(CONSTELLATION_XP_COST));
                        boolean paid = GoldCurrency.enabled()
                                ? GoldCurrency.canAfford(data, cost)
                                : GeneralServerMethods.consumeExperience(player, cost);
                        if (!paid) {
                            player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                                    GoldCurrency.enabled()
                                            ? "message.aegis_ascension.constellation.no_gold"
                                            : "message.aegis_ascension.constellation.no_experience",
                                    cost));
                            return;
                        }
                        if (GoldCurrency.enabled() && cost > 0) {
                            GoldCurrency.trySpend(data, cost);
                        }
                        data.setCustomStat(DIVINE_SAKURA_CONSTELLATIONS, fromExperience + 1);
                        player.sendSystemMessage(GeneralTextMethods.getTranslatableString(
                                "message.aegis_ascension.constellation.unlocked",
                                fromRanks + fromExperience + 1));
                        ModNetworking.syncTo(player);
                    })
            );
        });
        context.setPacketHandled(true);
    }
}
