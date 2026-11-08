package com.fbskills.storage;

import com.fbskills.model.LeaderboardEntry;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Enterprise-grade SQL storage engine backed by HikariCP connection pooling.
 * Compatible with SQLite and MySQL/MariaDB.
 */
public class SqlStorage implements StorageEngine {

    private final Plugin plugin;
    private final boolean isMySQL;
    private HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public SqlStorage(Plugin plugin, boolean isMySQL) {
        this.plugin = plugin;
        this.isMySQL = isMySQL;
        this.dbExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "FBSkills-DB-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void init() throws SQLException {
        HikariConfig config = new HikariConfig();
        FileConfiguration mainConfig = plugin.getConfig();

        if (isMySQL) {
            String host = mainConfig.getString("storage.mysql.host", "localhost");
            int port = mainConfig.getInt("storage.mysql.port", 3306);
            String db = mainConfig.getString("storage.mysql.database", "fbskills");
            String user = mainConfig.getString("storage.mysql.username", "root");
            String pass = mainConfig.getString("storage.mysql.password", "");
            boolean ssl = mainConfig.getBoolean("storage.mysql.ssl", false);

            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf8", host, port, db, ssl));
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(mainConfig.getInt("storage.mysql.pool.maximum-pool-size", 10));
            config.setMinimumIdle(mainConfig.getInt("storage.mysql.pool.minimum-idle", 3));
            config.setConnectionTimeout(mainConfig.getLong("storage.mysql.pool.connection-timeout-ms", 10000));
            config.setIdleTimeout(mainConfig.getLong("storage.mysql.pool.idle-timeout-ms", 600000));
            config.setMaxLifetime(mainConfig.getLong("storage.mysql.pool.max-lifetime-ms", 1800000));
            config.setPoolName("FBSkills-MySQL-Pool");
        } else {
            File dbFile = new File(plugin.getDataFolder(), "skills.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(4);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000);
            config.setPoolName("FBSkills-SQLite-Pool");
        }

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() throws SQLException {
        String createPlayers = "CREATE TABLE IF NOT EXISTS fbskills_players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(16) NOT NULL, " +
                "last_seen BIGINT NOT NULL" +
                ");";

        String createSkills = "CREATE TABLE IF NOT EXISTS fbskills_data (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "skill_type VARCHAR(32) NOT NULL, " +
                "level INT NOT NULL DEFAULT 1, " +
                "xp DOUBLE NOT NULL DEFAULT 0.0, " +
                "PRIMARY KEY (uuid, skill_type)" +
                ");";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_fbskill_leaderboard ON fbskills_data (skill_type, level DESC, xp DESC);";

        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(createPlayers);
            st.executeUpdate(createSkills);
            try {
                st.executeUpdate(createIndex);
            } catch (SQLException ignored) {
                // Some MySQL engines handle index already exists differently
            }
        }

        // Seamless legacy data migration from smp_skills_* if they exist
        try (Connection con = dataSource.getConnection()) {
            var meta = con.getMetaData();
            boolean hasLegacyPlayers = false;
            boolean hasLegacyData = false;

            try (ResultSet rs = meta.getTables(null, null, "smp_skills_players", null)) {
                if (rs.next()) hasLegacyPlayers = true;
            }
            try (ResultSet rs = meta.getTables(null, null, "smp_skills_data", null)) {
                if (rs.next()) hasLegacyData = true;
            }

            if (hasLegacyPlayers || hasLegacyData) {
                try (Statement st = con.createStatement()) {
                    if (hasLegacyPlayers) {
                        st.executeUpdate("INSERT OR IGNORE INTO fbskills_players SELECT * FROM smp_skills_players;");
                    }
                    if (hasLegacyData) {
                        st.executeUpdate("INSERT OR IGNORE INTO fbskills_data SELECT * FROM smp_skills_data;");
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid, String username) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = new PlayerProfile(uuid, username);
            String selectPlayer = "SELECT username FROM fbskills_players WHERE uuid = ?;";
            String selectSkills = "SELECT skill_type, level, xp FROM fbskills_data WHERE uuid = ?;";

            try (Connection con = dataSource.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement(selectPlayer)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            profile.setUsername(rs.getString("username"));
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(selectSkills)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String skillName = rs.getString("skill_type");
                            int level = rs.getInt("level");
                            double xp = rs.getDouble("xp");
                            SkillType.fromId(skillName).ifPresent(type -> {
                                profile.setLevel(type, level);
                                profile.setXp(type, xp);
                            });
                        }
                    }
                }
                profile.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load SQL profile for UUID: " + uuid, e);
            }
            return profile;
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        long snapshotRev = profile.getDirtyRevision();
        Map<SkillType, PlayerProfile.SkillSnapshot> snapshot = profile.createSnapshot();
        String currentUsername = profile.getUsername();
        UUID uuid = profile.getUuid();

        return CompletableFuture.runAsync(() -> {
            String upsertPlayer;
            String upsertSkill;

            if (isMySQL) {
                upsertPlayer = "INSERT INTO fbskills_players (uuid, username, last_seen) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = VALUES(last_seen);";
                upsertSkill = "INSERT INTO fbskills_data (uuid, skill_type, level, xp) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp);";
            } else {
                upsertPlayer = "INSERT INTO fbskills_players (uuid, username, last_seen) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, last_seen = excluded.last_seen;";
                upsertSkill = "INSERT INTO fbskills_data (uuid, skill_type, level, xp) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid, skill_type) DO UPDATE SET level = excluded.level, xp = excluded.xp;";
            }

            try (Connection con = dataSource.getConnection()) {
                con.setAutoCommit(false);

                try (PreparedStatement ps = con.prepareStatement(upsertPlayer)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, currentUsername);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(upsertSkill)) {
                    for (Map.Entry<SkillType, PlayerProfile.SkillSnapshot> entry : snapshot.entrySet()) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, entry.getKey().getId());
                        ps.setInt(3, entry.getValue().level());
                        ps.setDouble(4, entry.getValue().xp());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                profile.markSaved(snapshotRev);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save SQL profile for UUID: " + uuid, e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(SkillType skill, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            String query = "SELECT p.uuid, p.username, d.level, d.xp FROM fbskills_data d " +
                    "INNER JOIN fbskills_players p ON d.uuid = p.uuid " +
                    "WHERE d.skill_type = ? " +
                    "ORDER BY d.level DESC, d.xp DESC " +
                    "LIMIT ?;";

            try (Connection con = dataSource.getConnection();
                 PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, skill.getId());
                ps.setInt(2, limit);

                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String username = rs.getString("username");
                        int level = rs.getInt("level");
                        double xp = rs.getDouble("xp");
                        leaderboard.add(new LeaderboardEntry(uuid, username, level, xp, rank++));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve leaderboard for skill: " + skill.getId(), e);
            }
            return leaderboard;
        }, dbExecutor);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
