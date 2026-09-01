package com.whatever.aegis_ascension.mechanic;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.compat.WC3ModelCompat;
import com.whatever.aegis_ascension.config.ServerSettings;
import com.whatever.aegis_ascension.data.PerkData;
import com.whatever.aegis_ascension.network.ModNetworking;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.GeneralConstants;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, source-agnostic damage-shield engine.
 *
 * <p>Any effect can grant a player a shield through {@link #addShield}; this class
 * owns everything that happens afterwards — independent linear decay, first-in
 * first-out damage absorption across every source at once, the orbital model
 * visualizer, and cleanup on death, logout, and dimension change. Triggering is
 * left to each source: Angel's Aegis drives it on an Action Core interval, and
 * Koharu Sprite drives it from the per-player perk tick.</p>
 *
 * <h2>Shield instances and decay</h2>
 * <p>Each grant is one {@link ShieldInstance} — it is never merged into or
 * overwritten by another, so several shields of different ages and sizes coexist.
 * The player's effective shield is the live sum of every instance's remaining
 * capacity. Every {@link #DECAY_INTERVAL_TICKS} ticks the total shield decays by
 * {@link #DECAY_FRACTION} of itself plus {@link #DECAY_FLAT}, spent oldest-first,
 * so shields fade on a common schedule shared by every source.</p>
 *
 * <h2>Visualizer</h2>
 * <p>A grant may name a model. While at least one live instance names a given
 * model, exactly one orbital visualizer for that model follows the player;
 * grants with no model (a null {@code model}) contribute shield without any
 * visual. Multiple models are supported at once, one visualizer each.</p>
 */
public final class ShieldMechanic {
    /** Shield decay fires this often: every 2 seconds. */
    public static final int DECAY_INTERVAL_TICKS = 40;

    /** Fraction of the current total shield removed on each decay tick. */
    public static final float DECAY_FRACTION = 0.10F;

    /** Flat amount removed on each decay tick, on top of {@link #DECAY_FRACTION}. */
    public static final float DECAY_FLAT = 2.0F;

    /**
     * The common shield model, orbited around any shielded player. Every shield
     * source that does not ask for a custom model gets this one, so shields look
     * the same mod-wide. The client orbit pass keys off {@link #SHIELD_MODEL}.
     */
    public static final ResourceLocation SHIELD_MODEL = PlatformServices.resources().create(
            AegisAscensionMod.MOD_ID,
            "wc3model/angel_shield/shield.mdx"
    );

    /** Scale of the orbiting {@link #SHIELD_MODEL}. */
    public static final float SHIELD_MODEL_SCALE = GeneralConstants.DEFAULT_1F_MODEL_SCALE;

    /** Orbit height of {@link #SHIELD_MODEL} above the player's feet. */
    public static final float SHIELD_MODEL_OFFSET_Y = 1.0F;

    /** How many {@link #SHIELD_MODEL} copies orbit at once (evenly spaced). */
    public static final int SHIELD_MODEL_COUNT = 3;

    /** Particle emitted when a shield absorbs a hit. */
    public static final ParticleOptions SHIELD_PARTICLE = ParticleTypes.END_ROD;

    /** How often expiry and visualizer state are reconciled. */
    private static final int HEARTBEAT_TICKS = 5;

    /** Height above the player's feet used for absorption particles. */
    private static final double FEEDBACK_HEIGHT = 1.0D;

    private static final Map<UUID, PlayerShields> STATES = new ConcurrentHashMap<>();

    private ShieldMechanic() {
    }

    // ------------------------------------------------------------------
    // Data model
    // ------------------------------------------------------------------

    /** One granted shield: a remaining capacity plus the model to show for it. */
    private static final class ShieldInstance {
        private final long creationTick;
        private final ResourceLocation model;
        private final float modelScale;
        private final float modelOffsetY;
        private final int modelCount;
        /** Remaining capacity; reduced by both damage absorption and decay. */
        private float capacity;

        private ShieldInstance(float capacity, long creationTick,
                               ResourceLocation model, float modelScale, float modelOffsetY,
                               int modelCount) {
            // Math.max returns NaN if either argument is NaN, so it is not a guard on its
            // own; a non-finite grant would poison every later subtraction from this queue.
            this.capacity = Float.isFinite(capacity) ? Math.max(0.0F, capacity) : 0.0F;
            this.creationTick = creationTick;
            this.model = model;
            this.modelScale = modelScale;
            this.modelOffsetY = modelOffsetY;
            this.modelCount = Math.max(1, modelCount);
        }

        private boolean isDepleted() {
            return capacity <= 0.0F;
        }
    }

    /** A live model visualizer plus the level it was spawned in. */
    private static final class Visualizer {
        private final UUID id;
        private final ServerLevel level;

        private Visualizer(UUID id, ServerLevel level) {
            this.id = id;
            this.level = level;
        }
    }

    /** Per-player shield queue, its per-model visualizers, and its decay clock. */
    private static final class PlayerShields {
        private final Deque<ShieldInstance> instances = new ArrayDeque<>();
        private final Map<ResourceLocation, List<Visualizer>> visualizers = new LinkedHashMap<>();
        /** Game tick the decay clock last fired; {@code MIN_VALUE} until it starts. */
        private long lastDecayTick = Long.MIN_VALUE;
        /** Total last pushed to the client for the HUD; negative until first sent. */
        private float lastSentShield = -1.0F;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Grants a shield with the common {@link #SHIELD_MODEL}.
     *
     * <p>The default for shield-generating perks: they pass only how much, and the
     * shield gets the shared orbiting visual and the common decay automatically. Use
     * the full overload to opt out of the model (pass {@code null}) or use a custom
     * one.</p>
     */
    public static void addShield(ServerPlayer player, float amount) {
        addShield(player, amount,
                SHIELD_MODEL, SHIELD_MODEL_SCALE, SHIELD_MODEL_OFFSET_Y, SHIELD_MODEL_COUNT);
    }

    /**
     * Grants a shield using a source-specific Primary Attribute multiplier when one is
     * configured. A null multiplier keeps the server-wide setting as the fallback for
     * generic or older shield sources.
     */
    public static void addShieldWithPrimaryStatMultiplier(ServerPlayer player, float amount,
                                                           Double primaryStatMultiplier) {
        addShield(player, amount,
                SHIELD_MODEL, SHIELD_MODEL_SCALE, SHIELD_MODEL_OFFSET_Y,
                SHIELD_MODEL_COUNT, null, primaryStatMultiplier);
    }

    /**
     * Grants a shield while excluding one source perk from the additive Shield Gain
     * calculation. This is used when that perk's stats already define the raw grant.
     */
    public static void addShieldExcludingPerkGain(ServerPlayer player, float amount,
                                                   String excludedPerkId) {
        addShield(player, amount,
                SHIELD_MODEL, SHIELD_MODEL_SCALE, SHIELD_MODEL_OFFSET_Y,
                SHIELD_MODEL_COUNT, excludedPerkId);
    }

    /**
     * Koharu-style grant overload with both source exclusion and a source-specific
     * Primary Attribute multiplier.
     */
    public static void addShieldExcludingPerkGainWithPrimaryStatMultiplier(
            ServerPlayer player, float amount, String excludedPerkId,
            Double primaryStatMultiplier) {
        addShield(player, amount,
                SHIELD_MODEL, SHIELD_MODEL_SCALE, SHIELD_MODEL_OFFSET_Y,
                SHIELD_MODEL_COUNT, excludedPerkId, primaryStatMultiplier);
    }

    /**
     * Grants one shield instance to {@code player} with an explicit model.
     *
     * @param amount       initial capacity; non-positive amounts are ignored
     * @param model        orbital model to show while this shield is live, or null
     *                     for a shield with no visual
     * @param modelScale   model scale, used only when {@code model} is non-null
     * @param modelOffsetY orbit height above the player's feet, model only
     * @param modelCount   how many copies of the model orbit the player at once;
     *                     the client spaces them evenly. Ignored when {@code model}
     *                     is null.
     */
    public static void addShield(ServerPlayer player, float amount,
                                 ResourceLocation model, float modelScale, float modelOffsetY,
                                 int modelCount) {
        addShield(player, amount, model, modelScale, modelOffsetY, modelCount, null);
    }

    private static void addShield(ServerPlayer player, float amount,
                                  ResourceLocation model, float modelScale, float modelOffsetY,
                                  int modelCount, String excludedPerkId) {
        addShield(player, amount, model, modelScale, modelOffsetY, modelCount,
                excludedPerkId, null);
    }

    private static void addShield(ServerPlayer player, float amount,
                                  ResourceLocation model, float modelScale, float modelOffsetY,
                                  int modelCount, String excludedPerkId,
                                  Double primaryStatMultiplier) {
        if (player == null || player.isRemoved() || !player.isAlive() || amount <= 0.0F) {
            return;
        }
        float granted = applyShieldGain(player, amount, excludedPerkId, primaryStatMultiplier);
        if (granted <= 0.0F) {
            return;
        }
        PlayerShields state = STATES.computeIfAbsent(player.getUUID(), key -> new PlayerShields());
        long now = GeneralServerMethods.getLevelGameTime(player.level());
        synchronized (state.instances) {
            state.instances.addLast(new ShieldInstance(
                    granted, now, model, modelScale, modelOffsetY, modelCount));
        }
        updateVisualizers(player, state);
        syncShield(player, state);
    }

    /**
     * Applies the player's shield scaling to a raw grant, once, for every source.
     *
     * <p>The source-specific Primary Attribute multiplier is applied first when the
     * source defines one; otherwise the server-wide fallback setting is used.
     * {@code shield_gain} (and its per-level term) is then an additive bonus to shields
     * gained; {@code shield_gain_multiplier} (Alya) is an independent multiplier on the
     * final value. Callers pass a raw base and never apply these themselves, so a
     * source's own contribution cannot be counted twice.</p>
     */
    private static float applyShieldGain(ServerPlayer player, float amount,
                                         String excludedPerkId,
                                         Double primaryStatMultiplierOverride) {
        var data = PerkData.of(player);
        double shieldGain = excludedPerkId == null
                ? TalentEffects.shieldGain(player, data)
                : TalentEffects.shieldGainExcludingPerk(player, data, excludedPerkId);
        double primaryStatMultiplier = primaryStatMultiplierOverride != null
                ? primaryStatMultiplierOverride
                : data.hasChosenPrimarySkillEnhancement()
                ? ServerSettings.get().primaryStatMultiplier(
                        data.getPrimarySkillEnhancement().id()
                )
                : 1.0D;
        double scaled = amount
                * primaryStatMultiplier
                * (1.0D + shieldGain)
                * TalentEffects.shieldGainMultiplier(data);
        return (float) Math.max(0.0D, scaled);
    }

    /** Live sum of every unexpired instance for {@code player}. */
    public static float totalShield(ServerPlayer player) {
        PlayerShields state = STATES.get(player.getUUID());
        return state == null ? 0.0F : totalShield(state);
    }

    private static float totalShield(PlayerShields state) {
        float total = 0.0F;
        synchronized (state.instances) {
            for (ShieldInstance instance : state.instances) {
                total += instance.capacity;
            }
        }
        return total;
    }

    /** Drops every shield and visualizer for {@code player}. */
    public static void clear(ServerPlayer player) {
        PlayerShields state = STATES.remove(player.getUUID());
        if (state != null) {
            despawnAll(state);
            ModNetworking.sendShield(player, 0.0F);
        }
    }

    /**
     * Pushes the total to the client when the value shown by the HUD (the ceiling of
     * the total) changes, or when the shield empties, so the HUD updates without
     * syncing every tick.
     */
    private static void syncShield(ServerPlayer player, PlayerShields state) {
        float total = totalShield(state);
        int shown = (int) Math.ceil(total);
        int lastShown = (int) Math.ceil(Math.max(0.0F, state.lastSentShield));
        if (shown != lastShown || (total <= 0.0F && state.lastSentShield > 0.0F)) {
            state.lastSentShield = total;
            ModNetworking.sendShield(player, total);
        }
    }

    // ------------------------------------------------------------------
    // Tick: expiry and visualizer reconciliation
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (GeneralServerMethods.getEntityTickCount(player) % HEARTBEAT_TICKS != 0) {
            return;
        }
        PlayerShields state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        stepDecay(player, state);
        updateVisualizers(player, state);
        syncShield(player, state);

        boolean empty;
        synchronized (state.instances) {
            empty = state.instances.isEmpty();
        }
        if (empty && state.visualizers.isEmpty()) {
            STATES.remove(player.getUUID(), state);
        }
    }

    /**
     * Advances the decay clock, applying one decay tick per elapsed
     * {@link #DECAY_INTERVAL_TICKS} window since it last fired. Each tick removes
     * {@code DECAY_FRACTION * total + DECAY_FLAT} from the shield, spent oldest-first.
     */
    private static void stepDecay(ServerPlayer player, PlayerShields state) {
        long now = GeneralServerMethods.getLevelGameTime(player.level());
        if (state.lastDecayTick == Long.MIN_VALUE) {
            state.lastDecayTick = now;
            return;
        }
        while (now - state.lastDecayTick >= DECAY_INTERVAL_TICKS) {
            state.lastDecayTick += DECAY_INTERVAL_TICKS;
            float total = totalShield(state);
            if (total <= 0.0F) {
                continue;
            }
            spendFifo(state, DECAY_FRACTION * total + DECAY_FLAT);
        }
    }

    // ------------------------------------------------------------------
    // Damage absorption
    // ------------------------------------------------------------------

    /**
     * Spends the shield queue oldest-first.
     *
     * <p>The loader calls this during its post-mitigation damage stage, so the
     * amount absorbed is the figure about to leave the health bar. This makes
     * "total shield >= incoming damage negates it" mean exactly what it says.</p>
     */
    public static float absorbDamage(ServerPlayer player, float incoming) {
        if (incoming <= 0.0F) {
            return incoming;
        }
        PlayerShields state = STATES.get(player.getUUID());
        if (state == null) {
            return incoming;
        }
        float remaining = absorb(player, state, incoming);
        if (remaining >= incoming) {
            return incoming;
        }
        playAbsorbFeedback(player, remaining <= 0.0F);
        syncShield(player, state);
        return Math.max(0.0F, remaining);
    }

    /** Returns the damage left after the queue is spent, oldest instance first. */
    private static float absorb(ServerPlayer player, PlayerShields state, float incoming) {
        return spendFifo(state, incoming);
    }

    /**
     * Spends {@code amount} from the shield queue oldest-first, removing depleted
     * instances, and returns the amount left unspent. Both damage absorption and
     * periodic decay go through this so they share one first-in first-out rule.
     */
    private static float spendFifo(PlayerShields state, float amount) {
        if (!Float.isFinite(amount)) {
            // Infinity in means Infinity - Infinity inside the loop the moment any instance
            // also holds an infinite capacity, and that NaN then loses every comparison
            // downstream: the hit is neither absorbed nor cancelled, and it writes NaN health.
            // An unbounded hit should simply kill, so absorb nothing and hand it straight back.
            return amount;
        }
        float remaining = amount;
        synchronized (state.instances) {
            for (Iterator<ShieldInstance> queued = state.instances.iterator();
                 queued.hasNext() && remaining > 0.0F; ) {
                ShieldInstance instance = queued.next();
                if (instance.isDepleted()) {
                    queued.remove();
                    continue;
                }
                float spent = Math.min(instance.capacity, remaining);
                instance.capacity -= spent;
                remaining -= spent;
                if (instance.isDepleted()) {
                    queued.remove();
                }
            }
        }
        return Math.max(0.0F, remaining);
    }

    /** Audible/visible confirmation that the shield ate the hit, and whether it held. */
    private static void playAbsorbFeedback(ServerPlayer player, boolean fullyNegated) {
        GeneralServerMethods.playSoundAt(
                player,
                fullyNegated ? SoundEvents.SHIELD_BLOCK : SoundEvents.SHIELD_BREAK,
                SoundSource.PLAYERS,
                0.5F,
                fullyNegated ? 1.4F : 1.0F
        );
        GeneralServerMethods.spawnParticlesAt(
                GeneralServerMethods.getServerLevel(player),
                SHIELD_PARTICLE,
                player.getX(),
                player.getY() + FEEDBACK_HEIGHT,
                player.getZ(),
                fullyNegated ? 8 : 4,
                0.4D,
                0.02D
        );
    }

    // ------------------------------------------------------------------
    // Expiry and visualizers
    // ------------------------------------------------------------------


    /**
     * Reconciles the visualizer groups with the models named by live instances:
     * for each model with a live instance, {@code modelCount} copies follow the
     * player (the client spaces them evenly); models with no live instance have
     * all their copies removed. Copies that have actually disappeared (a reload or
     * unclean unload) are respawned to top the group back up.
     */
    private static void updateVisualizers(ServerPlayer player, PlayerShields state) {
        Map<ResourceLocation, ShieldInstance> liveModels = new LinkedHashMap<>();
        synchronized (state.instances) {
            for (ShieldInstance instance : state.instances) {
                if (instance.model != null) {
                    liveModels.putIfAbsent(instance.model, instance);
                }
            }
        }

        // Remove groups whose model no longer has any live instance.
        for (Iterator<Map.Entry<ResourceLocation, List<Visualizer>>> tracked =
                     state.visualizers.entrySet().iterator(); tracked.hasNext(); ) {
            Map.Entry<ResourceLocation, List<Visualizer>> entry = tracked.next();
            if (!liveModels.containsKey(entry.getKey())) {
                for (Visualizer visualizer : entry.getValue()) {
                    WC3ModelCompat.removeVisualizer(visualizer.level, visualizer.id);
                }
                tracked.remove();
            }
        }

        // Bring each live model's group to exactly its desired copy count.
        for (Map.Entry<ResourceLocation, ShieldInstance> live : liveModels.entrySet()) {
            ResourceLocation model = live.getKey();
            ShieldInstance spec = live.getValue();
            int desired = Math.max(1, spec.modelCount);
            List<Visualizer> group =
                    state.visualizers.computeIfAbsent(model, key -> new ArrayList<>());

            // Drop copies that have already vanished from the world.
            group.removeIf(visualizer ->
                    !WC3ModelCompat.isVisualizerAlive(visualizer.level, visualizer.id));

            // Trim any surplus (e.g. the desired count was lowered).
            while (group.size() > desired) {
                Visualizer removed = group.remove(group.size() - 1);
                WC3ModelCompat.removeVisualizer(removed.level, removed.id);
            }

            // Spawn up to the desired count.
            ServerLevel level = GeneralServerMethods.getServerLevel(player);
            while (group.size() < desired) {
                UUID spawned = WC3ModelCompat.spawnFollowVisualizer(
                        player, model, spec.modelScale, spec.modelOffsetY);
                if (spawned == null) {
                    break;
                }
                group.add(new Visualizer(spawned, level));
            }

            if (group.isEmpty()) {
                state.visualizers.remove(model);
            }
        }
    }

    /** Removes every visualizer through the level each was spawned in. */
    private static void despawnAll(PlayerShields state) {
        for (List<Visualizer> group : state.visualizers.values()) {
            for (Visualizer visualizer : group) {
                WC3ModelCompat.removeVisualizer(visualizer.level, visualizer.id);
            }
        }
        state.visualizers.clear();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public static void onPlayerChangedDimension(ServerPlayer player) {
        PlayerShields state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        // Follow entities cannot cross levels, so every model orbiting in the source
        // dimension is torn down here — through the level it was spawned in, since
        // the event fires after the player is already in the destination — and the
        // next heartbeat respawns whatever is still needed in the new dimension. The
        // shields themselves are dimension-independent data whose decay is keyed to
        // server game time, so they are deliberately kept.
        despawnAll(state);
    }
}
