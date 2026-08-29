package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.network.SyncPerkDataPacket;
import com.whatever.aegis_ascension.network.SyncDevourDataPacket;
import com.whatever.aegis_ascension.network.SyncShopDataPacket;
import com.whatever.aegis_ascension.network.SyncStorageDataPacket;
import com.whatever.aegis_ascension.client.screen.ACGPerkSelectionScreen;
import com.whatever.aegis_ascension.client.screen.ACGInventoryScreen;
import com.whatever.aegis_ascension.perk.Perk;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handle(SyncPerkDataPacket packet) {
        ClientPerkState.update(
                packet.selectionCharges(),
                packet.pendingBreakthroughTriggers(),
                packet.perkRefreshCharges(),
                packet.maxTalentSlots(),
                packet.skillEnhancementCharges(),
                packet.skillEnhancementChargesPerPerkExchange(),
                packet.skillEnhancementRefreshExperienceCost(),
                packet.skillEnhancementRefreshFree(),
                packet.aegisSelectionCharges(),
                packet.aegisRefreshCharges(),
                packet.liveCustomStatsRefreshAllowed(),
                packet.sharedFortunePartnerId(),
                packet.sharedFortunePartnerName(),
                packet.sharedFortuneRebindCooldownSeconds(),
                packet.hiddenTalentIds(),
                packet.perkRanks(),
                packet.enabledManualTalents(),
                packet.displayStats(),
                packet.skillEnhancementRanks(),
                packet.skillEnhancementOffers(),
                packet.primarySkillEnhancement(),
                packet.primarySkillEnhancementChosen(),
                packet.chosenAegises(),
                packet.disabledManualAegises()
        );
        AegisAscensionMod.getLogger().info(
                "Client received perk sync: {} / {} talent slot(s), {} charge(s), "
                        + "{} stored Breakthrough trigger(s)",
                packet.perkRanks().size(),
                packet.maxTalentSlots(),
                packet.selectionCharges(),
                packet.pendingBreakthroughTriggers()
        );
        if (packet.selectionCharges() <= 0 || !ClientPerkState.hasAvailableChoice()) {
            ClientPerkState.endOfferSession();
        }
        net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.refreshFromServer();
        }
        if (packet.aegisSelectionCharges() <= 0
                || !ClientPerkState.hasAvailableAegisChoice()) {
            ClientPerkState.endAegisOfferSession();
        }
    }

    public static void handle(SyncDevourDataPacket packet) {
        ClientPerkState.setDevouredAttributes(
                packet.entries(), packet.allowAgainAfterDiscard());
        if (Minecraft.getInstance().screen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.refreshFromServer();
        }
    }

    public static void handle(SyncShopDataPacket packet) {
        ClientShopState.accept(packet);
        if (Minecraft.getInstance().screen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.refreshFromServer();
        }
    }

    public static void handle(SyncStorageDataPacket packet) {
        ClientStorageState.accept(packet);
        if (Minecraft.getInstance().screen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.refreshFromServer();
        } else if (Minecraft.getInstance().screen instanceof ACGInventoryScreen inventoryScreen) {
            inventoryScreen.refreshStorage();
        }
    }

    public static void openScreen(List<Perk> offers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.setPerkOffers(offers);
            return;
        }
        boolean canReplaceCurrentScreen = minecraft.screen == null;
        if (ClientPerkState.isOfferSessionActive()
                && canReplaceCurrentScreen
                && !offers.isEmpty()
                && ClientPerkState.getSelectionCharges() > 0) {
            ACGPerkSelectionScreen screen = new ACGPerkSelectionScreen(
                    ACGPerkSelectionScreen.UIMode.PERK_SELECTION);
            screen.setPerkOffers(offers);
            minecraft.setScreen(screen);
        }
    }

    public static void openAegisScreen(List<Aegis> offers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ACGPerkSelectionScreen acgScreen) {
            acgScreen.setAegisOffers(offers);
            return;
        }
        boolean canReplaceCurrentScreen = minecraft.screen == null;
        if (ClientPerkState.isAegisOfferSessionActive()
                && canReplaceCurrentScreen
                && !offers.isEmpty()
                && ClientPerkState.getAegisSelectionCharges() > 0) {
            ACGPerkSelectionScreen screen = new ACGPerkSelectionScreen(
                    ACGPerkSelectionScreen.UIMode.AEGIS_SELECTION);
            screen.setAegisOffers(offers);
            minecraft.setScreen(screen);
        }
    }
}
