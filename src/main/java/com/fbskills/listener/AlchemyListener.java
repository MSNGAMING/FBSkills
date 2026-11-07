package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * Event listener managing potion synthesis, elixirs, and Alchemy skill progression.
 * Hardened with PDC tagging to prevent infinite XP from re-inserting existing potions.
 */
public class AlchemyListener implements Listener {

    private final FBSkillsPlugin plugin;
    private final NamespacedKey keyBrewed;

    public AlchemyListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
        this.keyBrewed = new NamespacedKey(plugin, "freshly_brewed");
    }

    /**
     * Tags potions newly synthesized by the brewing stand with PDC metadata.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        BrewerInventory inv = event.getContents();
        for (int i = 0; i < 3; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(keyBrewed, PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Awards XP strictly upon first retrieval of a freshly brewed potion.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionExtract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isValidPickup(event.getAction())) {
            return;
        }

        if (event.getInventory() instanceof BrewerInventory && (event.getRawSlot() == 0 || event.getRawSlot() == 1 || event.getRawSlot() == 2)) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;

            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(keyBrewed, PersistentDataType.BYTE)) {
                return;
            }

            // Remove marker so it can never award XP again
            meta.getPersistentDataContainer().remove(keyBrewed);
            item.setItemMeta(meta);

            Material mat = item.getType();
            if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
                if (meta instanceof PotionMeta potionMeta) {
                    PotionType baseType = potionMeta.getBasePotionType();
                    if (baseType != null && baseType != PotionType.WATER && baseType != PotionType.AWKWARD && baseType != PotionType.MUNDANE && baseType != PotionType.THICK) {
                        double xp = plugin.getConfigManager().getXpReward(SkillType.ALCHEMY, "POTION_BREWED");
                        if (xp <= 0) xp = 45.0;
                        plugin.awardXp(player, SkillType.ALCHEMY, xp);
                    }
                }
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
