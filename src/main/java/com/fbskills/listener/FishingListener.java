package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Event listener managing aquatic captures and Fishing skill progression.
 */
public class FishingListener implements Listener {

    private final FBSkillsPlugin plugin;

    public FishingListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }

        ItemStack itemStack = caughtItem.getItemStack();
        Material mat = itemStack.getType();

        double xp = plugin.getConfigManager().getXpReward(SkillType.FISHING, mat.name());
        if (xp <= 0.0) {
            if (isTreasure(mat)) {
                xp = plugin.getConfigManager().getXpReward(SkillType.FISHING, "TREASURE");
                if (xp <= 0) xp = 150.0;
            } else {
                xp = plugin.getConfigManager().getXpReward(SkillType.FISHING, "JUNK");
                if (xp <= 0) xp = 15.0;
            }
        }

        plugin.awardXp(player, SkillType.FISHING, xp);

        // Passive Perk: Master Angler Double Catch
        PlayerProfile profile = plugin.getStorageManager().getProfile(player.getUniqueId());
        if (profile != null && profile.getLevel(SkillType.FISHING) >= 10) {
            double chance = Math.min(0.35, 0.08 + (profile.getLevel(SkillType.FISHING) * 0.003));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                final Location dropLoc = player.getLocation().clone();
                final ItemStack dropStack = itemStack.clone();
                plugin.getSchedulerAdapter().runLocation(dropLoc, () -> {
                    if (dropLoc.getWorld() != null) {
                        dropLoc.getWorld().dropItemNaturally(dropLoc, dropStack);
                    }
                });
            }
        }
    }

    private boolean isTreasure(Material mat) {
        return mat == Material.ENCHANTED_BOOK ||
                mat == Material.BOW ||
                mat == Material.FISHING_ROD ||
                mat == Material.NAME_TAG ||
                mat == Material.NAUTILUS_SHELL ||
                mat == Material.SADDLE;
    }
}
