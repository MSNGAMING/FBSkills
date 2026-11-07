package com.fbskills.storage;

import com.fbskills.model.LeaderboardEntry;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * YAML flat-file storage implementation for lightweight server deployments.
 */
public class YamlStorage implements StorageEngine {

    private final Plugin plugin;
    private final File userFolder;
    private final ExecutorService ioExecutor;

    public YamlStorage(Plugin plugin) {
        this.plugin = plugin;
        this.userFolder = new File(plugin.getDataFolder(), "userdata");
        this.ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FBSkills-Yaml-IO");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void init() {
        if (!userFolder.exists() && !userFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create userdata directory for YAML storage!");
        }
    }

    @Override
    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid, String username) {
        return CompletableFuture.supplyAsync(() -> {
            File file = new File(userFolder, uuid.toString() + ".yml");
            PlayerProfile profile = new PlayerProfile(uuid, username);

            if (!file.exists()) {
                return profile;
            }

            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String savedName = yaml.getString("username", username);
                profile.setUsername(savedName);

                for (SkillType type : SkillType.values()) {
                    String path = "skills." + type.getId();
                    if (yaml.contains(path)) {
                        int level = yaml.getInt(path + ".level", 1);
                        double xp = yaml.getDouble(path + ".xp", 0.0);
                        profile.setLevel(type, level);
                        profile.setXp(type, xp);
                    }
                }
                profile.clearDirty();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load YAML profile for " + uuid, e);
            }

            return profile;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        long snapshotRev = profile.getDirtyRevision();
        Map<SkillType, PlayerProfile.SkillSnapshot> snapshot = profile.createSnapshot();
        String username = profile.getUsername();
        UUID uuid = profile.getUuid();

        return CompletableFuture.runAsync(() -> {
            File file = new File(userFolder, uuid.toString() + ".yml");
            YamlConfiguration yaml = new YamlConfiguration();

            yaml.set("uuid", uuid.toString());
            yaml.set("username", username);
            yaml.set("last_seen", System.currentTimeMillis());

            for (Map.Entry<SkillType, PlayerProfile.SkillSnapshot> entry : snapshot.entrySet()) {
                String path = "skills." + entry.getKey().getId();
                yaml.set(path + ".level", entry.getValue().level());
                yaml.set(path + ".xp", entry.getValue().xp());
            }

            try {
                yaml.save(file);
                profile.markSaved(snapshotRev);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save YAML profile for " + uuid, e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(SkillType skill, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            File[] files = userFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return list;

            for (File f : files) {
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                    String uuidStr = yaml.getString("uuid", f.getName().replace(".yml", ""));
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = yaml.getString("username", "Unknown");
                    int level = yaml.getInt("skills." + skill.getId() + ".level", 1);
                    double xp = yaml.getDouble("skills." + skill.getId() + ".xp", 0.0);
                    list.add(new LeaderboardEntry(uuid, name, level, xp, 0));
                } catch (Exception ignored) {}
            }

            list.sort((a, b) -> {
                int cmp = Integer.compare(b.level(), a.level());
                return cmp != 0 ? cmp : Double.compare(b.xp(), a.xp());
            });

            List<LeaderboardEntry> ranked = new ArrayList<>();
            int rank = 1;
            for (LeaderboardEntry entry : list) {
                if (rank > limit) break;
                ranked.add(new LeaderboardEntry(entry.uuid(), entry.username(), entry.level(), entry.xp(), rank++));
            }
            return ranked;
        }, ioExecutor);
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
