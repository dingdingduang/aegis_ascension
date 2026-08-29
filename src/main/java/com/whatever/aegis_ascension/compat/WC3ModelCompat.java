package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.wc3model2mc.entity.AnimatedMdxProjectile;
import com.wc3model2mc.registry.ModEntityTypes;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.UUID;

/**
 * Optional wc3model2mc bridge for spawning MDX visualizers as follow entities.
 *
 * <p>Every direct reference to {@code com.wc3model2mc} lives inside
 * {@link Bridge}, so this class stays loadable when the model mod is absent. The
 * visualizer is a real, server-spawned {@code AnimatedMdxProjectile} — a
 * stationary, gravity-less, non-colliding follow entity — that wc3model2mc
 * renders and that vanilla entity tracking replicates, so no custom packet or
 * per-player render state is required. It is spawned with an infinite lifespan
 * and lives until {@link #removeVisualizer} is called.</p>
 */
public final class WC3ModelCompat {
    public static final String MOD_ID = "wc3model2mc";

    private static final String BRIDGE_CLASS =
            "com.whatever.aegis_ascension.compat.WC3ModelCompat$Bridge";

    /** Resolved once: {@code TRUE} when wc3model2mc is present and linkable. */
    private static Boolean bridgeUsable;

    private WC3ModelCompat() {
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /**
     * Spawns an MDX visualizer pinned to {@code owner} and returns its id, or
     * {@code null} when the model mod is unavailable.
     *
     * <p>The visualizer is spawned with an infinite lifespan and follow duration,
     * so it persists until {@link #removeVisualizer} is called. The caller owns
     * its lifetime: spawn it once when the effect begins and remove it once when
     * the effect ends, rather than re-spawning or refreshing it every tick.</p>
     */
    public static UUID spawnFollowVisualizer(ServerPlayer owner, ResourceLocation modelId,
                                             float scale, float offsetY) {
        if (owner == null || !useBridge()) {
            return null;
        }
        return Bridge.spawnFollowVisualizer(owner, modelId, scale, offsetY);
    }

    /** Whether the tracked visualizer still exists and has not been removed. */
    public static boolean isVisualizerAlive(ServerLevel level, UUID visualizerId) {
        if (level == null || visualizerId == null || !useBridge()) {
            return false;
        }
        return Bridge.isVisualizerAlive(level, visualizerId);
    }

    /** Removes the visualizer immediately; unknown ids are ignored. */
    public static void removeVisualizer(ServerLevel level, UUID visualizerId) {
        if (level == null || visualizerId == null || !useBridge()) {
            return;
        }
        Bridge.removeVisualizer(level, visualizerId);
    }

    private static boolean useBridge() {
        Boolean resolved = bridgeUsable;
        if (resolved != null) {
            return resolved;
        }
        boolean usable = false;
        if (isLoaded()) {
            try {
                Class.forName(BRIDGE_CLASS, true, WC3ModelCompat.class.getClassLoader());
                usable = true;
                AegisAscensionMod.getLogger().info(
                        "Enabled optional wc3model2mc model visualizers"
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                AegisAscensionMod.getLogger().error(
                        "wc3model2mc is installed, but its visualizer bridge could not load",
                        exception
                );
            }
        }
        bridgeUsable = usable;
        return usable;
    }

    /** Holds every wc3model2mc symbol so the class only links when the mod exists. */
    private static final class Bridge {
        private Bridge() {
        }

        private static final int INFINITE = -1;

        private static UUID spawnFollowVisualizer(ServerPlayer owner, ResourceLocation modelId,
                                                  float scale, float offsetY) {
            if (!(owner.level() instanceof ServerLevel level)) {
                return null;
            }
            EntityType<AnimatedMdxProjectile> type = ModEntityTypes.ANIMATED_MDX_PROJECTILE.get();
            AnimatedMdxProjectile visualizer = type.create(level);
            if (visualizer == null) {
                return null;
            }
            visualizer.moveTo(owner.getX(), owner.getY(), owner.getZ(),
                    owner.getYRot(), 0.0F);
            visualizer.setMdxModelId(modelId);
            visualizer.setMdxModelScale(scale);
            visualizer.setMdxModelOffset(0.0F, offsetY, 0.0F);
            visualizer.setMdxAnimationLooping(true);
            visualizer.setMdxAffectedByLight(false);
            // Infinite lifespan: the visualizer lives for exactly as long as the
            // caller keeps it, and is torn down by removeVisualizer, not by a
            // self-destruct clock. StationaryMdxProjectile has no gravity, an empty
            // onHit, and cannot collide with entities, so an infinite-lived one is a
            // pure decoration rather than an accumulating hazard.
            visualizer.setMdxLifespanTicks(INFINITE);

            visualizer.setSilent(true);
            visualizer.setInvisible(false);

            if (!level.addFreshEntity(visualizer)) {
                return null;
            }
            // Follow duration -1 means "until cleared", matching the lifespan.
            visualizer.setMdxFollowTarget(owner, INFINITE, true);
            return visualizer.getUUID();
        }

        private static boolean isVisualizerAlive(ServerLevel level, UUID visualizerId) {
            return level.getEntity(visualizerId) instanceof AnimatedMdxProjectile visualizer
                    && !visualizer.isRemoved();
        }

        private static void removeVisualizer(ServerLevel level, UUID visualizerId) {
            Entity visualizer = level.getEntity(visualizerId);
            if (visualizer instanceof AnimatedMdxProjectile) {
                visualizer.discard();
            }
        }
    }
}
