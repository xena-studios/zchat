package co.xenastudios.zchat.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Thread-correctness helper for Paper <b>and</b> Folia.
 *
 * <p>On Folia every entity is owned by a region thread and must only be touched from
 * that thread; on regular Paper everything runs on the main thread. This helper hides
 * the difference: {@link #onEntity} runs a mutation on the entity's owning region
 * thread (Folia) or the main thread (Paper), executing inline when we're already on
 * the correct thread. {@link #global} runs region-agnostic work (e.g. broadcasting a
 * cleared chat) on the global region scheduler (Folia) or the main thread (Paper).
 *
 * <p>Detection is a one-time class-presence check; there is no per-call reflection and
 * no always-on work.
 */
public final class Schedulers {

    private static final boolean FOLIA = detectFolia();

    private Schedulers() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when running on a Folia (regionised) server. */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Run {@code task}, which touches {@code entity}, on the entity's owning thread.
     * On Folia this is the entity's region thread via its {@code EntityScheduler}; on
     * Paper it is the main thread (executed inline when already on it). Failures are
     * swallowed so a scheduling hiccup can never bubble out.
     */
    public static void onEntity(Plugin plugin, Entity entity, Runnable task) {
        try {
            if (FOLIA) {
                entity.getScheduler().run(plugin, scheduled -> task.run(), null);
            } else if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to schedule an entity task: " + t.getMessage());
        }
    }

    /**
     * Run {@code task} on a region-agnostic global thread (Folia global region
     * scheduler) or the main thread (Paper). Used for work not tied to one entity.
     */
    public static void global(Plugin plugin, Runnable task) {
        try {
            if (FOLIA) {
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
            } else if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to schedule a global task: " + t.getMessage());
        }
    }
}
