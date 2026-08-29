package com.whatever.aegis_ascension.platform;

/** Boundary for loader mod discovery and dynamically registered game-event handlers. */
public interface ModAccess {
    boolean isLoaded(String modId);

    void registerGameEventHandler(Object handler);

    void registerEndServerTick(Runnable listener);
}
