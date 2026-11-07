package com.fbskills.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, thread-safe memory-cached profile representing a player's skill data.
 */
public class PlayerProfile {

    private final UUID uuid;
    private volatile String username;
    private final Map<SkillType, SkillProgress> skills = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong dirtyRevision = new AtomicLong(0);
    private final AtomicLong savedRevision = new AtomicLong(0);
    private volatile long lastActivityTimestamp;

    public record SkillSnapshot(int level, double xp) {}

    public PlayerProfile(UUID uuid, String username) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.username = username != null ? username : "Unknown";
        this.lastActivityTimestamp = System.currentTimeMillis();

        for (SkillType type : SkillType.values()) {
            this.skills.put(type, new SkillProgress(type));
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username != null && !username.isBlank() && !username.equals(this.username)) {
            this.username = username;
            markDirty();
        }
    }

    public SkillProgress getProgress(SkillType type) {
        return skills.computeIfAbsent(type, SkillProgress::new);
    }

    public int getLevel(SkillType type) {
        return getProgress(type).getLevel();
    }

    public double getXp(SkillType type) {
        return getProgress(type).getCurrentXp();
    }

    public int getTotalLevel() {
        int total = 0;
        for (SkillProgress progress : skills.values()) {
            total += progress.getLevel();
        }
        return total;
    }

    public double getTotalXp() {
        double total = 0.0;
        for (SkillProgress progress : skills.values()) {
            total += progress.getCurrentXp();
        }
        return total;
    }

    public int addXp(SkillType type, double amount, double base, double multiplier, int maxLevel) {
        int gained = getProgress(type).addXp(amount, base, multiplier, maxLevel);
        markDirty();
        this.lastActivityTimestamp = System.currentTimeMillis();
        return gained;
    }

    public void setLevel(SkillType type, int level) {
        getProgress(type).setLevel(level);
        markDirty();
        this.lastActivityTimestamp = System.currentTimeMillis();
    }

    public void setXp(SkillType type, double xp) {
        getProgress(type).setCurrentXp(xp);
        markDirty();
        this.lastActivityTimestamp = System.currentTimeMillis();
    }

    public Map<SkillType, SkillProgress> getAllSkills() {
        return Collections.unmodifiableMap(skills);
    }

    /**
     * Creates an immutable snapshot of all skill data with the current revision.
     */
    public Map<SkillType, SkillSnapshot> createSnapshot() {
        Map<SkillType, SkillSnapshot> snapshot = new EnumMap<>(SkillType.class);
        for (Map.Entry<SkillType, SkillProgress> entry : skills.entrySet()) {
            SkillProgress progress = entry.getValue();
            snapshot.put(entry.getKey(), new SkillSnapshot(progress.getLevel(), progress.getCurrentXp()));
        }
        return snapshot;
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markDirty() {
        dirtyRevision.incrementAndGet();
        dirty.set(true);
    }

    public long getDirtyRevision() {
        return dirtyRevision.get();
    }

    /**
     * Marks changes up to the given revision as successfully saved.
     * If newer modifications occurred concurrently, remains dirty.
     */
    public void markSaved(long savedRev) {
        savedRevision.updateAndGet(current -> Math.max(current, savedRev));
        if (savedRevision.get() >= dirtyRevision.get()) {
            dirty.set(false);
        }
    }

    public void clearDirty() {
        markSaved(dirtyRevision.get());
    }

    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }
}
