package com.fbskills.model;

import java.util.Objects;

/**
 * Tracks the level and current XP progress for a single skill.
 */
public class SkillProgress {

    private final SkillType skillType;
    private int level;
    private double currentXp;

    public SkillProgress(SkillType skillType) {
        this(skillType, 1, 0.0);
    }

    public SkillProgress(SkillType skillType, int level, double currentXp) {
        this.skillType = Objects.requireNonNull(skillType, "skillType cannot be null");
        this.level = Math.max(1, level);
        this.currentXp = Math.max(0.0, currentXp);
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public synchronized int getLevel() {
        return level;
    }

    public synchronized void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public synchronized double getCurrentXp() {
        return currentXp;
    }

    public synchronized void setCurrentXp(double currentXp) {
        this.currentXp = Math.max(0.0, currentXp);
    }

    /**
     * Calculates the XP required to advance from the given level to the next level.
     * Formula: base * (level ^ multiplier)
     */
    public static double calculateRequiredXp(int level, double base, double multiplier) {
        if (level < 1) level = 1;
        return Math.floor(base * Math.pow(level, multiplier));
    }

    public synchronized double getRequiredXp(double base, double multiplier) {
        return calculateRequiredXp(this.level, base, multiplier);
    }

    /**
     * Adds XP and returns the number of levels gained.
     */
    public synchronized int addXp(double amount, double base, double multiplier, int maxLevel) {
        if (amount <= 0.0 || this.level >= maxLevel) {
            return 0;
        }

        this.currentXp += amount;
        int levelsGained = 0;

        while (this.level < maxLevel) {
            double needed = calculateRequiredXp(this.level, base, multiplier);
            if (this.currentXp >= needed) {
                this.currentXp -= needed;
                this.level++;
                levelsGained++;
            } else {
                break;
            }
        }

        if (this.level >= maxLevel) {
            this.level = maxLevel;
            this.currentXp = 0.0;
        }

        return levelsGained;
    }

    /**
     * Returns the completion ratio between 0.0 and 1.0 towards the next level.
     */
    public synchronized double getProgressRatio(double base, double multiplier, int maxLevel) {
        if (this.level >= maxLevel) {
            return 1.0;
        }
        double needed = getRequiredXp(base, multiplier);
        if (needed <= 0.0) return 1.0;
        return Math.min(1.0, Math.max(0.0, this.currentXp / needed));
    }
}
