package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event listener managing agricultural harvesting and Farming skill progression.
 */
public class FarmingListener implements Listener {

    private final FBSkillsPlugin plugin;

    public FarmingListener(FBSkillsPlugin plugin) {
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

        // Verify maturity for Ageable crops (Wheat, Carrots, Potatoes, Beetroots, Nether Wart, Cocoa)
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                return;
            }
        }

        double xp = plugin.getConfigManager().getXpReward(SkillType.FARMING, mat.name());
        if (xp <= 0.0) {
            return;
        }

        plugin.awardXp(player, SkillType.FARMING, xp);

        // Passive Perk: Double crop yield for experienced farmers
        PlayerProfile profile = plugin.getStorageManager().getProfile(player.getUniqueId());
        if (profile != null && profile.getLevel(SkillType.FARMING) >= 10) {
            double chance = Math.min(0.40, 0.10 + (profile.getLevel(SkillType.FARMING) * 0.003));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                final Location dropLoc = block.getLocation().clone();
                ItemStack handItem = player.getInventory().getItemInMainHand();
                final Collection<ItemStack> drops = block.getDrops(handItem, player);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getHarvestedBlock();
        double xp = plugin.getConfigManager().getXpReward(SkillType.FARMING, block.getType().name());
        if (xp > 0.0) {
            plugin.awardXp(player, SkillType.FARMING, xp);
        }
    }
}
