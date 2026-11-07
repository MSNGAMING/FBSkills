package com.fbskills.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Custom InventoryHolder identifying the main skills overview GUI.
 */
public class SkillsGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private Inventory inventory;

    public SkillsGuiHolder(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
