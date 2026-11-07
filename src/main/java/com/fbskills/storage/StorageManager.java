package com.fbskills.storage;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.LeaderboardEntry;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * High-performance orchestrator handling player memory cache, asynchronous loading,
 * dirty flush auto-saves, and cached leaderboards.
 */
public class StorageManager {

    private final FBSkillsPlugin plugin;
    private StorageEngine storageEngine;
    private final Map<UUID, PlayerProfile> activeProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> pendingProfiles = new ConcurrentHashMap<>();
    private final Map<SkillType, CachedLeaderboard> leaderboardCache = new ConcurrentHashMap<>();

    private record CachedLeaderboard(List<LeaderboardEntry> entries, long timestamp) {}

    public StorageManager(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String type = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase(Locale.ROOT);
        try {
            switch (type) {
                case "MYSQL" -> {
                    plugin.getLogger().info("Connecting to MySQL Database via HikariCP...");
                    this.storageEngine = new SqlStorage(plugin, true);
                }
                case "YAML" -> {
                    plugin.getLogger().info("Initializing YAML Flat-File User Storage...");
                    this.storageEngine = new YamlStorage(plugin);
                }
                case "SQLITE" -> {
                    plugin.getLogger().info("Initializing SQLite Embedded Database via HikariCP...");
                    this.storageEngine = new SqlStorage(plugin, false);
                }
                default -> {
                    plugin.getLogger().warning("Unknown storage type '" + type + "'. Defaulting to SQLITE.");
                    this.storageEngine = new SqlStorage(plugin, false);
                }
            }
            this.storageEngine.init();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize storage engine! Falling back to YAML.", e);
            this.storageEngine = new YamlStorage(plugin);
            try {
                this.storageEngine.init();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize fallback YAML storage!", ex);
            }
        }

        // Schedule periodic auto-save for dirty profiles
        long autoSaveSeconds = plugin.getConfig().getLong("cache.auto_save_interval_seconds", 300);
        long ticks = autoSaveSeconds * 20L;
        plugin.getSchedulerAdapter().runAsyncRepeating(this::saveDirtyProfiles, ticks, ticks);
    }

    /**
     * Preloads the player profile during AsyncPlayerPreLoginEvent.
     */
    public CompletableFuture<PlayerProfile> preloadProfile(UUID uuid, String username) {
        return storageEngine.loadProfile(uuid, username).thenApply(profile -> {
            pendingProfiles.put(uuid, profile);
            return profile;
        });
    }

    /**
     * Promotes preloaded profile to active profile cache on player join.
     */
    public void activateProfile(UUID uuid, String username) {
        PlayerProfile profile = pendingProfiles.remove(uuid);
        if (profile == null) {
            // Fallback in case preloading was skipped
            storageEngine.loadProfile(uuid, username).thenAccept(loaded -> {
                activeProfiles.put(uuid, loaded);
            });
        } else {
            profile.setUsername(username);
            activeProfiles.put(uuid, profile);
        }
    }

    /**
     * Instant thread-safe lookup from memory cache.
     */
    public PlayerProfile getProfile(UUID uuid) {
        return activeProfiles.get(uuid);
    }

    public Optional<PlayerProfile> findProfile(UUID uuid) {
        return Optional.ofNullable(activeProfiles.get(uuid));
    }

    /**
     * Saves and removes profile from memory cache when player quits.
     */
    public CompletableFuture<Void> saveAndUnload(UUID uuid) {
        PlayerProfile profile = activeProfiles.remove(uuid);
        pendingProfiles.remove(uuid);
        if (profile != null) {
            return storageEngine.saveProfile(profile);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Flushes all dirty profiles asynchronously.
     */
    public void saveDirtyProfiles() {
        int saved = 0;
        for (PlayerProfile profile : activeProfiles.values()) {
            if (profile.isDirty()) {
                storageEngine.saveProfile(profile);
                saved++;
            }
        }
        if (saved > 0) {
            plugin.getLogger().fine("Auto-saved " + saved + " modified player skill profiles.");
        }
    }

    /**
     * Flushes all active profiles synchronously or blocking during server shutdown.
     */
    public void saveAllBlocking() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PlayerProfile profile : activeProfiles.values()) {
            futures.add(storageEngine.saveProfile(profile));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
            plugin.getLogger().info("Successfully saved all player skill profiles to disk.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while saving player profiles on shutdown", e);
        }
    }

    /**
     * Retrieves top players with high-speed memory caching.
     */
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(SkillType skill, int limit) {
        long cacheTtl = plugin.getConfig().getLong("cache.leaderboard_cache_seconds", 120) * 1000L;
        CachedLeaderboard cached = leaderboardCache.get(skill);

        if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < cacheTtl) {
            return CompletableFuture.completedFuture(cached.entries());
        }

        return storageEngine.getLeaderboard(skill, limit).thenApply(list -> {
            leaderboardCache.put(skill, new CachedLeaderboard(list, System.currentTimeMillis()));
            return list;
        });
    }

    public void invalidateLeaderboardCache() {
        leaderboardCache.clear();
    }

    public void shutdown() {
        saveAllBlocking();
        activeProfiles.clear();
        pendingProfiles.clear();
        leaderboardCache.clear();
        if (storageEngine != null) {
            storageEngine.close();
        }
    }
}
