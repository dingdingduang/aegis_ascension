package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.actioncore.event.ActionCoreTickEventMethods;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Optional Action Core bridge for entity-bound server work.
 *
 * <p>Every direct reference to {@code com.whatever.actioncore} lives inside
 * {@link Bridge}, which is only class-loaded when Action Core is installed. When
 * the framework is absent an equivalent server-tick scheduler keeps the
 * dependent mechanics working instead of silently dropping them.</p>
 *
 * <p>Requires Action Core 1.1.0 or newer, whose {@code loopAction} carries the
 * initial-delay and bounded-repetition contract this class used to hand-roll on
 * top of {@code setMethodActionTimer}, and whose {@code execute} dispatches an
 * action payload synchronously for work that must resolve inside the caller's
 * own event.</p>
 */
public final class ActionCoreCompat {
    public static final String MOD_ID = "actioncore";

    /** Repetition count meaning "run until the caller cancels the task". */
    public static final int INFINITE = -1;

    private static final String BRIDGE_CLASS =
            "com.whatever.aegis_ascension.compat.ActionCoreCompat$Bridge";

    private static final AtomicLong TASK_SEQUENCE = new AtomicLong();

    /** Resolved once: {@code TRUE} when Action Core is present and linkable. */
    private static Boolean bridgeUsable;

    private ActionCoreCompat() {
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /** Returns a task id that never collides with another queued batch. */
    public static String uniqueTaskId(String prefix) {
        return prefix + "/" + TASK_SEQUENCE.incrementAndGet();
    }

    /**
     * Runs {@code action} immediately as an Action Core action payload.
     *
     * <p>Damage interception has to resolve inside the originating event, so this
     * cannot go through a timer. Wrapping the payload in the framework's own
     * {@code MethodAction} type still routes it through Action Core's action
     * contract, and degrades to a plain call when the framework is absent.</p>
     */
    public static void dispatch(LivingEntity entity, Consumer<LivingEntity> action) {
        if (entity == null || action == null) {
            return;
        }
        if (useBridge()) {
            Bridge.dispatch(entity, action);
            return;
        }
        action.accept(entity);
    }

    /**
     * Runs {@code action} every {@code periodTicks} server ticks, starting one
     * full period from now.
     *
     * <p>{@code repetitions} may be {@link #INFINITE}, in which case the task runs
     * until {@link #cancel} is called. The task is bound to {@code entity} and
     * hands the live entity back so callers can re-resolve its level and
     * capabilities at execution time.</p>
     */
    public static void scheduleRepeating(LivingEntity entity, String taskId,
                                         int periodTicks, int repetitions,
                                         Consumer<LivingEntity> action) {
        if (entity == null || repetitions == 0) {
            return;
        }
        int period = Math.max(1, periodTicks);
        if (useBridge()) {
            Bridge.scheduleRepeating(entity, taskId, period, repetitions, action);
            return;
        }
        FallbackScheduler.scheduleRepeating(entity, taskId, period, repetitions, action);
    }

    /** Stops a task started by {@link #scheduleRepeating}; unknown ids are ignored. */
    public static void cancel(LivingEntity entity, String taskId) {
        if (entity == null || taskId == null) {
            return;
        }
        if (useBridge()) {
            Bridge.cancel(entity, taskId);
            return;
        }
        FallbackScheduler.cancel(entity, taskId);
    }

    private static boolean useBridge() {
        Boolean resolved = bridgeUsable;
        if (resolved != null) {
            return resolved;
        }
        boolean usable = false;
        if (isLoaded()) {
            try {
                Class.forName(BRIDGE_CLASS, true, ActionCoreCompat.class.getClassLoader());
                usable = true;
                AegisAscensionMod.getLogger().info(
                        "Enabled optional Action Core action scheduling"
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                AegisAscensionMod.getLogger().error(
                        "Action Core is installed, but its scheduling bridge could not "
                                + "load; falling back to the internal scheduler",
                        exception
                );
            }
        }
        bridgeUsable = usable;
        return usable;
    }

    /** Holds every Action Core symbol so the class only links when the mod exists. */
    private static final class Bridge {
        private Bridge() {
        }

        private static void dispatch(LivingEntity entity, Consumer<LivingEntity> action) {
            ActionCoreTickEventMethods.execute(entity, action::accept);
        }

        private static void scheduleRepeating(LivingEntity entity, String taskId,
                                              int periodTicks, int repetitions,
                                              Consumer<LivingEntity> action) {
            // loopAction's initial delay equals its period, so the first run lands
            // one full interval out and the task retires itself after its last run.
            ActionCoreTickEventMethods.loopAction(
                    entity,
                    taskId,
                    periodTicks,
                    repetitions == INFINITE
                            ? ActionCoreTickEventMethods.INFINITE_REPETITIONS
                            : repetitions,
                    action::accept
            );
        }

        private static void cancel(LivingEntity entity, String taskId) {
            ActionCoreTickEventMethods.setTaskActionDone(entity, taskId);
        }
    }

    /** Server-tick equivalent used when Action Core is not installed. */
    private static final class FallbackScheduler {
        private static final List<PendingTask> TASKS = new ArrayList<>();
        private static boolean registered;

        private FallbackScheduler() {
        }

        private static void scheduleRepeating(LivingEntity entity, String taskId,
                                              int periodTicks, int repetitions,
                                              Consumer<LivingEntity> action) {
            synchronized (TASKS) {
                if (!registered) {
                    // A direct listener avoids the generated-wrapper access rules
                    // that annotation scanning applies to nested classes.
                    PlatformServices.mods().registerEndServerTick(
                            FallbackScheduler::onServerTick
                    );
                    registered = true;
                }
                TASKS.removeIf(task -> task.matches(entity, taskId));
                TASKS.add(new PendingTask(entity, taskId, periodTicks, repetitions, action));
            }
        }

        private static void cancel(LivingEntity entity, String taskId) {
            synchronized (TASKS) {
                TASKS.removeIf(task -> task.matches(entity, taskId));
            }
        }

        private static void onServerTick() {
            List<PendingTask> due = new ArrayList<>();
            synchronized (TASKS) {
                for (Iterator<PendingTask> tasks = TASKS.iterator(); tasks.hasNext(); ) {
                    PendingTask task = tasks.next();
                    if (!task.entity.isAlive() || task.remaining == 0) {
                        tasks.remove();
                        continue;
                    }
                    if (++task.ticks < task.periodTicks) {
                        continue;
                    }
                    task.ticks = 0;
                    if (task.remaining != INFINITE) {
                        task.remaining--;
                    }
                    due.add(task);
                    if (task.remaining == 0) {
                        tasks.remove();
                    }
                }
            }
            for (PendingTask task : due) {
                task.action.accept(task.entity);
            }
        }

        private static final class PendingTask {
            private final LivingEntity entity;
            private final String taskId;
            private final int periodTicks;
            private final Consumer<LivingEntity> action;
            private int remaining;
            private int ticks;

            private PendingTask(LivingEntity entity, String taskId, int periodTicks,
                                int repetitions, Consumer<LivingEntity> action) {
                this.entity = entity;
                this.taskId = taskId;
                this.periodTicks = periodTicks;
                this.remaining = repetitions;
                this.action = action;
            }

            private boolean matches(LivingEntity other, String otherTaskId) {
                return this.entity == other && this.taskId.equals(otherTaskId);
            }
        }
    }
}
