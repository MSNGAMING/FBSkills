package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.Set;

/**
 * Event listener managing smithing, anvil repairs, crafting, and Forging skill progression.
 * Hardened against crafting recursion (iron ingot/block looping) and unpaid anvil spam.
 */
public class ForgingListener implements Listener {

    private final FBSkillsPlugin plugin;

    private static final Set<Material> CRAFTABLE_DIAMOND_GEAR = Set.of(
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE, Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
    );

    private static final Set<Material> CRAFTABLE_IRON_GEAR = Set.of(
            Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
            Material.IRON_SHOVEL, Material.IRON_HOE, Material.IRON_HELMET,
            Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.SHIELD, Material.CROSSBOW, Material.SHEARS
    );

    public ForgingListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isValidPickup(event.getAction())) {
            return;
        }

        // Anvil repair / combine at result slot 2
        if (event.getInventory() instanceof AnvilInventory anvil && event.getRawSlot() == 2) {
            ItemStack result = anvil.getItem(2);
            if (result != null && result.getType() != Material.AIR) {
                // Ensure player can afford the anvil repair cost
                if (player.getLevel() < anvil.getRepairCost()) {
                    return;
                }

                double xp = plugin.getConfigManager().getXpReward(SkillType.FORGING, "ANVIL_REPAIR_BASE");
                if (xp <= 0) xp = 50.0;
                plugin.awardXp(player, SkillType.FORGING, xp);
            }
            return;
        }

        // Smithing Table upgrade at result slot 3
        if (event.getInventory() instanceof SmithingInventory smithing && event.getRawSlot() == 3) {
            ItemStack result = smithing.getItem(3);
            if (result != null && result.getType() != Material.AIR) {
                double xp = plugin.getConfigManager().getXpReward(SkillType.FORGING, "SMITHING_NETHERITE_UPGRADE");
                if (xp <= 0) xp = 250.0;
                plugin.awardXp(player, SkillType.FORGING, xp);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isValidPickup(event.getAction())) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        Material mat = result.getType();

        // Anti-Exploit: strictly award XP only to actual forged gear items (no ingots/blocks/nuggets)
        if (CRAFTABLE_DIAMOND_GEAR.contains(mat)) {
            double xp = plugin.getConfigManager().getXpReward(SkillType.FORGING, "CRAFT_DIAMOND_GEAR");
            if (xp <= 0) xp = 80.0;
            plugin.awardXp(player, SkillType.FORGING, xp);
        } else if (CRAFTABLE_IRON_GEAR.contains(mat)) {
            double xp = plugin.getConfigManager().getXpReward(SkillType.FORGING, "CRAFT_IRON_GEAR");
            if (xp <= 0) xp = 25.0;
            plugin.awardXp(player, SkillType.FORGING, xp);
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
