package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Highly optimized listener for Mining skill progression with anti-exploit verification.
 */
public class MiningListener implements Listener {

    private final FBSkillsPlugin plugin;

    public MiningListener(FBSkillsPlugin plugin) {
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

        // Anti-Exploit: Disallow XP from player-placed blocks
        if (plugin.getBlockTracker().isPlayerPlaced(block)) {
            plugin.getBlockTracker().unregister(block);
            return;
        }

        double xp = plugin.getConfigManager().getXpReward(SkillType.MINING, mat.name());
        if (xp <= 0.0) {
            return;
        }

        // Check Silk Touch exploit prevention
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            if (!plugin.getConfigManager().isAllowSilkTouchOreXp()) {
                xp = 0.0;
            } else {
                xp *= 0.5;
            }
        }

        if (xp > 0.0) {
            plugin.awardXp(player, SkillType.MINING, xp);
        }

        // Passive Perk: Double Ore Drop Chance for high level miners
        PlayerProfile profile = plugin.getStorageManager().getProfile(player.getUniqueId());
        if (profile != null && profile.getLevel(SkillType.MINING) >= 10) {
            double chance = Math.min(0.35, 0.05 + (profile.getLevel(SkillType.MINING) * 0.003));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                final Location dropLoc = block.getLocation().clone();
                final Collection<ItemStack> drops = block.getDrops(tool, player);
                plugin.getSchedulerAdapter().runLocation(dropLoc, () -> {
                    if (dropLoc.getWorld() != null) {
                        for (ItemStack drop : drops) {
                            dropLoc.getWorld().dropItemNaturally(dropLoc, drop.clone());
                        }
                    }
                });
            }
        }
    }
}
