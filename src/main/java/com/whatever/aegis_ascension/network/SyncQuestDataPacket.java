package com.whatever.aegis_ascension.network;

import com.whatever.aegis_ascension.client.ClientPacketHandler;
import com.whatever.aegis_ascension.quest.QuestCompletionView;
import com.whatever.aegis_ascension.quest.QuestObjective;
import com.whatever.aegis_ascension.quest.QuestType;
import com.whatever.aegis_ascension.quest.QuestView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;

public record SyncQuestDataPacket(List<QuestView> quests,
                                  List<QuestCompletionView> completions,
                                  boolean penaltyActive, int depositExperienceCost,
                                  boolean usesMinecraftDefaultLevel,
                                  boolean autoAcceptEligibleQuests,
                                  String questCompleteSound, String reputationIcon,
                                  Map<QuestObjective, Integer> lifetimeTotals) {
    public SyncQuestDataPacket {
        // Encoding reads this map directly, so it must never arrive null.
        lifetimeTotals = lifetimeTotals == null ? Map.of() : Map.copyOf(lifetimeTotals);
    }

    public static void encode(SyncQuestDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.penaltyActive);
        buffer.writeVarInt(Math.max(0, packet.depositExperienceCost));
        buffer.writeBoolean(packet.usesMinecraftDefaultLevel);
        buffer.writeBoolean(packet.autoAcceptEligibleQuests);
        buffer.writeUtf(packet.questCompleteSound == null ? "" : packet.questCompleteSound, 256);
        buffer.writeUtf(packet.reputationIcon == null ? "" : packet.reputationIcon, 256);
        buffer.writeVarInt(packet.lifetimeTotals.size());
        for (Map.Entry<QuestObjective, Integer> total : packet.lifetimeTotals.entrySet()) {
            buffer.writeEnum(total.getKey());
            buffer.writeVarInt(Math.max(0, total.getValue()));
        }
        buffer.writeVarInt(Math.min(NetworkLimits.MAX_QUESTS, packet.quests.size()));
        for (QuestView q : packet.quests.subList(
                0,
                Math.min(NetworkLimits.MAX_QUESTS, packet.quests.size()))) {
            buffer.writeUtf(q.id(), 128);
            buffer.writeEnum(q.type()); buffer.writeEnum(q.objective());
            buffer.writeUtf(q.targetId(), 256);
            buffer.writeVarInt(q.progress()); buffer.writeVarInt(q.target());
            buffer.writeBoolean(q.accepted()); buffer.writeBoolean(q.completed());
            buffer.writeBoolean(q.cancelled()); buffer.writeBoolean(q.expired());
            buffer.writeVarLong(Math.max(0L, q.expiresAt()));
            buffer.writeVarInt(q.experience());
            buffer.writeVarLong(Math.max(0L, q.goldReward()));
            buffer.writeUtf(q.rewardSummary(), 512);
            buffer.writeBoolean(q.prerequisiteMet());
            buffer.writeVarInt(Math.max(0, q.securityDepositPaid()));
            buffer.writeBoolean(q.repeatable());
            buffer.writeVarInt(Math.max(0, q.cycle()));
            buffer.writeVarLong(Math.max(0L, q.rewardReadyAt()));
            buffer.writeUtf(q.tier(), 8);
            buffer.writeVarInt(Math.max(0, q.securityDeposit()));
            int requirementCount = Math.min(NetworkLimits.MAX_QUEST_REQUIREMENTS,
                    q.requirements().size());
            buffer.writeVarInt(requirementCount);
            for (int index = 0; index < requirementCount; index++) {
                QuestView.Requirement requirement = q.requirements().get(index);
                buffer.writeEnum(requirement.objective());
                buffer.writeUtf(requirement.targetId(), 256);
                buffer.writeVarInt(Math.max(0, requirement.progress()));
                buffer.writeVarInt(Math.max(1, requirement.target()));
            }
            int choiceCount = Math.min(NetworkLimits.MAX_QUEST_REWARD_CHOICES,
                    q.rewardChoices().size());
            buffer.writeVarInt(choiceCount);
            for (int index = 0; index < choiceCount; index++) {
                buffer.writeUtf(q.rewardChoices().get(index), 256);
            }
        }
        buffer.writeVarInt(Math.min(
                NetworkLimits.MAX_QUEST_COMPLETIONS,
                packet.completions.size()
        ));
        for (QuestCompletionView completion : packet.completions.subList(
                0,
                Math.min(NetworkLimits.MAX_QUEST_COMPLETIONS, packet.completions.size()))) {
            buffer.writeUtf(completion.questId(), 128);
            buffer.writeVarInt(Math.max(0, completion.completions()));
            buffer.writeVarLong(Math.max(0L, completion.experienceEarned()));
        }
    }
    public static SyncQuestDataPacket decode(FriendlyByteBuf buffer) {
        boolean penalty = buffer.readBoolean(); int cost = buffer.readVarInt();
        boolean usesMinecraftDefaultLevel = buffer.readBoolean();
        boolean autoAcceptEligibleQuests = buffer.readBoolean();
        String questCompleteSound = buffer.readUtf(256);
        String reputationIcon = buffer.readUtf(256);
        int lifetimeCount = NetworkLimits.readBoundedCount(buffer,
                QuestObjective.values().length, "lifetime total");
        Map<QuestObjective, Integer> lifetimeTotals = new EnumMap<>(QuestObjective.class);
        for (int index = 0; index < lifetimeCount; index++) {
            lifetimeTotals.put(buffer.readEnum(QuestObjective.class),
                    Math.max(0, buffer.readVarInt()));
        }
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_QUESTS,
                "quest"
        );
        List<QuestView> quests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            QuestView flat = new QuestView(buffer.readUtf(128), buffer.readEnum(QuestType.class),
                    buffer.readEnum(QuestObjective.class),
                    buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readUtf(512),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readUtf(8),
                    buffer.readVarInt(), List.of(), List.of());
            int requirementCount = NetworkLimits.readBoundedCount(buffer,
                    NetworkLimits.MAX_QUEST_REQUIREMENTS, "quest requirement");
            List<QuestView.Requirement> requirements = new ArrayList<>(requirementCount);
            for (int index = 0; index < requirementCount; index++) {
                requirements.add(new QuestView.Requirement(
                        buffer.readEnum(QuestObjective.class), buffer.readUtf(256),
                        buffer.readVarInt(), buffer.readVarInt()));
            }
            int choiceCount = NetworkLimits.readBoundedCount(buffer,
                    NetworkLimits.MAX_QUEST_REWARD_CHOICES, "quest reward choice");
            List<String> rewardChoices = new ArrayList<>(choiceCount);
            for (int index = 0; index < choiceCount; index++) {
                rewardChoices.add(buffer.readUtf(256));
            }
            quests.add(flat.withRequirements(requirements).withRewardChoices(rewardChoices));
        }
        int completionCount = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_QUEST_COMPLETIONS,
                "quest completion"
        );
        List<QuestCompletionView> completions = new ArrayList<>(completionCount);
        for (int i = 0; i < completionCount; i++) {
            completions.add(new QuestCompletionView(
                    buffer.readUtf(128), buffer.readVarInt(), buffer.readVarLong()));
        }
        return new SyncQuestDataPacket(quests, completions, penalty, cost,
                usesMinecraftDefaultLevel, autoAcceptEligibleQuests,
                questCompleteSound, reputationIcon, lifetimeTotals);
    }
    public static void handle(SyncQuestDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(packet)));
        context.setPacketHandled(true);
    }
}
