package com.whatever.aegis_ascension;

import com.mojang.logging.LogUtils;
import com.whatever.aegis_ascension.lifecycle.ModLifecycle;
import com.whatever.aegis_ascension.platform.ForgeMenuAccess;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AegisAscensionMod.MOD_ID)
public final class AegisAscensionMod {
    public static final String MOD_ID = "aegis_ascension";
    public static final Logger LOGGER = LogUtils.getLogger();

    //Logger that will not be removed
    public static Logger getLogger() {
        return LOGGER;
    }

    public AegisAscensionMod(FMLJavaModLoadingContext context) {
        ForgeMenuAccess.register(context.getModEventBus());
        ModLifecycle.registerOptionalCompatibility();
        context.getModEventBus().addListener(this::commonSetup);
        context.getModEventBus().addListener(this::configReloaded);
        context.registerConfig(ModConfig.Type.COMMON, AegisAscensionConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModLifecycle::initializeCommon);
    }

    private void configReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != AegisAscensionConfig.SPEC) {
            return;
        }
        ModLifecycle.reloadCommonConfig();
    }
}
