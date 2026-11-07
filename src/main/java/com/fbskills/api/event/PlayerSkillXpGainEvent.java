package com.fbskills.api.event;

import com.fbskills.model.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player earns skill XP.
 * Cancellable and supports modifying the XP amount.
 */
public class PlayerSkillXpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillType skillType;
    private double amount;
    private boolean cancelled;

    public PlayerSkillXpGainEvent(@NotNull Player player, @NotNull SkillType skillType, double amount) {
        super(player);
        this.skillType = skillType;
        this.amount = amount;
    }

    public @NotNull SkillType getSkillType() {
        return skillType;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = Math.max(0.0, amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
