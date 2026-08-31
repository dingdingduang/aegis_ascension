package com.whatever.aegis_ascension.client;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.mechanic.ShieldMechanic;
import com.whatever.aegis_ascension.compat.WC3ModelCompat;
import com.wc3model2mc.api.client.WC3ModelResourceAPI;
import com.wc3model2mc.entity.AnimatedMdxProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client half of the Angel's Aegis shield visual.
 *
 * <p>Two responsibilities: registering this mod's namespace with
 * {@code WC3ModelResourceAPI} so {@code assets/aegis_ascension/wc3model/**} is
 * decoded at resource-load time, and driving the orbit of the shield model each
 * frame.</p>
 *
 * <p>The model itself is drawn by wc3model2mc's own entity renderer — the shield
 * visualizer is a real follow entity spawned by the server, so it replicates to
 * every client through vanilla entity tracking and needs no custom packet. What
 * the follow entity cannot do on its own is orbit: its server tick snaps it to
 * the owner's exact feet position. This class supplies the orbital offset
 * locally, per frame, which is both smoother than 20 Hz server updates and free
 * of network cost. Model offsets are synced entity data, but no client ever
 * pushes entity data upstream, so writing them here stays client-local.</p>
 */
public final class AngelsAegisClientRenderer {
    /** Horizontal orbit radius, in blocks. */
    private static final double ORBIT_RADIUS = 0.85D;

    /** Radians per tick; a full revolution takes about 5 seconds. */
    private static final double ORBIT_SPEED = 0.06D;

    /** Height above the owner's feet that the orbit is centered on. */
    private static final double ORBIT_HEIGHT = 1.0D;

    /** Vertical bob amplitude, in blocks. */
    private static final double BOB_AMPLITUDE = 0.12D;

    /** Skip orbit maths for shields too far away to read. */
    private static final double UPDATE_RANGE = 64.0D;

    private AngelsAegisClientRenderer() {
    }

    /** Mod-bus half: namespace registration during client setup. */
    @Mod.EventBusSubscriber(
            modid = AegisAscensionMod.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT
    )
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            if (!WC3ModelCompat.isLoaded()) {
                return;
            }
            event.enqueueWork(() -> {
                try {
                    Bridge.registerNamespace();
                    AegisAscensionMod.getLogger().info(
                            "Registered '{}' with WC3ModelResourceAPI",
                            AegisAscensionMod.MOD_ID
                    );
                } catch (LinkageError | RuntimeException exception) {
                    AegisAscensionMod.getLogger().error(
                            "Could not register the WC3 model namespace; Angel's Aegis "
                                    + "will fall back to no shield model",
                            exception
                    );
                }
            });
        }
    }

    /** Forge-bus half: per-frame orbital placement. */
    @Mod.EventBusSubscriber(
            modid = AegisAscensionMod.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.FORGE,
            value = Dist.CLIENT
    )
    public static final class Orbit {
        private Orbit() {
        }

        /**
         * Runs at {@link RenderLevelStageEvent.Stage#AFTER_SKY} — the earliest
         * per-frame stage — so the offsets written here are the ones the entity
         * pass reads later in the same frame, rather than lagging by one.
         */
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                    || !WC3ModelCompat.isLoaded()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null || minecraft.isPaused()) {
                return;
            }
            double time = level.getGameTime() + event.getPartialTick();
            Bridge.updateOrbits(level, minecraft, time);
        }
    }

    /**
     * Holds every wc3model2mc symbol. Loaded only after
     * {@link WC3ModelCompat#isLoaded()} has confirmed the mod is installed.
     */
    private static final class Bridge {
        private Bridge() {
        }

        private static void registerNamespace() {
            WC3ModelResourceAPI.registerModelNamespace(AegisAscensionMod.MOD_ID);
        }

        private static void updateOrbits(ClientLevel level, Minecraft minecraft, double time) {
            Entity camera = minecraft.getCameraEntity();
            if (camera == null) {
                return;
            }
            double rangeSquared = UPDATE_RANGE * UPDATE_RANGE;

            // Group in-range shield models by the player they follow, so each
            // player's copies can be spread evenly around that player.
            Map<UUID, List<AnimatedMdxProjectile>> rings = new HashMap<>();
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof AnimatedMdxProjectile visualizer)
                        || visualizer.isRemoved()
                        || !ShieldMechanic.SHIELD_MODEL.equals(visualizer.getMdxModelLocation())) {
                    continue;
                }
                if (camera.distanceToSqr(visualizer) > rangeSquared) {
                    continue;
                }
                // A copy not following anyone orbits solo under its own id.
                UUID owner = visualizer.getMdxFollowTargetUuid().orElseGet(visualizer::getUUID);
                rings.computeIfAbsent(owner, key -> new ArrayList<>()).add(visualizer);
            }

            for (Map.Entry<UUID, List<AnimatedMdxProjectile>> ring : rings.entrySet()) {
                List<AnimatedMdxProjectile> members = ring.getValue();
                // Stable slot assignment so a given copy keeps its place frame to frame.
                members.sort(Comparator.comparingInt(Entity::getId));
                int count = members.size();
                // Per-player phase so different players' rings are not synchronized.
                double basePhase = Math.floorMod(ring.getKey().hashCode(), 360) * (Math.PI / 180.0D);
                for (int slot = 0; slot < count; slot++) {
                    AnimatedMdxProjectile visualizer = members.get(slot);
                    // Evenly spaced: slot i sits 360/count degrees from its neighbor.
                    double angle = time * ORBIT_SPEED + basePhase + (2.0D * Math.PI * slot) / count;
                    visualizer.setMdxModelOffset(
                            (float) (Math.cos(angle) * ORBIT_RADIUS),
                            (float) (ORBIT_HEIGHT + Math.sin(angle * 2.0D) * BOB_AMPLITUDE),
                            (float) (Math.sin(angle) * ORBIT_RADIUS)
                    );
                    // Face along the direction of travel so the model banks into its orbit.
                    visualizer.setHorizontalFacingDeg((float) Math.toDegrees(-angle));
                }
            }
        }
    }
}
