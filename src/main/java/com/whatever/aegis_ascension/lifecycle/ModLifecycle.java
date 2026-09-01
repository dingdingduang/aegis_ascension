package com.whatever.aegis_ascension.lifecycle;

import com.whatever.aegis_ascension.compat.ArsNouveauCompat;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import com.whatever.aegis_ascension.config.ServerSettings;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.network.ServerCatalogSync;
import com.whatever.aegis_ascension.mechanic.AegisExperienceSystem;
import com.whatever.aegis_ascension.perk.talents.ShrineMaidenDance;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.shop.ShopConfig;
import com.whatever.aegis_ascension.storage.StorageConfig;
import com.whatever.aegis_ascension.virtualitem.VirtualItems;
import com.whatever.aegis_ascension.quest.QuestConfig;
import net.minecraft.server.MinecraftServer;

/** Loader-neutral mod startup and common-config reload orchestration. */
public final class ModLifecycle {
    private ModLifecycle() {
    }

    /** Registers handlers whose classes are only safe to load when an optional mod exists. */
    public static void registerOptionalCompatibility() {
        IronSpellsCompat.registerOptionalHandlers();
        ArsNouveauCompat.registerOptionalHandlers();
    }

    /** Runs once from the active loader's common-setup work queue. */
    public static void initializeCommon() {
        ModNetworking.register();

        // Materialize editable defaults on a fresh install instead of waiting for a player
        // to open the corresponding screen or trigger the related talent.
        ShopConfig.get();
        StorageConfig.get();
        VirtualItems.all();
        QuestConfig.get();
        ServerSettings.get();
        ShrineMaidenDance.initialize();
    }

    /** Reapplies the reloaded common settings to every connected player. */
    public static void reloadCommonConfig() {
        MinecraftServer server = PlatformServices.server().currentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> {
                PerkData.get(player).ifPresent(data -> {
                    AegisExperienceSystem.awardMilestones(player, data, false);
                    data.applyChosenPerks(player);
                });
                // The effective Mysterious Doll catalog includes common-config bans, so
                // re-handshake before sending progression state derived from the new config.
                ServerCatalogSync.begin(player);
        }));
    }
}
