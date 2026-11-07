package com.fbskills.storage;

import com.fbskills.model.LeaderboardEntry;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Common contract for persistent storage engines (SQLite, MySQL, YAML).
 */
public interface StorageEngine {

    void init() throws Exception;

    CompletableFuture<PlayerProfile> loadProfile(UUID uuid, String username);

    CompletableFuture<Void> saveProfile(PlayerProfile profile);

    CompletableFuture<List<LeaderboardEntry>> getLeaderboard(SkillType skill, int limit);

    void close();
}
