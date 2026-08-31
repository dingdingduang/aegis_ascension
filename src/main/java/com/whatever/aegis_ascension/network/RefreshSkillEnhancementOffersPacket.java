package com.whatever.aegis_ascension.network;

import static com.whatever.aegis_ascension.perk.TalentConstants.SOUL_LOGISTICS_COMBO;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.mechanic.GoldCurrency;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Requests a paid reroll of the server-locked Skill Enhancement choices. */
public record RefreshSkillEnhancementOffersPacket() {
    public static void encode(RefreshSkillEnhancementOffersPacket packet,
                              FriendlyByteBuf buffer) {
    }

    public static RefreshSkillEnhancementOffersPacket decode(FriendlyByteBuf buffer) {
        return new RefreshSkillEnhancementOffersPacket();
    }

    public static void handle(RefreshSkillEnhancementOffersPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!RefreshRequestLimiter.tryAcquire(sender)) {
                return;
            }

            PerkData.get(sender).ifPresent(data -> {
                int configuredCost = Math.max(
                        0,
                        PlatformServices.config().skillEnhancementRefreshExperienceCost()
                );
                boolean free = data.hasActiveSoulLink(SOUL_LOGISTICS_COMBO);
                int chargedCost = free ? 0 : configuredCost;
                long currencyCost = Math.max(0L, chargedCost);

                if (data.getSkillEnhancementCharges() <= 0
                        || data.getPendingSkillEnhancementOffers().isEmpty()) {
                    sender.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.skill_enhancement_refresh.unavailable"
                    ), true);
                } else if (GoldCurrency.enabled()
                        ? !free && !GoldCurrency.canAfford(data, currencyCost)
                        : sender.totalExperience < chargedCost) {
                    sender.displayClientMessage(getTranslatableString(
                            GoldCurrency.enabled()
                                    ? "message.aegis_ascension.skill_enhancement_refresh.insufficient_gold"
                                    : "message.aegis_ascension.skill_enhancement_refresh.insufficient_experience",
                            currencyCost,
                            GoldCurrency.enabled() ? data.getGoldCurrency() : sender.totalExperience
                    ), true);
                } else if (data.refreshSkillEnhancementOffers(sender)) {
                    if (chargedCost > 0) {
                        if (GoldCurrency.enabled()) GoldCurrency.trySpend(data, currencyCost);
                        else sender.giveExperiencePoints(-chargedCost);
                    }
                } else {
                    sender.displayClientMessage(getTranslatableString(
                            "message.aegis_ascension.skill_enhancement_refresh.no_alternative"
                    ), true);
                }

                // Return the authoritative price, choices, charges, and Soul Link state.
                ModNetworking.syncTo(sender);
            });
        });
        context.setPacketHandled(true);
    }
}
