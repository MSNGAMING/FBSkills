package com.fbskills.api.event;

import com.fbskills.model.SkillReward;
import com.fbskills.model.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Fired when a player advances to a new skill level.
 */
public class PlayerSkillLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillType skillType;
    private final int oldLevel;
    private final int newLevel;
    private final List<SkillReward> rewards;

    public PlayerSkillLevelUpEvent(@NotNull Player player, @NotNull SkillType skillType, int oldLevel, int newLevel, List<SkillReward> rewards) {
        super(player);
        this.skillType = skillType;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.rewards = rewards != null ? List.copyOf(rewards) : Collections.emptyList();
    }

    public @NotNull SkillType getSkillType() {
        return skillType;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public @NotNull List<SkillReward> getRewards() {
        return rewards;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
