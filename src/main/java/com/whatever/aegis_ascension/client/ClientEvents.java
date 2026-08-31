package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.screen.ACGPerkSelectionScreen;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.DevourItemPacket;
import com.whatever.aegis_ascension.network.StoreHeldItemPacket;
import com.whatever.aegis_ascension.network.StoreInventorySlotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.whatever.aegis_ascension.client.ClientLifecycle.*;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

public final class ClientEvents {
    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(
            modid = AegisAscensionMod.MOD_ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD
    )
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        /**
         * Materialises the client-only config files at startup, so they exist to be edited
         * before anything reads them; see AegisAscensionMod#commonSetup for the server-side
         * ones.
         */
        @SubscribeEvent
        public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(ClientLifecycle::initialize);
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            ClientLifecycle.keyMappings().forEach(event::register);
        }
    }

    @Mod.EventBusSubscriber(
            modid = AegisAscensionMod.MOD_ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE
    )
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        /**
         * While the vanilla E inventory is open, the storage key acts on the item under
         * the cursor. The packet names only the current menu and slot; the server resolves
         * and validates the real stack rather than trusting any client-supplied item data.
         */
        @SubscribeEvent
        public static void onInventoryKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            if (!(event.getScreen() instanceof InventoryScreen inventory)
                    || !PUT_INTO_STORAGE_UI.matches(event.getKeyCode(), event.getScanCode())) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || !inventory.getMenu().getCarried().isEmpty()) {
                return;
            }
            Slot hovered = inventory.getSlotUnderMouse();
            if (hovered == null || !hovered.hasItem()
                    || hovered.container != minecraft.player.getInventory()) {
                return;
            }
            int menuSlot = inventory.getMenu().slots.indexOf(hovered);
            if (menuSlot < 0) {
                return;
            }

            ModNetworking.sendToServer(new StoreInventorySlotPacket(
                    inventory.getMenu().containerId,
                    menuSlot
            ));
            // Prevent the same N press from also activating a vanilla inventory action.
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                // The client player is also briefly null while respawning. Do not
                // erase the state that the server just synchronized for the new
                // player entity; actual disconnect cleanup is handled below.
                return;
            }

            while (DEVOUR_HELD_ITEM.consumeClick()) {
                if (minecraft.screen == null) {
                    ModNetworking.sendToServer(new DevourItemPacket());
                }
            }

            while (PUT_INTO_STORAGE_UI.consumeClick()) {
                // Gated on no screen being open, matching DEVOUR_HELD_ITEM: this acts on
                // the held item in the world, so firing it while a GUI has focus would be
                // an invisible action on a hand the player can't currently see.
                if (minecraft.screen == null) {
                    ModNetworking.sendToServer(new StoreHeldItemPacket());
                }
            }

            while (OPEN_DEVOUR_SCREEN.consumeClick()) {
                if (minecraft.screen instanceof ACGPerkSelectionScreen acgScreen) {
                    acgScreen.onClose();
                } else if (minecraft.screen == null
                        && ClientPerkState.getChosenAegises().stream().anyMatch(aegis ->
                        aegis.id().equals(AegisConstants.DEVOUR))) {
                    minecraft.setScreen(new ACGPerkSelectionScreen(
                            ACGPerkSelectionScreen.UIMode.DEVOURED));
                } else if (minecraft.screen == null) {
                    minecraft.player.displayClientMessage(
                            getTranslatableString("message.aegis_ascension.devour.no_aegis"),
                            true
                    );
                }
            }

            while (OPEN_ACG_SCREEN.consumeClick()) {
                if (minecraft.screen instanceof ACGPerkSelectionScreen acgScreen) {
                    acgScreen.onClose();
                } else if (minecraft.screen == null) {
                    minecraft.setScreen(new ACGPerkSelectionScreen());
                }
            }

            while (TOGGLE_QUEST_TRACKER.consumeClick()) {
                QuestTrackerOverlay.toggleVisibility();
            }

            while (ADVANCE_CARD_PAGE.consumeClick()) {
                // ACGPerkSelectionScreen keeps handling this same binding directly for
                // hovered multi-page cards. In the world it cycles the Quest Tracker.
                if (minecraft.screen == null) {
                    QuestTrackerOverlay.advancePage();
                }
            }
        }

        @SubscribeEvent
        public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientLifecycle.clearSessionState();
        }
    }
}
