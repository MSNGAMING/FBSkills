package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Event listener managing arcane enhancements and Enchanting skill progression.
 * Hardened against grindstone click-spamming without item extraction.
 */
public class EnchantingListener implements Listener {

    private final FBSkillsPlugin plugin;

    public EnchantingListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        int levelCost = event.getExpLevelCost();
        double multiplier = plugin.getConfigManager().getXpReward(SkillType.ENCHANTING, "XP_COST_MULTIPLIER");
        if (multiplier <= 0) multiplier = 18.0;

        double xp = levelCost * multiplier;
        plugin.awardXp(player, SkillType.ENCHANTING, xp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrindstoneDisenchant(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isValidPickup(event.getAction())) {
            return;
        }

        if (event.getInventory() instanceof GrindstoneInventory grindstone && event.getRawSlot() == 2) {
            ItemStack result = grindstone.getItem(2);
            if (result != null && result.getType() != Material.AIR) {
                double xp = plugin.getConfigManager().getXpReward(SkillType.ENCHANTING, "GRINDSTONE_DISENCHANT");
                if (xp <= 0) xp = 15.0;
                plugin.awardXp(player, SkillType.ENCHANTING, xp);
            }
        }
    }

    private boolean isValidPickup(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL ||
                action == InventoryAction.PICKUP_HALF ||
                action == InventoryAction.PICKUP_ONE ||
                action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                action == InventoryAction.HOTBAR_SWAP;
    }
}
