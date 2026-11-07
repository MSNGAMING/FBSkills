package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.util.TextUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles asynchronous profile loading, memory cache injection, clean unloads,
 * and block placement/piston movement tracking for anti-exploit protections.
 */
public class PlayerConnectionListener implements Listener {

    private final FBSkillsPlugin plugin;

    public PlayerConnectionListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        // P0 Fix: Synchronously await the asynchronous preload with a safe timeout
        try {
            plugin.getStorageManager().preloadProfile(event.getUniqueId(), event.getName())
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load skills profile during pre-login for " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    TextUtil.parse("<red>Failed to load your skills data. Please reconnect.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStorageManager().activateProfile(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStorageManager().saveAndUnload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlockPlaced();
        Material mat = block.getType();

        if (isTrackable(mat)) {
            plugin.getBlockTracker().track(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.getConfigManager().isTrackPistonMovements()) return;

        BlockFace dir = event.getDirection();
        List<Block> blocks = event.getBlocks();
        // Process in reverse order so destinations don't overwrite source checks
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block b = blocks.get(i);
            if (plugin.getBlockTracker().isPlayerPlaced(b)) {
                plugin.getBlockTracker().shift(
                        b.getWorld().getUID(),
                        b.getX(), b.getY(), b.getZ(),
                        b.getX() + dir.getModX(), b.getY() + dir.getModY(), b.getZ() + dir.getModZ()
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.getConfigManager().isTrackPistonMovements()) return;

        BlockFace dir = event.getDirection();
        for (Block b : event.getBlocks()) {
            if (plugin.getBlockTracker().isPlayerPlaced(b)) {
                plugin.getBlockTracker().shift(
                        b.getWorld().getUID(),
                        b.getX(), b.getY(), b.getZ(),
                        b.getX() + dir.getModX(), b.getY() + dir.getModY(), b.getZ() + dir.getModZ()
                );
            }
        }
    }

    private boolean isTrackable(Material mat) {
        String name = mat.name();
        return name.endsWith("_ORE") ||
                name.contains("DEEPSLATE") ||
                mat == Material.ANCIENT_DEBRIS ||
                mat == Material.OBSIDIAN ||
                mat == Material.CLAY ||
                mat == Material.SUGAR_CANE ||
                mat == Material.CACTUS ||
                mat == Material.BAMBOO ||
                mat == Material.MELON ||
                mat == Material.PUMPKIN;
    }
}
