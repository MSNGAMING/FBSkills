package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Event listener managing geological earth extraction and Excavation skill progression.
 */
public class ExcavationListener implements Listener {

    private final FBSkillsPlugin plugin;

    public ExcavationListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        Material mat = block.getType();

        // Anti-Exploit
        if (plugin.getBlockTracker().isPlayerPlaced(block)) {
            plugin.getBlockTracker().unregister(block);
            return;
        }

        double xp = plugin.getConfigManager().getXpReward(SkillType.EXCAVATION, mat.name());
        if (xp <= 0.0) {
            return;
        }

        plugin.awardXp(player, SkillType.EXCAVATION, xp);

        // Passive Perk: Excavation Archeology / Treasure Discovery
        PlayerProfile profile = plugin.getStorageManager().getProfile(player.getUniqueId());
        if (profile != null && profile.getLevel(SkillType.EXCAVATION) >= 10) {
            double chance = Math.min(0.12, 0.02 + (profile.getLevel(SkillType.EXCAVATION) * 0.001));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                Material rewardMat = switch (ThreadLocalRandom.current().nextInt(5)) {
                    case 0 -> Material.BONE;
                    case 1 -> Material.GUNPOWDER;
                    case 2 -> Material.IRON_NUGGET;
                    case 3 -> Material.GOLD_NUGGET;
                    default -> Material.EMERALD;
                };

                final Location dropLoc = block.getLocation().clone();
                plugin.getSchedulerAdapter().runLocation(dropLoc, () -> {
                    if (dropLoc.getWorld() != null) {
                        dropLoc.getWorld().dropItemNaturally(dropLoc, new ItemStack(rewardMat, 1));
                    }
                });
            }
        }
    }
}
