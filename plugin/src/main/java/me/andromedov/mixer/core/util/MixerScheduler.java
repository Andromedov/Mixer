package me.andromedov.mixer.core.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.andromedov.mixer.core.MixerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Routes work through the scheduler that owns the Bukkit state it touches.
 * Paper implements these schedulers as main-thread tasks while Folia routes
 * them to the appropriate global, region, entity, or asynchronous executor.
 */
public final class MixerScheduler {
    private final MixerPlugin plugin;

    public MixerScheduler(MixerPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public ScheduledTask runAsync(Runnable action) {
        return plugin.getServer().getAsyncScheduler().runNow(plugin, task -> action.run());
    }

    public ScheduledTask runAsyncLater(Runnable action, long delayTicks) {
        return plugin.getServer().getAsyncScheduler().runDelayed(
                plugin, task -> action.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runGlobal(Runnable action) {
        return plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> action.run());
    }

    public ScheduledTask runAt(Location location, Runnable action) {
        requireWorld(location);
        return plugin.getServer().getRegionScheduler().run(plugin, location, task -> action.run());
    }

    public ScheduledTask runAtLater(Location location, Runnable action, long delayTicks) {
        requireWorld(location);
        return plugin.getServer().getRegionScheduler().runDelayed(
                plugin, location, task -> action.run(), delayTicks);
    }

    public ScheduledTask runFor(Entity entity, Runnable action, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        return entity.getScheduler().run(plugin, task -> action.run(), retired);
    }

    public ScheduledTask runForAtFixedRate(Entity entity, Runnable action, Runnable retired,
                                            long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        return entity.getScheduler().runAtFixedRate(
                plugin, task -> action.run(), retired, initialDelayTicks, periodTicks);
    }

    public static void requireOwned(Location location, String action) {
        requireWorld(location);
        if (!Bukkit.isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException("Must " + action + " on the owning region thread");
        }
    }

    public static void requireOwned(Entity entity, String action) {
        Objects.requireNonNull(entity, "entity");
        if (!Bukkit.isOwnedByCurrentRegion(entity)) {
            throw new IllegalStateException("Must " + action + " on the entity's owning region thread");
        }
    }

    public static void requireTickThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Must " + action + " on a server tick thread");
        }
    }

    public static void requireGlobalThread(String action) {
        if (!Bukkit.isGlobalTickThread()) {
            throw new IllegalStateException("Must " + action + " on the global region thread");
        }
    }

    public static boolean isTickThread() {
        return Bukkit.isPrimaryThread();
    }

    private static void requireWorld(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its world must not be null");
        }
    }
}
