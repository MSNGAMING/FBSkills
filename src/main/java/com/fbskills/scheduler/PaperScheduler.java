package com.fbskills.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Standard Paper & Purpur scheduler implementation utilizing BukkitScheduler.
 */
public class PaperScheduler implements SchedulerAdapter {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @Override
    public void runAsyncDelayed(Runnable runnable, long delayTicks) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, Math.max(1L, delayTicks));
    }

    @Override
    public void runAsyncRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    @Override
    public void runEntity(Entity entity, Consumer<Entity> action) {
        if (entity == null || !entity.isValid()) return;
        if (Bukkit.isPrimaryThread()) {
            action.accept(entity);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) {
                action.accept(entity);
            }
        });
    }

    @Override
    public void runEntityDelayed(Entity entity, Consumer<Entity> action, long delayTicks) {
        if (entity == null || !entity.isValid()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                action.accept(entity);
            }
        }, Math.max(1L, delayTicks));
    }

    @Override
    public void runLocation(Location location, Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void runGlobal(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
