package com.fbskills.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Detects the runtime server environment, constructs the optimal scheduler adapter,
 * and provides thread region checks for Folia and Paper.
 */
public final class PlatformScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private PlatformScheduler() {}

    public static SchedulerAdapter create(Plugin plugin) {
        if (IS_FOLIA) {
            plugin.getLogger().info("Detected Folia server environment: Using Region & Entity Schedulers.");
            return new FoliaScheduler(plugin);
        } else {
            plugin.getLogger().info("Detected Paper/Purpur server environment: Using Bukkit Schedulers.");
            return new PaperScheduler(plugin);
        }
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Checks if the current thread owns the region of the given location.
     */
    public static boolean isOwnedByCurrentRegion(Location location) {
        if (!IS_FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    /**
     * Checks if the current thread owns the given entity.
     */
    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (!IS_FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        if (entity == null || !entity.isValid()) {
            return false;
        }
        return Bukkit.isOwnedByCurrentRegion(entity);
    }
}
