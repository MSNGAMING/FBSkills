package com.fbskills.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

/**
 * Platform-independent scheduler abstraction supporting Paper, Purpur, and Folia.
 */
public interface SchedulerAdapter {

    /**
     * Executes a task asynchronously immediately.
     */
    void runAsync(Runnable runnable);

    /**
     * Executes a task asynchronously after a specified delay in ticks.
     */
    void runAsyncDelayed(Runnable runnable, long delayTicks);

    /**
     * Executes a task asynchronously on a repeating schedule.
     */
    void runAsyncRepeating(Runnable runnable, long delayTicks, long periodTicks);

    /**
     * Executes a task on the thread context bound to a specific entity.
     * On Folia, this executes via EntityScheduler. On Paper, executes on the main thread.
     */
    void runEntity(Entity entity, Consumer<Entity> action);

    /**
     * Executes a task on the thread context bound to a specific entity after a delay in ticks.
     */
    void runEntityDelayed(Entity entity, Consumer<Entity> action, long delayTicks);

    /**
     * Executes a task synchronized with the chunk/region of the given location.
     */
    void runLocation(Location location, Runnable runnable);

    /**
     * Executes a task synchronized with the global region (or main thread on Paper).
     */
    void runGlobal(Runnable runnable);

    /**
     * Cancels all scheduled tasks created by this adapter.
     */
    void cancelAll();
}
