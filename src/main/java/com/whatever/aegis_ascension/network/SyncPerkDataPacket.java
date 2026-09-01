package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.util.DisplayStatScope;
import com.whatever.aegis_ascension.perk.SkillEnhancement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public record SyncPerkDataPacket(int selectionCharges, int pendingBreakthroughTriggers,
                                 int perkRefreshCharges,
                                 int maxTalentSlots,
                                 int skillEnhancementCharges,
                                 int skillEnhancementChargesPerPerkExchange,
                                 int skillEnhancementRefreshExperienceCost,
                                 boolean skillEnhancementRefreshFree,
                                 int aegisSelectionCharges,
                                 int aegisRefreshCharges,
                                 boolean liveCustomStatsRefreshAllowed,
                                 boolean usesMinecraftDefaultLevel,
                                 boolean usesGoldCurrency,
                                 long goldCurrency,
                                 int progressionLevel,
                                 int aegisAscensionRank,
                                 long aegisAscensionExperience,
                                 long aegisAscensionExperienceToNextRank,
                                 int aegisAscensionMaximumRank,
                                 UUID sharedFortunePartnerId,
                                 String sharedFortunePartnerName,
                                 int sharedFortuneRebindCooldownSeconds,
                                 Set<String> hiddenTalentIds,
                                 Map<Perk, Integer> perkRanks,
                                 Set<String> enabledManualTalents,
                                 Map<String, Double> displayStats,
                                 DisplayStatScope displayStatScope,
                                 Map<SkillEnhancement, Integer> skillEnhancementRanks,
                                 List<SkillEnhancement> skillEnhancementOffers,
                                 SkillEnhancement primarySkillEnhancement,
                                 boolean primarySkillEnhancementChosen,
                                 Set<Aegis> chosenAegises,
                                 Set<String> disabledManualAegises) {
    public SyncPerkDataPacket {
        Map<Perk, Integer> copy = new LinkedHashMap<>();
        copy.putAll(perkRanks);
        perkRanks = Collections.unmodifiableMap(copy);
        hiddenTalentIds = Collections.unmodifiableSet(new LinkedHashSet<>(hiddenTalentIds));
        enabledManualTalents = Collections.unmodifiableSet(new LinkedHashSet<>(enabledManualTalents));
        displayStats = Collections.unmodifiableMap(new LinkedHashMap<>(displayStats));
        skillEnhancementRanks = Collections.unmodifiableMap(
                new LinkedHashMap<>(skillEnhancementRanks)
        );
        skillEnhancementOffers = List.copyOf(skillEnhancementOffers);
        primarySkillEnhancement = java.util.Objects.requireNonNull(
                primarySkillEnhancement,
                "primarySkillEnhancement"
        );
        sharedFortunePartnerName = sharedFortunePartnerId == null
                ? ""
                : java.util.Objects.requireNonNullElse(sharedFortunePartnerName, "");
        sharedFortuneRebindCooldownSeconds = Math.max(
                0,
                sharedFortuneRebindCooldownSeconds
        );
        chosenAegises = Collections.unmodifiableSet(new LinkedHashSet<>(chosenAegises));
        disabledManualAegises = Collections.unmodifiableSet(
                new LinkedHashSet<>(disabledManualAegises)
        );
    }

    public static void encode(SyncPerkDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.selectionCharges);
        buffer.writeVarInt(packet.pendingBreakthroughTriggers);
        buffer.writeVarInt(packet.perkRefreshCharges);
        buffer.writeVarInt(packet.maxTalentSlots);
        buffer.writeVarInt(packet.skillEnhancementCharges);
        buffer.writeVarInt(packet.skillEnhancementChargesPerPerkExchange);
        buffer.writeVarInt(packet.skillEnhancementRefreshExperienceCost);
        buffer.writeBoolean(packet.skillEnhancementRefreshFree);
        buffer.writeVarInt(packet.aegisSelectionCharges);
        buffer.writeVarInt(packet.aegisRefreshCharges);
        buffer.writeBoolean(packet.liveCustomStatsRefreshAllowed);
        buffer.writeBoolean(packet.usesMinecraftDefaultLevel);
        buffer.writeBoolean(packet.usesGoldCurrency);
        buffer.writeVarLong(Math.max(0L, packet.goldCurrency));
        buffer.writeVarInt(Math.max(0, packet.progressionLevel));
        buffer.writeVarInt(Math.max(1, packet.aegisAscensionRank));
        buffer.writeVarLong(Math.max(0L, packet.aegisAscensionExperience));
        buffer.writeVarLong(Math.max(0L, packet.aegisAscensionExperienceToNextRank));
        buffer.writeVarInt(Math.max(1, packet.aegisAscensionMaximumRank));
        buffer.writeBoolean(packet.sharedFortunePartnerId != null);
        if (packet.sharedFortunePartnerId != null) {
            buffer.writeUUID(packet.sharedFortunePartnerId);
            buffer.writeUtf(packet.sharedFortunePartnerName, 64);
        }
        buffer.writeVarInt(packet.sharedFortuneRebindCooldownSeconds);
        buffer.writeVarInt(packet.hiddenTalentIds.size());
        packet.hiddenTalentIds.forEach(perkId -> buffer.writeUtf(perkId, 128));
        buffer.writeVarInt(packet.perkRanks.size());
        packet.perkRanks.forEach((perk, rank) -> {
            buffer.writeUtf(perk.id(), 128);
            buffer.writeVarInt(rank);
        });
        buffer.writeVarInt(packet.enabledManualTalents.size());
        packet.enabledManualTalents.forEach(perkId -> buffer.writeUtf(perkId, 128));
        buffer.writeVarInt(packet.displayStatScope.wireValue());
        buffer.writeVarInt(packet.displayStats.size());
        packet.displayStats.forEach((key, value) -> {
            buffer.writeUtf(key, 128);
            buffer.writeDouble(value);
        });
        buffer.writeVarInt(packet.skillEnhancementRanks.size());
        packet.skillEnhancementRanks.forEach((enhancement, rank) -> {
            buffer.writeUtf(enhancement.id(), 128);
            buffer.writeVarInt(rank);
        });
        buffer.writeVarInt(packet.skillEnhancementOffers.size());
        packet.skillEnhancementOffers.forEach(enhancement ->
                buffer.writeUtf(enhancement.id(), 128)
        );
        buffer.writeUtf(packet.primarySkillEnhancement.id(), 128);
        buffer.writeBoolean(packet.primarySkillEnhancementChosen);
        buffer.writeVarInt(packet.chosenAegises.size());
        packet.chosenAegises.forEach(aegis -> buffer.writeUtf(aegis.id(), 128));
        buffer.writeVarInt(packet.disabledManualAegises.size());
        packet.disabledManualAegises.forEach(aegisId -> buffer.writeUtf(aegisId, 128));
    }

    public static SyncPerkDataPacket decode(FriendlyByteBuf buffer) {
        int charges = Math.max(0, buffer.readVarInt());
        int pendingBreakthroughTriggers = Math.max(0, buffer.readVarInt());
        int perkRefreshCharges = Math.max(0, buffer.readVarInt());
        int maxTalentSlots = Math.max(1, buffer.readVarInt());
        int skillEnhancementCharges = Math.max(0, buffer.readVarInt());
        int skillEnhancementChargesPerPerkExchange = Math.max(1, buffer.readVarInt());
        int skillEnhancementRefreshExperienceCost = Math.max(0, buffer.readVarInt());
        boolean skillEnhancementRefreshFree = buffer.readBoolean();
        int aegisSelectionCharges = Math.max(0, buffer.readVarInt());
        int aegisRefreshCharges = Math.max(0, buffer.readVarInt());
        boolean liveCustomStatsRefreshAllowed = buffer.readBoolean();
        boolean usesMinecraftDefaultLevel = buffer.readBoolean();
        boolean usesGoldCurrency = buffer.readBoolean();
        long goldCurrency = Math.max(0L, buffer.readVarLong());
        int progressionLevel = Math.max(0, buffer.readVarInt());
        int aegisAscensionRank = Math.max(1, buffer.readVarInt());
        long aegisAscensionExperience = Math.max(0L, buffer.readVarLong());
        long aegisAscensionExperienceToNextRank = Math.max(0L, buffer.readVarLong());
        int aegisAscensionMaximumRank = Math.max(1, buffer.readVarInt());
        UUID sharedFortunePartnerId = null;
        String sharedFortunePartnerName = "";
        if (buffer.readBoolean()) {
            sharedFortunePartnerId = buffer.readUUID();
            sharedFortunePartnerName = buffer.readUtf(64);
        }
        int sharedFortuneRebindCooldownSeconds = Math.max(0, buffer.readVarInt());
        int hiddenTalentCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_TALENTS,
                "hidden talent"
        );
        Set<String> hiddenTalentIds = new LinkedHashSet<>();
        for (int index = 0; index < hiddenTalentCount; index++) {
            Perk.byId(buffer.readUtf(128)).ifPresent(perk ->
                    hiddenTalentIds.add(perk.id())
            );
        }
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_TALENTS,
                "talent rank"
        );
        Map<Perk, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String perkId = buffer.readUtf(128);
            int rank = Math.max(0, buffer.readVarInt());
            Perk.byId(perkId).ifPresent(perk -> {
                if (rank > 0) {
                    ranks.put(perk, rank);
                }
            });
        }
        int enabledCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_TALENTS,
                "enabled manual talent"
        );
        Set<String> enabledTalents = new LinkedHashSet<>();
        for (int index = 0; index < enabledCount; index++) {
            String perkId = buffer.readUtf(128);
            Perk.byId(perkId).filter(Perk::manuallyToggleable)
                    .ifPresent(perk -> enabledTalents.add(perk.id()));
        }
        // Read in the order encode writes: scope first, then the entry count.
        DisplayStatScope displayStatScope =
                DisplayStatScope.fromWireValue(buffer.readVarInt());
        int statCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_DISPLAY_STATS,
                "display stat"
        );
        Map<String, Double> displayStats = new LinkedHashMap<>();
        for (int index = 0; index < statCount; index++) {
            String key = buffer.readUtf(128);
            double value = buffer.readDouble();
            if (!key.isBlank() && Double.isFinite(value)) {
                displayStats.put(key, value);
            }
        }
        int enhancementRankCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_SKILL_ENHANCEMENTS,
                "skill enhancement rank"
        );
        Map<SkillEnhancement, Integer> enhancementRanks = new LinkedHashMap<>();
        for (int index = 0; index < enhancementRankCount; index++) {
            String enhancementId = buffer.readUtf(128);
            int rank = Math.max(0, buffer.readVarInt());
            SkillEnhancement.byId(enhancementId).ifPresent(enhancement -> {
                if (rank > 0) {
                    enhancementRanks.put(enhancement, rank);
                }
            });
        }
        int offerCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_SKILL_ENHANCEMENTS,
                "skill enhancement offer"
        );
        List<SkillEnhancement> enhancementOffers = new java.util.ArrayList<>();
        for (int index = 0; index < offerCount; index++) {
            SkillEnhancement.byId(buffer.readUtf(128)).ifPresent(enhancement -> {
                if (!enhancementOffers.contains(enhancement)) {
                    enhancementOffers.add(enhancement);
                }
            });
        }
        SkillEnhancement primarySkillEnhancement = SkillEnhancement.byId(
                buffer.readUtf(128)
        ).orElseGet(SkillEnhancement::defaultPrimary);
        boolean primarySkillEnhancementChosen = buffer.readBoolean();
        int chosenAegisCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_AEGISES,
                "chosen Aegis"
        );
        Set<Aegis> chosenAegises = new LinkedHashSet<>();
        for (int index = 0; index < chosenAegisCount; index++) {
            Aegis.byId(buffer.readUtf(128)).ifPresent(chosenAegises::add);
        }
        int disabledAegisCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_AEGISES,
                "disabled Aegis"
        );
        Set<String> disabledManualAegises = new LinkedHashSet<>();
        for (int index = 0; index < disabledAegisCount; index++) {
            String aegisId = buffer.readUtf(128);
            Aegis.byId(aegisId)
                    .filter(Aegis::manuallyToggleable)
                    .filter(chosenAegises::contains)
                    .ifPresent(aegis -> disabledManualAegises.add(aegis.id()));
        }
        return new SyncPerkDataPacket(
                charges,
                pendingBreakthroughTriggers,
                perkRefreshCharges,
                maxTalentSlots,
                skillEnhancementCharges,
                skillEnhancementChargesPerPerkExchange,
                skillEnhancementRefreshExperienceCost,
                skillEnhancementRefreshFree,
                aegisSelectionCharges,
                aegisRefreshCharges,
                liveCustomStatsRefreshAllowed,
                usesMinecraftDefaultLevel,
                usesGoldCurrency,
                goldCurrency,
                progressionLevel,
                aegisAscensionRank,
                aegisAscensionExperience,
                aegisAscensionExperienceToNextRank,
                aegisAscensionMaximumRank,
                sharedFortunePartnerId,
                sharedFortunePartnerName,
                sharedFortuneRebindCooldownSeconds,
                hiddenTalentIds,
                ranks,
                enabledTalents,
                displayStats,
                displayStatScope,
                enhancementRanks,
                enhancementOffers,
                primarySkillEnhancement,
                primarySkillEnhancementChosen,
                chosenAegises,
                disabledManualAegises
        );
    }

    public static void handle(SyncPerkDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }
}
