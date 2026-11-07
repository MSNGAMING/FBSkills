package com.fbskills.model;

import java.util.UUID;

/**
 * High-speed immutable record representing a ranked leaderboard slot.
 */
public record LeaderboardEntry(
        UUID uuid,
        String username,
        int level,
        double xp,
        int rank
) {}
