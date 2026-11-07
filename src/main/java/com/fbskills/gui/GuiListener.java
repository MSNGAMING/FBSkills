package com.fbskills.gui;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.config.ConfigManager;
import com.fbskills.model.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Modern inventory event listener ensuring absolute security against item theft or manipulation.
 */
public class GuiListener implements Listener {

    private final FBSkillsPlugin plugin;

    public GuiListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SkillsGuiHolder || holder instanceof SkillDetailGuiHolder)) {
            return;
        }

        // Cancel all clicks inside GUI to avoid picking or moving items
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder instanceof SkillsGuiHolder) {
            handleMainMenuClick(player, slot);
        } else if (holder instanceof SkillDetailGuiHolder detailHolder) {
            handleDetailMenuClick(player, detailHolder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SkillsGuiHolder || holder instanceof SkillDetailGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (slot == 40) {
            player.closeInventory();
            return;
        }

        for (SkillType type : SkillType.values()) {
            ConfigManager.SkillDefinition def = plugin.getConfigManager().getSkillDefinition(type);
            if (def != null && def.guiSlot() == slot) {
                plugin.getGuiManager().openSkillDetail(player, type);
                return;
            }
        }
    }

    private void handleDetailMenuClick(Player player, SkillDetailGuiHolder holder, int slot) {
        if (slot == 49) {
            plugin.getGuiManager().openMainMenu(player);
        }
    }
}
