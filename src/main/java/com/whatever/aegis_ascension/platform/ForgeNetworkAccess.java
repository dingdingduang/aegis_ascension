package com.whatever.aegis_ascension.platform;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** Forge 1.20.1 implementation of {@link NetworkAccess}. */
public final class ForgeNetworkAccess implements NetworkAccess {
    // Bumped whenever the channel's message-id table changes shape, so an old client is
    // refused rather than silently misreading ids. 19 -> 20: daily-shop packets.
    // 20 -> 21: virtual-storage packets. 21 -> 22: store-held-item packet.
    // 22 -> 23: shop offer rarity tier. 23 -> 24: stored-item rarity tier.
    // 24 -> 25: storage sort mode enum. 25 -> 26: storage rows addressed by identity.
    // 26 -> 27: Authority Aegis claim-all Perk offers request.
    // 27 -> 28: Shared Fortune binding request and synchronized bond state.
    // 28 -> 29: store hovered player-inventory slot request.
    // 29 -> 30: open the server-backed ACG inventory/workbench menu.
    private static final String PROTOCOL_VERSION = "30";

    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    @Override
    public void registerPackets() {
        int id = 0;
        channel.registerMessage(
                id++,
                RequestPerkOffersPacket.class,
                RequestPerkOffersPacket::encode,
                RequestPerkOffersPacket::decode,
                RequestPerkOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RequestPerkDataPacket.class,
                RequestPerkDataPacket::encode,
                RequestPerkDataPacket::decode,
                RequestPerkDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SelectPerkPacket.class,
                SelectPerkPacket::encode,
                SelectPerkPacket::decode,
                SelectPerkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                ToggleTalentPacket.class,
                ToggleTalentPacket::encode,
                ToggleTalentPacket::decode,
                ToggleTalentPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SyncPerkDataPacket.class,
                SyncPerkDataPacket::encode,
                SyncPerkDataPacket::decode,
                SyncPerkDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                OpenPerkScreenPacket.class,
                OpenPerkScreenPacket::encode,
                OpenPerkScreenPacket::decode,
                OpenPerkScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                RequestSkillEnhancementOffersPacket.class,
                RequestSkillEnhancementOffersPacket::encode,
                RequestSkillEnhancementOffersPacket::decode,
                RequestSkillEnhancementOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SelectSkillEnhancementPacket.class,
                SelectSkillEnhancementPacket::encode,
                SelectSkillEnhancementPacket::decode,
                SelectSkillEnhancementPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RefreshSkillEnhancementOffersPacket.class,
                RefreshSkillEnhancementOffersPacket::encode,
                RefreshSkillEnhancementOffersPacket::decode,
                RefreshSkillEnhancementOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RequestAegisOffersPacket.class,
                RequestAegisOffersPacket::encode,
                RequestAegisOffersPacket::decode,
                RequestAegisOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SelectAegisPacket.class,
                SelectAegisPacket::encode,
                SelectAegisPacket::decode,
                SelectAegisPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                OpenAegisScreenPacket.class,
                OpenAegisScreenPacket::encode,
                OpenAegisScreenPacket::decode,
                OpenAegisScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                ToggleAegisPacket.class,
                ToggleAegisPacket::encode,
                ToggleAegisPacket::decode,
                ToggleAegisPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RefreshPerkOffersPacket.class,
                RefreshPerkOffersPacket::encode,
                RefreshPerkOffersPacket::decode,
                RefreshPerkOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RefreshAegisOffersPacket.class,
                RefreshAegisOffersPacket::encode,
                RefreshAegisOffersPacket::decode,
                RefreshAegisOffersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                ExchangePerkChargePacket.class,
                ExchangePerkChargePacket::encode,
                ExchangePerkChargePacket::decode,
                ExchangePerkChargePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                DevourItemPacket.class,
                DevourItemPacket::encode,
                DevourItemPacket::decode,
                DevourItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                RequestDevourDataPacket.class,
                RequestDevourDataPacket::encode,
                RequestDevourDataPacket::decode,
                RequestDevourDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SyncDevourDataPacket.class,
                SyncDevourDataPacket::encode,
                SyncDevourDataPacket::decode,
                SyncDevourDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                DiscardDevouredItemPacket.class,
                DiscardDevouredItemPacket::encode,
                DiscardDevouredItemPacket::decode,
                DiscardDevouredItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SetPrimarySkillEnhancementPacket.class,
                SetPrimarySkillEnhancementPacket::encode,
                SetPrimarySkillEnhancementPacket::decode,
                SetPrimarySkillEnhancementPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                UnlockConstellationPacket.class,
                UnlockConstellationPacket::encode,
                UnlockConstellationPacket::decode,
                UnlockConstellationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SyncShieldPacket.class,
                SyncShieldPacket::encode,
                SyncShieldPacket::decode,
                SyncShieldPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                RequestShopDataPacket.class,
                RequestShopDataPacket::encode,
                RequestShopDataPacket::decode,
                RequestShopDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                BuyShopItemPacket.class,
                BuyShopItemPacket::encode,
                BuyShopItemPacket::decode,
                BuyShopItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                ManualRefreshShopPacket.class,
                ManualRefreshShopPacket::encode,
                ManualRefreshShopPacket::decode,
                ManualRefreshShopPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SyncShopDataPacket.class,
                SyncShopDataPacket::encode,
                SyncShopDataPacket::decode,
                SyncShopDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                RequestStorageDataPacket.class,
                RequestStorageDataPacket::encode,
                RequestStorageDataPacket::decode,
                RequestStorageDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                StorageActionPacket.class,
                StorageActionPacket::encode,
                StorageActionPacket::decode,
                StorageActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SyncStorageDataPacket.class,
                SyncStorageDataPacket::encode,
                SyncStorageDataPacket::decode,
                SyncStorageDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                id++,
                StoreHeldItemPacket.class,
                StoreHeldItemPacket::encode,
                StoreHeldItemPacket::decode,
                StoreHeldItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SelectAllOfferedPerksPacket.class,
                SelectAllOfferedPerksPacket::encode,
                SelectAllOfferedPerksPacket::decode,
                SelectAllOfferedPerksPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                SetSharedFortunePartnerPacket.class,
                SetSharedFortunePartnerPacket::encode,
                SetSharedFortunePartnerPacket::decode,
                SetSharedFortunePartnerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id++,
                StoreInventorySlotPacket.class,
                StoreInventorySlotPacket::encode,
                StoreInventorySlotPacket::decode,
                StoreInventorySlotPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        channel.registerMessage(
                id,
                OpenACGInventoryPacket.class,
                OpenACGInventoryPacket::encode,
                OpenACGInventoryPacket::decode,
                OpenACGInventoryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    @Override
    public void sendToServer(Object packet) {
        channel.sendToServer(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
