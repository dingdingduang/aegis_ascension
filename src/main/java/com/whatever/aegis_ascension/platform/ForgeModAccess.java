package com.whatever.aegis_ascension.platform;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

/** Forge 1.20.1 implementation of {@link ModAccess}. */
public final class ForgeModAccess implements ModAccess {
    @Override
    public boolean isLoaded(String modId) {
        return modId != null && !modId.trim().isEmpty() && ModList.get().isLoaded(modId);
    }

    @Override
    public void registerGameEventHandler(Object handler) {
        MinecraftForge.EVENT_BUS.register(handler);
    }

    @Override
    public void registerEndServerTick(Runnable listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.run();
            }
        });
    }
}
