package com.fbskills.gui;

import com.fbskills.model.SkillType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Custom InventoryHolder identifying the skill detail/milestones GUI.
 */
public class SkillDetailGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final SkillType skill;
    private Inventory inventory;

    public SkillDetailGuiHolder(UUID playerUuid, SkillType skill) {
        this.playerUuid = playerUuid;
        this.skill = skill;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public SkillType getSkill() {
        return skill;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
