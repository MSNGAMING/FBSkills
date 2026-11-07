package com.fbskills.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia scheduler implementation utilizing region and entity schedulers with inline fast-paths.
 */
public class FoliaScheduler implements SchedulerAdapter {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    @Override
    public void runAsyncDelayed(Runnable runnable, long delayTicks) {
        long delayMillis = Math.max(1L, delayTicks * 50L);
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runAsyncRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        long initialDelayMillis = Math.max(1L, delayTicks * 50L);
        long periodMillis = Math.max(1L, periodTicks * 50L);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runEntity(Entity entity, Consumer<Entity> action) {
        if (entity == null || !entity.isValid()) return;
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            action.accept(entity);
            return;
        }
        entity.getScheduler().run(plugin, task -> action.accept(entity), null);
    }

    @Override
    public void runEntityDelayed(Entity entity, Consumer<Entity> action, long delayTicks) {
        if (entity == null || !entity.isValid()) return;
        entity.getScheduler().runDelayed(plugin, task -> action.accept(entity), null, Math.max(1L, delayTicks));
    }

    @Override
    public void runLocation(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) return;
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            runnable.run();
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, location, task -> runnable.run());
    }

    @Override
    public void runGlobal(Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
    }

    @Override
    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
